package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
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
        assertEquals(30, service.config().waveIntervalSeconds);
        assertEquals(6, service.config().mobsPerPlayer);
        assertEquals(36, service.config().maxHostilesNearPlayer);
    }
}
