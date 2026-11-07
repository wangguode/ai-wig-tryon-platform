package com.wig.tryon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 火山方舟（Volcengine Ark）配置：Doubao Seedream 等图像生成模型直连。
 */
@Component
@ConfigurationProperties(prefix = "volcengine.ark")
public class VolcengineArkProperties {

    private String apiKey = "";
    private String baseUrl = "https://ark.cn-beijing.volces.com/api/v3";
    /** 默认 Doubao Seedream 5.0 lite（260128 版本） */
    private String imageModel = "doubao-seedream-5-0-260128";
    /** 输出尺寸：2K / 1024x1024 等，留空则走模型默认 */
    private String imageSize = "2K";
    private boolean watermark = false;
    private int timeoutSeconds = 180;

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getImageModel() {
        return imageModel;
    }

    public void setImageModel(String imageModel) {
        this.imageModel = imageModel;
    }

    public String getImageSize() {
        return imageSize;
    }

    public void setImageSize(String imageSize) {
        this.imageSize = imageSize;
    }

    public boolean isWatermark() {
        return watermark;
    }

    public void setWatermark(boolean watermark) {
        this.watermark = watermark;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
