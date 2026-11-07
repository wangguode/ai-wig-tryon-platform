package com.wig.tryon.controller;

import com.wig.tryon.model.ApiResponse;
import com.wig.tryon.model.BatchJob;
import com.wig.tryon.model.ModelAsset;
import com.wig.tryon.model.WigAsset;
import com.wig.tryon.model.WigShowcase;
import com.wig.tryon.repository.CatalogRepository;
import com.wig.tryon.repository.ModelAssetRepository;
import com.wig.tryon.repository.WigAssetRepository;
import com.wig.tryon.repository.WigShowcaseRepository;
import com.wig.tryon.service.BareWigGenerator;
import com.wig.tryon.service.BatchJobRegistry;
import com.wig.tryon.service.ModelPortraitGenerator;
import com.wig.tryon.service.ShowcaseService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运营后台 Dashboard：物料就绪进度统计 + 一键批量填充入口。
 * <p>
 * 这里把链路闭环的"是否就绪"做成可量化的进度卡，运营点 3 个按钮即可把
 * 16 个本体 + 5 个模特 + 16 个 showcase 全部就位。
 */
@RestController
@RequestMapping("/admin/dashboard")
public class AdminDashboardController {

    private final WigAssetRepository wigAssetRepository;
    private final ModelAssetRepository modelAssetRepository;
    private final WigShowcaseRepository showcaseRepository;
    private final CatalogRepository catalogRepository;
    private final BareWigGenerator bareWigGenerator;
    private final ShowcaseService showcaseService;
    private final ModelPortraitGenerator modelPortraitGenerator;
    private final BatchJobRegistry batchJobRegistry;

    public AdminDashboardController(WigAssetRepository wigAssetRepository,
                                    ModelAssetRepository modelAssetRepository,
                                    WigShowcaseRepository showcaseRepository,
                                    CatalogRepository catalogRepository,
                                    BareWigGenerator bareWigGenerator,
                                    ShowcaseService showcaseService,
                                    ModelPortraitGenerator modelPortraitGenerator,
                                    BatchJobRegistry batchJobRegistry) {
        this.wigAssetRepository = wigAssetRepository;
        this.modelAssetRepository = modelAssetRepository;
        this.showcaseRepository = showcaseRepository;
        this.catalogRepository = catalogRepository;
        this.bareWigGenerator = bareWigGenerator;
        this.showcaseService = showcaseService;
        this.modelPortraitGenerator = modelPortraitGenerator;
        this.batchJobRegistry = batchJobRegistry;
    }

    /** 当前物料就绪状况 + 最近 10 个批量任务。 */
    @GetMapping
    public ApiResponse<Map<String, Object>> dashboard() {
        List<WigAsset> wigs = wigAssetRepository.findAll();
        List<ModelAsset> models = modelAssetRepository.findAll();
        List<WigShowcase> showcases = showcaseRepository.findAll();

        long realWigs = wigs.stream()
                .filter(a -> a.productImage != null && a.productImage.startsWith("http"))
                .count();
        long realModels = models.stream()
                .filter(m -> m.portraitImage != null && m.portraitImage.startsWith("http"))
                .count();
        long approvedShowcases = showcases.stream()
                .filter(s -> "APPROVED".equals(s.status))
                .count();
        long primaryShowcases = showcases.stream()
                .filter(s -> Boolean.TRUE.equals(s.isPrimary) && "APPROVED".equals(s.status))
                .count();
        long productsWithPrimary = catalogRepository.findAll().stream()
                .filter(p -> p.wigAssetId != null
                        && showcaseRepository.findPrimaryByWigAssetId(p.wigAssetId).isPresent())
                .count();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("wigAssetTotal", wigs.size());
        body.put("wigAssetRealCount", realWigs);
        body.put("modelAssetTotal", models.size());
        body.put("modelAssetRealCount", realModels);
        body.put("showcaseTotal", showcases.size());
        body.put("showcaseApprovedCount", approvedShowcases);
        body.put("showcasePrimaryCount", primaryShowcases);
        body.put("productTotal", catalogRepository.findAll().size());
        body.put("productWithPrimaryCount", productsWithPrimary);
        body.put("recentJobs", batchJobRegistry.recent(10));
        body.put("ready", !wigs.isEmpty()
                && productsWithPrimary == catalogRepository.findAll().size()
                && productsWithPrimary == wigs.size());
        return ApiResponse.success(body);
    }

    /** 一键批量重画本体图（不传 ids = 全量）。 */
    @PostMapping("/regenerate-bare-wigs")
    public ApiResponse<BatchJob> regenerateBareWigs(@RequestBody(required = false) Map<String, Object> body) {
        try {
            List<Long> ids = parseIds(body, "ids");
            return ApiResponse.success(bareWigGenerator.startBatch(ids));
        } catch (IllegalStateException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /** 一键批量生成 showcase + 自动审批 + 自动设主图。 */
    @PostMapping("/fill-showcases")
    public ApiResponse<BatchJob> fillShowcases(@RequestBody Map<String, Object> body) {
        if (body == null || body.get("modelAssetId") == null) {
            return ApiResponse.error("modelAssetId 必填（指定用哪个模特生成）");
        }
        try {
            Long modelAssetId = Long.valueOf(body.get("modelAssetId").toString());
            List<Long> ids = parseIds(body, "wigAssetIds");
            boolean autoApprove = body.get("autoApprove") == null
                    || Boolean.parseBoolean(body.get("autoApprove").toString());
            boolean autoPrimary = body.get("autoPrimary") == null
                    || Boolean.parseBoolean(body.get("autoPrimary").toString());
            return ApiResponse.success(showcaseService.startBatchFill(modelAssetId, ids, autoApprove, autoPrimary));
        } catch (NumberFormatException e) {
            return ApiResponse.error("ID 格式错误: " + e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /** 一键 AI 生成模特照片。 */
    @PostMapping("/regenerate-model-portraits")
    public ApiResponse<BatchJob> regenerateModelPortraits(@RequestBody(required = false) Map<String, Object> body) {
        try {
            List<Long> ids = parseIds(body, "ids");
            return ApiResponse.success(modelPortraitGenerator.startBatch(ids));
        } catch (IllegalStateException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /** 查询某个批量任务进度。 */
    @GetMapping("/jobs/{jobId}")
    public ApiResponse<BatchJob> jobStatus(@PathVariable String jobId) {
        return batchJobRegistry.find(jobId)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error("任务不存在: " + jobId));
    }

    /**
     * 演示重置：清空 showcase / 本体 / 模特 / 商品 catalog，回到运营自建数据的空白态。
     * 请求 body 必须 {"confirm": "RESET-DEMO"} 防误调。
     */
    @PostMapping("/reset")
    public ApiResponse<Map<String, Object>> reset(@RequestBody(required = false) Map<String, Object> body) {
        Object confirm = body == null ? null : body.get("confirm");
        if (!"RESET-DEMO".equals(String.valueOf(confirm))) {
            return ApiResponse.error("需要 confirm=RESET-DEMO 才能执行重置");
        }
        if (batchJobRegistry.recent(3).stream().anyMatch(j -> j.status.name().equals("RUNNING"))) {
            return ApiResponse.error("还有批量任务在跑，等它完成再重置");
        }
        showcaseRepository.deleteAll();
        wigAssetRepository.clearAll();
        modelAssetRepository.clearAll();
        catalogRepository.clearAllProducts();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("message", "运营数据已清空，请从「假发本体」开始上传");
        return ApiResponse.success(result);
    }

    @SuppressWarnings("unchecked")
    private List<Long> parseIds(Map<String, Object> body, String key) {
        if (body == null || body.get(key) == null) return List.of();
        Object raw = body.get(key);
        if (!(raw instanceof List<?> list)) return List.of();
        List<Long> ids = new java.util.ArrayList<>();
        for (Object o : list) {
            if (o == null) continue;
            try {
                ids.add(Long.valueOf(o.toString()));
            } catch (NumberFormatException ignored) {
            }
        }
        return ids;
    }
}
