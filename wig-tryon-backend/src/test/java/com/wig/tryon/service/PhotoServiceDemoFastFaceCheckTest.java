package com.wig.tryon.service;

import com.wig.tryon.config.AppProperties;
import com.wig.tryon.repository.SessionRepository;
import com.wig.tryon.service.ai.AiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PhotoServiceDemoFastFaceCheckTest {

    @Mock StorageService storageService;
    @Mock SessionRepository sessionRepository;
    @Mock AiService aiService;
    @Mock TaskExecutor photoCheckExecutor;

    AppProperties appProperties;
    PhotoService photoService;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        photoService = new PhotoService(
                storageService, sessionRepository, aiService, photoCheckExecutor, appProperties);
    }

    @Test
    void demoFastFaceCheckTrue_skipsAsyncDetectFace() throws Exception {
        appProperties.getAi().setDemoFastFaceCheck(true);
        MultipartFile file = new MockMultipartFile("file", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        Path savedPath = Path.of("/tmp/test-face.jpg");
        when(storageService.save(eq(file), anyString()))
                .thenReturn(new StorageService.Stored("http://localhost/files/x.jpg", savedPath));
        when(sessionRepository.createSession(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> {
                    SessionRepository.Session s = new SessionRepository.Session();
                    s.sessionId = inv.getArgument(0);
                    s.faceCheckStatus = PhotoService.FACE_PENDING;
                    return s;
                });

        Map<String, Object> result = photoService.uploadAndPreCheck(file);

        assertEquals(PhotoService.FACE_PASSED, result.get("faceCheckStatus"));
        verify(sessionRepository).updateFaceCheck(anyString(), eq(PhotoService.FACE_PASSED), isNull());
        verify(photoCheckExecutor, never()).execute(any(Runnable.class));
        verify(aiService, never()).detectFace(any(), any());
    }

    @Test
    void demoFastFaceCheckFalse_stillSchedulesAsyncDetectFace() throws Exception {
        appProperties.getAi().setDemoFastFaceCheck(false);
        MultipartFile file = new MockMultipartFile("file", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        Path savedPath = Path.of("/tmp/test-face.jpg");
        when(storageService.save(eq(file), anyString()))
                .thenReturn(new StorageService.Stored("http://localhost/files/x.jpg", savedPath));
        when(storageService.readBytes(savedPath)).thenReturn(new byte[]{1, 2, 3});
        when(sessionRepository.createSession(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> new SessionRepository.Session());
        doAnswer(inv -> {
            Runnable r = inv.getArgument(0);
            r.run();
            return null;
        }).when(photoCheckExecutor).execute(any(Runnable.class));
        when(aiService.detectFace(any(), anyString())).thenReturn(true);

        Map<String, Object> result = photoService.uploadAndPreCheck(file);

        assertEquals(PhotoService.FACE_PENDING, result.get("faceCheckStatus"));
        verify(photoCheckExecutor, times(1)).execute(any(Runnable.class));
        verify(aiService, times(1)).detectFace(any(), eq("image/jpeg"));
        verify(sessionRepository).updateFaceCheck(anyString(), eq(PhotoService.FACE_PASSED), isNull());
    }
}
