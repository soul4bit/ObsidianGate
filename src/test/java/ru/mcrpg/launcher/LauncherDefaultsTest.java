package ru.mcrpg.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LauncherDefaultsTest {

    @Test
    void applyMissingValuesFillsZeroConfigLauncherFields() {
        LauncherConfig config = new LauncherConfig();
        config.setUsername("");
        config.setJavaCommand("");
        config.setManifestUrl("");
        config.setAuthBaseUrl("");
        config.setServerId("");
        config.setGameDirectory("");
        config.setWorkingDirectory("");
        config.setServerHost("");
        config.setServerPort(0);
        config.setLaunchTemplate("");
        config.setMemoryMinMb(0);
        config.setMemoryMaxMb(0);
        config.setUpdateFilesBeforeLaunch(true);

        LauncherDefaults.applyMissingValues(config);

        assertFalse(config.getUsername().isEmpty());
        assertEquals("java", config.getJavaCommand());
        assertFalse(config.getGameDirectory().isEmpty());
        assertEquals(LauncherConfig.DEFAULT_SERVER_HOST, config.getServerHost());
        assertEquals(LauncherConfig.DEFAULT_SERVER_PORT, config.getServerPort());
        assertEquals(
            "https://" + LauncherConfig.DEFAULT_SERVER_HOST + ":8080/manifest.json",
            config.getManifestUrl()
        );
        assertEquals(
            "https://" + LauncherConfig.DEFAULT_SERVER_HOST + ":8081",
            config.getAuthBaseUrl()
        );
        assertEquals("obsidiangate-main", config.getServerId());
        assertEquals(LauncherConfig.DEFAULT_LAUNCH_TEMPLATE, config.getLaunchTemplate());
        assertEquals(LauncherConfig.DEFAULT_MEMORY_MIN_MB, config.getMemoryMinMb());
        assertEquals(LauncherConfig.DEFAULT_MEMORY_MAX_MB, config.getMemoryMaxMb());
    }

    @Test
    void defaultManifestUrlUsesConfiguredServerHost() {
        assertEquals(
            "https://example.local:8080/manifest.json",
            LauncherDefaults.defaultManifestUrl("example.local")
        );
        assertEquals(
            "https://example.local:8081",
            LauncherDefaults.defaultAuthBaseUrl("example.local")
        );
        assertEquals("obsidiangate-main", LauncherDefaults.defaultServerId());
        assertTrue(LauncherDefaults.defaultGameDirectory().contains("rpg-client"));
    }

    @Test
    void applyMissingValuesNormalizesManifestUrlWithoutPort() {
        LauncherConfig config = LauncherConfig.defaults();
        config.setServerHost("example.local");
        config.setManifestUrl("http://example.local/manifest.json");

        LauncherDefaults.applyMissingValues(config);

        assertEquals("https://example.local:8080/manifest.json", config.getManifestUrl());
    }

    @Test
    void applyMissingValuesNormalizesBrokenSecureDefaultUrls() {
        LauncherConfig config = LauncherConfig.defaults();
        config.setServerHost("example.local");
        config.setManifestUrl("https://example.local/manifest.json");
        config.setAuthBaseUrl("https://example.local:8081");

        LauncherDefaults.applyMissingValues(config);

        assertEquals("https://example.local:8080/manifest.json", config.getManifestUrl());
        assertEquals("https://example.local:8081", config.getAuthBaseUrl());
    }

    @Test
    void applyMissingValuesKeepsMemoryMaxAtLeastMin() {
        LauncherConfig config = LauncherConfig.defaults();
        config.setMemoryMinMb(8192);
        config.setMemoryMaxMb(4096);

        LauncherDefaults.applyMissingValues(config);

        assertEquals(8192, config.getMemoryMinMb());
        assertEquals(8192, config.getMemoryMaxMb());
    }
}
