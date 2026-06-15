package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegionProtectionServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void claimsPersistAndProtectSelectedHeight() {
        Path path = tempDirectory.resolve("regions.properties");
        RegionProtectionService service = service(path);
        select(service, "owner", 0, 10, 20, 20, 30);

        RegionProtectionService.ClaimResult result = service.claim("Base_1", "owner", "Owner", 3);

        assertTrue(result.success);
        assertEquals("base_1", result.region.name);
        assertEquals(121L, result.region.horizontalArea());
        assertTrue(service.canBuild("stranger", "Stranger", false, 0, 15, 0, 25));
        assertTrue(service.canBuild("stranger", "Stranger", false, 0, 15, 255, 25));
        assertFalse(service.canBuild("stranger", "Stranger", false, 0, 15, 65, 25));
        assertTrue(service.canBuild("owner", "Owner", false, 0, 15, 64, 25));
        assertTrue(service.canBuild("stranger", "Stranger", false, 0, 30, 64, 25));

        RegionProtectionService restored = service(path);
        assertNotNull(restored.region("base_1"));
        assertFalse(restored.canBuild("stranger", "Stranger", false, 0, 15, 64, 25));
    }

    @Test
    void expandsSelectionInMultipleDirectionsAndClampsHeight() {
        RegionProtectionService service = service(tempDirectory.resolve("regions.properties"));
        select(service, "owner", 0, 10, 10, 20, 20);

        RegionProtectionService.Selection expanded = service.expandSelection("owner", 50, "up", "down", "west");

        assertEquals(-40, expanded.first.x);
        assertEquals(14, expanded.first.y);
        assertEquals(120, expanded.second.y);
        assertEquals(20, expanded.second.x);

        service.expandSelection("owner", 255, "up", "down");
        assertEquals(0, expanded.first.y);
        assertEquals(255, expanded.second.y);
    }

    @Test
    void loadsLegacyRegionsAtFullWorldHeight() throws Exception {
        Path path = tempDirectory.resolve("legacy-regions.properties");
        Files.write(
            path,
            (
                "region.legacy.ownerId=owner\n"
                    + "region.legacy.ownerName=Owner\n"
                    + "region.legacy.dimension=0\n"
                    + "region.legacy.minX=0\n"
                    + "region.legacy.minZ=0\n"
                    + "region.legacy.maxX=5\n"
                    + "region.legacy.maxZ=5\n"
            ).getBytes(StandardCharsets.ISO_8859_1)
        );

        RegionProtectionService service = service(path);

        assertFalse(service.canBuild("stranger", "Stranger", false, 0, 1, 0, 1));
        assertFalse(service.canBuild("stranger", "Stranger", false, 0, 1, 255, 1));
    }

    @Test
    void rejectsOverlapAndOversizedSelections() {
        RegionProtectionService service = service(tempDirectory.resolve("regions.properties"));
        select(service, "first", 0, 0, 0, 10, 10);
        assertTrue(service.claim("first", "first", "First", 3).success);

        select(service, "second", 0, 10, 10, 20, 20);
        assertFalse(service.claim("second", "second", "Second", 3).success);

        select(service, "third", 0, 100, 100, 500, 500);
        assertFalse(service.claim("huge", "third", "Third", 3).success);
    }

    @Test
    void membersCanBuildAndOwnerCanRemoveThem() {
        RegionProtectionService service = service(tempDirectory.resolve("regions.properties"));
        select(service, "owner", 0, -5, -5, 5, 5);
        assertTrue(service.claim("home", "owner", "Owner", 3).success);

        assertTrue(service.addMember("home", "owner", "Friend", false));
        assertTrue(service.canBuild("friend-id", "friend", false, 0, 0, 64, 0));
        assertTrue(service.removeMember("home", "owner", "Friend", false));
        assertFalse(service.canBuild("friend-id", "friend", false, 0, 0, 64, 0));
        assertThrows(
            IllegalArgumentException.class,
            () -> service.delete("home", "stranger", false)
        );
        assertTrue(service.delete("home", "operator", true));

        RegionProtectionService reloaded = service(tempDirectory.resolve("regions.properties"));
        assertTrue(reloaded.restore("home"));
        assertNotNull(reloaded.region("home"));
    }

    @Test
    void enforcesPerPlayerRegionLimit() {
        RegionProtectionService service = service(tempDirectory.resolve("regions.properties"));
        for (int index = 0; index < 3; index++) {
            int start = index * 20;
            select(service, "owner", 0, start, 0, start + 5, 5);
            assertTrue(service.claim("r" + index, "owner", "Owner", 3).success);
        }
        select(service, "owner", 0, 100, 0, 105, 5);
        assertFalse(service.claim("extra", "owner", "Owner", 3).success);
    }

    @Test
    void flagsPersistAndControlPublicAccess() {
        Path path = tempDirectory.resolve("regions.properties");
        RegionProtectionService service = service(path);
        select(service, "owner", 0, 0, 0, 5, 5);
        assertTrue(service.claim("flags", "owner", "Owner", 3).success);

        assertFalse(service.allows(
            RegionProtectionService.RegionFlag.DOORS,
            "stranger",
            "Stranger",
            false,
            0,
            1,
            64,
            1
        ));
        assertTrue(service.setFlag("flags", "owner", "doors", true, false));
        assertTrue(service.allows(
            RegionProtectionService.RegionFlag.DOORS,
            "stranger",
            "Stranger",
            false,
            0,
            1,
            64,
            1
        ));

        RegionProtectionService restored = service(path);
        assertTrue(restored.region("flags").flag(RegionProtectionService.RegionFlag.DOORS));
    }

    @Test
    void vipLimitAllowsFiveRegions() {
        RegionProtectionService service = service(tempDirectory.resolve("regions.properties"));
        for (int index = 0; index < 5; index++) {
            int start = index * 20;
            select(service, "vip", 0, start, 0, start + 5, 5);
            assertTrue(service.claim("vip" + index, "vip", "Vip", 5).success);
        }
        assertEquals(5, service.ownedRegions("vip").size());
        assertEquals(5, service.find("Vip").size());
    }

    @Test
    void nestedAdminRegionsUsePriorityAndRejectPartialOverlap() {
        Path path = tempDirectory.resolve("regions.properties");
        RegionProtectionService service = service(path);
        select(service, "owner", 0, 0, 0, 20, 20);
        assertTrue(service.claim("outer", "owner", "Owner", 3).success);

        select(service, "admin", 0, 5, 5, 10, 10);
        assertTrue(service.claim("inner", "admin", "Admin", Integer.MAX_VALUE, true).success);
        service.setPriority("inner", 10);
        assertEquals("inner", service.regionAt(0, 7, 64, 7).name);
        assertEquals("outer", service.regionAt(0, 15, 64, 15).name);

        select(service, "admin", 0, 18, 18, 25, 25);
        assertFalse(service.claim("partial", "admin", "Admin", Integer.MAX_VALUE, true).success);

        RegionProtectionService restored = service(path);
        assertEquals(10, restored.region("inner").priority);
        assertEquals("inner", restored.regionAt(0, 7, 64, 7).name);
    }

    @Test
    void redefinePreservesSettingsAndTransferChangesOwner() {
        Path path = tempDirectory.resolve("regions.properties");
        RegionProtectionService service = service(path);
        select(service, "owner", 0, 0, 0, 5, 5);
        assertTrue(service.claim("home", "owner", "Owner", 3).success);
        service.addMember("home", "owner", "Friend", false);
        service.setFlag("home", "owner", "enderpearl", true, false);

        select(service, "owner", 0, 20, 20, 30, 30);
        RegionProtectionService.Region redefined = service.redefine("home", "owner", false);
        assertEquals(20, redefined.minX);
        assertTrue(redefined.members.contains("friend"));
        assertTrue(redefined.flag(RegionProtectionService.RegionFlag.ENDERPEARL));

        RegionProtectionService.Region transferred = service.transfer("home", "owner", "new-owner", "NewOwner", 3, false);
        assertEquals("new-owner", transferred.ownerId);
        assertFalse(service.canBuild("owner", "Owner", false, 0, 25, 64, 25));
        assertTrue(service.canBuild("new-owner", "NewOwner", false, 0, 25, 64, 25));

        RegionProtectionService restored = service(path);
        assertEquals("new-owner", restored.region("home").ownerId);
        assertTrue(restored.region("home").flag(RegionProtectionService.RegionFlag.ENDERPEARL));
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
