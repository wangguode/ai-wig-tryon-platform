package com.wig.tryon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云百炼 DashScope 配置。
 */
@Component
@ConfigurationProperties(prefix = "dashscope")
public class DashScopeProperties {

    private String apiKey = "";
    private String visionModel = "qwen-vl-max";
    private String imageEditModel = "wanx2.1-imageedit";
    private String backupImageEditModel = "";
    private String tryonStrategy = "replace_first";
    private String tryonFunction = "description_edit";
    private boolean strictReplaceInputs = true;
    private double minChangeScore = 7.0;
    private String multimodalUrl;
    private String imageSynthesisUrl;
    private String taskQueryUrl;
    private int taskTimeoutSeconds = 120;

    // ---- 文生图（运营后台「AI 重画本体图」用） ----
    /** 文生图模型，wan2.2-t2i-plus 高质量 / wan2.2-t2i-flash 快但糙。 */
    private String textToImageModel = "wan2.2-t2i-plus";
    /** 文生图 URL，与 image2image 是不同端点。 */
    private String textToImageUrl = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis";
    /** 文生图输出分辨率，与本体图横纵比保持竖版。 */
    private String textToImageSize = "1024*1280";

    // ---- wan2.5/2.7 多图融合相关参数（仅当 image-edit-model 为 wan2.5+ 时生效） ----
    // prompt 智能改写：开启后模型会补全细节，对中文短 prompt 友好
    private boolean promptExtend = true;
    // 是否给结果图加水印（商用演示一般关闭）
    private boolean watermark = false;
    // 输出分辨率，如 "1280*1280" / "2K"；留空表示跟随输入图自动决定
    private String imageSize = "";
    // 人脸区护栏阈值：>0 时启用。若结果图“下半脸区域”相对原图变化超过该值，
    // 视为疑似改脸/掉份额，触发重试或回退（不依赖任何人脸识别库，纯像素差近似）。
    private double faceMaxChangeScore = 0.0;

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("sk-你的");
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getVisionModel() {
        return visionModel;
    }

    public void setVisionModel(String visionModel) {
        this.visionModel = visionModel;
    }

    public String getImageEditModel() {
        return imageEditModel;
    }

    public void setImageEditModel(String imageEditModel) {
        this.imageEditModel = imageEditModel;
    }

    public String getMultimodalUrl() {
        return multimodalUrl;
    }

    public void setMultimodalUrl(String multimodalUrl) {
        this.multimodalUrl = multimodalUrl;
    }

    public String getImageSynthesisUrl() {
        return imageSynthesisUrl;
    }

    public void setImageSynthesisUrl(String imageSynthesisUrl) {
        this.imageSynthesisUrl = imageSynthesisUrl;
    }

    public String getBackupImageEditModel() {
        return backupImageEditModel;
    }

    public void setBackupImageEditModel(String backupImageEditModel) {
        this.backupImageEditModel = backupImageEditModel;
    }

    public String getTryonStrategy() {
        return tryonStrategy;
    }

    public void setTryonStrategy(String tryonStrategy) {
        this.tryonStrategy = tryonStrategy;
    }

    public String getTryonFunction() {
        return tryonFunction;
    }

    public void setTryonFunction(String tryonFunction) {
        this.tryonFunction = tryonFunction;
    }

    public boolean isStrictReplaceInputs() {
        return strictReplaceInputs;
    }

    public void setStrictReplaceInputs(boolean strictReplaceInputs) {
        this.strictReplaceInputs = strictReplaceInputs;
    }

    public double getMinChangeScore() {
        return minChangeScore;
    }

    public void setMinChangeScore(double minChangeScore) {
        this.minChangeScore = minChangeScore;
    }

    public String getTaskQueryUrl() {
        return taskQueryUrl;
    }

    public void setTaskQueryUrl(String taskQueryUrl) {
        this.taskQueryUrl = taskQueryUrl;
    }

    public int getTaskTimeoutSeconds() {
        return taskTimeoutSeconds;
    }

    public void setTaskTimeoutSeconds(int taskTimeoutSeconds) {
        this.taskTimeoutSeconds = taskTimeoutSeconds;
    }

    public boolean isPromptExtend() {
        return promptExtend;
    }

    public void setPromptExtend(boolean promptExtend) {
        this.promptExtend = promptExtend;
    }

    public boolean isWatermark() {
        return watermark;
    }

    public void setWatermark(boolean watermark) {
        this.watermark = watermark;
    }

    public String getImageSize() {
        return imageSize;
    }

    public void setImageSize(String imageSize) {
        this.imageSize = imageSize;
    }

    public double getFaceMaxChangeScore() {
        return faceMaxChangeScore;
    }

    public void setFaceMaxChangeScore(double faceMaxChangeScore) {
        this.faceMaxChangeScore = faceMaxChangeScore;
    }

    public String getTextToImageModel() {
        return textToImageModel;
    }

    public void setTextToImageModel(String textToImageModel) {
        this.textToImageModel = textToImageModel;
    }

    public String getTextToImageUrl() {
        return textToImageUrl;
    }

    public void setTextToImageUrl(String textToImageUrl) {
        this.textToImageUrl = textToImageUrl;
    }

    public String getTextToImageSize() {
        return textToImageSize;
    }

    public void setTextToImageSize(String textToImageSize) {
        this.textToImageSize = textToImageSize;
    }
}
