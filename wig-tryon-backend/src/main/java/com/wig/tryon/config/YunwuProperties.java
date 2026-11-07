package com.wig.tryon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 中转站（OpenAI 兼容代理）配置。
 * 当前仅用于 /api/system/yunwu-ping 联调验证，验证通过后再决定是否接入主试戴流程。
 */
@Component
@ConfigurationProperties(prefix = "yunwu")
public class YunwuProperties {

    private String apiKey = "";
    private String baseUrl = "https://yunwu.ai/v1";
    private String imageModel = "gpt-image-2";
    private int timeoutSeconds = 60;
    /**
     * 试戴接入端点：chat（推荐，/v1/chat/completions 多模态多图融合，能吃 用户图+假发图+prompt）
     *              edits（/v1/images/edits，仅单张用户图+prompt，无法精确还原参考图）
     */
    private String tryonEndpoint = "chat";
    private String chatPath = "/chat/completions";
    private String editsPath = "/images/edits";
    private String generationsPath = "/images/generations";

    /**
     * Flux Kontext 专用配置。
     * 由于云雾的 dall-e edits 兼容路径需要"渠道分组"包含 Flux 才能跑，
     * 默认走 Replicate 异步路径（{@code /replicate/v1/models/.../predictions}），
     * 这条路径不依赖分组，文档示例就用它。
     * 见 YunwuFluxKontextAiService。
     */
    /**
     * Flux Kontext 在云雾上的 model 名。
     * <p>
     * 注意是 <b>flux.1-kontext-pro</b>（带 .1），不是 flux-kontext-pro。
     * 云雾平台模型卡片显示的就是这个名字，default 分组按次计费 ¥0.12。
     * 调错名字（缺 .1）会被云雾返回 503 "无可用渠道"。
     */
    private String fluxModel = "flux.1-kontext-pro";

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

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getTryonEndpoint() {
        return tryonEndpoint;
    }

    public void setTryonEndpoint(String tryonEndpoint) {
        this.tryonEndpoint = tryonEndpoint;
    }

    public String getChatPath() {
        return chatPath;
    }

    public void setChatPath(String chatPath) {
        this.chatPath = chatPath;
    }

    public String getEditsPath() {
        return editsPath;
    }

    public void setEditsPath(String editsPath) {
        this.editsPath = editsPath;
    }

    public String getGenerationsPath() {
        return generationsPath;
    }

    public void setGenerationsPath(String generationsPath) {
        this.generationsPath = generationsPath;
    }

    public String getFluxModel() {
        return fluxModel;
    }

    public void setFluxModel(String fluxModel) {
        this.fluxModel = fluxModel;
    }
}
