package com.tasneem.taskmanager.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Bound from CORS_ALLOWED_ORIGINS (comma-separated). */
@ConfigurationProperties("app.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
