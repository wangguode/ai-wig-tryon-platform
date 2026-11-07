package com.wig.tryon.service;

import com.wig.tryon.model.BatchJob;
import com.wig.tryon.model.ModelAsset;
import com.wig.tryon.model.WigAsset;
import com.wig.tryon.model.WigProduct;
import com.wig.tryon.model.WigShowcase;
import com.wig.tryon.repository.ModelAssetRepository;
import com.wig.tryon.repository.WigAssetRepository;
import com.wig.tryon.repository.WigShowcaseRepository;
import com.wig.tryon.service.ai.AiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 成品图工坊服务：本体图 + 模特图 → AI 合成 → "模特戴假发"陈列图。
 * <p>
 * 与用户试戴流程同源——都是 {@link AiService#generateTryon} 接口，
 * 只不过左图换成"模特照片"而非"用户自拍"。语义干净，零侵入 AI 层。
 * <p>
 * 异步生成：每个组合都进 tryonExecutor 线程池，不阻塞 admin 接口。
 * 失败时也入库一条 status=REJECTED + errorMessage 的 showcase，便于运营复盘。
 * 见 docs/wig-asset-system-design.md 第 5 章。
 */
@Service
public class ShowcaseService {

    private static final Logger log = LoggerFactory.getLogger(ShowcaseService.class);

    private final WigAssetRepository wigAssetRepository;
    private final ModelAssetRepository modelAssetRepository;
    private final WigShowcaseRepository showcaseRepository;
    private final AiService aiService;
    private final StorageService storageService;
    private final BatchJobRegistry batchJobRegistry;
    private final TaskExecutor executor;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public ShowcaseService(WigAssetRepository wigAssetRepository,
                            ModelAssetRepository modelAssetRepository,
                            WigShowcaseRepository showcaseRepository,
                            AiService aiService,
                            StorageService storageService,
                            BatchJobRegistry batchJobRegistry,
                            @Qualifier("tryonExecutor") TaskExecutor executor) {
        this.wigAssetRepository = wigAssetRepository;
        this.modelAssetRepository = modelAssetRepository;
        this.showcaseRepository = showcaseRepository;
        this.aiService = aiService;
        this.storageService = storageService;
        this.batchJobRegistry = batchJobRegistry;
        this.executor = executor;
    }

    /**
     * 批量提交：每个 modelAssetId 都生成一条独立 showcase。
     * 立即返回 PENDING 状态记录，AI 调用在后台线程完成。
     */
    public List<WigShowcase> submitBatch(Long wigAssetId, List<Long> modelAssetIds) {
        WigAsset asset = wigAssetRepository.findById(wigAssetId)
                .orElseThrow(() -> new IllegalArgumentException("假发本体不存在: " + wigAssetId));
        List<WigShowcase> created = new ArrayList<>();
        for (Long modelId : modelAssetIds) {
            ModelAsset model = modelAssetRepository.findById(modelId).orElse(null);
            if (model == null) {
                log.warn("[ShowcaseService] 模特不存在 id={}, 跳过", modelId);
                continue;
            }
            // 占位记录：DRAFT 状态，resultImage 暂为 null。AI 完成后补回。
            WigShowcase pending = new WigShowcase();
            pending.wigAssetId = wigAssetId;
            pending.modelAssetId = modelId;
            pending.status = "DRAFT";
            pending = showcaseRepository.save(pending);
            created.add(pending);

            final WigShowcase finalRef = pending;
            executor.execute(() -> generateOne(finalRef.id, asset, model));
        }
        return created;
    }

    /** 单个生成（异步背景任务调用）。 */
    private void generateOne(Long showcaseId, WigAsset asset, ModelAsset model) {
        WigShowcase showcase = showcaseRepository.findById(showcaseId).orElse(null);
        if (showcase == null) return;
        try {
            byte[] modelBytes = readImageBytes(model.portraitImage);
            if (modelBytes.length == 0) {
                throw new IllegalStateException("无法读取模特图: " + model.portraitImage);
            }

            // 构造一个临时 WigProduct 适配现有 GenContext——AI 层零改动
            // PromptBuilder.tryonPromptWithBareWig 仅取 name/category/colorFamily/description/tags
            WigProduct adapter = new WigProduct();
            adapter.id = -1L; // 占位 id，明确表示这不是上架产品
            adapter.name = asset.name;
            adapter.category = asset.category;
            adapter.colorFamily = asset.colorFamily;
            adapter.description = asset.descriptionForAi;
            adapter.tags = asset.tags;

            AiService.GenContext ctx = new AiService.GenContext(
                    model.portraitImage, modelBytes, "image/jpeg",
                    adapter, null, "FULL_WIG", null,
                    asset.productImage, asset.descriptionForAi);

            log.info("[ShowcaseService] 开始生成 showcaseId={}, wigAssetId={}, modelId={}",
                    showcaseId, asset.id, model.id);
            AiService.GenResult result = aiService.generateTryon(ctx, p -> {});

            String storedUrl = persistResult(result.externalUrl(), showcaseId);
            showcase.resultImage = storedUrl;
            showcase.changeScore = result.changeScore();
            showcase.prompt = result.prompt();
            showcase.strategyUsed = result.strategyUsed();
            showcase.status = "DRAFT"; // 生成成功 = DRAFT 等待审核
            showcaseRepository.save(showcase);
            log.info("[ShowcaseService] 生成完成 showcaseId={}, url={}", showcaseId, storedUrl);
        } catch (Exception e) {
            log.error("[ShowcaseService] 生成失败 showcaseId={}", showcaseId, e);
            showcase.status = "REJECTED";
            showcase.errorMessage = e.getMessage();
            showcase.reviewedAt = Instant.now();
            showcase.reviewedBy = "system-auto-failed";
            showcaseRepository.save(showcase);
        }
    }

    private String persistResult(String externalUrl, Long showcaseId) throws IOException, InterruptedException {
        if (externalUrl == null || externalUrl.isBlank()) {
            throw new IllegalStateException("AI 未返回结果图");
        }
        String objectName = "showcases/" + showcaseId + "-" + UUID.randomUUID() + ".png";
        if (externalUrl.startsWith("http://") || externalUrl.startsWith("https://")) {
            return storageService.saveFromUrl(externalUrl, objectName).url();
        }
        // mock/seed 场景返回的是 /images/xxx 相对路径——直接透传
        return externalUrl;
    }

    /**
     * 读取一张图像的字节（来源类型与 DashScopeAiService.materializeWigImage 一致）。
     * 把这逻辑放在 ShowcaseService 内部是为了保持 AiService 实现层无感。
     */
    private byte[] readImageBytes(String ref) throws IOException, InterruptedException {
        if (ref == null || ref.isBlank()) return new byte[0];
        String trimmed = ref.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            HttpRequest req = HttpRequest.newBuilder(URI.create(trimmed)).GET().build();
            HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() >= 300) {
                throw new IOException("下载模特图失败 status=" + resp.statusCode());
            }
            return resp.body();
        }
        Path candidate;
        if (trimmed.startsWith("/images/")) {
            candidate = Paths.get("..", "ai-hairstyle-mini-program-ui", "public", trimmed.substring(1)).normalize();
        } else if (trimmed.startsWith("/files/")) {
            // 内置 storage 文件：去掉 /files/ 前缀按 storage 根目录读
            candidate = Paths.get("./data/storage", trimmed.substring("/files/".length())).normalize();
        } else {
            candidate = Paths.get(trimmed).normalize();
        }
        if (!Files.exists(candidate)) {
            throw new IOException("模特图不存在: " + candidate);
        }
        return Files.readAllBytes(candidate);
    }

    public WigShowcase approve(Long showcaseId, String reviewer, boolean setPrimary) {
        WigShowcase s = showcaseRepository.findById(showcaseId)
                .orElseThrow(() -> new IllegalArgumentException("成品图不存在: " + showcaseId));
        s.status = "APPROVED";
        s.reviewedAt = Instant.now();
        s.reviewedBy = reviewer == null ? "admin" : reviewer;
        showcaseRepository.save(s);
        if (setPrimary) {
            showcaseRepository.markPrimary(showcaseId);
        } else if (!showcaseRepository.hasApprovedPrimary(s.wigAssetId)) {
            // 首款通过且尚无主图时自动设为主图（含 AI / 手工）
            showcaseRepository.markPrimary(showcaseId);
        }
        return showcaseRepository.findById(showcaseId).orElse(s);
    }

    public WigShowcase reject(Long showcaseId, String reviewer) {
        WigShowcase s = showcaseRepository.findById(showcaseId)
                .orElseThrow(() -> new IllegalArgumentException("成品图不存在: " + showcaseId));
        s.status = "REJECTED";
        s.reviewedAt = Instant.now();
        s.reviewedBy = reviewer == null ? "admin" : reviewer;
        return showcaseRepository.save(s);
    }

    /**
     * 一键批量填充：用 1 个模特给所有（或指定）本体批量生成 showcase + 自动审批 + 自动设主图。
     * 同类任务并发限 1（防误点）。
     *
     * @param modelAssetId 用哪个模特生成
     * @param wigAssetIds  指定本体；空则全部
     * @param autoApprove  生成成功后自动 APPROVED
     * @param autoPrimary  自动设为该 wigAsset 的主图（取消旧主图）
     */
    public BatchJob startBatchFill(Long modelAssetId, List<Long> wigAssetIds,
                                     boolean autoApprove, boolean autoPrimary) {
        ModelAsset model = modelAssetRepository.findById(modelAssetId)
                .orElseThrow(() -> new IllegalArgumentException("模特不存在: " + modelAssetId));
        BatchJob existing = batchJobRegistry.findRunning(BatchJob.Type.FILL_SHOWCASES).orElse(null);
        if (existing != null) {
            return existing;
        }
        List<WigAsset> assets;
        if (wigAssetIds == null || wigAssetIds.isEmpty()) {
            assets = wigAssetRepository.findAll();
        } else {
            assets = new ArrayList<>();
            for (Long id : wigAssetIds) {
                wigAssetRepository.findById(id).ifPresent(assets::add);
            }
        }
        if (assets.isEmpty()) {
            throw new IllegalStateException("没有可填充的本体资产");
        }

        BatchJob job = new BatchJob(BatchJob.Type.FILL_SHOWCASES, assets.size(),
                "用模特 #" + modelAssetId + " 给 " + assets.size() + " 张本体生成成品图"
                        + (autoApprove ? " + 自动审批" : "") + (autoPrimary ? " + 设为主图" : ""));
        for (WigAsset a : assets) {
            job.items.add(new BatchJob.Item(a.id, a.name == null ? ("#" + a.id) : a.name));
        }
        batchJobRegistry.register(job);
        // 串行 + 间隔：showcase 走 image2image 链路，DashScope 同样有限速；
        // 同时也避免一次提交 16 个图生图任务把 tryonExecutor（C 端用户试戴共享）打满
        Thread worker = new Thread(() -> {
            for (int i = 0; i < assets.size(); i++) {
                processFillOne(job, job.items.get(i), assets.get(i), model, autoApprove, autoPrimary);
                if (i < assets.size() - 1) {
                    try { Thread.sleep(2000L); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, "showcase-fill-" + job.jobId);
        worker.setDaemon(true);
        worker.start();
        return job;
    }

    private void processFillOne(BatchJob job, BatchJob.Item item, WigAsset asset,
                                 ModelAsset model, boolean autoApprove, boolean autoPrimary) {
        item.state = "RUNNING";
        item.updatedAt = Instant.now();
        WigShowcase pending = new WigShowcase();
        pending.wigAssetId = asset.id;
        pending.modelAssetId = model.id;
        pending.status = "DRAFT";
        pending = showcaseRepository.save(pending);
        try {
            generateOne(pending.id, asset, model);
            WigShowcase result = showcaseRepository.findById(pending.id).orElse(pending);
            if (result.resultImage == null || result.errorMessage != null) {
                throw new IllegalStateException(result.errorMessage == null ? "AI 未返回图" : result.errorMessage);
            }
            if (autoApprove) {
                result.status = "APPROVED";
                result.reviewedAt = Instant.now();
                result.reviewedBy = "system-batch-fill";
                showcaseRepository.save(result);
            }
            if (autoPrimary) {
                showcaseRepository.markPrimary(result.id);
            }
            item.state = "DONE";
            item.resultUrl = result.resultImage;
            job.completed.incrementAndGet();
            log.info("[ShowcaseService/Batch] DONE wigAssetId={}, showcaseId={}", asset.id, result.id);
        } catch (Exception e) {
            item.state = "FAILED";
            item.reason = e.getMessage() == null ? "未知错误" : e.getMessage();
            job.failed.incrementAndGet();
            log.warn("[ShowcaseService/Batch] FAILED wigAssetId={}: {}", asset.id, e.getMessage());
        } finally {
            item.updatedAt = Instant.now();
            if (job.completed.get() + job.failed.get() >= job.total) {
                job.status = BatchJob.Status.DONE;
                job.finishedAt = Instant.now();
                log.info("[ShowcaseService/Batch] 任务完成 jobId={}, done={}, failed={}",
                        job.jobId, job.completed.get(), job.failed.get());
            }
        }
    }
}
