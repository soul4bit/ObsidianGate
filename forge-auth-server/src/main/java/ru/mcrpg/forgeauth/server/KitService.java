package ru.mcrpg.forgeauth.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

final class KitService {

    private static final Path DEFAULT_CLAIMS_PATH = Paths.get("obsidiangate", "kit-claims.properties");
    private static final String START_KIT_PREFIX = "start.";
    private static final Duration START_KIT_COOLDOWN = Duration.ofDays(30);

    private final Logger logger;
    private final Path claimsPath;
    private final Properties claims = new Properties();
    private boolean loaded;

    KitService(Logger logger) {
        this(logger, DEFAULT_CLAIMS_PATH);
    }

    KitService(Logger logger, Path claimsPath) {
        this.logger = logger;
        this.claimsPath = claimsPath;
    }

    synchronized void load() {
        claims.clear();
        if (Files.exists(claimsPath)) {
            try (InputStream input = Files.newInputStream(claimsPath)) {
                claims.load(input);
            } catch (IOException exception) {
                logger.log(Level.WARNING, "Не удалось прочитать выдачи китов. Запускаем пустое хранилище.", exception);
            }
        }
        loaded = true;
        logger.info(String.format("Выдачи китов загружены из %s. Записей=%d", claimsPath, claimCount()));
    }

    synchronized boolean hasClaimedStart(String playerId) {
        ensureLoaded();
        return claims.containsKey(startKitKey(playerId));
    }

    synchronized StartKitStatus startStatus(String playerId) {
        return startStatus(playerId, Instant.now());
    }

    synchronized StartKitStatus startStatus(String playerId, Instant now) {
        ensureLoaded();
        Instant safeNow = now == null ? Instant.now() : now;
        String key = startKitKey(playerId);
        if (!claims.containsKey(key)) {
            return StartKitStatus.available();
        }

        Instant claimedAt = claimInstant(key, safeNow);
        Instant nextAvailableAt = claimedAt.plus(START_KIT_COOLDOWN);
        if (!nextAvailableAt.isAfter(safeNow)) {
            return StartKitStatus.available(claimedAt, nextAvailableAt);
        }
        return StartKitStatus.cooldown(claimedAt, nextAvailableAt, Duration.between(safeNow, nextAvailableAt));
    }

    synchronized void recordStartClaim(String playerId, String playerName) {
        recordStartClaim(playerId, playerName, "", "");
    }

    synchronized void recordStartClaim(String playerId, String playerName, String accountId, String playerUuid) {
        recordStartClaim(playerId, playerName, accountId, playerUuid, Instant.now());
    }

    synchronized void recordStartClaim(String playerId, String playerName, String accountId, String playerUuid, Instant claimedAt) {
        ensureLoaded();
        String key = startKitKey(playerId);
        claims.setProperty(key, playerName == null ? "" : playerName);
        claims.setProperty(key + ".accountId", accountId == null ? "" : accountId.trim());
        claims.setProperty(key + ".playerName", playerName == null ? "" : playerName.trim());
        claims.setProperty(key + ".playerUuid", playerUuid == null ? "" : playerUuid.trim());
        claims.setProperty(key + ".claimedAt", (claimedAt == null ? Instant.now() : claimedAt).toString());
        save();
    }

    private Instant claimInstant(String key, Instant fallback) {
        String raw = claims.getProperty(key + ".claimedAt", "").trim();
        if (!raw.isEmpty()) {
            try {
                return Instant.parse(raw);
            } catch (RuntimeException ignored) {
            }
        }

        Instant repaired = fallback == null ? Instant.now() : fallback;
        claims.setProperty(key + ".claimedAt", repaired.toString());
        save();
        return repaired;
    }

    private void ensureLoaded() {
        if (!loaded) {
            load();
        }
    }

    private void save() {
        try {
            Path parent = claimsPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(claimsPath)) {
                claims.store(output, "Одноразовые выдачи китов ObsidianGate.");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось сохранить выдачи китов.", exception);
        }
    }

    private int claimCount() {
        int count = 0;
        for (Object key : claims.keySet()) {
            String name = String.valueOf(key);
            if (name.startsWith(START_KIT_PREFIX) && name.indexOf('.', START_KIT_PREFIX.length()) < 0) {
                count++;
            }
        }
        return count;
    }

    private static String startKitKey(String playerId) {
        if (playerId == null || playerId.trim().isEmpty()) {
            throw new IllegalArgumentException("playerId не должен быть пустым.");
        }
        return START_KIT_PREFIX + playerId.trim().toLowerCase();
    }

    static final class StartKitStatus {
        private final boolean available;
        private final Instant claimedAt;
        private final Instant nextAvailableAt;
        private final Duration remaining;

        private StartKitStatus(boolean available, Instant claimedAt, Instant nextAvailableAt, Duration remaining) {
            this.available = available;
            this.claimedAt = claimedAt;
            this.nextAvailableAt = nextAvailableAt;
            this.remaining = remaining == null ? Duration.ZERO : remaining;
        }

        static StartKitStatus available() {
            return new StartKitStatus(true, null, null, Duration.ZERO);
        }

        static StartKitStatus available(Instant claimedAt, Instant nextAvailableAt) {
            return new StartKitStatus(true, claimedAt, nextAvailableAt, Duration.ZERO);
        }

        static StartKitStatus cooldown(Instant claimedAt, Instant nextAvailableAt, Duration remaining) {
            return new StartKitStatus(false, claimedAt, nextAvailableAt, remaining);
        }

        boolean isAvailable() {
            return available;
        }

        Instant getClaimedAt() {
            return claimedAt;
        }

        Instant getNextAvailableAt() {
            return nextAvailableAt;
        }

        Duration getRemaining() {
            return remaining;
        }
    }
}
