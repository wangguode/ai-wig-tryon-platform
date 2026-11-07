package com.wig.tryon.controller;

import com.wig.tryon.model.ApiResponse;
import com.wig.tryon.model.TryonRequest;
import com.wig.tryon.service.TryonService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/tryon")
public class TryonController {

    private final TryonService tryonService;

    public TryonController(TryonService tryonService) {
        this.tryonService = tryonService;
    }

    /** 提交试戴任务（异步），返回 taskId。 */
    @PostMapping("/submit")
    public ApiResponse<Map<String, Object>> submit(@Valid @RequestBody TryonRequest request) {
        return ApiResponse.success(tryonService.submit(request));
    }

    /** 查询任务状态（前端轮询）。 */
    @GetMapping("/status/{taskId}")
    public ApiResponse<Map<String, Object>> status(@PathVariable String taskId) {
        return ApiResponse.success(tryonService.getStatus(taskId));
    }

    /** 调整重新生成（如：增加刘海 / 修复发际线）。 */
    @PostMapping("/adjust")
    public ApiResponse<Map<String, Object>> adjust(
            @RequestParam String taskId,
            @RequestParam String adjustmentNote) {
        return ApiResponse.success(tryonService.adjust(taskId, adjustmentNote));
    }
}
