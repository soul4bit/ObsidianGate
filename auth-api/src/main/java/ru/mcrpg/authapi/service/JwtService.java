package ru.mcrpg.authapi.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;
import ru.mcrpg.authapi.config.AuthApiProperties;
import ru.mcrpg.authapi.domain.entity.AccountEntity;
import ru.mcrpg.authapi.web.error.ApiException;

@Service
public class JwtService {

    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String TOKEN_TYPE_ACCESS = "access";

    private final AuthApiProperties properties;
    private final SecretKey secretKey;

    public JwtService(AuthApiProperties properties) {
        this.properties = properties;
        secretKey = Keys.hmacShaKeyFor(resolveSecretBytes(properties.getJwtSecret()));
    }

    public IssuedAccessToken issueAccessToken(AccountEntity account) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.getAccessTokenTtlSeconds());

        String token = Jwts.builder()
            .subject(account.getId().toString())
            .claim("username", account.getUsername())
            .claim("role", account.getRole())
            .claim(TOKEN_TYPE_CLAIM, TOKEN_TYPE_ACCESS)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(secretKey)
            .compact();

        return new IssuedAccessToken(token, expiresAt);
    }

    public UUID parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
            if (!TOKEN_TYPE_ACCESS.equals(tokenType)) {
                throw ApiException.unauthorized("invalid_token", "Неподдерживаемый тип token.");
            }
            return UUID.fromString(claims.getSubject());
        } catch (IllegalArgumentException | JwtException exception) {
            throw ApiException.unauthorized("invalid_token", "Access token недействителен или истек.");
        }
    }

    private static byte[] resolveSecretBytes(String rawSecret) {
        String secret = rawSecret == null ? "" : rawSecret.trim();
        if (secret.isEmpty() || "change-me".equalsIgnoreCase(secret)) {
            throw new IllegalStateException("auth.jwt-secret must be set to a non-default value.");
        }
        byte[] secretBytes;
        if (secret.matches("(?i)^[0-9a-f]+$") && secret.length() % 2 == 0) {
            byte[] decoded = new byte[secret.length() / 2];
            for (int index = 0; index < secret.length(); index += 2) {
                decoded[index / 2] = (byte) Integer.parseInt(secret.substring(index, index + 2), 16);
            }
            secretBytes = decoded;
        } else {
            secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (secretBytes.length < 32) {
            throw new IllegalStateException("auth.jwt-secret must be at least 32 bytes for HS256.");
        }
        return secretBytes;
    }

    public record IssuedAccessToken(String token, Instant expiresAt) {
    }
}
