package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JudgementNightServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void loadCreatesDefaultConfig() {
        Path configPath = tempDirectory.resolve("obsidiangate-judgement-night.properties");
        JudgementNightService service = new JudgementNightService(Logger.getLogger("test"), configPath, () -> null, new Random(1));

        service.load();

        assertTrue(Files.exists(configPath));
        assertTrue(service.config().enabled);
        assertEquals(7, service.config().periodDays);
        assertEquals(20, service.config().waveIntervalSeconds);
        assertEquals(10, service.config().mobsPerPlayer);
        assertEquals(96, service.config().maxHostilesNearPlayer);
        assertTrue(service.config().mobClassNames.contains("divinerpg.objects.entities.entity.vanilla.EntityTheGrue"));
        assertFalse(service.config().mobClassNames.contains("divinerpg.objects.entities.entity.vanilla.EntityStoneGolem"));
    }

    @Test
    void loadAllowsCustomMobPool() throws Exception {
        Path configPath = tempDirectory.resolve("obsidiangate-judgement-night.properties");
        Files.write(
            configPath,
            ("mobClassNames=net.minecraft.entity.monster.EntityZombie, divinerpg.objects.entities.entity.vanilla.EntityTheEye\n")
                .getBytes(StandardCharsets.UTF_8)
        );
        JudgementNightService service = new JudgementNightService(Logger.getLogger("test"), configPath, () -> null, new Random(1));

        service.load();

        assertEquals(2, service.config().mobClassNames.size());
        assertEquals("net.minecraft.entity.monster.EntityZombie", service.config().mobClassNames.get(0));
        assertEquals("divinerpg.objects.entities.entity.vanilla.EntityTheEye", service.config().mobClassNames.get(1));
    }

    @Test
    void usesActualSeventhDayMultiples() {
        JudgementNightService.Config config = JudgementNightService.Config.defaults();

        assertTrue(JudgementNightService.isJudgementNight(new FakeWorld(7L * 24000L + 13000L), config));
        assertTrue(JudgementNightService.isJudgementNight(new FakeWorld(14L * 24000L + 13000L), config));
        assertTrue(JudgementNightService.isJudgementNight(new FakeWorld(21L * 24000L + 13000L), config));
        assertEquals(14L, JudgementNightService.dayNumber(new FakeWorld(14L * 24000L + 13000L)));
    }

    @Test
    void ignoresNonJudgementDays() {
        JudgementNightService.Config config = JudgementNightService.Config.defaults();

        assertEquals(13L, JudgementNightService.dayNumber(new FakeWorld(13L * 24000L + 13000L)));
        assertFalse(JudgementNightService.isJudgementNight(new FakeWorld(13L * 24000L + 13000L), config));
        assertFalse(JudgementNightService.isJudgementNight(new FakeWorld(14L * 24000L + 11999L), config));
    }

    static final class FakeWorld {
        private final long worldTime;

        FakeWorld(long worldTime) {
            this.worldTime = worldTime;
        }

        public long getWorldTime() {
            return worldTime;
        }
    }
}
