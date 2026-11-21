package com.tasneem.taskmanager.auth;

import com.tasneem.taskmanager.user.User;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/** Issues and verifies HS256-signed access tokens. The subject is the user id. */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration expiration;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
        this.expiration = properties.expiration();
        this.clock = clock;
    }

    public String generateToken(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    /** Returns the user id if the token has a valid signature and has not expired. */
    public Optional<Long> extractUserId(String token) {
        try {
            String subject = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Optional.of(Long.valueOf(subject));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long getExpirationSeconds() {
        return expiration.toSeconds();
    }
}
