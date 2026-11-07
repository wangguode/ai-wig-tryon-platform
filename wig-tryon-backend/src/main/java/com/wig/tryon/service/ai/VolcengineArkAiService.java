package com.wig.tryon.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wig.tryon.config.VolcengineArkProperties;
import com.wig.tryon.model.FaceAnalysisResult;
import com.wig.tryon.model.WigProduct;
import com.wig.tryon.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
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
 * 火山方舟 Doubao Seedream 直连：仅承担试戴生成。
 * detectFace / analyze 仍由 dashscope/seed/mock 链路承担。
 */
@Component
public class VolcengineArkAiService implements AiService {

    private static final Logger log = LoggerFactory.getLogger(VolcengineArkAiService.class);
    private static final int MAX_DIMENSION = 1024;
    private static final float JPEG_QUALITY = 0.85f;

    private final VolcengineArkProperties props;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    private final StorageService storageService;
    private final RestTemplate arkRestTemplate;

    public VolcengineArkAiService(VolcengineArkProperties props, PromptBuilder promptBuilder,
                                   ObjectMapper objectMapper, StorageService storageService,
                                   RestTemplateBuilder restTemplateBuilder) {
        this.props = props;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
        this.storageService = storageService;
        this.arkRestTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(15))
                .setReadTimeout(Duration.ofSeconds(Math.max(60, props.getTimeoutSeconds())))
                .build();
    }

    public boolean hasApiKey() {
        return props.hasApiKey();
    }

    @Override
    public boolean detectFace(byte[] imageBytes, String mimeType) {
        throw new UnsupportedOperationException("VolcengineArkAiService 不承担人脸检测");
    }

    @Override
    public FaceAnalysisResult analyze(byte[] imageBytes, String mimeType, String preference, List<WigProduct> catalog) {
        throw new UnsupportedOperationException("VolcengineArkAiService 不承担分析推荐");
    }

    @Override
    public GenResult generateTryon(GenContext ctx, IntConsumer progress) {
        progress.accept(10);

        String wigRef = (ctx.wigImageUrl() != null && !ctx.wigImageUrl().isBlank())
                ? ctx.wigImageUrl()
                : (ctx.product() == null ? null : ctx.product().refImage);
        byte[] refBytes = loadWigRefBytes(wigRef);
        if (refBytes == null) {
            throw new IllegalStateException("假发本体图未加载，Doubao Seedream 双图试戴需要 productImage");
        }
        log.info("[Ark/Seedream] 假发本体图已加载 size={}KB ref={}", refBytes.length / 1024, wigRef);

        String prompt = promptBuilder.tryonPromptWithBareWig(
                ctx.product(), ctx.analysis(), ctx.tryonType(), ctx.adjustNote(), ctx.wigDescriptionForAi());
        progress.accept(20);

        String model = resolveEffectiveModel(ctx.preferredModel());
        String userDataUri = toJpegDataUri(compressToJpeg(ctx.userBytes(), "user"));
        String wigDataUri = toJpegDataUri(compressToJpeg(refBytes, "wig-ref"));
        progress.accept(35);

        List<String> images = new ArrayList<>();
        images.add(userDataUri);
        images.add(wigDataUri);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("prompt", prompt);
        body.put("image", images);
        body.put("sequential_image_generation", "disabled");
        body.put("response_format", "url");
        body.put("stream", false);
        body.put("watermark", props.isWatermark());
        if (props.getImageSize() != null && !props.getImageSize().isBlank()) {
            body.put("size", props.getImageSize());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(props.getApiKey());

        String url = trimTrailingSlash(props.getBaseUrl()) + "/images/generations";
        log.info("[Ark/Seedream] 提交 model={}, 2img=true, promptLen={}", model, prompt.length());
        progress.accept(45);

        ResponseEntity<String> resp;
        try {
            resp = arkRestTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
        } catch (Exception e) {
            throw new RuntimeException("火山方舟 Seedream 调用失败: " + safeMsg(e), e);
        }
        progress.accept(85);

        String remoteUrl = extractImageUrl(resp.getBody());
        if (remoteUrl == null) {
            String snippet = resp.getBody() == null ? "null"
                    : (resp.getBody().length() > 300 ? resp.getBody().substring(0, 300) + "..." : resp.getBody());
            throw new IllegalStateException("火山方舟响应未包含图像 URL: " + snippet);
        }
        log.info("[Ark/Seedream] 成功 remoteUrl={}", remoteUrl.length() > 80 ? remoteUrl.substring(0, 80) + "..." : remoteUrl);

        String localUrl = downloadAndStore(remoteUrl);
        progress.accept(95);
        String strategy = "ark|seedream|" + model + "|2img";
        return new GenResult(localUrl, prompt, 0, -1.0, null, strategy);
    }

    @Override
    public boolean isMock() {
        return false;
    }

    @Override
    public String modeName() {
        return "real";
    }

    private String resolveEffectiveModel(String preferredModel) {
        if (preferredModel == null || preferredModel.isBlank()) {
            return props.getImageModel();
        }
        return switch (preferredModel.toLowerCase()) {
            case "doubao-seedream-5", "seedream-5", "seedream-5-lite" -> props.getImageModel();
            default -> props.getImageModel();
        };
    }

    private String extractImageUrl(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.has("error")) {
                throw new RuntimeException("火山方舟返回错误: " + root.path("error").path("message").asText());
            }
            JsonNode data = root.path("data");
            if (data.isArray() && !data.isEmpty()) {
                String url = textOrNull(data.get(0).path("url"));
                if (url != null) return url;
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[Ark/Seedream] 解析响应失败", e);
        }
        return null;
    }

    private String downloadAndStore(String url) {
        try {
            String objectName = "tryon-results/ark-seedream-" + UUID.randomUUID() + ".png";
            return storageService.saveFromUrl(url, objectName).url();
        } catch (Exception e) {
            throw new IllegalStateException("下载火山方舟结果图失败: " + safeMsg(e), e);
        }
    }

    private byte[] loadWigRefBytes(String ref) {
        Path path = resolveWigRefPath(ref);
        if (path == null) return null;
        String fname = path.getFileName().toString().toLowerCase();
        if (fname.endsWith(".svg")) return null;
        try {
            if (!Files.exists(path)) {
                log.warn("[Ark/Seedream] 假发本体图不存在: {}", path);
                return null;
            }
            return Files.readAllBytes(path);
        } catch (IOException e) {
            log.warn("[Ark/Seedream] 读取假发本体图失败: {}", path, e);
            return null;
        }
    }

    private Path resolveWigRefPath(String ref) {
        if (ref == null || ref.isBlank()) return null;
        String trimmed = ref.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            int filesIdx = trimmed.indexOf("/files/");
            if (filesIdx >= 0) {
                return Paths.get("./data/storage", trimmed.substring(filesIdx + "/files/".length())).normalize();
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

    private byte[] compressToJpeg(byte[] origin, String tag) {
        if (origin == null || origin.length == 0) return origin;
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(origin));
            if (src == null) {
                log.warn("[Ark/Seedream] {} 图像无法解码，跳过压缩", tag);
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
            if (!writers.hasNext()) return origin;
            ImageWriter writer = writers.next();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
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
            log.info("[Ark/Seedream] {} 压缩 {}x{}({}KB) -> {}x{}({}KB)",
                    tag, w, h, origin.length / 1024, nw, nh, out.size() / 1024);
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("[Ark/Seedream] {} 压缩失败，使用原图: {}", tag, e.getMessage());
            return origin;
        }
    }

    private String toJpegDataUri(byte[] jpeg) {
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg);
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        String t = node.asText("");
        return t.isBlank() ? null : t;
    }

    private String trimTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private String safeMsg(Exception e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (props.getApiKey() != null && !props.getApiKey().isBlank()) {
            return msg.replace(props.getApiKey(), "***");
        }
        return msg;
    }
}
