package com.wig.tryon.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wig.tryon.model.ApiResponse;
import com.wig.tryon.model.ModelAsset;
import com.wig.tryon.repository.ModelAssetRepository;
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
 * 运营后台 - 模特库管理。与 AdminWigAssetController 同结构。
 */
@RestController
@RequestMapping("/admin/model-assets")
public class AdminModelAssetController {

    private static final Logger log = LoggerFactory.getLogger(AdminModelAssetController.class);
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final List<String> ALLOWED_MIME = List.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");

    private final ModelAssetRepository repository;
    private final StorageService storageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminModelAssetController(ModelAssetRepository repository, StorageService storageService) {
        this.repository = repository;
        this.storageService = storageService;
    }

    @GetMapping
    public ApiResponse<List<ModelAsset>> list() {
        return ApiResponse.success(repository.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<ModelAsset> detail(@PathVariable Long id) {
        return repository.findById(id)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error("模特不存在: " + id));
    }

    @PostMapping
    public ApiResponse<ModelAsset> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("meta") String metaJson,
            @RequestParam(value = "id", required = false) Long id) {
        if (file == null || file.isEmpty()) {
            return ApiResponse.error("请选择模特肖像");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            return ApiResponse.error("图片大小不能超过 10MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || ALLOWED_MIME.stream().noneMatch(contentType::startsWith)) {
            return ApiResponse.error("仅支持 JPG/PNG/WEBP/GIF 图片");
        }

        ModelAsset incoming;
        try {
            Map<String, Object> meta = objectMapper.readValue(metaJson, new TypeReference<>() {});
            incoming = objectMapper.convertValue(meta, ModelAsset.class);
        } catch (IOException e) {
            return ApiResponse.error("meta JSON 格式错误: " + e.getMessage());
        }

        ModelAsset target;
        if (id != null) {
            target = repository.findById(id).orElse(null);
            if (target == null) {
                return ApiResponse.error("待更新的模特不存在: " + id);
            }
        } else {
            target = new ModelAsset();
        }
        target.name = incoming.name;
        target.gender = incoming.gender;
        target.ageGroup = incoming.ageGroup;
        target.faceShape = incoming.faceShape;
        target.tags = incoming.tags;

        target = repository.save(target);
        try {
            String ext = guessExtension(file.getOriginalFilename(), contentType);
            String objectName = "model-assets/" + target.id + ext;
            StorageService.Stored stored = storageService.save(file, objectName);
            target.portraitImage = stored.url();
            if (target.createdAt == null) {
                target.createdAt = Instant.now();
            }
            target = repository.save(target);
            log.info("[Admin/ModelAsset] 上传成功 id={}, url={}", target.id, target.portraitImage);
            return ApiResponse.success(target);
        } catch (IOException e) {
            log.error("[Admin/ModelAsset] 保存图片失败", e);
            return ApiResponse.error("保存图片失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<ModelAsset> updateMeta(@PathVariable Long id, @RequestBody Map<String, Object> meta) {
        ModelAsset target = repository.findById(id).orElse(null);
        if (target == null) {
            return ApiResponse.error("模特不存在: " + id);
        }
        ModelAsset incoming = objectMapper.convertValue(meta, ModelAsset.class);
        if (incoming.name != null) target.name = incoming.name;
        if (incoming.gender != null) target.gender = incoming.gender;
        if (incoming.ageGroup != null) target.ageGroup = incoming.ageGroup;
        if (incoming.faceShape != null) target.faceShape = incoming.faceShape;
        if (incoming.tags != null) target.tags = incoming.tags;
        return ApiResponse.success(repository.save(target));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        boolean ok = repository.delete(id);
        return ok ? ApiResponse.success(true) : ApiResponse.error("模特不存在: " + id);
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
