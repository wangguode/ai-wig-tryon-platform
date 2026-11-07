package com.wig.tryon.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wig.tryon.model.ApiResponse;
import com.wig.tryon.model.WigAsset;
import com.wig.tryon.repository.CatalogRepository;
import com.wig.tryon.repository.WigAssetRepository;
import com.wig.tryon.service.BareWigGenerator;
import com.wig.tryon.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 运营后台 - 假发本体（WigAsset）管理。
 * 仅在 app.admin.enabled=true 且通过 AdminAuthInterceptor 鉴权后可访问。
 * 路径前缀 /admin（被 AdminAuthInterceptor 拦截）。
 *
 * 资产分层架构落地点之一：运营在这里上传"光秃秃假发本体图"，
 * C 端用户的试戴生成会自动用这些图作为 AI 的"参考图"，从而避免模型被模特图误导。
 * 见 docs/wig-asset-system-design.md。
 */
@RestController
@RequestMapping("/admin/wig-assets")
public class AdminWigAssetController {

    private static final Logger log = LoggerFactory.getLogger(AdminWigAssetController.class);
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024; // 10MB
    private static final List<String> ALLOWED_MIME = List.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");

    private final WigAssetRepository repository;
    private final CatalogRepository catalogRepository;
    private final StorageService storageService;
    private final BareWigGenerator bareWigGenerator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminWigAssetController(WigAssetRepository repository,
                                   CatalogRepository catalogRepository,
                                   StorageService storageService,
                                   BareWigGenerator bareWigGenerator) {
        this.repository = repository;
        this.catalogRepository = catalogRepository;
        this.storageService = storageService;
        this.bareWigGenerator = bareWigGenerator;
    }

    /** 列表（按 id 升序）。 */
    @GetMapping
    public ApiResponse<List<WigAsset>> list() {
        return ApiResponse.success(repository.findAll());
    }

    /** 单个详情。 */
    @GetMapping("/{id}")
    public ApiResponse<WigAsset> detail(@PathVariable Long id) {
        return repository.findById(id)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error("资产不存在: " + id));
    }

    /**
     * 上传一张本体图 + 元数据。
     * <p>
     * 表单字段：
     *   - file       (multipart) 必填，假发本体图（建议白底/透明 PNG）
     *   - meta       (string)    必填，JSON 字符串：{ code, name, descriptionForAi, category, colorFamily, tags[] }
     *   - id         (string)    可选，更新已有资产时填
     * 上传成功后图片落到 storage/wig-assets/{id}.{ext}，URL 通过 /files/wig-assets/{id}.{ext} 访问。
     */
    @PostMapping
    public ApiResponse<WigAsset> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("meta") String metaJson,
            @RequestParam(value = "id", required = false) Long id) {
        if (file == null || file.isEmpty()) {
            return ApiResponse.error("请选择假发本体图");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            return ApiResponse.error("图片大小不能超过 10MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || ALLOWED_MIME.stream().noneMatch(contentType::startsWith)) {
            return ApiResponse.error("仅支持 JPG/PNG/WEBP/GIF 图片");
        }

        WigAsset incoming;
        try {
            // 用 Map 中转避免 Jackson 反序列化时把 createdAt 等系统字段从前端覆盖掉
            Map<String, Object> meta = objectMapper.readValue(metaJson, new TypeReference<>() {});
            incoming = objectMapper.convertValue(meta, WigAsset.class);
        } catch (IOException e) {
            return ApiResponse.error("meta JSON 格式错误: " + e.getMessage());
        }

        WigAsset target;
        if (id != null) {
            target = repository.findById(id).orElse(null);
            if (target == null) {
                return ApiResponse.error("待更新的资产不存在: " + id);
            }
        } else {
            target = new WigAsset();
        }
        // 仅允许从前端覆盖业务字段；id/createdAt/createdBy 由后端控制
        target.code = incoming.code;
        target.name = incoming.name;
        target.descriptionForAi = incoming.descriptionForAi;
        target.category = incoming.category;
        target.colorFamily = incoming.colorFamily;
        target.tags = incoming.tags;

        // 先保存以拿到 id，再保存图片到 wig-assets/{id}.{ext}
        target = repository.save(target);
        try {
            String ext = guessExtension(file.getOriginalFilename(), contentType);
            String objectName = "wig-assets/" + target.id + ext;
            StorageService.Stored stored = storageService.save(file, objectName);
            target.productImage = stored.url();
            target.productImageThumbnail = stored.url();
            if (target.createdAt == null) {
                target.createdAt = Instant.now();
            }
            target = repository.save(target);
            catalogRepository.upsertFromWigAsset(target);
            log.info("[Admin/WigAsset] 上传成功 id={}, url={}", target.id, target.productImage);
            return ApiResponse.success(target);
        } catch (IOException e) {
            log.error("[Admin/WigAsset] 保存图片失败", e);
            return ApiResponse.error("保存图片失败: " + e.getMessage());
        }
    }

    /** 仅更新元数据（不替换图片）。前端编辑场景用。 */
    @PutMapping("/{id}")
    public ApiResponse<WigAsset> updateMeta(@PathVariable Long id, @RequestBody Map<String, Object> meta) {
        WigAsset target = repository.findById(id).orElse(null);
        if (target == null) {
            return ApiResponse.error("资产不存在: " + id);
        }
        WigAsset incoming = objectMapper.convertValue(meta, WigAsset.class);
        if (incoming.code != null) target.code = incoming.code;
        if (incoming.name != null) target.name = incoming.name;
        if (incoming.descriptionForAi != null) target.descriptionForAi = incoming.descriptionForAi;
        if (incoming.category != null) target.category = incoming.category;
        if (incoming.colorFamily != null) target.colorFamily = incoming.colorFamily;
        if (incoming.tags != null) target.tags = incoming.tags;
        target = repository.save(target);
        catalogRepository.upsertFromWigAsset(target);
        return ApiResponse.success(target);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        boolean ok = repository.delete(id);
        if (ok) {
            catalogRepository.removeProduct(id);
        }
        return ok ? ApiResponse.success(true) : ApiResponse.error("资产不存在: " + id);
    }

    /**
     * 用 AI 文生图根据 descriptionForAi 重画一张白底纯本体图，覆盖现有 productImage。
     * 同步阻塞调用——前端需要 loading 蒙层（30~60s）。
     * <p>
     * 仅 DashScope 已配置 Key 时可用；mock/seed 模式或未配 key 时返回错误。
     */
    @PostMapping("/{id}/regenerate-bare")
    public ApiResponse<WigAsset> regenerateBare(@PathVariable Long id) {
        WigAsset asset = repository.findById(id).orElse(null);
        if (asset == null) {
            return ApiResponse.error("资产不存在: " + id);
        }
        try {
            String url = bareWigGenerator.regenerate(asset);
            asset.productImage = url;
            asset.productImageThumbnail = url;
            asset = repository.save(asset);
            catalogRepository.upsertFromWigAsset(asset);
            log.info("[Admin/WigAsset] AI 重画本体成功 id={}, url={}", id, url);
            return ApiResponse.success(asset);
        } catch (IllegalStateException e) {
            log.warn("[Admin/WigAsset] AI 重画本体失败 id={}: {}", id, e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("[Admin/WigAsset] AI 重画本体异常 id=" + id, e);
            return ApiResponse.error("AI 调用异常: " + e.getMessage());
        }
    }

    private String guessExtension(String filename, String contentType) {
        if (filename != null) {
            int dot = filename.lastIndexOf('.');
            if (dot >= 0 && dot < filename.length() - 1) {
                String ext = filename.substring(dot).toLowerCase();
                if (ext.length() <= 5) return ext;
            }
        }
        if (contentType == null) return ".png";
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".png";
        };
    }
}
