package ru.mcrpg.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class XaeroUpdateNotificationOptionsTest {

    @TempDir
    Path tempDirectory;

    @Test
    void disablesNotificationsAndPreservesOtherOptions() throws IOException {
        Path configDirectory = Files.createDirectories(tempDirectory.resolve("config/xaero"));
        Path minimap = configDirectory.resolve("xaerominimap.txt");
        Path worldMap = configDirectory.resolve("xaeroworldmap.txt");
        Files.write(
            minimap,
            List.of("minimap:true", "update_notifications:true", "zoom:2"),
            StandardCharsets.UTF_8
        );
        Files.write(worldMap, List.of("update_notifications: true", "caveMaps:1"), StandardCharsets.UTF_8);

        assertTrue(XaeroUpdateNotificationOptions.disable(tempDirectory));

        assertEquals(
            List.of("minimap:true", "update_notifications:false", "zoom:2"),
            Files.readAllLines(minimap, StandardCharsets.UTF_8)
        );
        assertEquals(
            List.of("update_notifications:false", "caveMaps:1"),
            Files.readAllLines(worldMap, StandardCharsets.UTF_8)
        );
    }

    @Test
    void createsMinimalConfigsWhenMissing() throws IOException {
        assertTrue(XaeroUpdateNotificationOptions.disable(tempDirectory));

        assertEquals(
            List.of("update_notifications:false"),
            Files.readAllLines(tempDirectory.resolve("config/xaero/xaerominimap.txt"), StandardCharsets.UTF_8)
        );
        assertEquals(
            List.of("update_notifications:false"),
            Files.readAllLines(tempDirectory.resolve("config/xaero/xaeroworldmap.txt"), StandardCharsets.UTF_8)
        );
    }

    @Test
    void doesNothingWhenNotificationsAreAlreadyDisabled() throws IOException {
        Path configDirectory = Files.createDirectories(tempDirectory.resolve("config/xaero"));
        Files.write(
            configDirectory.resolve("xaerominimap.txt"),
            List.of("update_notifications:false"),
            StandardCharsets.UTF_8
        );
        Files.write(
            configDirectory.resolve("xaeroworldmap.txt"),
            List.of("update_notifications:false"),
            StandardCharsets.UTF_8
        );

        assertFalse(XaeroUpdateNotificationOptions.disable(tempDirectory));
    }
}
