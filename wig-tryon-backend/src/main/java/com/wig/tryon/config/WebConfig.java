package com.wig.tryon.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 跨域 + 本地存储静态资源映射（/files/** -> 本地存储目录）+ 运营后台鉴权拦截。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties appProperties;
    private final AdminAuthInterceptor adminAuthInterceptor;

    public WebConfig(AppProperties appProperties, AdminAuthInterceptor adminAuthInterceptor) {
        this.appProperties = appProperties;
        this.adminAuthInterceptor = adminAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 仅拦截 /admin/**；C 端业务接口不受影响
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/admin/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(appProperties.getStorage().getDir())
                .toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/files/**")
                .addResourceLocations(location);
    }
}
