package com.wig.tryon.model;

import java.time.Instant;

/**
 * 试戴任务（内存态）。
 */
public class TryonTask {

    public enum Status {
        PENDING, PROCESSING, SUCCESS, FAILED
    }

    public String taskId;
    public String sessionId;
    public String userPhotoUrl;
    public Long wigProductId;
    public volatile Status status = Status.PENDING;
    public volatile int progress = 0;
    public volatile String resultImageUrl;
    public volatile String errorMessage;
    public String aiPrompt;
    public String aiMode;             // real / seed / mock
    public int retryCount = 0;        // 变化不足触发的自动重试次数
    public Double changeScore;        // 原图与结果图平均像素变化分（越大变化越明显）
    public String qualityNote;        // 质量提示（如变化偏小）
    public String strategyUsed;       // 本次使用的模型/提示词策略
    public String adjustmentNote;
    public String tryonType;          // FULL_WIG / LOCAL_BANGS / LOCAL_HAIRLINE
    /** 演示模式下前端选择的模型 key（如 gpt-image-1.5），null 则由后端环境变量决定。 */
    public String preferredModel;
    public Instant createdAt = Instant.now();
    public volatile Instant updatedAt = Instant.now();

    public TryonTask() {
    }
}
