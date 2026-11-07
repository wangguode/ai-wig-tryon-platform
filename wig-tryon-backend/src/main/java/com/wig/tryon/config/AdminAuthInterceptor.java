package com.wig.tryon.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 运营后台简易鉴权拦截器（MVP）。
 * 规则：
 *   - admin.enabled=false → 所有 /admin/** 请求直接 404，演示环境零暴露
 *   - admin.enabled=true 但未配置 token → 启动失败前置校验（在 BeanConfig 里检查），这里只做请求侧鉴权
 *   - 请求头 Authorization: Bearer ${token} 必须匹配，否则 401
 *
 * 不放行 OPTIONS 预检——CORS 由 Spring 的 CORS filter 在更早阶段处理。
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthInterceptor.class);

    private final AdminProperties props;

    public AdminAuthInterceptor(AdminProperties props) {
        this.props = props;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 关闭：所有 admin 路径全部 404，外界感知不到管理后台存在
        if (!props.isEnabled()) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return false;
        }
        // CORS 预检直接放行，避免浏览器拿不到 Allow 头
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!props.hasToken()) {
            log.error("[Admin] 已开启但未配置 token，拒绝所有请求");
            writeJsonError(response, HttpStatus.SERVICE_UNAVAILABLE,
                    "管理后台未配置 token，请先设置 WIG_ADMIN_TOKEN 环境变量");
            return false;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            writeJsonError(response, HttpStatus.UNAUTHORIZED, "缺少管理员 token");
            return false;
        }
        String token = header.substring("Bearer ".length()).trim();
        if (!props.getToken().equals(token)) {
            log.warn("[Admin] token 不匹配，来源 IP={}, path={}", request.getRemoteAddr(), request.getRequestURI());
            writeJsonError(response, HttpStatus.UNAUTHORIZED, "管理员 token 无效");
            return false;
        }
        return true;
    }

    private void writeJsonError(HttpServletResponse response, HttpStatus status, String message) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // 与 ApiResponse 统一返回体保持一致：{code, message, data}
        String body = String.format("{\"code\":%d,\"message\":\"%s\",\"data\":null}",
                status.value(), message.replace("\"", "\\\""));
        response.getWriter().write(body);
    }
}
