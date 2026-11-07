package com.wig.tryon.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wig.tryon.config.DashScopeProperties;
import com.wig.tryon.model.BatchJob;
import com.wig.tryon.model.ModelAsset;
import com.wig.tryon.repository.ModelAssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 模特肖像 AI 生成：根据 ModelAsset 元数据（性别/年龄/脸型）用 wan2.x-t2i 生成
 * 一张白底人脸特写，覆盖 portraitImage。仅供运营快速搭建演示物料用。
 * <p>
 * 真实生产应该让运营上传**有授权的真模特照片**——AI 生成的人脸虽然好看但有
 * "免费证件照感"。当前是 MVP 阶段补占位图的便利工具。
 */
@Service
public class ModelPortraitGenerator {

    private static final Logger log = LoggerFactory.getLogger(ModelPortraitGenerator.class);

    private final DashScopeProperties props;
    private final StorageService storageService;
    private final RestTemplate restTemplate;
    private final ModelAssetRepository modelAssetRepository;
    private final BatchJobRegistry batchJobRegistry;
    private final TaskExecutor executor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ModelPortraitGenerator(DashScopeProperties props,
                                   StorageService storageService,
                                   RestTemplate restTemplate,
                                   ModelAssetRepository modelAssetRepository,
                                   BatchJobRegistry batchJobRegistry,
                                   @Qualifier("tryonExecutor") TaskExecutor executor) {
        this.props = props;
        this.storageService = storageService;
        this.restTemplate = restTemplate;
        this.modelAssetRepository = modelAssetRepository;
        this.batchJobRegistry = batchJobRegistry;
        this.executor = executor;
    }

    /** 单条生成（后端管理员单条触发用）。 */
    public String regenerate(ModelAsset model) {
        if (!props.hasApiKey()) {
            throw new IllegalStateException("未配置 DASHSCOPE_API_KEY，无法文生图");
        }
        String prompt = buildPrompt(model);
        log.info("[ModelGen] 开始 modelId={}, promptLen={}", model.id, prompt.length());
        String taskId = submit(prompt);
        String resultUrl = poll(taskId);
        if (resultUrl == null || resultUrl.isBlank()) {
            throw new IllegalStateException("AI 未返回结果图");
        }
        String objectName = "model-assets/" + model.id + "-" + UUID.randomUUID() + ".png";
        try {
            return storageService.saveFromUrl(resultUrl, objectName).url();
        } catch (Exception e) {
            throw new IllegalStateException("落地模特图失败: " + e.getMessage(), e);
        }
    }

    /** 批量生成（同类型并发限 1）。 */
    public BatchJob startBatch(List<Long> modelIds) {
        if (!props.hasApiKey()) {
            throw new IllegalStateException("未配置 DASHSCOPE_API_KEY，无法文生图");
        }
        BatchJob existing = batchJobRegistry.findRunning(BatchJob.Type.REGENERATE_MODEL_PORTRAIT).orElse(null);
        if (existing != null) {
            return existing;
        }
        List<ModelAsset> targets;
        if (modelIds == null || modelIds.isEmpty()) {
            targets = modelAssetRepository.findAll();
        } else {
            targets = new ArrayList<>();
            for (Long id : modelIds) {
                modelAssetRepository.findById(id).ifPresent(targets::add);
            }
        }
        if (targets.isEmpty()) {
            throw new IllegalStateException("没有可生成的模特");
        }
        BatchJob job = new BatchJob(BatchJob.Type.REGENERATE_MODEL_PORTRAIT, targets.size(),
                "AI 生成 " + targets.size() + " 张模特照片");
        for (ModelAsset m : targets) {
            job.items.add(new BatchJob.Item(m.id, m.name == null ? ("#" + m.id) : m.name));
        }
        batchJobRegistry.register(job);
        // 串行 + 间隔，DashScope 文生图有并发限速
        Thread worker = new Thread(() -> {
            for (int i = 0; i < targets.size(); i++) {
                processOne(job, job.items.get(i), targets.get(i));
                if (i < targets.size() - 1) {
                    try { Thread.sleep(2000L); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, "model-portrait-batch-" + job.jobId);
        worker.setDaemon(true);
        worker.start();
        return job;
    }

    private void processOne(BatchJob job, BatchJob.Item item, ModelAsset model) {
        item.state = "RUNNING";
        item.updatedAt = Instant.now();
        try {
            String url = regenerate(model);
            model.portraitImage = url;
            modelAssetRepository.save(model);
            item.state = "DONE";
            item.resultUrl = url;
            job.completed.incrementAndGet();
        } catch (Exception e) {
            item.state = "FAILED";
            item.reason = e.getMessage() == null ? "未知错误" : e.getMessage();
            job.failed.incrementAndGet();
            log.warn("[ModelGen/Batch] FAILED modelId={}: {}", model.id, e.getMessage());
        } finally {
            item.updatedAt = Instant.now();
            if (job.completed.get() + job.failed.get() >= job.total) {
                job.status = BatchJob.Status.DONE;
                job.finishedAt = Instant.now();
            }
        }
    }

    /**
     * 把模特元数据翻译成英文文生图 prompt：
     * - "professional studio portrait of an Asian {gender}" 锚定亚洲面孔（避免欧美默认）
     * - 显式禁掉头发"hair: bald or wig-cap" — 因为这张图后面要给 AI 拿来戴假发，
     *   如果原图有刘海/长发，AI 戴假发后会"两套头发叠加"。这里强制让模特"光头"或最小头发感。
     */
    private String buildPrompt(ModelAsset model) {
        String gender = "M".equalsIgnoreCase(model.gender) ? "man" : "woman";
        String age;
        if ("elder".equalsIgnoreCase(model.ageGroup)) age = "60 years old";
        else if ("mid".equalsIgnoreCase(model.ageGroup)) age = "40 years old";
        else age = "25 years old";
        String face = model.faceShape == null ? "natural face shape" : (model.faceShape + " face");

        StringBuilder sb = new StringBuilder();
        sb.append("Professional studio portrait photograph of an Asian ").append(gender).append(", ");
        sb.append("approximately ").append(age).append(", ");
        sb.append(face).append(", ");
        sb.append("front view, looking at camera, neutral relaxed expression, ");
        sb.append("clean skin, soft natural makeup, no glasses, no jewelry, no hat. ");
        sb.append("Wearing a plain light-gray t-shirt, plain neutral light-gray background, ");
        sb.append("soft even studio lighting from above, slight rim light. ");
        // 关键：给 AI 戴假发预留头型——头发尽量贴头皮、无明显发型，避免"两套头发"
        sb.append("Hair: very short cropped buzz cut tight to scalp, almost like a wig cap, ");
        sb.append("absolutely no bangs, no long hair, no hair styling. ");
        sb.append("Photorealistic, high detail, sharp focus on face, 8K, e-commerce model casting reference shot.");
        return sb.toString();
    }

    private String submit(String prompt) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", props.getTextToImageModel());
        body.put("input", Map.of("prompt", prompt));
        body.put("parameters", Map.of(
                "size", "1024*1280",
                "n", 1,
                "prompt_extend", props.isPromptExtend(),
                "watermark", props.isWatermark()));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(props.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);
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
                throw new IllegalStateException("文生图响应缺 task_id");
            }
            return taskId;
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new IllegalStateException("解析响应失败: " + e.getMessage(), e);
        }
    }

    private String poll(String taskId) {
        long deadline = System.currentTimeMillis() + props.getTaskTimeoutSeconds() * 1000L;
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(props.getApiKey());
        while (System.currentTimeMillis() < deadline) {
            try { Thread.sleep(2000L); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("轮询被中断", ie);
            }
            ResponseEntity<String> resp;
            try {
                String url = (props.getTaskQueryUrl() == null ? "" : props.getTaskQueryUrl()) + taskId;
                resp = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            } catch (Exception e) {
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
                    throw new IllegalStateException("任务成功但无 url");
                } else if ("FAILED".equals(status)) {
                    String code = root.path("output").path("code").asText("");
                    String msg = root.path("output").path("message").asText("");
                    throw new IllegalStateException("任务失败 code=" + code + ", msg=" + msg);
                }
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception ignored) {
            }
        }
        throw new IllegalStateException("任务超时");
    }
}
