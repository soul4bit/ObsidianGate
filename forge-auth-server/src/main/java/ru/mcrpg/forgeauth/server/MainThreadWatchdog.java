package ru.mcrpg.forgeauth.server;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

final class MainThreadWatchdog {

    private static final String WARN_MILLIS_PROPERTY = "obsidiangate.watchdog.warnMillis";
    private static final long DEFAULT_WARN_MILLIS = 75L;

    private MainThreadWatchdog() {
    }

    static long start() {
        return System.nanoTime();
    }

    static void warnIfSlow(Logger logger, String operation, long startNanos, String detail) {
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        long thresholdMillis = thresholdMillis();
        if (elapsedMillis < thresholdMillis) {
            return;
        }

        StringBuilder message = new StringBuilder();
        message
            .append("[MAIN_THREAD_WATCHDOG] ")
            .append(operation)
            .append(" took ")
            .append(elapsedMillis)
            .append("ms")
            .append(" threshold=")
            .append(thresholdMillis)
            .append("ms");
        if (detail != null && !detail.trim().isEmpty()) {
            message.append(" ").append(detail.trim());
        }
        logger.warning(message.toString());
    }

    private static long thresholdMillis() {
        String raw = System.getProperty(WARN_MILLIS_PROPERTY);
        if (raw == null || raw.trim().isEmpty()) {
            return DEFAULT_WARN_MILLIS;
        }
        try {
            return Math.max(1L, Long.parseLong(raw.trim()));
        } catch (NumberFormatException ignored) {
            return DEFAULT_WARN_MILLIS;
        }
    }
}
