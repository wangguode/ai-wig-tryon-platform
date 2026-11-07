package com.wig.tryon.controller;

import com.wig.tryon.model.ApiResponse;
import com.wig.tryon.service.PhotoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/photo")
public class PhotoController {

    private final PhotoService photoService;

    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }

    @PostMapping("/upload")
    public ApiResponse<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            return ApiResponse.error("请选择照片");
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            return ApiResponse.error("图片大小不能超过 10MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ApiResponse.error("请上传图片文件 (jpg/png/webp)");
        }
        return ApiResponse.success(photoService.uploadAndPreCheck(file));
    }

    /** 轮询异步人脸预检结果。 */
    @GetMapping("/face-check/{sessionId}")
    public ApiResponse<Map<String, Object>> faceCheck(@PathVariable String sessionId) {
        return ApiResponse.success(photoService.getFaceCheckStatus(sessionId));
    }
}
