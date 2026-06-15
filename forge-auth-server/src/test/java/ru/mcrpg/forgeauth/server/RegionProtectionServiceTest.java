package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegionProtectionServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void claimsPersistAndProtectFullHeight() {
        Path path = tempDirectory.resolve("regions.properties");
        RegionProtectionService service = service(path);
        select(service, "owner", 0, 10, 20, 20, 30);

        RegionProtectionService.ClaimResult result = service.claim("Base_1", "owner", "Owner");

        assertTrue(result.success);
        assertEquals("base_1", result.region.name);
        assertEquals(121L, result.region.horizontalArea());
        assertFalse(service.canBuild("stranger", "Stranger", false, 0, 15, 0, 25));
        assertFalse(service.canBuild("stranger", "Stranger", false, 0, 15, 255, 25));
        assertTrue(service.canBuild("owner", "Owner", false, 0, 15, 64, 25));
        assertTrue(service.canBuild("stranger", "Stranger", false, 0, 30, 64, 25));

        RegionProtectionService restored = service(path);
        assertNotNull(restored.region("base_1"));
        assertFalse(restored.canBuild("stranger", "Stranger", false, 0, 15, 64, 25));
    }

    @Test
    void rejectsOverlapAndOversizedSelections() {
        RegionProtectionService service = service(tempDirectory.resolve("regions.properties"));
        select(service, "first", 0, 0, 0, 10, 10);
        assertTrue(service.claim("first", "first", "First").success);

        select(service, "second", 0, 10, 10, 20, 20);
        assertFalse(service.claim("second", "second", "Second").success);

        select(service, "third", 0, 100, 100, 500, 500);
        assertFalse(service.claim("huge", "third", "Third").success);
    }

    @Test
    void membersCanBuildAndOwnerCanRemoveThem() {
        RegionProtectionService service = service(tempDirectory.resolve("regions.properties"));
        select(service, "owner", 0, -5, -5, 5, 5);
        assertTrue(service.claim("home", "owner", "Owner").success);

        assertTrue(service.addMember("home", "owner", "Friend", false));
        assertTrue(service.canBuild("friend-id", "friend", false, 0, 0, 64, 0));
        assertTrue(service.removeMember("home", "owner", "Friend", false));
        assertFalse(service.canBuild("friend-id", "friend", false, 0, 0, 64, 0));
        assertThrows(
            IllegalArgumentException.class,
            () -> service.delete("home", "stranger", false)
        );
        assertTrue(service.delete("home", "operator", true));
    }

    @Test
    void enforcesPerPlayerRegionLimit() {
        RegionProtectionService service = service(tempDirectory.resolve("regions.properties"));
        for (int index = 0; index < RegionProtectionService.MAX_REGIONS_PER_PLAYER; index++) {
            int start = index * 20;
            select(service, "owner", 0, start, 0, start + 5, 5);
            assertTrue(service.claim("r" + index, "owner", "Owner").success);
        }
        select(service, "owner", 0, 100, 0, 105, 5);
        assertFalse(service.claim("extra", "owner", "Owner").success);
    }

    @Test
    void validatesRegionNames() {
        assertEquals("base-1", RegionProtectionService.normalizeRegionName("Base-1"));
        assertThrows(IllegalArgumentException.class, () -> RegionProtectionService.normalizeRegionName("дом"));
        assertThrows(IllegalArgumentException.class, () -> RegionProtectionService.normalizeRegionName("bad name"));
    }

    private RegionProtectionService service(Path path) {
        RegionProtectionService service = new RegionProtectionService(Logger.getLogger("test"), path);
        service.load();
        return service;
    }

    private static void select(
        RegionProtectionService service,
        String playerId,
        int dimension,
        int x1,
        int z1,
        int x2,
        int z2
    ) {
        service.setSelectionPoint(playerId, 1, new RegionProtectionService.Position(dimension, x1, 64, z1));
        service.setSelectionPoint(playerId, 2, new RegionProtectionService.Position(dimension, x2, 70, z2));
    }
}
