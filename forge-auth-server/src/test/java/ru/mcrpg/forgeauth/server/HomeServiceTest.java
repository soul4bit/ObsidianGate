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

class HomeServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void homesPersistAndRespectLimit() {
        Path homesPath = tempDirectory.resolve("homes.properties");
        HomeService service = new HomeService(Logger.getLogger("test"), homesPath);
        service.load();

        assertTrue(service.setHome("Player-UUID", "home", location(0.0D), 2).success);
        assertTrue(service.setHome("Player-UUID", "mine", location(10.0D), 2).success);

        HomeService.SetHomeResult limited = service.setHome("Player-UUID", "third", location(20.0D), 2);
        assertFalse(limited.success);
        assertEquals(2, limited.limit);

        HomeService restored = new HomeService(Logger.getLogger("test"), homesPath);
        restored.load();
        assertEquals(2, restored.listHomes("player-uuid").size());
        assertNotNull(restored.getHome("PLAYER-UUID", "home"));
        assertEquals(10.0D, restored.getHome("PLAYER-UUID", "mine").x);
    }

    @Test
    void existingHomeCanBeUpdatedBeyondLimitAndDeleted() {
        HomeService service = new HomeService(Logger.getLogger("test"), tempDirectory.resolve("homes.properties"));
        service.load();

        assertTrue(service.setHome("player", "home", location(1.0D), 1).success);
        HomeService.SetHomeResult updated = service.setHome("player", "home", location(2.0D), 1);

        assertTrue(updated.success);
        assertTrue(updated.updated);
        assertEquals(2.0D, service.getHome("player", "home").x);
        assertTrue(service.deleteHome("player", "home"));
        assertFalse(service.deleteHome("player", "home"));
    }

    @Test
    void primaryHomePrefersDefaultHomeName() {
        HomeService service = new HomeService(Logger.getLogger("test"), tempDirectory.resolve("homes.properties"));
        service.load();

        assertTrue(service.setHome("player", "mine", location(1.0D), 3).success);
        assertTrue(service.setHome("player", "home", location(2.0D), 3).success);
        assertTrue(service.setHome("player", "base", location(3.0D), 3).success);

        assertEquals("home", service.primaryHomeName("player"));
    }

    @Test
    void homeNamesAreValidated() {
        assertEquals("home", HomeService.normalizeName(""));
        assertEquals("base_1", HomeService.normalizeName("Base_1"));
        assertThrows(IllegalArgumentException.class, () -> HomeService.normalizeName("bad.name"));
    }

    private static HomeService.HomeLocation location(double x) {
        return new HomeService.HomeLocation(0, x, 64.0D, 0.0D, 90.0F, 0.0F);
    }
}
