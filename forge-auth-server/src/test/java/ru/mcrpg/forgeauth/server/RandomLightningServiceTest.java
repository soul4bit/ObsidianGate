package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RandomLightningServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void loadCreatesDefaultConfig() {
        Path configPath = tempDirectory.resolve("obsidiangate-random-lightning.properties");
        RandomLightningService service = new RandomLightningService(Logger.getLogger("test"), configPath, () -> null, new Random(1));

        service.load();

        assertTrue(Files.exists(configPath));
        assertTrue(service.config().enabled);
        assertEquals(900, service.config().intervalSeconds);
        assertEquals(35, service.config().chancePercent);
        assertEquals(384, service.config().radius);
    }
}
