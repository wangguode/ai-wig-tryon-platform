package com.wig.tryon.service;

import com.wig.tryon.model.FaceAnalysisResult;
import com.wig.tryon.model.TryonRequest;
import com.wig.tryon.model.TryonTask;
import com.wig.tryon.model.WigAsset;
import com.wig.tryon.model.WigProduct;
import com.wig.tryon.repository.CatalogRepository;
import com.wig.tryon.repository.SessionRepository;
import com.wig.tryon.repository.WigAssetRepository;
import com.wig.tryon.service.ai.AiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 试戴生成编排：提交异步任务 -> 轮询状态 -> 调整重生成。
 */
@Service
public class TryonService {

    private static final Logger log = LoggerFactory.getLogger(TryonService.class);

    private final AiService aiService;
    private final CatalogRepository catalogRepository;
    private final SessionRepository sessionRepository;
    private final StorageService storageService;
    private final WigAssetRepository wigAssetRepository;
    private final TaskExecutor executor;
    /**
     * 伪进度推进器：图像生成模型本身没有可靠的"百分比"事件，
     * 用按时间曲线平滑推进的方式给前端一个连续可感知的进度（封顶 92%，留 8% 给真实完成）。
     * daemon 线程，不阻塞 JVM 关闭。
     */
    private final ScheduledExecutorService progressTicker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "tryon-progress-tick");
        t.setDaemon(true);
        return t;
    });

    public TryonService(AiService aiService, CatalogRepository catalogRepository,
                        SessionRepository sessionRepository, StorageService storageService,
                        WigAssetRepository wigAssetRepository,
                        @Qualifier("tryonExecutor") TaskExecutor executor) {
        this.aiService = aiService;
        this.catalogRepository = catalogRepository;
        this.sessionRepository = sessionRepository;
        this.storageService = storageService;
        this.wigAssetRepository = wigAssetRepository;
        this.executor = executor;
    }

    public Map<String, Object> submit(TryonRequest request) {
        SessionRepository.Session session = sessionRepository.getSession(request.sessionId);
        if (session == null) {
            throw new IllegalArgumentException("会话已过期，请重新上传照片");
        }
        if (PhotoService.FACE_FAILED.equals(session.faceCheckStatus)) {
            throw new IllegalArgumentException(session.faceCheckMessage != null
                    ? session.faceCheckMessage
                    : "未检测到清晰的正面人脸，请重新拍摄");
        }
        if (PhotoService.FACE_PENDING.equals(session.faceCheckStatus)) {
            throw new IllegalArgumentException("人脸检测进行中，请稍候再试");
        }
        WigProduct product = catalogRepository.findProduct(request.wigProductId)
                .orElseThrow(() -> new IllegalArgumentException("发型不存在"));

        TryonTask task = new TryonTask();
        task.taskId = UUID.randomUUID().toString().replace("-", "");
        task.sessionId = request.sessionId;
        task.userPhotoUrl = session.photoUrl;
        task.wigProductId = request.wigProductId;
        task.adjustmentNote = request.adjustmentNote;
        task.tryonType = resolveType(request.tryonType, request.adjustmentNote);
        task.preferredModel = request.preferredModel;
        task.status = TryonTask.Status.PENDING;
        task.aiMode = aiService.modeName();
        sessionRepository.saveTask(task);

        executor.execute(() -> runGeneration(task, product, session));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", task.taskId);
        result.put("status", task.status.name());
        result.put("aiMode", task.aiMode);
        result.put("message", "任务已提交，预计 15-30 秒生成完成");
        return result;
    }

    public Map<String, Object> getStatus(String taskId) {
        TryonTask task = sessionRepository.getTask(taskId);
        Map<String, Object> result = new LinkedHashMap<>();
        if (task == null) {
            result.put("status", "NOT_FOUND");
            return result;
        }
        result.put("taskId", taskId);
        result.put("status", task.status.name());
        result.put("progress", task.progress);
        result.put("resultImageUrl", task.resultImageUrl);
        result.put("errorMessage", task.errorMessage);
        result.put("aiMode", task.aiMode);
        result.put("retryCount", task.retryCount);
        result.put("changeScore", task.changeScore);
        result.put("qualityNote", task.qualityNote);
        result.put("strategyUsed", task.strategyUsed);
        return result;
    }

    public Map<String, Object> adjust(String taskId, String adjustmentNote) {
        TryonTask old = sessionRepository.getTask(taskId);
        if (old == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        TryonRequest req = new TryonRequest();
        req.sessionId = old.sessionId;
        req.wigProductId = old.wigProductId;
        req.adjustmentNote = adjustmentNote;
        return submit(req);
    }

    private void runGeneration(TryonTask task, WigProduct product, SessionRepository.Session session) {
        // 启动伪进度推进器，每 1s 按时间曲线推进 progress；真实回调（updateStatus）若值更大会覆盖
        long startNanos = System.nanoTime();
        ScheduledFuture<?> ticker = progressTicker.scheduleAtFixedRate(() -> {
            if (task.status != TryonTask.Status.PROCESSING) return;
            long elapsedSec = (System.nanoTime() - startNanos) / 1_000_000_000L;
            int target = computeFakeProgress(elapsedSec);
            if (target > task.progress) {
                task.progress = target;
                task.updatedAt = Instant.now();
            }
        }, 1, 1, TimeUnit.SECONDS);

        try {
            updateStatus(task, TryonTask.Status.PROCESSING, 10, null, null);
            FaceAnalysisResult analysis = session.faceAnalysis;
            byte[] userBytes = readBytes(session.photoPath);

            // 从 WigAsset 取"光秃秃假发本体图"喂给 AI——这是修复"模型重画用户照片"的核心
            // 取不到时回退到 product.refImage（旧路径），保证零数据时演示不中断
            WigAsset asset = product.wigAssetId == null ? null
                    : wigAssetRepository.findById(product.wigAssetId).orElse(null);
            String wigImageUrl;
            String wigDescription;
            if (asset != null && asset.productImage != null && !asset.productImage.isBlank()) {
                wigImageUrl = asset.productImage;
                wigDescription = asset.descriptionForAi;
                log.info("[TryonService] 使用 WigAsset productImage productId={}, assetId={}",
                        product.id, asset.id);
            } else {
                wigImageUrl = product.refImage;
                wigDescription = null;
                log.warn("[TryonService] 未找到 WigAsset，回退 refImage productId={}, wigAssetId={}",
                        product.id, product.wigAssetId);
            }

            AiService.GenContext ctx = new AiService.GenContext(
                    task.userPhotoUrl, userBytes, "image/jpeg",
                    product, analysis, task.tryonType, task.adjustmentNote,
                    wigImageUrl, wigDescription, task.preferredModel);

            AiService.GenResult gen = aiService.generateTryon(ctx, pct ->
                    updateStatus(task, TryonTask.Status.PROCESSING, pct, null, null));
            task.aiPrompt = gen.prompt();
            task.aiMode = aiService.modeName();
            task.retryCount = gen.retryCount();
            task.changeScore = gen.changeScore();
            task.qualityNote = gen.qualityNote();
            task.strategyUsed = gen.strategyUsed();

            String ext = gen.externalUrl();
            String resultUrl;
            if (ext == null) {
                // Mock 回退：复用用户原图作为"效果图"（仅演示流程）
                resultUrl = storageService
                        .copyLocal(Paths.get(session.photoPath), "tryon-results/" + task.taskId + extension(session.photoPath))
                        .url();
            } else if (ext.startsWith("http://") || ext.startsWith("https://")) {
                // 真实大模型返回公网图：下载落地到本地存储
                resultUrl = storageService
                        .saveFromUrl(ext, "tryon-results/" + task.taskId + ".png")
                        .url();
            } else {
                // 种子模式返回的相对路径（如 /images/xxx.jpg）：交由前端按自身域名解析
                resultUrl = ext;
            }

            updateStatus(task, TryonTask.Status.SUCCESS, 100, resultUrl, null);
            log.info("试戴生成成功 taskId={}, url={}", task.taskId, resultUrl);
        } catch (Exception e) {
            log.error("试戴生成失败 taskId={}", task.taskId, e);
            String msg = e.getMessage() == null ? "生成失败，请重试" : e.getMessage();
            updateStatus(task, TryonTask.Status.FAILED, task.progress, null, msg);
        } finally {
            ticker.cancel(false);
        }
    }

    private void updateStatus(TryonTask task, TryonTask.Status status, int progress,
                              String resultUrl, String error) {
        task.status = status;
        // progress 单调递增（避免真实回调小于伪进度时把 UI 进度条往回推）
        // SUCCESS 时强制写 100；其它状态取 max
        if (status == TryonTask.Status.SUCCESS) {
            task.progress = 100;
        } else {
            task.progress = Math.max(task.progress, progress);
        }
        if (resultUrl != null) {
            task.resultImageUrl = resultUrl;
        }
        if (error != null) {
            task.errorMessage = error;
        }
        task.updatedAt = Instant.now();
    }

    /**
     * 时间曲线：92 * (1 - e^(-t/30))
     *   0s→ 5%   5s→14%   15s→36%   30s→58%   45s→72%   60s→80%   90s→88%   120s→92%
     * 让用户感觉前 30s 进度推进很快，后段平滑封顶到 92% 等真实完成。
     */
    private int computeFakeProgress(long elapsedSec) {
        if (elapsedSec <= 0) return 5;
        double pct = 92.0 * (1.0 - Math.exp(-elapsedSec / 30.0));
        return Math.max(5, Math.min(92, (int) Math.round(pct)));
    }

    private String resolveType(String explicit, String adjustNote) {
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        if (adjustNote == null) {
            return "FULL_WIG";
        }
        if (adjustNote.contains("刘海")) {
            return "LOCAL_BANGS";
        }
        if (adjustNote.contains("发际线") || adjustNote.contains("M型")) {
            return "LOCAL_HAIRLINE";
        }
        return "FULL_WIG";
    }

    private byte[] readBytes(String path) {
        try {
            return Files.readAllBytes(Paths.get(path));
        } catch (IOException e) {
            return new byte[0];
        }
    }

    private String extension(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot) : ".jpg";
    }
}
