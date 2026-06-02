package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Logger;
import net.minecraft.entity.player.EntityPlayerMP;
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
        assertEquals(12000, service.config().warningStartTick);
        assertEquals(20, service.config().survivalRewardLevels);
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

    @Test
    void warnsOnEveningBeforeJudgementNight() {
        JudgementNightService.Config config = JudgementNightService.Config.defaults();

        assertTrue(JudgementNightService.isJudgementEve(new FakeWorld(6L * 24000L + 12000L), config));
        assertTrue(JudgementNightService.isJudgementEve(new FakeWorld(13L * 24000L + 13000L), config));
        assertFalse(JudgementNightService.isJudgementEve(new FakeWorld(6L * 24000L + 11999L), config));
        assertFalse(JudgementNightService.isJudgementEve(new FakeWorld(5L * 24000L + 13000L), config));
    }

    @Test
    void rewardsOnlineSurvivorsAfterJudgementNightEnds() throws Exception {
        Path configPath = tempDirectory.resolve("obsidiangate-judgement-night.properties");
        Files.write(
            configPath,
            ("periodDays=2\nsurvivalRewardLevels=20\nwaveIntervalSeconds=3600\n")
                .getBytes(StandardCharsets.UTF_8)
        );
        FakeWorld world = new FakeWorld(2L * 24000L + 12000L);
        FakePlayer player = new FakePlayer(0);
        FakeServer server = new FakeServer(world, player);
        JudgementNightService service = new JudgementNightService(Logger.getLogger("test"), configPath, () -> server, new Random(1));

        service.load();
        runSeconds(service, 1);
        world.worldTime = 3L * 24000L;
        runSeconds(service, 1);
        runSeconds(service, 2);

        assertEquals(20, player.levels);
    }

    @Test
    void doesNotRewardPlayersWhoDiedDuringJudgementNight() throws Exception {
        Path configPath = tempDirectory.resolve("obsidiangate-judgement-night.properties");
        Files.write(
            configPath,
            ("periodDays=2\nsurvivalRewardLevels=20\nwaveIntervalSeconds=3600\n")
                .getBytes(StandardCharsets.UTF_8)
        );
        FakeWorld world = new FakeWorld(2L * 24000L + 12000L);
        FakePlayer player = new FakePlayer(0);
        FakeServer server = new FakeServer(world, player);
        JudgementNightService service = new JudgementNightService(Logger.getLogger("test"), configPath, () -> server, new Random(1));

        service.load();
        runSeconds(service, 1);
        service.recordDeathIfActive(player);
        world.worldTime = 3L * 24000L;
        runSeconds(service, 1);

        assertEquals(0, player.levels);
    }

    @Test
    void summarizesJudgementNightKillsAndDeaths() throws Exception {
        Path configPath = tempDirectory.resolve("obsidiangate-judgement-night.properties");
        Files.write(
            configPath,
            ("periodDays=2\nsurvivalRewardLevels=20\nwaveIntervalSeconds=3600\n")
                .getBytes(StandardCharsets.UTF_8)
        );
        FakeWorld world = new FakeWorld(2L * 24000L + 12000L);
        FakePlayer player = new FakePlayer(0);
        FakeServer server = new FakeServer(world, player);
        JudgementNightService service = new JudgementNightService(Logger.getLogger("test"), configPath, () -> server, new Random(1));

        service.load();
        runSeconds(service, 1);
        service.recordKillIfActive(new FakeHostile(), player);
        service.recordDeathIfActive(player);

        String summary = service.judgementStatsSummary(0, 20);

        assertTrue(summary.contains("Knight: 1"));
        assertTrue(summary.contains("1 \u0441\u043c\u0435\u0440\u0442\u0435\u0439"));
        assertTrue(summary.contains("\u043d\u0430\u0433\u0440\u0430\u0434\u0430 +20"));
    }

    @Test
    void formatsJudgementStatsAsCompactRankedLine() {
        String line = JudgementNightService.formatJudgementStatsLine(1, "soul4bit", 40, 0);

        assertEquals("\u00A78  #1 \u00A77soul4bit \u00A78- \u00A7c\u2694 40\u00A78, \u00A77\u2620 0", line);
    }

    static final class FakeHostile implements net.minecraft.entity.monster.IMob {
    }

    private static void runSeconds(JudgementNightService service, int seconds) {
        for (int tick = 0; tick < seconds * 20; tick++) {
            service.runServerEndTick();
        }
    }

    static final class FakeWorld {
        private long worldTime;

        FakeWorld(long worldTime) {
            this.worldTime = worldTime;
        }

        public long getWorldTime() {
            return worldTime;
        }
    }

    static final class FakePlayer extends EntityPlayerMP {
        int levels;

        FakePlayer(int dimension) {
            super(UUID.randomUUID(), "Knight", dimension, 0.0D, 64.0D, 0.0D);
        }

        public void addExperienceLevel(int value) {
            levels += value;
        }
    }

    static final class FakeServer {
        private final FakeWorld world;
        private final FakePlayerList playerList;

        FakeServer(FakeWorld world, FakePlayer... players) {
            this.world = world;
            this.playerList = new FakePlayerList(Arrays.asList(players));
        }

        public FakeWorld getWorld(int dimension) {
            return dimension == 0 ? world : null;
        }

        public FakePlayerList getPlayerList() {
            return playerList;
        }
    }

    static final class FakePlayerList {
        private final List<FakePlayer> players;

        FakePlayerList(List<FakePlayer> players) {
            this.players = players;
        }

        public List<FakePlayer> getPlayers() {
            return players;
        }
    }
}
