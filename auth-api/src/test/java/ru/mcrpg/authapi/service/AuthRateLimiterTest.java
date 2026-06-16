package ru.mcrpg.authapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import ru.mcrpg.authapi.config.AuthApiProperties;
import ru.mcrpg.authapi.web.error.ApiException;

class AuthRateLimiterTest {

    @Test
    void loginLimiterBlocksRepeatedAttemptsForSameAddressAndLogin() {
        AuthApiProperties properties = new AuthApiProperties();
        properties.setLoginRateLimit(2);
        AuthRateLimiter limiter = new AuthRateLimiter(properties);

        limiter.checkLogin("203.0.113.10", "Player");
        limiter.checkLogin("203.0.113.10", "player");

        ApiException exception = assertThrows(
            ApiException.class,
            () -> limiter.checkLogin("203.0.113.10", "PLAYER")
        );
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
        assertEquals("rate_limited", exception.getError());
    }

    @Test
    void loginLimiterKeepsDifferentIdentitiesSeparate() {
        AuthApiProperties properties = new AuthApiProperties();
        properties.setLoginRateLimit(1);
        AuthRateLimiter limiter = new AuthRateLimiter(properties);

        limiter.checkLogin("203.0.113.10", "PlayerOne");
        limiter.checkLogin("203.0.113.10", "PlayerTwo");
    }

    @Test
    void refreshLimiterUsesClientAddressOnly() {
        AuthApiProperties properties = new AuthApiProperties();
        properties.setRefreshRateLimit(1);
        AuthRateLimiter limiter = new AuthRateLimiter(properties);

        limiter.checkRefresh("203.0.113.10");

        ApiException exception = assertThrows(
            ApiException.class,
            () -> limiter.checkRefresh("203.0.113.10")
        );
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
    }
}
