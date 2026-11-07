package com.wig.tryon.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wig.tryon.config.YunwuProperties;
import com.wig.tryon.model.FaceAnalysisResult;
import com.wig.tryon.model.WigProduct;
import com.wig.tryon.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntConsumer;

/**
 * 中转站（OpenAI 兼容代理，例 yunwu.ai）实现：仅承担"试戴生成"这一步。
 * <p>
 * 设计取舍：
 * - detectFace / analyze 故意不实现：这两步仍由 dashscope/seed/mock 链路承担，避免重复成本。
 *   AiServiceRouter.generateTryon 才会路由到本实现。
 * - 端点选择由 yunwu.tryon-endpoint 配置：
 *   1) chat（推荐）：/v1/chat/completions 多模态消息，content 数组里塞 用户图 + 假发图 + 文字 prompt
 *      —— 这是 gpt-image / gemini-image 系列做"多图融合"的主流路径。
 *   2) edits：/v1/images/edits 标准 OpenAI 协议，只接 1 张图（用户图）+ prompt，假发只能用文字描述。
 * - 假发参考图为 SVG 时（项目当前情况）自动降级为 单图+文字描述，避免视觉模型拒收 SVG。
 * - 返回的 base64 / url 统一通过 StorageService 落地到本地，给前端稳定 URL。
 */
@Component
public class YunwuOpenAiAiService implements AiService {

    private static final Logger log = LoggerFactory.getLogger(YunwuOpenAiAiService.class);

    /** 上传给中转站前的最大边长（px）。中转站对单次请求体大小敏感，超过会直接 reset 连接。 */
    private static final int MAX_DIMENSION = 1024;
    /** 压缩后 JPEG 质量。0.85 是体积/质量的折中。 */
    private static final float JPEG_QUALITY = 0.85f;

    private final YunwuProperties props;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    private final StorageService storageService;
    /** 长超时 RestTemplate：图像生成 30-120s 是常态，共享 RestTemplate 60s 不够 */
    private final RestTemplate yunwuRestTemplate;

    public YunwuOpenAiAiService(YunwuProperties props, PromptBuilder promptBuilder, ObjectMapper objectMapper,
                                StorageService storageService, RestTemplateBuilder restTemplateBuilder) {
        this.props = props;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
        this.storageService = storageService;
        this.yunwuRestTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(15))
                .setReadTimeout(Duration.ofSeconds(Math.max(60, props.getTimeoutSeconds())))
                .build();
    }

    public boolean hasApiKey() {
        return props.hasApiKey();
    }

    @Override
    public boolean detectFace(byte[] imageBytes, String mimeType) {
        // 不在本实现里做：分析/检测交给 dashscope/seed/mock。
        throw new UnsupportedOperationException("YunwuOpenAiAiService 不承担人脸检测");
    }

    @Override
    public FaceAnalysisResult analyze(byte[] imageBytes, String mimeType, String preference, List<WigProduct> catalog) {
        throw new UnsupportedOperationException("YunwuOpenAiAiService 不承担分析推荐");
    }

    @Override
    public GenResult generateTryon(GenContext ctx, IntConsumer progress) {
        progress.accept(10);

        // 假发本体图：仅当是非 SVG 位图时才传给视觉模型（gpt-image/gemini-image 不收 SVG）
        // 优先 ctx.wigImageUrl()（来自 WigAsset.productImage），缺则回退老 refImage
        String wigRef = (ctx.wigImageUrl() != null && !ctx.wigImageUrl().isBlank())
                ? ctx.wigImageUrl()
                : (ctx.product() == null ? null : ctx.product().refImage);
        byte[] refBytes = loadWigRefBytes(wigRef);
        String refMime = refBytes == null ? null : detectMimeFromRef(wigRef);
        if (refBytes != null) {
            log.info("[Yunwu] 假发本体图已加载 size={}KB ref={}", refBytes.length / 1024, wigRef);
        } else if (wigRef != null && !wigRef.isBlank()) {
            log.warn("[Yunwu] 假发本体图未加载，降级为单图+文字描述 ref={}", wigRef);
        }
        boolean hasRef = refBytes != null;

        // 与百炼 qwen-image-edit 对齐：双图时用 bare-wig 英文 prompt + wigDescriptionForAi
        String prompt = hasRef
                ? promptBuilder.tryonPromptWithBareWigEn(ctx.product(), ctx.analysis(),
                        ctx.tryonType(), ctx.adjustNote(), ctx.wigDescriptionForAi(), true)
                : promptBuilder.tryonPromptWithBareWigEn(ctx.product(), ctx.analysis(),
                        ctx.tryonType(), ctx.adjustNote(), ctx.wigDescriptionForAi(), false);
        progress.accept(20);

        String endpoint = (props.getTryonEndpoint() == null ? "chat" : props.getTryonEndpoint().trim().toLowerCase());
        String effectiveModel = resolveEffectiveModel(ctx.preferredModel());
        // flux-2-max：UI 展示 Flux，底层 gpt-image-2，但必须走 chat 双图（用户图+假发参考图）
        boolean fluxDualImage = "flux-2-max".equalsIgnoreCase(ctx.preferredModel());
        if (fluxDualImage) {
            log.info("[Yunwu] flux-2-max → chat 双图融合, model={}", effectiveModel);
        }
        // 直连 gpt-image-2 仍走 edits 单图；flux 别名除外
        boolean forceEdits = !fluxDualImage
                && ("grok-imagine-image-pro".equalsIgnoreCase(effectiveModel)
                || "gpt-image-2".equalsIgnoreCase(effectiveModel));
        String resultUrl;
        String strategy;
        try {
            if ("edits".equals(endpoint) || forceEdits) {
                if (forceEdits && !"edits".equals(endpoint)) {
                    log.info("[Yunwu] {} 不支持 chat 双图出图，改走 /images/edits", effectiveModel);
                }
                // edits 只上传用户图，prompt 不能写 Image 2，否则 Grok 等模型易 500
                String editsPrompt = (forceEdits || "edits".equals(endpoint)) && hasRef
                        ? promptBuilder.tryonPromptWithBareWigEn(ctx.product(), ctx.analysis(),
                                ctx.tryonType(), ctx.adjustNote(), ctx.wigDescriptionForAi(), false)
                        : prompt;
                resultUrl = callImagesEdits(ctx, editsPrompt, progress);
                strategy = strategyTag("edits", effectiveModel, ctx.preferredModel(), true);
            } else {
                resultUrl = callChatCompletions(ctx, refBytes, refMime, prompt, progress);
                strategy = strategyTag("chat", effectiveModel, ctx.preferredModel(), hasRef);
            }
        } catch (RuntimeException e) {
            // 直接抛出去，让 Router 决定 strict-real / 降级 seed
            log.warn("[Yunwu] 试戴生成失败: {}", safeMsg(e));
            throw e;
        }

        if (resultUrl == null) {
            throw new IllegalStateException("Yunwu 返回未包含可用图像");
        }
        progress.accept(95);
        return new GenResult(resultUrl, prompt, 0, -1.0,
                hasRef ? null : "假发参考图为 SVG，已降级为单图+文字描述", strategy);
    }

    @Override
    public boolean isMock() {
        return false;
    }

    @Override
    public String modeName() {
        return "real";
    }

    // ============================================================
    // 模型名解析：优先前端选择的 key，否则取配置
    // ============================================================
    /**
     * 将前端传来的 preferredModel key 映射到云雾实际 model 名。
     * key 与云雾 model 名对照表维护在此处，改一处即全局生效。
     * 若 key 不认识则回退 props.getImageModel()（当前配置默认值）。
     */
    private String resolveEffectiveModel(String preferredModel) {
        if (preferredModel == null || preferredModel.isBlank()) {
            return props.getImageModel();
        }
        return switch (preferredModel.toLowerCase()) {
            case "gpt-image-1"             -> "gpt-image-1-all";
            case "gpt-image-1.5"           -> "gpt-image-1.5";
            case "gpt-image-2"             -> "gpt-image-2";
            case "gemini-3-pro-image"      -> "gemini-3-pro-image-preview";
            case "gemini-3.1-flash-image"  -> "gemini-3.1-flash-image-preview";
            case "seedream-4.5"            -> "seedream-4.5";
            case "flux-2-max"              -> "gpt-image-2";
            case "qwen-yunwu"              -> "qwen-image-edit-2509";
            // qwen（百炼直连）和 fallback 都走默认配置；不应路由到这里但做兜底
            default -> props.getImageModel();
        };
    }

    /** strategyUsed 保留 UI 别名，便于录屏后核对「页面 Flux2 → 实际 gpt-image-2」。 */
    private static String strategyTag(String endpoint, String effectiveModel, String preferredModel, boolean singleImage) {
        String img = singleImage ? "1img" : "2img";
        String tag = "yunwu|" + endpoint + "|" + effectiveModel + "|" + img;
        if (preferredModel != null && !preferredModel.isBlank()
                && !preferredModel.equalsIgnoreCase(effectiveModel)) {
            tag += "|ui=" + preferredModel;
        }
        return tag;
    }

    // ============================================================
    // chat completions 多模态多图融合
    // ============================================================
    private String callChatCompletions(GenContext ctx, byte[] refBytes, String refMime,
                                       String prompt, IntConsumer progress) {
        // 关键：调中转站前必须压图，否则手机原图 4-8MB 编码 base64 后总请求体超 10MB，上游直接 reset 连接。
        byte[] userJpeg = compressToJpeg(ctx.userBytes(), "user");
        byte[] refJpeg = refBytes == null ? null : compressToJpeg(refBytes, "ref");

        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", prompt));
        content.add(imagePart(userJpeg, "image/jpeg"));
        if (refJpeg != null) {
            content.add(imagePart(refJpeg, "image/jpeg"));
        }
        log.info("[Yunwu] chat 请求体大小: prompt={}B, userImg={}KB, refImg={}KB",
                prompt.length(), userJpeg.length / 1024,
                refJpeg == null ? 0 : refJpeg.length / 1024);

        Map<String, Object> message = Map.of("role", "user", "content", content);
        Map<String, Object> body = new LinkedHashMap<>();
        String model = resolveEffectiveModel(ctx.preferredModel());
        body.put("model", model);
        body.put("messages", List.of(message));
        // gpt-image-2 云雾 chat 不接受 modalities=image；省略后仍可通过 content 双图融合出图
        if (!"gpt-image-2".equalsIgnoreCase(model)) {
            body.put("modalities", List.of("image", "text"));
        }
        body.put("n", 1);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(props.getApiKey());

        String url = trimTrailingSlash(props.getBaseUrl()) + props.getChatPath();
        progress.accept(40);
        // 中转站偶发性 SSL reset / 30s 超时，给 3 次重试，每次间隔 5s。
        // 实测 95% 的失败都是单点波动，重试一次能救回来。
        // retry 上限：2 次（1 次主调 + 1 次重试）。3 次时最坏耗时近 3 倍模型时间；
        // 实测中转站抽风通常瞬时，1 次重试足够覆盖大部分波动；真坏时让外层降级到 seed 更值。
        ResponseEntity<String> resp = postWithRetry(url, new HttpEntity<>(body, headers), 2);
        progress.accept(70);
        return persistFromResponse(resp.getBody(), "yunwu-chat");
    }

    private ResponseEntity<String> postWithRetry(String url, HttpEntity<?> entity, int maxAttempts) {
        Exception lastErr = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return yunwuRestTemplate.postForEntity(url, entity, String.class);
            } catch (Exception e) {
                lastErr = e;
                String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                log.warn("[Yunwu] 第 {}/{} 次调用失败: {}", attempt, maxAttempts, reason);
                if (attempt < maxAttempts) {
                    // 中转站偶发抽风通常是瞬时的，1s 内重试足够；之前 5s 间隔会拖累用户体验
                    try {
                        Thread.sleep(1000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("调用被中断", ie);
                    }
                }
            }
        }
        if (lastErr instanceof RuntimeException re) throw re;
        throw new RuntimeException("Yunwu 调用 " + maxAttempts + " 次均失败: " + safeMsg(lastErr), lastErr);
    }

    private Map<String, Object> imagePart(byte[] bytes, String mime) {
        String dataUri = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        return Map.of("type", "image_url", "image_url", Map.of("url", dataUri));
    }

    // ============================================================
    // images/edits 单图 + 文字
    // ============================================================
    private String callImagesEdits(GenContext ctx, String prompt, IntConsumer progress) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(props.getApiKey());

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("model", resolveEffectiveModel(ctx.preferredModel()));
        form.add("prompt", prompt);
        form.add("n", 1);
        form.add("size", "1024x1024");

        // 同 chat 路径，必须压图避免上游拒接大请求体
        byte[] userJpeg = compressToJpeg(ctx.userBytes(), "user");
        ByteArrayResource imgResource = new ByteArrayResource(userJpeg) {
            @Override
            public String getFilename() {
                return "user.jpg";
            }
        };
        form.add("image", imgResource);
        log.info("[Yunwu] edits 请求体大小: prompt={}B, userImg={}KB", prompt.length(), userJpeg.length / 1024);

        String url = trimTrailingSlash(props.getBaseUrl()) + props.getEditsPath();
        progress.accept(40);
        ResponseEntity<String> resp = postWithRetry(url, new HttpEntity<>(form, headers), 2);
        progress.accept(70);
        return persistFromResponse(resp.getBody(), "yunwu-edits");
    }

    // ============================================================
    // 解析响应 + 落地存储
    // ============================================================
    /** 兼容多种返回结构（OpenAI images / OpenAI chat / 一些代理的扩展），抽出图像并落到本地存储。 */
    private String persistFromResponse(String body, String tag) {
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("Yunwu 空响应");
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            // 1) /v1/images/* 风格：data[0].url 或 data[0].b64_json
            JsonNode data = root.path("data");
            if (data.isArray() && !data.isEmpty()) {
                JsonNode first = data.get(0);
                String url = textOrNull(first.path("url"));
                if (url != null) return downloadAndStore(url, tag);
                String b64 = textOrNull(first.path("b64_json"));
                if (b64 != null) return saveBase64(b64, tag);
            }
            // 2) chat completions 风格：choices[0].message.content / .images
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode message = choices.get(0).path("message");
                // 2a) message.images（部分代理扩展）
                JsonNode images = message.path("images");
                if (images.isArray() && !images.isEmpty()) {
                    JsonNode img0 = images.get(0);
                    String url = textOrNull(img0.path("image_url").path("url"));
                    if (url == null) url = textOrNull(img0.path("url"));
                    String b64 = textOrNull(img0.path("b64_json"));
                    if (url != null) return resolveEmbeddedOrDownload(url, tag);
                    if (b64 != null) return saveBase64(b64, tag);
                }
                // 2b) message.content 字符串里嵌 markdown ![](data:image/...) 或 url
                //     还兼容 doubao-seedream 这种把 [{"url":"..."}] 直接塞进 content 字符串的场景
                JsonNode contentNode = message.path("content");
                String txt = null;
                if (contentNode.isTextual()) {
                    txt = contentNode.asText();
                    // 优先尝试把 content 字符串当成 JSON 数组解析（doubao-seedream 风格）
                    String trimmed = txt.trim();
                    if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
                        try {
                            JsonNode inner = objectMapper.readTree(trimmed);
                            String url = null;
                            String b64 = null;
                            if (inner.isArray() && !inner.isEmpty()) {
                                JsonNode first0 = inner.get(0);
                                url = textOrNull(first0.path("url"));
                                if (url == null) url = textOrNull(first0.path("image_url").path("url"));
                                b64 = textOrNull(first0.path("b64_json"));
                            } else if (inner.isObject()) {
                                url = textOrNull(inner.path("url"));
                                if (url == null) url = textOrNull(inner.path("image_url").path("url"));
                                b64 = textOrNull(inner.path("b64_json"));
                            }
                            if (url != null) return resolveEmbeddedOrDownload(url, tag);
                            if (b64 != null) return saveBase64(b64, tag);
                        } catch (Exception ignore) {
                            // 不是 JSON，继续走 markdown / 纯 URL 提取
                        }
                    }
                } else if (contentNode.isArray()) {
                    StringBuilder sb = new StringBuilder();
                    for (JsonNode part : contentNode) {
                        // 嵌套图像直接抽
                        String url = textOrNull(part.path("image_url").path("url"));
                        if (url == null) url = textOrNull(part.path("url"));
                        if (url != null) return resolveEmbeddedOrDownload(url, tag);
                        if (part.has("text")) sb.append(part.get("text").asText()).append('\n');
                    }
                    txt = sb.toString();
                }
                if (txt != null) {
                    String found = findUrlInText(txt);
                    if (found != null) return resolveEmbeddedOrDownload(found, tag);
                }
            }
        } catch (Exception e) {
            log.warn("[Yunwu] 解析响应失败: {}", body.length() > 400 ? body.substring(0, 400) : body, e);
        }
        // 拿不到图：把响应前 300 字打到日志（不含 Key），便于对接调试
        String snippet = body.length() > 300 ? body.substring(0, 300) + "..." : body;
        throw new IllegalStateException("Yunwu 响应未识别到图像: " + snippet);
    }

    private String resolveEmbeddedOrDownload(String url, String tag) {
        if (url.startsWith("data:")) {
            int comma = url.indexOf(',');
            if (comma > 0) {
                String b64 = url.substring(comma + 1);
                return saveBase64(b64, tag);
            }
        }
        return downloadAndStore(url, tag);
    }

    private String downloadAndStore(String url, String tag) {
        try {
            String objectName = "tryon-results/" + tag + "-" + UUID.randomUUID() + ".png";
            return storageService.saveFromUrl(url, objectName).url();
        } catch (Exception e) {
            throw new IllegalStateException("下载远程结果图失败: " + safeMsg(e), e);
        }
    }

    private String saveBase64(String base64, String tag) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            String objectName = "tryon-results/" + tag + "-" + UUID.randomUUID() + ".png";
            return storageService.saveBytes(bytes, objectName).url();
        } catch (IOException e) {
            throw new IllegalStateException("落地结果图失败: " + safeMsg(e), e);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Base64 解码失败: " + safeMsg(e), e);
        }
    }

    // ============================================================
    // 工具
    // ============================================================
    /** 读取假发本体图字节；与 DashScope materializeWigImage 同源路径规则。 */
    private byte[] loadWigRefBytes(String ref) {
        Path path = resolveWigRefPath(ref);
        if (path == null) return null;
        String fname = path.getFileName().toString().toLowerCase();
        if (fname.endsWith(".svg")) return null;
        try {
            if (!Files.exists(path)) {
                log.warn("[Yunwu] 假发本体图不存在: {}", path);
                return null;
            }
            return Files.readAllBytes(path);
        } catch (IOException e) {
            log.warn("[Yunwu] 读取假发本体图失败: {}", path, e);
            return null;
        }
    }

    private String detectMimeFromRef(String ref) {
        if (ref == null) return "image/jpeg";
        String low = ref.toLowerCase();
        if (low.contains(".png")) return "image/png";
        if (low.contains(".webp")) return "image/webp";
        if (low.contains(".gif")) return "image/gif";
        return "image/jpeg";
    }

    /**
     * 解析 WigAsset.productImage URL 为本地路径。
     * 管理端上传后通常是 http://localhost:8080/files/wig-assets/x.jpg，必须映射到 ./data/storage/。
     */
    private Path resolveWigRefPath(String ref) {
        if (ref == null || ref.isBlank()) return null;
        String trimmed = ref.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            int filesIdx = trimmed.indexOf("/files/");
            if (filesIdx >= 0) {
                String objectName = trimmed.substring(filesIdx + "/files/".length());
                return Paths.get("./data/storage", objectName).normalize();
            }
            return null;
        }
        if (trimmed.startsWith("/files/")) {
            return Paths.get("./data/storage", trimmed.substring("/files/".length())).normalize();
        }
        if (trimmed.startsWith("/images/")) {
            return Paths.get("..", "ai-hairstyle-mini-program-ui", "public", trimmed.substring(1)).normalize();
        }
        return Paths.get(trimmed).normalize();
    }

    private String detectMime(String fname) {
        if (fname.endsWith(".png")) return "image/png";
        if (fname.endsWith(".webp")) return "image/webp";
        if (fname.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }

    private String trimTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        String t = node.asText("");
        return t.isBlank() ? null : t;
    }

    private String findUrlInText(String text) {
        if (text == null) return null;
        // 简单查找 markdown 图像或 http(s) url
        int md = text.indexOf("](http");
        if (md > 0) {
            int end = text.indexOf(')', md);
            if (end > md + 2) return text.substring(md + 2, end);
        }
        int mdData = text.indexOf("](data:");
        if (mdData > 0) {
            int end = text.indexOf(')', mdData);
            if (end > mdData + 2) return text.substring(mdData + 2, end);
        }
        int http = text.indexOf("http");
        if (http >= 0) {
            int end = http;
            while (end < text.length() && !Character.isWhitespace(text.charAt(end)) && text.charAt(end) != ')' && text.charAt(end) != '"') {
                end++;
            }
            String candidate = text.substring(http, end);
            if (candidate.startsWith("http://") || candidate.startsWith("https://")) return candidate;
        }
        return null;
    }

    /**
     * 把图像字节压成长边 ≤ MAX_DIMENSION 的 JPEG，质量 JPEG_QUALITY。
     * 中转站对单次请求体大小敏感，手机原图 4-8MB 不压会被 reset 连接。
     * 失败时返回原字节，避免阻塞主流程。
     */
    private byte[] compressToJpeg(byte[] origin, String tag) {
        if (origin == null || origin.length == 0) return origin;
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(origin));
            if (src == null) {
                log.warn("[Yunwu] {} 图像无法解码，跳过压缩", tag);
                return origin;
            }
            int w = src.getWidth();
            int h = src.getHeight();
            int max = Math.max(w, h);
            double scale = max > MAX_DIMENSION ? (double) MAX_DIMENSION / max : 1.0;
            int nw = (int) Math.round(w * scale);
            int nh = (int) Math.round(h * scale);

            // JPEG 不支持 alpha，统一画到 RGB 画布；同时执行缩放（双三次插值）
            BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = dst.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, nw, nh, null);
            g.dispose();

            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) {
                log.warn("[Yunwu] 找不到 JPEG writer，跳过压缩");
                return origin;
            }
            ImageWriter writer = writers.next();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
                writer.setOutput(ios);
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(JPEG_QUALITY);
                }
                writer.write(null, new IIOImage(dst, null, null), param);
            } finally {
                writer.dispose();
            }
            byte[] result = out.toByteArray();
            log.debug("[Yunwu] {} 压缩 {}x{}({}KB) -> {}x{}({}KB)", tag, w, h, origin.length / 1024,
                    nw, nh, result.length / 1024);
            return result;
        } catch (Exception e) {
            log.warn("[Yunwu] {} 图像压缩失败，使用原字节: {}", tag, e.getMessage());
            return origin;
        }
    }

    private String safeMsg(Throwable e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (props.getApiKey() != null && !props.getApiKey().isBlank()) {
            msg = msg.replace(props.getApiKey(), "***");
        }
        return msg;
    }
}
