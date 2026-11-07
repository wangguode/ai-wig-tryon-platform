package com.wig.tryon.controller;

import com.wig.tryon.model.ApiResponse;
import com.wig.tryon.service.CustomerCatalogImportService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 客户素材文件夹预览 + 一键导入向导 API。 */
@RestController
@RequestMapping("/admin/catalog/import")
public class AdminCatalogImportController {

    private final CustomerCatalogImportService importService;

    public AdminCatalogImportController(CustomerCatalogImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/preview")
    public ApiResponse<Map<String, Object>> preview(@RequestBody Map<String, String> body) {
        try {
            String path = body != null ? body.get("sourcePath") : null;
            return ApiResponse.success(importService.preview(path));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/execute")
    public ApiResponse<Map<String, Object>> execute(@RequestBody Map<String, String> body) {
        try {
            String path = body != null ? body.get("sourcePath") : null;
            return ApiResponse.success(importService.execute(path));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/reload")
    public ApiResponse<Map<String, Object>> reload() {
        importService.reloadAllFromDisk();
        return ApiResponse.success(Map.of(
                "ok", true,
                "message", "已从 data/*.json 热加载到内存"
        ));
    }
}
