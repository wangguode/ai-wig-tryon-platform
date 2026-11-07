package com.wig.tryon.controller;

import com.wig.tryon.model.ApiResponse;
import com.wig.tryon.model.Store;
import com.wig.tryon.repository.CatalogRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/store")
public class StoreController {

    private final CatalogRepository catalogRepository;

    public StoreController(CatalogRepository catalogRepository) {
        this.catalogRepository = catalogRepository;
    }

    /** 附近门店（MVP：按评分排序返回）。 */
    @GetMapping("/nearby")
    public ApiResponse<List<Store>> nearby(
            @RequestParam(required = false, defaultValue = "116.4") double longitude,
            @RequestParam(required = false, defaultValue = "39.9") double latitude,
            @RequestParam(required = false, defaultValue = "10") int limit) {
        return ApiResponse.success(catalogRepository.findStores(limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<Store> detail(@PathVariable Long id) {
        return catalogRepository.findStore(id)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error("门店不存在"));
    }
}
