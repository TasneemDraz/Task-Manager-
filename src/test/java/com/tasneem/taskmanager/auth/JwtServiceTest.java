package com.tasneem.taskmanager.auth;

import com.tasneem.taskmanager.user.User;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQtdGhhdC1pcy1hdC1sZWFzdC0zMi1ieXRlcy1sb25n";
    private static final String OTHER_SECRET = "YW5vdGhlci1zZWNyZXQtdGhhdC1pcy1hbHNvLTMyLWJ5dGVzLWxvbmc=";
    private static final Instant ISSUED_AT = Instant.parse("2026-11-21T10:00:00Z");

    private static JwtService serviceAt(String secret, Instant now) {
        return new JwtService(new JwtProperties(secret, Duration.ofHours(24)), Clock.fixed(now, ZoneOffset.UTC));
    }

    private static User user() {
        User user = new User("Jane Doe", "jane@example.com", "hash");
        user.setId(7L);
        return user;
    }

    @Test
    void acceptsOwnTokenBeforeExpiry() {
        String token = serviceAt(SECRET, ISSUED_AT).generateToken(user());

        assertThat(serviceAt(SECRET, ISSUED_AT.plus(Duration.ofHours(23))).extractUserId(token)).contains(7L);
    }

    @Test
    void rejectsExpiredToken() {
        String token = serviceAt(SECRET, ISSUED_AT).generateToken(user());

        assertThat(serviceAt(SECRET, ISSUED_AT.plus(Duration.ofHours(25))).extractUserId(token)).isEmpty();
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        String token = serviceAt(OTHER_SECRET, ISSUED_AT).generateToken(user());

        assertThat(serviceAt(SECRET, ISSUED_AT).extractUserId(token)).isEmpty();
    }

    @Test
    void rejectsGarbage() {
        assertThat(serviceAt(SECRET, ISSUED_AT).extractUserId("not-a-jwt")).isEmpty();
    }
}
