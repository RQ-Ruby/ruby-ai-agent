package com.ruby.common.config;

import com.ruby.common.constant.FileConstant;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 全局跨域 + 静态资源映射配置
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 覆盖所有请求
        registry.addMapping("/**")
                // 允许发送 Cookie
                .allowCredentials(true)
                // 放行哪些域名（必须用 patterns，否则 * 会和 allowCredentials 冲突）
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("*");
    }

    /**
     * 把工具产物目录（PDF / 下载资源 / 文件）暴露成可下载的静态资源。
     * 例如：tmp/pdf/xxx.pdf  →  GET /api/files/pdf/xxx.pdf
     * 注：context-path 是 /api，所以前端访问形如 http://host:8080/api/files/...
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String fileSaveDir = FileConstant.FILE_SAVE_DIR;
        registry.addResourceHandler("/files/**")
                .addResourceLocations("file:" + fileSaveDir + "/");
    }
}
