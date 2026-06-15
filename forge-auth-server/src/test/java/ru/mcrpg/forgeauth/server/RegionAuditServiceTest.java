package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegionAuditServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void regionActionsUseSeparateAuditLog() throws Exception {
        Path blockLog = tempDirectory.resolve("region-audit.log");
        RegionAuditService audit = new RegionAuditService(Logger.getLogger("test"), blockLog);
        RegionProtectionService.Region region = new RegionProtectionService.Region(
            "home",
            "owner-id",
            "Owner",
            0,
            0,
            64,
            0,
            5,
            70,
            5,
            Collections.<String>emptySet(),
            Collections.<RegionProtectionService.RegionFlag, Boolean>emptyMap(),
            0
        );

        audit.recordAction(new FakePlayer(UUID.randomUUID(), "Admin"), "claim", region, "bounds=X 0..5");

        assertTrue(Files.notExists(blockLog));
        Path actionLog = tempDirectory.resolve("region-actions.log");
        assertTrue(Files.isRegularFile(actionLog));
        String line = Files.readAllLines(actionLog, StandardCharsets.UTF_8).get(0);
        assertTrue(line.startsWith("ACTION\t"));
        assertEquals(7, line.split("\\t", -1).length);
    }

    private static final class FakePlayer {
        private final UUID id;
        private final String name;

        private FakePlayer(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        public UUID getUniqueID() {
            return id;
        }

        public String getName() {
            return name;
        }
    }
}
