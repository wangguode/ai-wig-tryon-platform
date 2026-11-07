package com.wig.tryon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 运营管理后台配置。
 * <p>
 * MVP 阶段：单一 token 鉴权，token 通过环境变量 WIG_ADMIN_TOKEN 注入，避免硬编码。
 * 上线前必须替换为账号体系。enabled=false 时所有 /admin/** 请求 404，演示零暴露。
 */
@Component
@ConfigurationProperties(prefix = "app.admin")
public class AdminProperties {

    private boolean enabled = false;
    private String token = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }
}
