package com.wig.tryon.service;

import com.wig.tryon.config.AppProperties;
import com.wig.tryon.repository.SessionRepository;
import com.wig.tryon.service.ai.AiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 照片上传 + 异步人脸预检。
 * 上传接口立即返回 sessionId；百炼 qwen-vl 检测在后台线程执行，避免阻塞前端「确认上传」。
 * demo-fast-face-check=true 时同步 PASSED，演示链路不调 VL。
 */
@Service
public class PhotoService {

    private static final Logger log = LoggerFactory.getLogger(PhotoService.class);

    public static final String FACE_PENDING = "PENDING";
    public static final String FACE_PASSED = "PASSED";
    public static final String FACE_FAILED = "FAILED";

    private final StorageService storageService;
    private final SessionRepository sessionRepository;
    private final AiService aiService;
    private final TaskExecutor photoCheckExecutor;
    private final AppProperties appProperties;

    public PhotoService(StorageService storageService, SessionRepository sessionRepository,
                        AiService aiService,
                        @Qualifier("photoCheckExecutor") TaskExecutor photoCheckExecutor,
                        AppProperties appProperties) {
        this.storageService = storageService;
        this.sessionRepository = sessionRepository;
        this.aiService = aiService;
        this.photoCheckExecutor = photoCheckExecutor;
        this.appProperties = appProperties;
    }

    /** 落盘 + 创建会话后立即返回；人脸检测异步进行（演示模式可同步跳过）。 */
    public Map<String, Object> uploadAndPreCheck(MultipartFile file) throws IOException {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        String objectName = "user-photos/" + sessionId + extension(file);
        StorageService.Stored stored = storageService.save(file, objectName);

        sessionRepository.createSession(sessionId, stored.url(), stored.path().toString());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("photoUrl", stored.url());

        if (appProperties.getAi().isDemoFastFaceCheck()) {
            sessionRepository.updateFaceCheck(sessionId, FACE_PASSED, null);
            result.put("faceCheckStatus", FACE_PASSED);
            result.put("message", "照片上传成功（演示模式，已跳过人脸检测）");
            log.info("照片上传成功 sessionId={}, url={}, faceCheck=demo-skip", sessionId, stored.url());
            return result;
        }

        byte[] imageBytes = readSafe(stored.path());
        String mimeType = file.getContentType();
        photoCheckExecutor.execute(() ->
                runFaceCheckAsync(sessionId, imageBytes, mimeType, objectName));

        result.put("faceCheckStatus", FACE_PENDING);
        result.put("message", "照片上传成功，人脸检测进行中");
        log.info("照片上传成功 sessionId={}, url={}, faceCheck=async", sessionId, stored.url());
        return result;
    }

    public Map<String, Object> getFaceCheckStatus(String sessionId) {
        SessionRepository.Session session = sessionRepository.getSession(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("会话已过期，请重新上传照片");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("status", session.faceCheckStatus != null ? session.faceCheckStatus : FACE_PASSED);
        result.put("message", session.faceCheckMessage);
        return result;
    }

    private void runFaceCheckAsync(String sessionId, byte[] imageBytes, String mimeType, String objectName) {
        try {
            boolean hasFace = aiService.detectFace(imageBytes, mimeType);
            if (!hasFace) {
                sessionRepository.updateFaceCheck(sessionId, FACE_FAILED,
                        "未检测到清晰的正面人脸，请重新拍摄");
                storageService.delete(objectName);
                log.info("[PhotoCheck] 未通过 sessionId={}", sessionId);
                return;
            }
            sessionRepository.updateFaceCheck(sessionId, FACE_PASSED, null);
            log.info("[PhotoCheck] 通过 sessionId={}", sessionId);
        } catch (Exception e) {
            log.warn("[PhotoCheck] 异常 sessionId={}", sessionId, e);
            sessionRepository.updateFaceCheck(sessionId, FACE_FAILED, "人脸检测失败，请重试");
        }
    }

    private byte[] readSafe(java.nio.file.Path path) {
        try {
            return storageService.readBytes(path);
        } catch (IOException e) {
            return new byte[0];
        }
    }

    private String extension(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name != null && name.contains(".")) {
            return name.substring(name.lastIndexOf('.'));
        }
        return ".jpg";
    }
}
