package com.stayscale.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configures Cross-Origin Resource Sharing (CORS) so the Next.js app on :3000
 * can call the Spring Boot API on :8080 directly from the browser.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${stayscale.cors.allowed-origins:http://localhost:3000}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                // Browsers hide custom response headers from fetch() unless explicitly listed here.
                .exposedHeaders("X-Trace-Id", "X-Experiment-Variant", "X-Cache-Status")
                .maxAge(3600);
    }
}
