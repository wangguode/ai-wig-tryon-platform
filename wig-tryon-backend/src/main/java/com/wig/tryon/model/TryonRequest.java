package com.wig.tryon.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 提交试戴请求。
 */
public class TryonRequest {

    @NotBlank(message = "sessionId 不能为空")
    public String sessionId;

    @NotNull(message = "wigProductId 不能为空")
    public Long wigProductId;

    /** 用户自定义调整备注，例如 "想要齐刘海"、"遮盖白发"。 */
    public String adjustmentNote;

    /** 调整类型：FULL_WIG / LOCAL_BANGS / LOCAL_HAIRLINE，留空自动判断。 */
    public String tryonType;

    /**
     * 演示模式下前端选择的模型 key。
     * 有效值：gpt-image-1.5 / gpt-image-2 / gemini-3-pro-image / gemini-3.1-flash-image
     *         / seedream-4.5 / flux-2-max / qwen。
     * null 或空串 = 由后端环境变量 AI_TRYON_PROVIDER 决定。
     */
    public String preferredModel;

    public String getSessionId() {
        return sessionId;
    }

    public Long getWigProductId() {
        return wigProductId;
    }

    public String getAdjustmentNote() {
        return adjustmentNote;
    }

    public String getTryonType() {
        return tryonType;
    }

    public String getPreferredModel() {
        return preferredModel;
    }
}
