package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerAchievementServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void oreProgressUnlocksAndPersistsTitles() {
        Path statePath = tempDirectory.resolve("player-achievements.properties");
        PlayerAchievementService service = new PlayerAchievementService(Logger.getLogger("test"), statePath);
        FakePlayer player = new FakePlayer("soul4bit");

        service.load();
        for (int index = 0; index < 100; index++) {
            service.recordOreBreakForPlayer(player, "minecraft:iron_ore");
        }

        PlayerAchievementService.PlayerProgress progress = service.snapshotFor(player);
        assertEquals(100L, progress.ores());
        assertTrue(progress.unlocked().contains("ore_digger"));
        assertTrue(service.setActiveTitle(player, "ore_digger"));
        assertEquals("\u00A76Рудокоп\u00A7r", service.activeTitleLabelFor(player));

        PlayerAchievementService restored = new PlayerAchievementService(Logger.getLogger("test"), statePath);
        restored.load();
        PlayerAchievementService.PlayerProgress restoredProgress = restored.snapshotFor(player);
        assertEquals(100L, restoredProgress.ores());
        assertEquals("ore_digger", restoredProgress.activeTitle());
    }

    @Test
    void diamondOreCountsBothOreAndDiamondProgress() {
        PlayerAchievementService service = new PlayerAchievementService(Logger.getLogger("test"), tempDirectory.resolve("state.properties"));
        FakePlayer player = new FakePlayer("alex");

        service.load();
        service.recordOreBreakForPlayer(player, "minecraft:diamond_ore");

        PlayerAchievementService.PlayerProgress progress = service.snapshotFor(player);
        assertEquals(1L, progress.ores());
        assertEquals(1L, progress.diamonds());
    }

    @Test
    void recognizesCommonModdedOreNames() {
        assertTrue(PlayerAchievementService.isOreBlock("thaumcraft:ore_cinnabar"));
        assertTrue(PlayerAchievementService.isOreBlock("immersiveengineering:ore"));
        assertTrue(PlayerAchievementService.isOreBlock("minecraft:lit_redstone_ore"));
    }

    static final class FakePlayer {
        private final String name;
        private final UUID uuid = UUID.randomUUID();

        FakePlayer(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public UUID getUniqueID() {
            return uuid;
        }
    }
}
