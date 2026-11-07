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
import java.time.Duration;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.IntConsumer;

/**
 * Flux Kontext via 云雾的 OpenAI dall-e-3 兼容 edits 端点。
 * <p>
 * 接入背景：客户反馈 AI 痕迹明显，主动推荐 Flux Kontext（BFL 2025 的图像编辑模型，
 * 单图 + prompt 做局部编辑、强保 ID）。正对症客户痛点。
 * <p>
 * 关键：model 名必须是 <b>flux.1-kontext-pro</b>（带 .1），不是 flux-kontext-pro。
 * 后者是 BFL 在 Replicate 上的原生命名，云雾 default 分组没有渠道；
 * 前者是云雾平台正式上架名，default 分组按次 ¥0.12，开箱可用。
 * <p>
 * 调用协议（同步 multipart/form-data，OpenAI dall-e-3 edits 兼容）：
 * <pre>
 *   POST {baseUrl}/images/edits
 *   Authorization: Bearer {YUNWU_API_KEY}
 *   Content-Type: multipart/form-data
 *   form: model=flux.1-kontext-pro
 *         prompt={text}
 *         image={binary user photo, jpg/png ≤ 4MB}
 *         n=1
 *         size=1024x1024
 *   → 同步返回 {"data":[{"url":"https://..."}]} 或 {"data":[{"b64_json":"..."}]}
 * </pre>
 * <p>
 * 与 {@link YunwuOpenAiAiService} 关键差异：
 *   - 后者 chat 端点支持双图（用户图 + 假发参考图）多模态融合
 *   - 本类 edits 端点是 dall-e 标准协议，**只接 1 张图**（用户图）
 *     假发款式只能 prompt 文字描述 —— 但 Flux Kontext 单图保 ID 编辑正是它的强项
 * <p>
 * 三模式约束：不实现 detectFace/analyze（throw UOE），这两步仍由 dashscope/seed/mock 链路承担。
 *
 * @see PromptBuilder#tryonPromptForFlux
 */
@Component
public class YunwuFluxKontextAiService implements AiService {

    private static final Logger log = LoggerFactory.getLogger(YunwuFluxKontextAiService.class);

    /** 上传给中转站前的最大边长（px）。中转站对单次请求体大小敏感。 */
    private static final int MAX_DIMENSION = 1024;
    /** 压缩后 JPEG 质量。0.85 是体积/质量的折中。 */
    private static final float JPEG_QUALITY = 0.85f;

    private final YunwuProperties props;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    private final StorageService storageService;
    private final RestTemplate yunwuRestTemplate;

    public YunwuFluxKontextAiService(YunwuProperties props, PromptBuilder promptBuilder,
                                     ObjectMapper objectMapper, StorageService storageService,
                                     RestTemplateBuilder restTemplateBuilder) {
        this.props = props;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
        this.storageService = storageService;
        // Flux Kontext 单次推理 30~90s，超时给到 120s 兜底
        this.yunwuRestTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(15))
                .setReadTimeout(Duration.ofSeconds(Math.max(120, props.getTimeoutSeconds())))
                .build();
    }

    public boolean hasApiKey() {
        return props.hasApiKey();
    }

    @Override
    public boolean detectFace(byte[] imageBytes, String mimeType) {
        throw new UnsupportedOperationException("YunwuFluxKontextAiService 不承担人脸检测");
    }

    @Override
    public FaceAnalysisResult analyze(byte[] imageBytes, String mimeType, String preference, List<WigProduct> catalog) {
        throw new UnsupportedOperationException("YunwuFluxKontextAiService 不承担分析推荐");
    }

    @Override
    public GenResult generateTryon(GenContext ctx, IntConsumer progress) {
        progress.accept(10);
        String prompt = promptBuilder.tryonPromptForFlux(ctx.product(), ctx.analysis(),
                ctx.tryonType(), ctx.adjustNote(), ctx.wigDescriptionForAi());
        progress.accept(20);

        String resultUrl;
        try {
            resultUrl = callImagesEdits(ctx, prompt, progress);
        } catch (RuntimeException e) {
            log.warn("[YunwuFlux] 试戴生成失败: {}", safeMsg(e));
            throw e;
        }
        if (resultUrl == null) {
            throw new IllegalStateException("Flux Kontext 返回未包含可用图像");
        }
        progress.accept(95);
        return new GenResult(resultUrl, prompt, 0, -1.0,
                "Flux Kontext 单图编辑：假发款式由文字描述指定",
                strategyTagForFlux(ctx.preferredModel()));
    }

    private String strategyTagForFlux(String preferredModel) {
        String alias = (preferredModel == null || preferredModel.isBlank()) ? "flux" : preferredModel;
        return "yunwu|flux|" + props.getFluxModel() + "|ui=" + alias;
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
    // dall-e-3 edits 兼容端点（multipart/form-data 同步调用）
    // ============================================================
    private String callImagesEdits(GenContext ctx, String prompt, IntConsumer progress) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(props.getApiKey());

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("model", props.getFluxModel());
        form.add("prompt", prompt);
        form.add("n", 1);
        // size 字段：OpenAI dall-e 协议必填。Flux Kontext 内部会按 input_image 比例处理
        form.add("size", "1024x1024");

        // 必须压图：手机原图 4-8MB 编码 multipart 后请求体超 10MB，云雾会直接 reset 连接。
        // OpenAI dall-e 协议要求 image 是 < 4MB 的方形 png；Flux Kontext 实测 jpg 也接受。
        byte[] userJpeg = compressToJpeg(ctx.userBytes(), "user");
        ByteArrayResource imgResource = new ByteArrayResource(userJpeg) {
            @Override
            public String getFilename() {
                return "user.jpg";
            }
        };
        form.add("image", imgResource);
        log.info("[YunwuFlux] dall-e edits 请求: model={}, prompt={}B, userImg(jpeg)={}KB",
                props.getFluxModel(), prompt.length(), userJpeg.length / 1024);

        String url = trimTrailingSlash(props.getBaseUrl()) + props.getEditsPath();
        progress.accept(40);
        ResponseEntity<String> resp = postWithRetry(url, new HttpEntity<>(form, headers), 2);
        progress.accept(75);
        return persistFromResponse(resp.getBody(), "yunwu-flux");
    }

    private ResponseEntity<String> postWithRetry(String url, HttpEntity<?> entity, int maxAttempts) {
        Exception lastErr = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return yunwuRestTemplate.postForEntity(url, entity, String.class);
            } catch (Exception e) {
                lastErr = e;
                log.warn("[YunwuFlux] 第 {}/{} 次调用失败: {}", attempt, maxAttempts, safeMsg(e));
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(1500L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("调用被中断", ie);
                    }
                }
            }
        }
        if (lastErr instanceof RuntimeException re) throw re;
        throw new RuntimeException("Flux Kontext 调用 " + maxAttempts + " 次均失败: " + safeMsg(lastErr), lastErr);
    }

    // ============================================================
    // 解析响应 + 落地存储
    // ============================================================
    /** 兼容 OpenAI images 标准返回 + 部分代理的扩展返回。 */
    private String persistFromResponse(String body, String tag) {
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("Flux Kontext 空响应");
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            // 标准 OpenAI 返回：data[0].url 或 data[0].b64_json
            JsonNode data = root.path("data");
            if (data.isArray() && !data.isEmpty()) {
                JsonNode first = data.get(0);
                String url = textOrNull(first.path("url"));
                if (url != null) return resolveEmbeddedOrDownload(url, tag);
                String b64 = textOrNull(first.path("b64_json"));
                if (b64 != null) return saveBase64(b64, tag);
            }
            // 兜底：response 不规范时尝试在 choices[0].message.content 提取
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode contentNode = choices.get(0).path("message").path("content");
                if (contentNode.isTextual()) {
                    String txt = contentNode.asText();
                    String found = findUrlInText(txt);
                    if (found != null) return resolveEmbeddedOrDownload(found, tag);
                }
            }
        } catch (Exception e) {
            log.warn("[YunwuFlux] 解析响应失败: {}", truncate(body, 400), e);
        }
        throw new IllegalStateException("Flux Kontext 响应未识别到图像: " + truncate(body, 300));
    }

    private String resolveEmbeddedOrDownload(String url, String tag) {
        if (url.startsWith("data:")) {
            int comma = url.indexOf(',');
            if (comma > 0) {
                return saveBase64(url.substring(comma + 1), tag);
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
        int md = text.indexOf("](http");
        if (md > 0) {
            int end = text.indexOf(')', md);
            if (end > md + 2) return text.substring(md + 2, end);
        }
        int http = text.indexOf("http");
        if (http >= 0) {
            int end = http;
            while (end < text.length() && !Character.isWhitespace(text.charAt(end))
                    && text.charAt(end) != ')' && text.charAt(end) != '"') {
                end++;
            }
            String candidate = text.substring(http, end);
            if (candidate.startsWith("http://") || candidate.startsWith("https://")) return candidate;
        }
        return null;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private byte[] compressToJpeg(byte[] origin, String tag) {
        if (origin == null || origin.length == 0) return origin;
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(origin));
            if (src == null) {
                log.warn("[YunwuFlux] {} 图像无法解码，跳过压缩", tag);
                return origin;
            }
            int w = src.getWidth();
            int h = src.getHeight();
            int max = Math.max(w, h);
            double scale = max > MAX_DIMENSION ? (double) MAX_DIMENSION / max : 1.0;
            int nw = (int) Math.round(w * scale);
            int nh = (int) Math.round(h * scale);

            BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = dst.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, nw, nh, null);
            g.dispose();

            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) {
                log.warn("[YunwuFlux] 找不到 JPEG writer，跳过压缩");
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
            log.debug("[YunwuFlux] {} 压缩 {}x{}({}KB) -> {}x{}({}KB)", tag, w, h, origin.length / 1024,
                    nw, nh, result.length / 1024);
            return result;
        } catch (Exception e) {
            log.warn("[YunwuFlux] {} 图像压缩失败，使用原字节: {}", tag, e.getMessage());
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
