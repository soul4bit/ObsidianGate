package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KitServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void startClaimPersistsAcrossServiceReloads() {
        Path claimsPath = tempDirectory.resolve("kit-claims.properties");
        KitService service = new KitService(Logger.getLogger("test"), claimsPath);

        service.load();
        assertFalse(service.hasClaimedStart("Player-UUID"));

        service.recordStartClaim("Player-UUID", "Player");
        assertTrue(service.hasClaimedStart("player-uuid"));

        KitService restored = new KitService(Logger.getLogger("test"), claimsPath);
        restored.load();
        assertTrue(restored.hasClaimedStart("PLAYER-UUID"));
    }

    @Test
    void startClaimStoresAuditMetadata() throws Exception {
        Path claimsPath = tempDirectory.resolve("kit-claims.properties");
        KitService service = new KitService(Logger.getLogger("test"), claimsPath);

        service.load();
        service.recordStartClaim("account:acc-123", "Knight", "acc-123", "uuid-123");

        Properties stored = new Properties();
        try (InputStream input = java.nio.file.Files.newInputStream(claimsPath)) {
            stored.load(input);
        }

        assertTrue(service.hasClaimedStart("account:acc-123"));
        assertTrue("Knight".equals(stored.getProperty("start.account:acc-123.playerName")));
        assertTrue("acc-123".equals(stored.getProperty("start.account:acc-123.accountId")));
        assertTrue("uuid-123".equals(stored.getProperty("start.account:acc-123.playerUuid")));
        assertNotNull(stored.getProperty("start.account:acc-123.claimedAt"));
    }

    @Test
    void startClaimBecomesAvailableAfterThirtyDays() {
        Path claimsPath = tempDirectory.resolve("kit-claims.properties");
        KitService service = new KitService(Logger.getLogger("test"), claimsPath);
        Instant claimedAt = Instant.parse("2026-06-01T00:00:00Z");

        service.load();
        service.recordStartClaim("account:acc-123", "Knight", "acc-123", "uuid-123", claimedAt);

        KitService.StartKitStatus cooldown = service.startStatus(
            "account:acc-123",
            claimedAt.plus(Duration.ofDays(29)).plus(Duration.ofHours(23))
        );
        assertFalse(cooldown.isAvailable());
        assertEquals(Duration.ofHours(1), cooldown.getRemaining());

        KitService.StartKitStatus available = service.startStatus("account:acc-123", claimedAt.plus(Duration.ofDays(30)));
        assertTrue(available.isAvailable());
    }
}
