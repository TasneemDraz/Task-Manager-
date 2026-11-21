package com.tasneem.taskmanager.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Bound from JWT_SECRET (Base64, at least 256 bits) and JWT_EXPIRATION (ISO-8601 duration). */
@Validated
@ConfigurationProperties("app.jwt")
public record JwtProperties(@NotBlank String secret, @NotNull Duration expiration) {
}
