package com.wig.tryon.controller;

import com.wig.tryon.model.ApiResponse;
import com.wig.tryon.model.WigProduct;
import com.wig.tryon.service.RecommendService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recommend")
public class RecommendController {

    private final RecommendService recommendService;

    public RecommendController(RecommendService recommendService) {
        this.recommendService = recommendService;
    }

    /** AI 分析 + 个性化推荐。 */
    @GetMapping("/analyze/{sessionId}")
    public ApiResponse<Map<String, Object>> analyze(
            @PathVariable String sessionId,
            @RequestParam(required = false, defaultValue = "") String preference,
            @RequestParam(required = false, defaultValue = "") String category) {
        return ApiResponse.success(recommendService.analyzeAndRecommend(sessionId, preference, category));
    }

    /** 发型库列表（支持按分类/脸型筛选）。 */
    @GetMapping("/products")
    public ApiResponse<List<WigProduct>> products(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String faceType) {
        return ApiResponse.success(recommendService.getProducts(category, faceType));
    }
}
