package ru.mcrpg.authapi.service;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
import ru.mcrpg.authapi.config.AuthApiProperties;
import ru.mcrpg.authapi.web.error.ApiException;

@Service
public class AuthRateLimiter {

    private static final long DEFAULT_WINDOW_MILLIS = 60_000L;
    private static final long CLEANUP_INTERVAL_MILLIS = 60_000L;
    private static final String REFRESH_IDENTITY = "refresh";

    private final AuthApiProperties properties;
    private final ConcurrentMap<String, Counter> counters = new ConcurrentHashMap<String, Counter>();
    private final AtomicLong lastCleanupAt = new AtomicLong();

    public AuthRateLimiter(AuthApiProperties properties) {
        this.properties = properties;
    }

    public void checkLogin(String clientAddress, String login) {
        check("login", clientAddress, login, properties.getLoginRateLimit());
    }

    public void checkRegister(String clientAddress, String username) {
        check("register", clientAddress, username, properties.getRegisterRateLimit());
    }

    public void checkRefresh(String clientAddress) {
        check("refresh", clientAddress, REFRESH_IDENTITY, properties.getRefreshRateLimit());
    }

    private void check(String scope, String clientAddress, String identity, int maxAttempts) {
        if (!properties.isRateLimitEnabled() || maxAttempts <= 0) {
            return;
        }

        long now = System.currentTimeMillis();
        long windowMillis = windowMillis();
        String key = scope + '\n' + normalize(clientAddress) + '\n' + normalize(identity);
        boolean allowed = incrementAndCheck(key, now, windowMillis, maxAttempts);
        cleanup(now, windowMillis);
        if (!allowed) {
            throw ApiException.tooManyRequests("rate_limited", "Слишком много попыток. Попробуйте позже.");
        }
    }

    private boolean incrementAndCheck(String key, long now, long windowMillis, int maxAttempts) {
        boolean[] allowed = new boolean[] { true };
        counters.compute(key, (ignored, counter) -> {
            if (counter == null || counter.isExpired(now, windowMillis)) {
                return new Counter(now, 1);
            }
            if (counter.count >= maxAttempts) {
                allowed[0] = false;
                return counter;
            }
            return new Counter(counter.windowStartedAtMillis, counter.count + 1);
        });
        return allowed[0];
    }

    private void cleanup(long now, long windowMillis) {
        long previous = lastCleanupAt.get();
        if (now - previous < CLEANUP_INTERVAL_MILLIS || !lastCleanupAt.compareAndSet(previous, now)) {
            return;
        }
        counters.entrySet().removeIf(entry -> entry.getValue().isExpired(now, windowMillis));
    }

    private long windowMillis() {
        long configuredSeconds = properties.getRateLimitWindowSeconds();
        if (configuredSeconds <= 0) {
            return DEFAULT_WINDOW_MILLIS;
        }
        return Math.multiplyExact(Math.min(configuredSeconds, 86_400L), 1_000L);
    }

    private static String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "-";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class Counter {

        private final long windowStartedAtMillis;
        private final int count;

        private Counter(long windowStartedAtMillis, int count) {
            this.windowStartedAtMillis = windowStartedAtMillis;
            this.count = count;
        }

        private boolean isExpired(long now, long windowMillis) {
            return now - windowStartedAtMillis >= windowMillis;
        }
    }
}
