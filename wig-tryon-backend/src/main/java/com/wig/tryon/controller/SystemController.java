package com.wig.tryon.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wig.tryon.config.AppProperties;
import com.wig.tryon.config.DashScopeProperties;
import com.wig.tryon.config.VolcengineArkProperties;
import com.wig.tryon.config.YunwuProperties;
import com.wig.tryon.model.ApiResponse;
import com.wig.tryon.service.ai.AiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统状态与联调辅助接口。
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private static final Logger log = LoggerFactory.getLogger(SystemController.class);

    private final AppProperties appProperties;
    private final DashScopeProperties dashScopeProperties;
    private final AiService aiService;
    private final YunwuProperties yunwuProperties;
    private final VolcengineArkProperties volcengineArkProperties;
    /** 长超时 RestTemplate：图像生成接口经常 60-120s，共享 RestTemplate 60s 读超时不够 */
    private final RestTemplate yunwuRestTemplate;
    private final RestTemplate arkRestTemplate;
    private final ObjectMapper objectMapper;

    public SystemController(AppProperties appProperties, DashScopeProperties dashScopeProperties, AiService aiService,
                            YunwuProperties yunwuProperties, VolcengineArkProperties volcengineArkProperties,
                            RestTemplateBuilder restTemplateBuilder, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.dashScopeProperties = dashScopeProperties;
        this.aiService = aiService;
        this.yunwuProperties = yunwuProperties;
        this.volcengineArkProperties = volcengineArkProperties;
        this.yunwuRestTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(15))
                .setReadTimeout(Duration.ofSeconds(Math.max(30, yunwuProperties.getTimeoutSeconds())))
                .build();
        this.arkRestTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(15))
                .setReadTimeout(Duration.ofSeconds(Math.max(60, volcengineArkProperties.getTimeoutSeconds())))
                .build();
        this.objectMapper = objectMapper;
    }

    @GetMapping("/ai-status")
    public ApiResponse<Map<String, Object>> aiStatus() {
        String configured = appProperties.getAi().resolvedMode();
        String publicBase = appProperties.getStorage().getPublicBaseUrl();
        boolean likelyPublic = isLikelyPublic(publicBase);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("configuredMode", configured);
        r.put("effectiveMode", aiService.modeName());
        r.put("strictReal", appProperties.getAi().isStrictReal());
        r.put("demoFastFaceCheck", appProperties.getAi().isDemoFastFaceCheck());
        r.put("demoFastRecommend", appProperties.getAi().isDemoFastRecommend());
        r.put("tryonProvider", appProperties.getAi().getTryonProvider());
        r.put("dashscopeKeyConfigured", dashScopeProperties.hasApiKey());
        r.put("yunwuKeyConfigured", yunwuProperties.hasApiKey());
        r.put("yunwuImageModel", yunwuProperties.getImageModel());
        r.put("yunwuTryonEndpoint", yunwuProperties.getTryonEndpoint());
        r.put("arkKeyConfigured", volcengineArkProperties.hasApiKey());
        r.put("arkImageModel", volcengineArkProperties.getImageModel());
        r.put("storagePublicBaseUrl", publicBase);
        r.put("storageUrlLikelyPublic", likelyPublic);
        boolean readyByDashscope = "real".equals(configured) && dashScopeProperties.hasApiKey();
        boolean readyByYunwu = "real".equals(configured) && "yunwu".equalsIgnoreCase(appProperties.getAi().getTryonProvider())
                && yunwuProperties.hasApiKey();
        boolean readyByArk = "real".equals(configured) && volcengineArkProperties.hasApiKey();
        r.put("readyForRealDemo", "real".equals(aiService.modeName()) || readyByDashscope || readyByYunwu || readyByArk);
        r.put("suggestion", buildSuggestion(configured,
                dashScopeProperties.hasApiKey(),
                yunwuProperties.hasApiKey(),
                volcengineArkProperties.hasApiKey(),
                appProperties.getAi().getTryonProvider(),
                likelyPublic));
        return ApiResponse.success(r);
    }

    private boolean isLikelyPublic(String url) {
        if (url == null) return false;
        String low = url.toLowerCase();
        return !(low.contains("localhost")
                || low.contains("127.0.0.1")
                || low.contains("0.0.0.0")
                || low.contains("192.168.")
                || low.contains("10.")
                || low.contains("172.16.")
                || low.contains("172.17.")
                || low.contains("172.18.")
                || low.contains("172.19.")
                || low.contains("172.2")
                || low.contains("172.30.")
                || low.contains("172.31."));
    }

    private String buildSuggestion(String configured, boolean hasDashscopeKey,
                                    boolean hasYunwuKey, boolean hasArkKey,
                                    String tryonProvider, boolean likelyPublic) {
        if (!"real".equals(configured)) {
            return "当前非 real 模式。设置 AI_MODE=real 可进行真实模型联调。";
        }
        boolean tryonReady = switch (tryonProvider == null ? "" : tryonProvider.trim().toLowerCase()) {
            case "yunwu" -> hasYunwuKey;
            case "ark", "volcengine", "doubao" -> hasArkKey;
            default -> hasDashscopeKey;
        };
        if (!tryonReady && !hasArkKey && !hasDashscopeKey && !hasYunwuKey) {
            return "缺少模型 Key。Doubao 需 ARK_API_KEY；Qwen 需 DASHSCOPE_API_KEY。";
        }
        if (!tryonReady) {
            return switch (tryonProvider == null ? "" : tryonProvider.trim().toLowerCase()) {
                case "yunwu" -> "缺少 YUNWU_API_KEY。请先配置 Key。";
                case "ark", "volcengine", "doubao" -> "缺少 ARK_API_KEY（火山方舟）。请先配置 Key。";
                default -> "缺少 DASHSCOPE_API_KEY。请先配置 Key。";
            };
        }
        if (!likelyPublic) {
            return "当前 storage.public-base-url 看起来是本地地址。已支持 data URI 联调；线上建议换 OSS/公网 URL。";
        }
        return "真实联调条件基本就绪，可直接上传照片测试生成质量。";
    }

    /**
     * 中转站（OpenAI 兼容代理）连通性调测：
     * 1. 探测 /v1/images/generations（纯文本生图，验证 Key 与模型是否可用）
     * 2. 探测 /v1/images/edits（图像编辑，验证是否能做"保身份试戴"）
     * 两条路独立，任一失败不影响另一条。响应里给出每条路的耗时、样本图、错误信息。
     * 不会把 Key 写入响应或日志。
     */
    @GetMapping("/yunwu-ping")
    public ApiResponse<Map<String, Object>> yunwuPing() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("baseUrl", yunwuProperties.getBaseUrl());
        result.put("imageModel", yunwuProperties.getImageModel());
        result.put("apiKeyConfigured", yunwuProperties.hasApiKey());

        if (!yunwuProperties.hasApiKey()) {
            result.put("ready", false);
            result.put("hint", "未配置 YUNWU_API_KEY，无法发起调测请求。");
            return ApiResponse.success(result);
        }

        result.put("generations", probeGenerations());
        result.put("edits", probeEdits());
        result.put("hint", "如果 generations.ok=true 且 edits.ok=true，可继续把试戴接到中转站；"
                + "如果只有 generations.ok=true，则该中转不支持试戴所需的图像编辑接口，"
                + "建议继续用 DashScope wan2.5 做试戴，把中转站留给文生图场景。");
        return ApiResponse.success(result);
    }

    /**
     * 火山方舟 Doubao Seedream 连通性调测（官方 images/generations 双图示例）。
     */
    @GetMapping("/ark-ping")
    public ApiResponse<Map<String, Object>> arkPing() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("baseUrl", volcengineArkProperties.getBaseUrl());
        result.put("imageModel", volcengineArkProperties.getImageModel());
        result.put("apiKeyConfigured", volcengineArkProperties.hasApiKey());

        if (!volcengineArkProperties.hasApiKey()) {
            result.put("ready", false);
            result.put("hint", "未配置 ARK_API_KEY，无法发起调测请求。");
            return ApiResponse.success(result);
        }

        result.put("generations", probeArkGenerations());
        result.put("hint", "generations.ok=true 表示火山方舟 Key 与 doubao-seedream-5-0-260128 可用；"
                + "小程序选 Doubao Seedream 5.0 时将走此直连链路。");
        return ApiResponse.success(result);
    }

    private Map<String, Object> probeArkGenerations() {
        Map<String, Object> r = new LinkedHashMap<>();
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", volcengineArkProperties.getImageModel());
            body.put("prompt", "将图1的服装换为图2的服装");
            body.put("image", List.of(
                    "https://ark-project.tos-cn-beijing.volces.com/doc_image/seedream4_imagesToimage_1.png",
                    "https://ark-project.tos-cn-beijing.volces.com/doc_image/seedream4_imagesToimage_2.png"));
            body.put("sequential_image_generation", "disabled");
            body.put("response_format", "url");
            body.put("size", volcengineArkProperties.getImageSize() != null
                    && !volcengineArkProperties.getImageSize().isBlank()
                    ? volcengineArkProperties.getImageSize() : "2K");
            body.put("stream", false);
            body.put("watermark", volcengineArkProperties.isWatermark());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(volcengineArkProperties.getApiKey());

            String url = trimTrailingSlash(volcengineArkProperties.getBaseUrl()) + "/images/generations";
            ResponseEntity<String> resp = arkRestTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            r.put("httpStatus", resp.getStatusCode().value());
            r.put("latencyMs", System.currentTimeMillis() - start);
            r.put("sampleUrl", extractFirstImage(resp.getBody()));
            r.put("ok", resp.getStatusCode().is2xxSuccessful() && r.get("sampleUrl") != null);
            if (!Boolean.TRUE.equals(r.get("ok"))) {
                r.put("rawSnippet", trimSnippet(resp.getBody()));
            }
        } catch (Exception e) {
            r.put("ok", false);
            r.put("latencyMs", System.currentTimeMillis() - start);
            r.put("error", safeError(e));
            log.warn("[Ark] generations 探测失败: {}", safeError(e));
        }
        return r;
    }

    private Map<String, Object> probeGenerations() {
        Map<String, Object> r = new LinkedHashMap<>();
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", yunwuProperties.getImageModel());
            body.put("prompt", "studio portrait of a young woman with elegant black bob haircut, soft natural lighting, high quality");
            body.put("n", 1);
            body.put("size", "1024x1024");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(yunwuProperties.getApiKey());

            String url = trimTrailingSlash(yunwuProperties.getBaseUrl()) + "/images/generations";
            ResponseEntity<String> resp = yunwuRestTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            r.put("httpStatus", resp.getStatusCode().value());
            r.put("latencyMs", System.currentTimeMillis() - start);
            r.put("sampleUrl", extractFirstImage(resp.getBody()));
            r.put("ok", resp.getStatusCode().is2xxSuccessful() && r.get("sampleUrl") != null);
            if (!Boolean.TRUE.equals(r.get("ok"))) {
                // 提取响应里的报错信息（不包含 Key）
                r.put("rawSnippet", trimSnippet(resp.getBody()));
            }
        } catch (Exception e) {
            r.put("ok", false);
            r.put("latencyMs", System.currentTimeMillis() - start);
            r.put("error", safeError(e));
            log.warn("[Yunwu] generations 探测失败: {}", safeError(e));
        }
        return r;
    }

    private Map<String, Object> probeEdits() {
        Map<String, Object> r = new LinkedHashMap<>();
        long start = System.currentTimeMillis();
        try {
            byte[] tinyPng = buildTinyPng();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBearerAuth(yunwuProperties.getApiKey());

            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("model", yunwuProperties.getImageModel());
            form.add("prompt", "add a soft natural-looking bob haircut, keep face and skin unchanged");
            form.add("n", 1);
            form.add("size", "1024x1024");
            // multipart 文件字段：image（必须带文件名/Content-Type，让中转能识别）
            ByteArrayResource imgResource = new ByteArrayResource(tinyPng) {
                @Override
                public String getFilename() {
                    return "probe.png";
                }
            };
            form.add("image", imgResource);

            String url = trimTrailingSlash(yunwuProperties.getBaseUrl()) + "/images/edits";
            ResponseEntity<String> resp = yunwuRestTemplate.postForEntity(url, new HttpEntity<>(form, headers), String.class);
            r.put("httpStatus", resp.getStatusCode().value());
            r.put("latencyMs", System.currentTimeMillis() - start);
            r.put("sampleUrl", extractFirstImage(resp.getBody()));
            r.put("ok", resp.getStatusCode().is2xxSuccessful() && r.get("sampleUrl") != null);
            if (!Boolean.TRUE.equals(r.get("ok"))) {
                r.put("rawSnippet", trimSnippet(resp.getBody()));
            }
        } catch (Exception e) {
            r.put("ok", false);
            r.put("latencyMs", System.currentTimeMillis() - start);
            r.put("error", safeError(e));
            log.warn("[Yunwu] edits 探测失败: {}", safeError(e));
        }
        return r;
    }

    /** 从 OpenAI 兼容响应里取第一张图（兼容 url / b64_json 两种格式）。 */
    private String extractFirstImage(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("data");
            if (data.isArray() && !data.isEmpty()) {
                JsonNode first = data.get(0);
                JsonNode url = first.path("url");
                if (!url.isMissingNode() && !url.asText().isBlank()) {
                    return url.asText();
                }
                JsonNode b64 = first.path("b64_json");
                if (!b64.isMissingNode() && !b64.asText().isBlank()) {
                    // 不打印整段 base64，只回个标记，避免日志/响应过大
                    return "data:image/png;base64,<truncated:" + b64.asText().length() + "bytes>";
                }
            }
        } catch (Exception ignore) {
            // 非 JSON 或结构不一致：返回 null，让 ok=false
        }
        return null;
    }

    private String trimSnippet(String body) {
        if (body == null) return null;
        String s = body.length() > 400 ? body.substring(0, 400) + "..." : body;
        s = s.replace(yunwuProperties.getApiKey(), "***");
        if (volcengineArkProperties.getApiKey() != null) {
            s = s.replace(volcengineArkProperties.getApiKey(), "***");
        }
        return s;
    }

    private String safeError(Exception e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        msg = msg.replace(yunwuProperties.getApiKey(), "***");
        if (volcengineArkProperties.getApiKey() != null) {
            msg = msg.replace(volcengineArkProperties.getApiKey(), "***");
        }
        return msg;
    }

    private String trimTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    /**
     * 构造一张 1x1 像素的纯灰 PNG（约 67 字节），供 /v1/images/edits 探测使用。
     * 真正调测试戴效果时再换成完整人脸照即可。
     */
    private byte[] buildTinyPng() {
        // 预生成的合法 1x1 灰色 PNG
        return Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
    }

}

