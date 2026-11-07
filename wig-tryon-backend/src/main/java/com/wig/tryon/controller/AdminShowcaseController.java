package com.wig.tryon.controller;

import com.wig.tryon.model.ApiResponse;
import com.wig.tryon.model.WigShowcase;
import com.wig.tryon.repository.WigShowcaseRepository;
import com.wig.tryon.service.CustomerCatalogImportService;
import com.wig.tryon.service.ShowcaseImportService;
import com.wig.tryon.service.ShowcaseIntegrityService;
import com.wig.tryon.service.ShowcaseService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/showcases")
public class AdminShowcaseController {

    private final ShowcaseService showcaseService;
    private final WigShowcaseRepository showcaseRepository;
    private final ShowcaseImportService showcaseImportService;
    private final ShowcaseIntegrityService showcaseIntegrityService;

    public AdminShowcaseController(ShowcaseService showcaseService,
                                    WigShowcaseRepository showcaseRepository,
                                    ShowcaseImportService showcaseImportService,
                                    ShowcaseIntegrityService showcaseIntegrityService) {
        this.showcaseService = showcaseService;
        this.showcaseRepository = showcaseRepository;
        this.showcaseImportService = showcaseImportService;
        this.showcaseIntegrityService = showcaseIntegrityService;
    }

    @GetMapping
    public ApiResponse<List<WigShowcase>> list(@RequestParam(required = false) Long wigAssetId) {
        if (wigAssetId != null) {
            return ApiResponse.success(showcaseRepository.findByWigAssetId(wigAssetId));
        }
        return ApiResponse.success(showcaseRepository.findAll());
    }

    /** 关联体检：本体 / 商品 / 陈列 / 主图是否齐全。 */
    @GetMapping("/integrity")
    public ApiResponse<Map<String, Object>> integrity(@RequestParam Long wigAssetId) {
        try {
            return ApiResponse.success(showcaseIntegrityService.check(wigAssetId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /** 手工上传客户现成成品图（不调 AI）。 */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<List<WigShowcase>> importShowcases(
            @RequestParam Long wigAssetId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(required = false) List<Long> modelAssetIds,
            @RequestParam(defaultValue = "true") boolean autoApprove,
            @RequestParam(defaultValue = "true") boolean setFirstPrimary) {
        try {
            return ApiResponse.success(
                    showcaseImportService.importFiles(wigAssetId, files, modelAssetIds, autoApprove, setFirstPrimary));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/generate")
    public ApiResponse<List<WigShowcase>> generate(@RequestBody Map<String, Object> body) {
        Object wigAssetIdRaw = body.get("wigAssetId");
        Object idsRaw = body.get("modelAssetIds");
        if (wigAssetIdRaw == null) {
            return ApiResponse.error("wigAssetId 必填");
        }
        if (!(idsRaw instanceof List<?> idList) || idList.isEmpty()) {
            return ApiResponse.error("modelAssetIds 至少 1 个");
        }
        Long wigAssetId;
        List<Long> modelIds;
        try {
            wigAssetId = Long.valueOf(wigAssetIdRaw.toString());
            modelIds = idList.stream().map(o -> Long.valueOf(o.toString())).toList();
        } catch (NumberFormatException e) {
            return ApiResponse.error("ID 格式错误: " + e.getMessage());
        }
        try {
            return ApiResponse.success(showcaseService.submitBatch(wigAssetId, modelIds));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<WigShowcase> approve(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        boolean setPrimary = body != null && Boolean.TRUE.equals(body.get("isPrimary"));
        try {
            return ApiResponse.success(showcaseService.approve(id, "admin", setPrimary));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<WigShowcase> reject(@PathVariable Long id) {
        try {
            return ApiResponse.success(showcaseService.reject(id, "admin"));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        boolean ok = showcaseRepository.delete(id);
        return ok ? ApiResponse.success(true) : ApiResponse.error("成品图不存在: " + id);
    }
}
