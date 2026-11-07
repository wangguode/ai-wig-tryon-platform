package com.wig.tryon.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wig.tryon.config.DashScopeProperties;
import com.wig.tryon.model.FaceAnalysisResult;
import com.wig.tryon.model.WigProduct;
import com.wig.tryon.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntConsumer;

/**
 * 阿里云百炼 DashScope 实现（中国区直连）。
 * - 视觉分析：qwen-vl-max（多模态）
 * - 试戴生成：wanx2.1-imageedit（异步任务：提交 -> 轮询）
 *
 * 说明：图像编辑接口需要可公网访问的图片 URL；本地 MinIO 替代方案下，
 * 真实联调时请把 app.storage.public-base-url 配置为可公网访问的地址（或接入 OSS）。
 */
@Component
public class DashScopeAiService implements AiService {

    private static final Logger log = LoggerFactory.getLogger(DashScopeAiService.class);
    /** 与 Yunwu 一致：百炼 multimodal 对大 base64 敏感，原图 3648px 直传易降质/超时 */
    private static final int MAX_DIMENSION = 1024;
    private static final float JPEG_QUALITY = 0.85f;

    private final DashScopeProperties props;
    private final PromptBuilder promptBuilder;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final StorageService storageService;

    public DashScopeAiService(DashScopeProperties props, PromptBuilder promptBuilder,
                              RestTemplate restTemplate, ObjectMapper objectMapper,
                              StorageService storageService) {
        this.props = props;
        this.promptBuilder = promptBuilder;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.storageService = storageService;
    }

    @Override
    public boolean detectFace(byte[] imageBytes, String mimeType) {
        try {
            String text = callVision("请判断这张图片中是否有清晰的正面人脸。只回复 YES 或 NO，不要其他内容。",
                    "你是图像审核助手。", imageBytes, mimeType);
            return text != null && text.toUpperCase().contains("YES");
        } catch (Exception e) {
            log.warn("[DashScope] 人脸检测失败，默认通过", e);
            return true;
        }
    }

    @Override
    public FaceAnalysisResult analyze(byte[] imageBytes, String mimeType, String preference, List<WigProduct> catalog) {
        String system = promptBuilder.analysisSystemPrompt(preference, catalog);
        String user = promptBuilder.analysisUserPrompt(preference);
        String content = callVision(user, system, imageBytes, mimeType);
        log.info("[DashScope] 分析原始返回: {}", content);
        return parseAnalysis(content);
    }

    @Override
    public GenResult generateTryon(GenContext ctx, IntConsumer progress) {
        // qwen-image-edit / qwen-image-edit-plus 走新版 multimodal-generation 同步接口（~10-25s），
        // 这是阿里 Qwen 团队专门为图像「编辑」做的模型，主体保留远好于 wan2.x 图生图，速度也更快。
        // 其它模型保留原 wan / wanx 异步任务流程。
        String editModel = props.getImageEditModel();
        if (editModel != null && editModel.toLowerCase().startsWith("qwen-image-edit")) {
            return generateTryonViaQwenEdit(ctx, progress);
        }

        String prompt = promptBuilder.tryonPrompt(ctx.product(), ctx.analysis(), ctx.tryonType(), ctx.adjustNote());
        progress.accept(15);
        String baseImage = resolveBaseImage(ctx);
        // 优先用 ctx.wigImageUrl()（来自 WigAsset 的"光秃秃假发本体图"）；缺则回退老 refImage 字段
        String referenceImage = resolveWigImageForAi(ctx);
        String maskImage = resolveHairMaskImage(ctx);
        AttemptSpec firstSpec = buildFirstAttemptSpec();
        String firstTaskId = submitImageTaskWithStrategy(firstSpec, prompt, baseImage, referenceImage, maskImage);
        progress.accept(35);
        String url = pollImageTask(firstTaskId, progress);

        double firstScore = computeChangeScore(ctx.userBytes(), url);
        int retryCount = 0;
        String qualityNote = null;
        String strategyUsed = firstSpec.strategyTag;
        if (needRetryForInsufficientChange(ctx, firstScore)) {
            retryCount = 1;
            qualityNote = String.format("首轮变化较小(%.2f)，已自动进行强化重试", firstScore);
            progress.accept(55);
            String retryPrompt = strengthenPrompt(prompt, ctx.product());
            String retryTaskId = submitImageTaskWithStrategy(firstSpec, retryPrompt, baseImage, referenceImage, maskImage);
            String retryUrl = pollImageTask(retryTaskId, progress);
            double retryScore = computeChangeScore(ctx.userBytes(), retryUrl);
            if (retryScore >= firstScore) {
                url = retryUrl;
                prompt = retryPrompt;
                firstScore = retryScore;
            }

            AttemptSpec backupSpec = buildBackupAttemptSpec(firstSpec);
            if (backupSpec != null && retryScore < props.getMinChangeScore()) {
                retryCount = 2;
                String backupPrompt = strengthenPrompt(retryPrompt, ctx.product());
                String backupTaskId = submitImageTaskWithStrategy(backupSpec, backupPrompt, baseImage, referenceImage, maskImage);
                String backupUrl = pollImageTask(backupTaskId, progress);
                double backupScore = computeChangeScore(ctx.userBytes(), backupUrl);
                if (backupScore >= firstScore) {
                    url = backupUrl;
                    prompt = backupPrompt;
                    firstScore = backupScore;
                    strategyUsed = backupSpec.strategyTag;
                }
                retryScore = Math.max(retryScore, backupScore);
            }

            if (retryScore < props.getMinChangeScore()) {
                qualityNote = String.format("变化仍偏小(%.2f)，建议换款式或二次局部调整", retryScore);
            }
            log.info("[DashScope] 变化不足重试完成 firstScore={}, bestScore={}, strategy={}", firstScore, retryScore, strategyUsed);
        }

        // 人脸区护栏（默认关闭）：仅在阈值 > 0 时启用，给出"疑似改脸"提示，不改变已选结果。
        if (props.getFaceMaxChangeScore() > 0) {
            double faceChange = computeFaceRegionChangeScore(ctx.userBytes(), url);
            if (faceChange > props.getFaceMaxChangeScore()) {
                String warn = String.format("人脸区变化较大(%.2f>%.2f)，疑似动到脸部，建议重试或换款",
                        faceChange, props.getFaceMaxChangeScore());
                qualityNote = (qualityNote == null) ? warn : qualityNote + "；" + warn;
                log.info("[DashScope] 人脸区护栏触发 faceChange={}, threshold={}", faceChange, props.getFaceMaxChangeScore());
            }
        }
        return new GenResult(url, prompt, retryCount, firstScore, qualityNote, strategyUsed);
    }

    /**
     * 走百炼 multimodal-generation 接口调用 qwen-image-edit / qwen-image-edit-plus。
     * 同步接口（不是 wan 系列的异步任务），单次请求即拿到 OSS 图 URL，~10-25s。
     * <p>
     * 多图语义（修复版）：image[0]=用户自拍（主体），image[1]=假发本体（已抠出，无模特）。
     * 这里的关键是 image[1] 必须是来自 WigAsset.productImage 的"光秃秃假发图"——
     * 旧版本传"模特戴假发的成品图"会让模型把它当成主体重画一张广告图。
     * 见 docs/wig-asset-system-design.md。
     */
    private GenResult generateTryonViaQwenEdit(GenContext ctx, IntConsumer progress) {
        // 双图编辑场景用专用 prompt，明确告诉模型"第二张是纯假发"
        String prompt = promptBuilder.tryonPromptWithBareWig(
                ctx.product(), ctx.analysis(), ctx.tryonType(), ctx.adjustNote(),
                ctx.wigDescriptionForAi());
        progress.accept(15);
        String baseImage = resolveBaseImage(ctx);
        String referenceImage = resolveWigImageForAi(ctx);
        if (referenceImage == null || referenceImage.isBlank()) {
            throw new IllegalStateException(
                    "假发本体图未加载：请确认管理端已上传 productImage（白底本体图，不是成品模特图）");
        }

        String model = resolveImageEditModel(ctx.preferredModel());
        String alias = ctx.preferredModel() != null && !ctx.preferredModel().isBlank()
                ? ctx.preferredModel() : "qwen";
        log.info("[DashScope/QwenEdit] 提交 model={}, uiAlias={}, 2img=true, promptLen={}",
                model, alias, prompt.length());

        progress.accept(30);
        String remoteUrl = invokeQwenEditApi(model, baseImage, referenceImage, prompt);
        progress.accept(85);

        byte[] resultBytes = downloadResultBytes(remoteUrl);
        String url = persistResultBytes(resultBytes, "dashscope-qwen");
        int retryCount = 0;
        String strategyUsed = strategyTagForQwenEdit(model, ctx.preferredModel());

        byte[] refBytes = loadWigRefBytes(ctx);
        if (refBytes != null && looksLikeRefRegurgitation(ctx.userBytes(), refBytes, resultBytes)) {
            log.warn("[DashScope/QwenEdit] 成图疑似参考图本体(userDiff={}, refDiff={})，切换单图重试",
                    computePixelDiff(ctx.userBytes(), resultBytes),
                    computePixelDiff(refBytes, resultBytes));
            retryCount = 1;
            String singlePrompt = promptBuilder.tryonPromptSingleImage(
                    ctx.product(), ctx.analysis(), ctx.tryonType(), ctx.adjustNote(),
                    ctx.wigDescriptionForAi());
            progress.accept(90);
            String retryRemote = invokeQwenEditApi(model, baseImage, null, singlePrompt);
            byte[] retryBytes = downloadResultBytes(retryRemote);
            url = persistResultBytes(retryBytes, "dashscope-qwen-single");
            resultBytes = retryBytes;
            prompt = singlePrompt;
            strategyUsed = strategyTagForQwenEdit(model, ctx.preferredModel()) + "|single-fallback";
        }

        double changeScore = computeChangeScore(ctx.userBytes(), resultBytes);
        String qualityNote = null;
        if (retryCount > 0) {
            qualityNote = "检测到输出接近商品参考图，已自动切换单图文字描述重试";
        }
        if (props.getFaceMaxChangeScore() > 0) {
            double faceChange = computeFaceRegionChangeScore(ctx.userBytes(), resultBytes);
            if (faceChange > props.getFaceMaxChangeScore()) {
                String warn = String.format("人脸区变化较大(%.2f>%.2f)，疑似动到脸部，建议重试或换款",
                        faceChange, props.getFaceMaxChangeScore());
                qualityNote = (qualityNote == null) ? warn : qualityNote + "；" + warn;
            }
        }

        log.info("[DashScope/QwenEdit] 成功 url={}, retryCount={}, changeScore={}", url, retryCount, changeScore);
        return new GenResult(url, prompt, retryCount, changeScore, qualityNote, strategyUsed);
    }

    /**
     * 调用 qwen-image-edit 同步接口。referenceImage 为 null 时走单图+文字编辑（避免复刻头模图）。
     */
    private String invokeQwenEditApi(String model, String userImage, String referenceImage, String prompt) {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("image", userImage));
        if (referenceImage != null && !referenceImage.isBlank()) {
            content.add(Map.of("image", referenceImage));
        }
        content.add(Map.of("text", prompt));

        Map<String, Object> userMsg = Map.of("role", "user", "content", content);
        Map<String, Object> input = Map.of("messages", List.of(userMsg));

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("watermark", props.isWatermark());
        String negative = promptBuilder.tryonNegativePrompt();
        if (negative != null && !negative.isBlank()) {
            parameters.put("negative_prompt", negative);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", input);
        body.put("parameters", parameters);

        HttpHeaders headers = jsonHeaders();
        ResponseEntity<String> resp;
        try {
            resp = restTemplate.postForEntity(
                    props.getMultimodalUrl(), new HttpEntity<>(body, headers), String.class);
        } catch (Exception e) {
            throw new RuntimeException("qwen-image-edit 调用失败: " + e.getMessage(), e);
        }

        String url = extractQwenEditImageUrl(resp.getBody());
        if (url == null || url.isBlank()) {
            throw new RuntimeException("qwen-image-edit 未返回图片：" + truncate(resp.getBody(), 300));
        }
        return url;
    }

    /** 立即下载 OSS 结果图（避免签名 URL 过期导致变化分计算 403）。 */
    private byte[] downloadResultBytes(String resultUrl) {
        try {
            Path path = storageService.saveFromUrl(resultUrl,
                    "tryon-results/tmp-" + UUID.randomUUID() + ".png").path();
            return Files.readAllBytes(path);
        } catch (Exception e) {
            throw new RuntimeException("下载 qwen-image-edit 结果图失败: " + e.getMessage(), e);
        }
    }

    private String persistResultBytes(byte[] bytes, String tag) {
        try {
            return storageService.saveBytes(bytes,
                    "tryon-results/" + tag + "-" + UUID.randomUUID() + ".png").url();
        } catch (IOException e) {
            throw new RuntimeException("保存 qwen-image-edit 结果图失败: " + e.getMessage(), e);
        }
    }

    /** 读取假发参考图原始字节，用于检测模型是否直接输出商品图。 */
    private byte[] loadWigRefBytes(GenContext ctx) {
        String ref = ctx.wigImageUrl();
        if (ref == null || ref.isBlank()) {
            ref = ctx.product() != null ? ctx.product().refImage : null;
        }
        return readWigRefBytes(ref);
    }

    private byte[] readWigRefBytes(String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        String trimmed = ref.trim();
        if (trimmed.startsWith("data:")) {
            int comma = trimmed.indexOf(',');
            if (comma < 0) {
                return null;
            }
            return Base64.getDecoder().decode(trimmed.substring(comma + 1));
        }
        if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
            String low = trimmed.toLowerCase();
            boolean localHost = low.contains("localhost") || low.contains("127.0.0.1")
                    || low.contains("192.168.") || low.contains("/10.");
            if (!localHost) {
                try {
                    return downloadResultBytes(trimmed);
                } catch (Exception e) {
                    log.warn("[DashScope] 下载假发参考图失败: {}", trimmed, e);
                    return null;
                }
            }
            int filesIdx = trimmed.indexOf("/files/");
            if (filesIdx >= 0) {
                String objectName = trimmed.substring(filesIdx + "/files/".length());
                Path local = Paths.get("./data/storage", objectName).normalize();
                if (Files.exists(local)) {
                    try {
                        return Files.readAllBytes(local);
                    } catch (IOException e) {
                        log.warn("[DashScope] 读取假发参考图失败: {}", local, e);
                    }
                }
            }
            return null;
        }
        Path candidate;
        if (trimmed.startsWith("/images/")) {
            candidate = Paths.get("..", "ai-hairstyle-mini-program-ui", "public", trimmed.substring(1)).normalize();
        } else {
            candidate = Paths.get(trimmed).normalize();
        }
        try {
            if (Files.exists(candidate)) {
                return Files.readAllBytes(candidate);
            }
        } catch (IOException e) {
            log.warn("[DashScope] 读取假发参考图失败: {}", candidate, e);
        }
        return null;
    }

    /**
     * 成图是否更像商品参考图而非用户自拍（qwen-image-edit-plus 双图常见故障）。
     */
    private boolean looksLikeRefRegurgitation(byte[] userBytes, byte[] refBytes, byte[] resultBytes) {
        double userDiff = computePixelDiff(userBytes, resultBytes);
        double refDiff = computePixelDiff(refBytes, resultBytes);
        if (userDiff < 0 || refDiff < 0) {
            return false;
        }
        return userDiff > 35 && refDiff < 30 && refDiff + 8 < userDiff;
    }

    private double computePixelDiff(byte[] aBytes, byte[] bBytes) {
        try {
            if (aBytes == null || aBytes.length == 0 || bBytes == null || bBytes.length == 0) {
                return -1;
            }
            BufferedImage a = ImageIO.read(new ByteArrayInputStream(aBytes));
            BufferedImage b = ImageIO.read(new ByteArrayInputStream(bBytes));
            if (a == null || b == null) {
                return -1;
            }
            BufferedImage sa = scale(a, 256, 256);
            BufferedImage sb = scale(b, 256, 256);
            long sum = 0;
            int total = 256 * 256 * 3;
            for (int y = 0; y < 256; y++) {
                for (int x = 0; x < 256; x++) {
                    int ra = sa.getRGB(x, y);
                    int rb = sb.getRGB(x, y);
                    int ar = (ra >> 16) & 0xFF, ag = (ra >> 8) & 0xFF, ab = ra & 0xFF;
                    int br = (rb >> 16) & 0xFF, bg = (rb >> 8) & 0xFF, bb = rb & 0xFF;
                    sum += Math.abs(ar - br) + Math.abs(ag - bg) + Math.abs(ab - bb);
                }
            }
            return (double) sum / total;
        } catch (Exception e) {
            log.warn("[DashScope] 计算像素差异失败", e);
            return -1;
        }
    }

    /**
     * UI 模型名 → 百炼实际 model。演示时 Flux/Doubao/GPT 等走别名，底层统一 qwen-image-edit 系。
     */
    private String resolveImageEditModel(String preferredModel) {
        if (preferredModel == null || preferredModel.isBlank()) {
            return props.getImageEditModel();
        }
        return switch (preferredModel.toLowerCase()) {
            case "flux-2-max", "gpt-image-1.5", "gpt-image-2", "gemini-3-pro-image" ->
                    preferPlusOrDefault();
            case "qwen", "doubao-seedream-5", "seedream-4.5", "gemini-3.1-flash-image",
                 "gpt-image-1", "qwen-yunwu" -> props.getImageEditModel();
            default -> props.getImageEditModel();
        };
    }

    /** plus 未配置时回退 base，避免空模型名。 */
    private String preferPlusOrDefault() {
        String plus = props.getBackupImageEditModel();
        if (plus != null && plus.toLowerCase().startsWith("qwen-image-edit")) {
            return plus;
        }
        if ("qwen-image-edit-plus".equalsIgnoreCase(props.getImageEditModel())) {
            return props.getImageEditModel();
        }
        return "qwen-image-edit-plus";
    }

    private static String strategyTagForQwenEdit(String model, String uiAlias) {
        String alias = (uiAlias == null || uiAlias.isBlank()) ? "qwen" : uiAlias;
        return "dashscope|qwen-edit|" + model + "|ui=" + alias;
    }

    /**
     * 解析 qwen-image-edit 响应，提取生成图 URL。
     * 响应结构：output.choices[0].message.content[].image
     */
    private String extractQwenEditImageUrl(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            // 错误响应：{"code":"...","message":"..."}
            if (root.has("code") && !root.path("code").asText().isEmpty()) {
                throw new RuntimeException("DashScope 返回错误: code=" + root.path("code").asText()
                        + ", message=" + root.path("message").asText());
            }
            JsonNode choices = root.path("output").path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode content = choices.get(0).path("message").path("content");
                if (content.isArray()) {
                    for (JsonNode part : content) {
                        if (part.has("image")) {
                            return part.get("image").asText();
                        }
                    }
                }
            }
            return null;
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            log.warn("[DashScope/QwenEdit] 解析响应失败", e);
            return null;
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    @Override
    public boolean isMock() {
        return false;
    }

    @Override
    public String modeName() {
        return "real";
    }

    private String resolveBaseImage(GenContext ctx) {
        String url = ctx.userPhotoUrl();
        if (url != null && (url.startsWith("https://") || url.startsWith("http://"))) {
            String low = url.toLowerCase();
            // DashScope 图像编辑对公网 URL 兼容性最好；本地地址改为 data URI 便于本机联调。
            boolean localHost = low.contains("localhost") || low.contains("127.0.0.1")
                    || low.contains("0.0.0.0") || low.contains("192.168.")
                    || low.contains("10.") || low.contains("172.16.")
                    || low.contains("172.17.") || low.contains("172.18.")
                    || low.contains("172.19.") || low.contains("172.2")
                    || low.contains("172.30.") || low.contains("172.31.");
            if (!localHost) {
                return url;
            }
            log.info("[DashScope] 检测到本地图片地址，转为 data URI 直传");
        }
        return toCompressedJpegDataUri(ctx.userBytes(), "user");
    }

    private String toCompressedJpegDataUri(byte[] origin, String tag) {
        byte[] jpeg = compressToJpeg(origin, tag);
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg);
    }

    /**
     * 长边压至 {@link #MAX_DIMENSION} 再转 JPEG data URI，避免 3MB+ PNG 直传百炼。
     */
    private byte[] compressToJpeg(byte[] origin, String tag) {
        if (origin == null || origin.length == 0) {
            return origin;
        }
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(origin));
            if (src == null) {
                log.warn("[DashScope] {} 无法解码，跳过压缩", tag);
                return origin;
            }
            int w = src.getWidth();
            int h = src.getHeight();
            int max = Math.max(w, h);
            double scale = max > MAX_DIMENSION ? (double) MAX_DIMENSION / max : 1.0;
            int nw = Math.max(1, (int) Math.round(w * scale));
            int nh = Math.max(1, (int) Math.round(h * scale));

            BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = dst.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(src, 0, 0, nw, nh, null);
            g.dispose();

            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) {
                return origin;
            }
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
            byte[] result = out.toByteArray();
            log.info("[DashScope] {} 压缩 {}x{}({}KB) -> {}x{}({}KB)", tag, w, h, origin.length / 1024,
                    nw, nh, result.length / 1024);
            return result;
        } catch (Exception e) {
            log.warn("[DashScope] {} 压缩失败，使用原字节: {}", tag, e.getMessage());
            return origin;
        }
    }

    /**
     * 取喂给 AI 的假发图（优先 ctx.wigImageUrl 即 WigAsset.productImage，否则回退老 refImage）。
     * 这是 P0 资产分层架构的接入点：调用方不再关心数据来自哪个表，统一从 ctx 取。
     */
    private String resolveWigImageForAi(GenContext ctx) {
        String wigUrl = ctx.wigImageUrl();
        if (wigUrl != null && !wigUrl.isBlank()) {
            return materializeWigImage(wigUrl);
        }
        return resolveReferenceImage(ctx.product());
    }

    /**
     * 解析"所选假发参考图"并尽量转为 data URI，保证 DashScope 服务端可直接读取。
     * 兼容老路径，从 product.refImage 取，已被 resolveWigImageForAi 包装。
     */
    private String resolveReferenceImage(WigProduct product) {
        if (product == null || product.refImage == null || product.refImage.isBlank()) {
            return null;
        }
        return materializeWigImage(product.refImage);
    }

    /**
     * 把"假发图引用"（http URL / /images 资源 / 本地路径）落地为 DashScope 可读的字符串：
     *   - http(s) 公网 URL：原样
     *   - /images/xxx：到前端 public/images 取
     *   - 其他：当作工作目录相对路径
     * 取不到一律返回 null，调用方自行决定降级。
     */
    private String materializeWigImage(String ref) {
        if (ref == null || ref.isBlank()) return null;
        String trimmed = ref.trim();
        if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
            // DashScope 防 SSRF 会拒绝 localhost/内网 URL，必须把这类 URL 落地为 data URI
            String low = trimmed.toLowerCase();
            boolean localHost = low.contains("localhost") || low.contains("127.0.0.1")
                    || low.contains("0.0.0.0") || low.contains("192.168.")
                    || low.contains("/10.") || low.startsWith("http://10.")
                    || low.contains("172.16.") || low.contains("172.17.")
                    || low.contains("172.18.") || low.contains("172.19.")
                    || low.contains("172.2") || low.contains("172.30.") || low.contains("172.31.");
            if (!localHost) {
                return trimmed;
            }
            // 把 http://localhost:8080/files/wig-assets/x.png 还原为 ./data/storage/wig-assets/x.png
            int filesIdx = trimmed.indexOf("/files/");
            if (filesIdx >= 0) {
                String objectName = trimmed.substring(filesIdx + "/files/".length());
                Path local = Paths.get("./data/storage", objectName).normalize();
                if (Files.exists(local)) {
                    try {
                        byte[] bytes = Files.readAllBytes(local);
                        log.info("[DashScope] 本地假发本体转 data URI: {}", local);
                        return toCompressedJpegDataUri(bytes, "wig-ref");
                    } catch (IOException e) {
                        log.warn("[DashScope] 读取本地图片失败: {}", local, e);
                    }
                }
            }
            log.warn("[DashScope] 检测到 localhost URL 但无法落地: {}", trimmed);
            return null;
        }
        if (trimmed.startsWith("data:")) {
            return trimmed;
        }
        Path candidate;
        if (trimmed.startsWith("/images/")) {
            candidate = Paths.get("..", "ai-hairstyle-mini-program-ui", "public", trimmed.substring(1)).normalize();
        } else {
            candidate = Paths.get(trimmed).normalize();
        }
        try {
            if (!Files.exists(candidate)) {
                log.warn("[DashScope] 假发本体图不存在: {}", candidate);
                return null;
            }
            byte[] bytes = Files.readAllBytes(candidate);
            return toCompressedJpegDataUri(bytes, "wig-ref");
        } catch (IOException e) {
            log.warn("[DashScope] 读取假发本体图失败: {}", candidate, e);
            return null;
        }
    }

    private String resolveHairMaskImage(GenContext ctx) {
        if (!needMaskStrategy(ctx)) {
            return null;
        }
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(ctx.userBytes()));
            if (source == null) {
                return null;
            }
            int w = source.getWidth();
            int h = source.getHeight();
            BufferedImage mask = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g = mask.createGraphics();
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, w, h);
            // 用椭圆而非矩形：更贴合头部/发区轮廓，减少额头与耳侧的硬直边导致的"抠图感"。
            // 覆盖范围：顶部稍微出血到画面外，纵向到约 70% 高度（覆盖刘海/两鬓，不动下半脸）。
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int ovalLeft = (int) (w * 0.06);
            int ovalTop = (int) (-h * 0.04);
            int ovalWidth = (int) (w * 0.88);
            int ovalHeight = (int) (h * 0.74);
            g.setColor(Color.WHITE);
            g.fillOval(ovalLeft, ovalTop, ovalWidth, ovalHeight);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(mask, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            log.warn("[DashScope] 生成发区mask失败，跳过 mask_image_url", e);
            return null;
        }
    }

    private boolean needMaskStrategy(GenContext ctx) {
        if (ctx == null || ctx.product() == null) {
            return false;
        }
        if ("LOCAL_BANGS".equals(ctx.tryonType()) || "LOCAL_HAIRLINE".equals(ctx.tryonType())) {
            return true;
        }
        return "短发".equals(ctx.product().category) || (ctx.product().tags != null
                && (ctx.product().tags.contains("遮发际线") || ctx.product().tags.contains("遮白发")));
    }

    private boolean needRetryForInsufficientChange(GenContext ctx, double changeScore) {
        if (changeScore < 0) {
            return false;
        }
        if (changeScore >= props.getMinChangeScore()) {
            return false;
        }
        if (ctx == null || ctx.product() == null) {
            return false;
        }
        if ("LOCAL_HAIRLINE".equals(ctx.tryonType()) || "LOCAL_BANGS".equals(ctx.tryonType())) {
            return true;
        }
        return "短发".equals(ctx.product().category) || (ctx.product().tags != null
                && (ctx.product().tags.contains("遮发际线") || ctx.product().tags.contains("遮白发")));
    }

    private String strengthenPrompt(String prompt, WigProduct product) {
        StringBuilder sb = new StringBuilder(prompt == null ? "" : prompt);
        sb.append(" 强制要求：发型改动必须明显可见，不得仅做磨皮或轻微色调变化。");
        if (product != null && "短发".equals(product.category)) {
            sb.append(" 必须改为短发长度（耳下到下巴），删除长发尾部。");
        }
        sb.append(" 保持人脸身份不变，仅在头发区域做清晰改造。");
        return sb.toString();
    }

    private String submitImageTaskWithStrategy(AttemptSpec spec, String prompt,
                                               String baseImage, String referenceImage, String maskImage) {
        // wan2.5+ 走多图融合接口（仅 prompt + images[]），主体一致性更好，是"换发不换脸"的首选。
        if (isMultiImageFusionModel(spec.modelName)) {
            return submitFusionTask(spec, prompt, baseImage, referenceImage);
        }
        // 以下为旧版 wanx2.1 通用图像编辑接口（function + reference_image_url + mask_image_url）。
        Map<String, Object> input = new HashMap<>();
        input.put("function", spec.functionName);
        input.put("prompt", prompt);
        input.put("base_image_url", baseImage);
        if (spec.useReference && referenceImage != null && !referenceImage.isBlank()) {
            input.put("reference_image_url", referenceImage);
        }
        if (spec.useMask && maskImage != null && !maskImage.isBlank()) {
            input.put("mask_image_url", maskImage);
        }
        Map<String, Object> body = new HashMap<>();
        body.put("model", spec.modelName);
        body.put("input", input);
        body.put("parameters", Map.of("n", 1));

        try {
            return submitImageTask(body);
        } catch (Exception first) {
            if (props.isStrictReplaceInputs()) {
                throw first;
            }
            if (input.containsKey("mask_image_url")) {
                log.warn("[DashScope] mask_image_url 不被接受，回退无mask模式: {}", first.getMessage());
                input.remove("mask_image_url");
                try {
                    return submitImageTask(body);
                } catch (Exception second) {
                    if (input.containsKey("reference_image_url")) {
                        log.warn("[DashScope] reference_image_url 也不被接受，回退单图模式: {}", second.getMessage());
                        input.remove("reference_image_url");
                        return submitImageTask(body);
                    }
                    throw second;
                }
            }
            if (input.containsKey("reference_image_url")) {
                log.warn("[DashScope] reference_image_url 不被接受，回退单图模式: {}", first.getMessage());
                input.remove("reference_image_url");
                return submitImageTask(body);
            }
            throw first;
        }
    }

    /**
     * wan2.5/2.6/2.7 多图融合：图1=用户照片、图2=假发参考图，配合 prompt 完成"只换头发"。
     * 不再使用 function / mask（新模型靠主体一致性保持脸部），异步提交方式与旧接口一致。
     */
    private String submitFusionTask(AttemptSpec spec, String prompt, String baseImage, String referenceImage) {
        List<String> images = new ArrayList<>();
        if (baseImage != null && !baseImage.isBlank()) {
            images.add(baseImage);
        }
        if (spec.useReference && referenceImage != null && !referenceImage.isBlank()) {
            images.add(referenceImage);
        }

        Map<String, Object> input = new HashMap<>();
        input.put("prompt", prompt);
        input.put("images", images);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("n", 1);
        parameters.put("prompt_extend", props.isPromptExtend());
        parameters.put("watermark", props.isWatermark());
        String negative = promptBuilder.tryonNegativePrompt();
        if (negative != null && !negative.isBlank()) {
            parameters.put("negative_prompt", negative);
        }
        if (props.getImageSize() != null && !props.getImageSize().isBlank()) {
            parameters.put("size", props.getImageSize());
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", spec.modelName);
        body.put("input", input);
        body.put("parameters", parameters);

        try {
            return submitImageTask(body);
        } catch (Exception first) {
            // 多图被拒：非严格模式下回退为仅用户图单图编辑，避免直接失败（仍能出图）。
            if (images.size() > 1 && !props.isStrictReplaceInputs()) {
                log.warn("[DashScope] wan2.5 多图被拒，回退单图模式: {}", first.getMessage());
                input.put("images", List.of(baseImage));
                return submitImageTask(body);
            }
            throw first;
        }
    }

    private boolean isMultiImageFusionModel(String model) {
        // wanx2.1-imageedit 用旧式 function 接口；wan2.5 / wan2.6 / wan2.7 用多图融合接口。
        return model != null && model.startsWith("wan2.");
    }

    private AttemptSpec buildFirstAttemptSpec() {
        String strategy = normalizeStrategy(props.getTryonStrategy());
        String function = resolveTryonFunction();
        String model = props.getImageEditModel();
        boolean replaceFirst = "replace_first".equals(strategy);
        String tag = strategy + "|" + model + "|" + function;
        return new AttemptSpec(model, function, true, replaceFirst, tag);
    }

    private AttemptSpec buildBackupAttemptSpec(AttemptSpec first) {
        String backupModel = props.getBackupImageEditModel();
        if (backupModel == null || backupModel.isBlank()) {
            return null;
        }
        if (backupModel.equals(first.modelName)) {
            return null;
        }
        String function = resolveTryonFunction();
        return new AttemptSpec(backupModel, function, true, true, "backup_replace|" + backupModel + "|" + function);
    }

    private String normalizeStrategy(String raw) {
        if (raw == null || raw.isBlank()) {
            return "replace_first";
        }
        String low = raw.trim().toLowerCase();
        return "prompt_only".equals(low) ? "prompt_only" : "replace_first";
    }

    private String resolveTryonFunction() {
        String f = props.getTryonFunction();
        return (f == null || f.isBlank()) ? "description_edit" : f.trim();
    }

    private record AttemptSpec(String modelName, String functionName, boolean useReference, boolean useMask,
                               String strategyTag) {
    }

    private double computeChangeScore(byte[] baseBytes, String resultUrl) {
        try {
            if (baseBytes == null || baseBytes.length == 0 || resultUrl == null || resultUrl.isBlank()) {
                return -1;
            }
            ResponseEntity<byte[]> resp = restTemplate.exchange(resultUrl, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), byte[].class);
            byte[] resultBytes = resp.getBody();
            return computeChangeScore(baseBytes, resultBytes);
        } catch (Exception e) {
            log.warn("[DashScope] 计算变化分失败", e);
            return -1;
        }
    }

    private double computeChangeScore(byte[] baseBytes, byte[] resultBytes) {
        return computePixelDiff(baseBytes, resultBytes);
    }

    /**
     * 仅比较“下半脸中心区域”的像素变化，近似判断是否动到了脸部（换脸 / 五官改变）。
     * 不依赖任何人脸识别库；区域取归一化后纵向 50%~92%、横向 22%~78% 的中心矩形。
     */
    private double computeFaceRegionChangeScore(byte[] baseBytes, String resultUrl) {
        try {
            if (baseBytes == null || baseBytes.length == 0 || resultUrl == null || resultUrl.isBlank()) {
                return -1;
            }
            ResponseEntity<byte[]> resp = restTemplate.exchange(resultUrl, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), byte[].class);
            return computeFaceRegionChangeScore(baseBytes, resp.getBody());
        } catch (Exception e) {
            log.warn("[DashScope] 计算人脸区变化分失败", e);
            return -1;
        }
    }

    private double computeFaceRegionChangeScore(byte[] baseBytes, byte[] resultBytes) {
        try {
            if (baseBytes == null || baseBytes.length == 0 || resultBytes == null || resultBytes.length == 0) {
                return -1;
            }
            BufferedImage a = ImageIO.read(new ByteArrayInputStream(baseBytes));
            BufferedImage b = ImageIO.read(new ByteArrayInputStream(resultBytes));
            if (a == null || b == null) {
                return -1;
            }
            BufferedImage sa = scale(a, 256, 256);
            BufferedImage sb = scale(b, 256, 256);
            int x0 = (int) (256 * 0.22), x1 = (int) (256 * 0.78);
            int y0 = (int) (256 * 0.50), y1 = (int) (256 * 0.92);
            long sum = 0;
            int total = (x1 - x0) * (y1 - y0) * 3;
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    int ra = sa.getRGB(x, y);
                    int rb = sb.getRGB(x, y);
                    int ar = (ra >> 16) & 0xFF, ag = (ra >> 8) & 0xFF, ab = ra & 0xFF;
                    int br = (rb >> 16) & 0xFF, bg = (rb >> 8) & 0xFF, bb = rb & 0xFF;
                    sum += Math.abs(ar - br) + Math.abs(ag - bg) + Math.abs(ab - bb);
                }
            }
            return total == 0 ? -1 : (double) sum / total;
        } catch (Exception e) {
            log.warn("[DashScope] 计算人脸区变化分失败", e);
            return -1;
        }
    }

    private BufferedImage scale(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private String detectMime(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }

    // ----------------- 内部：视觉多模态 -----------------

    private String callVision(String userText, String systemText, byte[] imageBytes, String mimeType) {
        String dataUri = "data:" + (mimeType == null ? "image/jpeg" : mimeType)
                + ";base64," + Base64.getEncoder().encodeToString(imageBytes);

        Map<String, Object> systemMsg = Map.of("role", "system",
                "content", List.of(Map.of("text", systemText)));
        Map<String, Object> userMsg = Map.of("role", "user",
                "content", List.of(Map.of("image", dataUri), Map.of("text", userText)));
        Map<String, Object> body = Map.of(
                "model", props.getVisionModel(),
                "input", Map.of("messages", List.of(systemMsg, userMsg)));

        HttpHeaders headers = jsonHeaders();
        ResponseEntity<String> resp = restTemplate.postForEntity(
                props.getMultimodalUrl(), new HttpEntity<>(body, headers), String.class);
        return extractVisionText(resp.getBody());
    }

    private String extractVisionText(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("output").path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode content = choices.get(0).path("message").path("content");
                if (content.isArray()) {
                    StringBuilder sb = new StringBuilder();
                    for (JsonNode part : content) {
                        if (part.has("text")) {
                            sb.append(part.get("text").asText());
                        }
                    }
                    return sb.toString();
                }
                return content.asText();
            }
            // 兼容 output.text
            JsonNode text = root.path("output").path("text");
            if (!text.isMissingNode()) {
                return text.asText();
            }
        } catch (Exception e) {
            log.error("[DashScope] 解析视觉返回失败: {}", responseBody, e);
        }
        return "";
    }

    private FaceAnalysisResult parseAnalysis(String content) {
        try {
            String cleaned = content.replace("```json", "").replace("```", "").trim();
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start >= 0 && end > start) {
                cleaned = cleaned.substring(start, end + 1);
            }
            return objectMapper.readValue(cleaned, FaceAnalysisResult.class);
        } catch (Exception e) {
            log.error("[DashScope] 解析分析 JSON 失败，使用兜底: {}", content, e);
            FaceAnalysisResult fallback = new FaceAnalysisResult();
            fallback.faceShape = "未识别";
            fallback.analysisText = "已为您推荐热门发型";
            fallback.recommendedIds = List.of(1L, 2L, 3L, 4L);
            return fallback;
        }
    }

    // ----------------- 内部：图像异步任务 -----------------

    private String submitImageTask(Map<String, Object> body) {
        HttpHeaders headers = jsonHeaders();
        headers.set("X-DashScope-Async", "enable");
        ResponseEntity<String> resp = restTemplate.postForEntity(
                props.getImageSynthesisUrl(), new HttpEntity<>(body, headers), String.class);
        try {
            JsonNode root = objectMapper.readTree(resp.getBody());
            JsonNode taskId = root.path("output").path("task_id");
            if (taskId.isMissingNode()) {
                throw new IllegalStateException("提交图像任务失败: " + resp.getBody());
            }
            return taskId.asText();
        } catch (Exception e) {
            throw new RuntimeException("提交图像任务异常", e);
        }
    }

    private String pollImageTask(String taskId, IntConsumer progress) {
        String url = props.getTaskQueryUrl() + taskId;
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(props.getApiKey());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        long start = System.currentTimeMillis();
        long timeout = props.getTaskTimeoutSeconds() * 1000L;
        int progressPct = 35;
        while (System.currentTimeMillis() - start < timeout) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            progressPct = Math.min(90, progressPct + 8);
            progress.accept(progressPct);

            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            try {
                JsonNode root = objectMapper.readTree(resp.getBody());
                String status = root.path("output").path("task_status").asText("");
                if ("SUCCEEDED".equals(status)) {
                    JsonNode results = root.path("output").path("results");
                    if (results.isArray() && !results.isEmpty()) {
                        return results.get(0).path("url").asText();
                    }
                    throw new IllegalStateException("任务完成但无结果图: " + resp.getBody());
                } else if ("FAILED".equals(status)) {
                    throw new IllegalStateException("图像任务失败: " + resp.getBody());
                }
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("轮询图像任务异常", e);
            }
        }
        throw new RuntimeException("图像生成超时");
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(props.getApiKey());
        return headers;
    }
}
