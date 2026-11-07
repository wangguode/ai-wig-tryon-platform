package com.wig.tryon.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wig.tryon.config.DashScopeProperties;
import com.wig.tryon.model.BatchJob;
import com.wig.tryon.model.WigAsset;
import com.wig.tryon.repository.WigAssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "AI 重画本体图"服务：用 DashScope wan2.2-t2i 文生图根据 WigAsset.descriptionForAi 生成
 * 一张白底纯假发产品图，落地后写回 WigAsset.productImage。
 * <p>
 * 这绕开了 {@link com.wig.tryon.service.ai.AiService} 抽象——因为 generateTryon 是图生图语义
 * （需要用户/模特图作主体），而本场景纯文生图，强行复用反而污染抽象。
 * 同样原因：mock/seed 模式下此功能直接返回错误（admin 接口层判断 dashscope.hasApiKey）。
 * <p>
 * 见 docs/wig-asset-system-design.md。
 */
@Service
public class BareWigGenerator {

    private static final Logger log = LoggerFactory.getLogger(BareWigGenerator.class);

    private final DashScopeProperties props;
    private final StorageService storageService;
    private final RestTemplate restTemplate;
    private final WigAssetRepository wigAssetRepository;
    private final BatchJobRegistry batchJobRegistry;
    private final TaskExecutor executor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BareWigGenerator(DashScopeProperties props,
                             StorageService storageService,
                             RestTemplate restTemplate,
                             WigAssetRepository wigAssetRepository,
                             BatchJobRegistry batchJobRegistry,
                             @Qualifier("tryonExecutor") TaskExecutor executor) {
        this.props = props;
        this.storageService = storageService;
        this.restTemplate = restTemplate;
        this.wigAssetRepository = wigAssetRepository;
        this.batchJobRegistry = batchJobRegistry;
        this.executor = executor;
    }

    /**
     * 批量重画本体图。同一类型只允许 1 个并发任务（防运营误点）。
     * 通过 tryonExecutor 并发跑（线程池 4 路并行）。
     *
     * @param assetIds 要重画的资产 id 列表；为空 = 全部
     * @return 启动的 BatchJob（前端用 jobId 轮询进度）
     */
    public BatchJob startBatch(List<Long> assetIds) {
        if (!props.hasApiKey()) {
            throw new IllegalStateException("未配置 DASHSCOPE_API_KEY，无法文生图");
        }
        BatchJob existing = batchJobRegistry.findRunning(BatchJob.Type.REGENERATE_BARE_WIG).orElse(null);
        if (existing != null) {
            return existing;
        }
        List<WigAsset> targets = resolveTargets(assetIds);
        if (targets.isEmpty()) {
            throw new IllegalStateException("没有可重画的本体资产");
        }
        BatchJob job = new BatchJob(BatchJob.Type.REGENERATE_BARE_WIG, targets.size(),
                "AI 文生图重画 " + targets.size() + " 张本体图");
        for (WigAsset a : targets) {
            job.items.add(new BatchJob.Item(a.id, a.name == null ? ("#" + a.id) : a.name));
        }
        batchJobRegistry.register(job);

        // 串行跑：DashScope 文生图有并发限速（一次性提交多个会触发 429 RateQuota）
        // 单线程 daemon，间隔 2s 提交，避免触发限流
        Thread worker = new Thread(() -> runSerial(job, targets), "bare-wig-batch-" + job.jobId);
        worker.setDaemon(true);
        worker.start();
        return job;
    }

    private void runSerial(BatchJob job, List<WigAsset> targets) {
        for (int i = 0; i < targets.size(); i++) {
            processOne(job, job.items.get(i), targets.get(i));
            if (i < targets.size() - 1) {
                try { Thread.sleep(2000L); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private List<WigAsset> resolveTargets(List<Long> ids) {
        List<WigAsset> all = wigAssetRepository.findAll();
        if (ids == null || ids.isEmpty()) {
            return all;
        }
        List<WigAsset> result = new ArrayList<>();
        for (Long id : ids) {
            wigAssetRepository.findById(id).ifPresent(result::add);
        }
        return result;
    }

    private void processOne(BatchJob job, BatchJob.Item item, WigAsset asset) {
        item.state = "RUNNING";
        item.updatedAt = Instant.now();
        try {
            String url = regenerate(asset);
            asset.productImage = url;
            asset.productImageThumbnail = url;
            wigAssetRepository.save(asset);
            item.state = "DONE";
            item.resultUrl = url;
            job.completed.incrementAndGet();
            log.info("[BareWigGen/Batch] DONE assetId={}", asset.id);
        } catch (Exception e) {
            item.state = "FAILED";
            item.reason = truncate(e.getMessage(), 200);
            job.failed.incrementAndGet();
            log.warn("[BareWigGen/Batch] FAILED assetId={}: {}", asset.id, e.getMessage());
        } finally {
            item.updatedAt = Instant.now();
            checkJobDone(job);
        }
    }

    private void checkJobDone(BatchJob job) {
        if (job.completed.get() + job.failed.get() >= job.total) {
            job.status = BatchJob.Status.DONE;
            job.finishedAt = Instant.now();
            log.info("[BareWigGen/Batch] 批量任务完成 jobId={}, total={}, done={}, failed={}",
                    job.jobId, job.total, job.completed.get(), job.failed.get());
        }
    }

    /**
     * 根据 WigAsset 的中文描述用 AI 文生图生成一张白底假发本体图，落地存储后返回 URL。
     *
     * @return 落地后的本地可访问 URL（如 http://localhost:8080/files/wig-assets/3.png）
     * @throws IllegalStateException 当未配置 DashScope Key、AI 调用失败、或返回内容异常时
     */
    public String regenerate(WigAsset asset) {
        if (!props.hasApiKey()) {
            throw new IllegalStateException("未配置 DASHSCOPE_API_KEY，无法文生图");
        }
        String prompt = buildPrompt(asset);
        log.info("[BareWigGen] 开始 assetId={}, model={}, promptLen={}",
                asset.id, props.getTextToImageModel(), prompt.length());

        String taskId = submitText2Image(prompt);
        String resultUrl = pollTask(taskId);
        if (resultUrl == null || resultUrl.isBlank()) {
            throw new IllegalStateException("AI 未返回结果图");
        }
        // 落地到 storage/wig-assets/<id>.png（覆盖旧本体图）。带 uuid 后缀避免浏览器缓存
        String objectName = "wig-assets/" + asset.id + "-" + UUID.randomUUID() + ".png";
        try {
            StorageService.Stored stored = storageService.saveFromUrl(resultUrl, objectName);
            log.info("[BareWigGen] 完成 assetId={}, url={}", asset.id, stored.url());
            return stored.url();
        } catch (Exception e) {
            throw new IllegalStateException("落地结果图失败: " + e.getMessage(), e);
        }
    }

    /**
     * 把 WigAsset 的中文描述包装成"产品图"风格的英文 prompt。
     * 英文 prompt 对 wan2.x-t2i 更友好（中文长描述容易触发 prompt-extend 走偏）。
     * <p>
     * 关键约束：
     *   - "isolated wig"、"no model / no human face / no body"——避免画出人
     *   - "ghost mannequin head form"——给假发一个隐形支撑，看起来更像产品图
     *   - "pure white seamless background"——确保白底
     *   - 主体描述用中文（直接来自 descriptionForAi）保持发色/长度/卷直精度
     */
    private String buildPrompt(WigAsset asset) {
        StringBuilder sb = new StringBuilder();
        sb.append("Studio product photography of a single wig, ");
        sb.append("displayed on a transparent ghost mannequin head form, fully isolated. ");
        sb.append("Pure white seamless background. Soft studio lighting from above. ");
        sb.append("No model, no human face, no body, no hands, no props, no text. ");
        sb.append("Front view, e-commerce product shot, photorealistic, ultra-detailed, 8K. ");
        sb.append("Wig details: ");
        if (asset.descriptionForAi != null && !asset.descriptionForAi.isBlank()) {
            sb.append(asset.descriptionForAi).append(" ");
        } else if (asset.name != null) {
            sb.append(asset.name).append(". ");
        }
        if (asset.colorFamily != null) {
            sb.append("Color: ").append(asset.colorFamily).append(". ");
        }
        if (asset.category != null) {
            sb.append("Style: ").append(asset.category).append(". ");
        }
        sb.append("Make sure the background is pure white, hair edges are soft and natural, ");
        sb.append("no clipping artifacts, no halo, no shadow on background.");
        return sb.toString();
    }

    /** 提交文生图异步任务，返回 task_id。 */
    private String submitText2Image(String prompt) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", props.getTextToImageModel());
        body.put("input", Map.of("prompt", prompt));
        body.put("parameters", Map.of(
                "size", props.getTextToImageSize(),
                "n", 1,
                "prompt_extend", props.isPromptExtend(),
                "watermark", props.isWatermark()));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(props.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 文生图必须异步，所有 wan 模型仅接受 X-DashScope-Async: enable
        headers.set("X-DashScope-Async", "enable");

        ResponseEntity<String> resp;
        try {
            resp = restTemplate.postForEntity(
                    props.getTextToImageUrl(), new HttpEntity<>(body, headers), String.class);
        } catch (Exception e) {
            throw new IllegalStateException("文生图提交失败: " + e.getMessage(), e);
        }
        try {
            JsonNode root = objectMapper.readTree(resp.getBody());
            String taskId = root.path("output").path("task_id").asText("");
            if (taskId.isBlank()) {
                throw new IllegalStateException("文生图响应缺 task_id: " + truncate(resp.getBody(), 300));
            }
            return taskId;
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new IllegalStateException("解析文生图响应失败: " + e.getMessage(), e);
        }
    }

    /** 轮询任务直到 SUCCEEDED/FAILED/超时；返回结果图 URL。 */
    private String pollTask(String taskId) {
        long deadline = System.currentTimeMillis() + props.getTaskTimeoutSeconds() * 1000L;
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(props.getApiKey());

        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("轮询被中断", ie);
            }
            ResponseEntity<String> resp;
            try {
                String url = (props.getTaskQueryUrl() == null ? "" : props.getTaskQueryUrl()) + taskId;
                resp = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            } catch (Exception e) {
                log.warn("[BareWigGen] 轮询单次失败，继续: {}", e.getMessage());
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(resp.getBody());
                String status = root.path("output").path("task_status").asText("");
                if ("SUCCEEDED".equals(status)) {
                    JsonNode results = root.path("output").path("results");
                    if (results.isArray() && !results.isEmpty()) {
                        String url = results.get(0).path("url").asText("");
                        if (!url.isBlank()) return url;
                    }
                    throw new IllegalStateException("任务成功但未返回 url: " + truncate(resp.getBody(), 300));
                } else if ("FAILED".equals(status)) {
                    String code = root.path("output").path("code").asText("");
                    String msg = root.path("output").path("message").asText("");
                    throw new IllegalStateException("文生图任务失败: code=" + code + ", message=" + msg);
                }
                // RUNNING / PENDING 继续轮
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                log.warn("[BareWigGen] 解析轮询响应失败: {}", e.getMessage());
            }
        }
        throw new IllegalStateException("文生图任务超时（>" + props.getTaskTimeoutSeconds() + "s）");
    }

    private String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
