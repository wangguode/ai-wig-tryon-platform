package com.wig.tryon.repository;

import com.wig.tryon.config.AppProperties;
import com.wig.tryon.model.FaceAnalysisResult;
import com.wig.tryon.model.TryonTask;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版会话 + 任务存储（替代 Redis）。会话含 TTL，过期视为失效。
 */
@Repository
public class SessionRepository {

    private final AppProperties appProperties;

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TryonTask> tasks = new ConcurrentHashMap<>();

    public SessionRepository(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public static class Session {
        public String sessionId;
        public String photoUrl;        // 对外可访问 URL
        public String photoPath;       // 本地绝对路径（供 AI 读取字节）
        public FaceAnalysisResult faceAnalysis;
        /** 异步人脸预检：PENDING / PASSED / FAILED */
        public String faceCheckStatus = "PENDING";
        public String faceCheckMessage;
        public Instant createdAt = Instant.now();
    }

    public Session createSession(String sessionId, String photoUrl, String photoPath) {
        Session s = new Session();
        s.sessionId = sessionId;
        s.photoUrl = photoUrl;
        s.photoPath = photoPath;
        s.faceCheckStatus = "PENDING";
        sessions.put(sessionId, s);
        return s;
    }

    public void updateFaceCheck(String sessionId, String status, String message) {
        Session s = sessions.get(sessionId);
        if (s != null) {
            s.faceCheckStatus = status;
            s.faceCheckMessage = message;
        }
    }

    public Session getSession(String sessionId) {
        Session s = sessions.get(sessionId);
        if (s == null) {
            return null;
        }
        Duration ttl = Duration.ofHours(appProperties.getSessionTtlHours());
        if (Instant.now().isAfter(s.createdAt.plus(ttl))) {
            sessions.remove(sessionId);
            return null;
        }
        return s;
    }

    public void saveAnalysis(String sessionId, FaceAnalysisResult result) {
        Session s = sessions.get(sessionId);
        if (s != null) {
            s.faceAnalysis = result;
        }
    }

    public void saveTask(TryonTask task) {
        tasks.put(task.taskId, task);
    }

    public TryonTask getTask(String taskId) {
        return tasks.get(taskId);
    }
}
