package ru.mcrpg.launcher;

import java.nio.file.Paths;

public final class LauncherDefaults {

    private static final String DEFAULT_GAME_DIRECTORY_NAME = "rpg-client";
    private static final String DEFAULT_MANIFEST_PORT = "8080";
    private static final String DEFAULT_MANIFEST_PATH = "/manifest.json";
    private static final String DEFAULT_AUTH_PORT = "8081";
    private static final String HTTP_SCHEME = "http://";
    private static final String HTTPS_SCHEME = "https://";
    private static final String DEFAULT_SERVER_ID = "obsidiangate-main";
    private static final int MIN_MEMORY_MB = 512;
    private static final int MAX_MEMORY_MB = 65536;

    private LauncherDefaults() {
    }

    public static LauncherConfig applyMissingValues(LauncherConfig config) {
        if (!hasText(config.getUsername())) {
            config.setUsername(defaultUsername());
        }
        if (!hasText(config.getJavaCommand())) {
            config.setJavaCommand("java");
        }
        if (!hasText(config.getGameDirectory())) {
            config.setGameDirectory(defaultGameDirectory());
        }
        if (!hasText(config.getServerHost())) {
            config.setServerHost(LauncherConfig.DEFAULT_SERVER_HOST);
        }
        if (config.getServerPort() < 1 || config.getServerPort() > 65535) {
            config.setServerPort(LauncherConfig.DEFAULT_SERVER_PORT);
        }
        if (isKnownDefaultManifestUrl(config.getManifestUrl(), config.getServerHost())) {
            config.setManifestUrl(defaultManifestUrl(config.getServerHost()));
        } else if (!hasText(config.getManifestUrl())) {
            config.setManifestUrl(defaultManifestUrl(config.getServerHost()));
        }
        if (isKnownDefaultAuthBaseUrl(config.getAuthBaseUrl(), config.getServerHost())) {
            config.setAuthBaseUrl(defaultAuthBaseUrl(config.getServerHost()));
        } else if (!hasText(config.getAuthBaseUrl())) {
            config.setAuthBaseUrl(defaultAuthBaseUrl(config.getServerHost()));
        }
        if (!hasText(config.getServerId())) {
            config.setServerId(defaultServerId());
        }
        if (!hasText(config.getLaunchTemplate())) {
            config.setLaunchTemplate(LauncherConfig.DEFAULT_LAUNCH_TEMPLATE);
        }
        normalizeMemory(config);
        return config;
    }

    public static String defaultGameDirectory() {
        return Paths.get(System.getProperty("user.home"), DEFAULT_GAME_DIRECTORY_NAME).toString();
    }

    public static String defaultManifestUrl(String serverHost) {
        String host = hasText(serverHost) ? serverHost.trim() : LauncherConfig.DEFAULT_SERVER_HOST;
        return HTTPS_SCHEME + host + ":" + DEFAULT_MANIFEST_PORT + DEFAULT_MANIFEST_PATH;
    }

    public static String defaultAuthBaseUrl(String serverHost) {
        String host = hasText(serverHost) ? serverHost.trim() : LauncherConfig.DEFAULT_SERVER_HOST;
        return HTTPS_SCHEME + host + ":" + DEFAULT_AUTH_PORT;
    }

    public static String defaultServerId() {
        return DEFAULT_SERVER_ID;
    }

    public static String defaultUsername() {
        String raw = System.getProperty("user.name", "");
        String sanitized = raw.replaceAll("[^A-Za-z0-9_]", "");
        if (sanitized.length() >= 3 && sanitized.length() <= 16) {
            return sanitized;
        }
        return "Player";
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static boolean isKnownDefaultManifestUrl(String manifestUrl, String serverHost) {
        if (!hasText(manifestUrl)) {
            return false;
        }
        String host = hasText(serverHost) ? serverHost.trim() : LauncherConfig.DEFAULT_SERVER_HOST;
        String normalized = manifestUrl.trim();
        return (HTTP_SCHEME + host + DEFAULT_MANIFEST_PATH).equalsIgnoreCase(normalized)
            || (HTTP_SCHEME + host + ":" + DEFAULT_MANIFEST_PORT + DEFAULT_MANIFEST_PATH).equalsIgnoreCase(normalized)
            || (HTTPS_SCHEME + host + DEFAULT_MANIFEST_PATH).equalsIgnoreCase(normalized)
            || (HTTPS_SCHEME + host + ":" + DEFAULT_MANIFEST_PORT + DEFAULT_MANIFEST_PATH).equalsIgnoreCase(normalized);
    }

    private static boolean isKnownDefaultAuthBaseUrl(String authBaseUrl, String serverHost) {
        if (!hasText(authBaseUrl)) {
            return false;
        }
        String host = hasText(serverHost) ? serverHost.trim() : LauncherConfig.DEFAULT_SERVER_HOST;
        String normalized = authBaseUrl.trim();
        return (HTTP_SCHEME + host + ":" + DEFAULT_AUTH_PORT).equalsIgnoreCase(normalized)
            || (HTTPS_SCHEME + host + ":" + DEFAULT_AUTH_PORT).equalsIgnoreCase(normalized);
    }

    private static void normalizeMemory(LauncherConfig config) {
        int minMemory = normalizeMemoryValue(config.getMemoryMinMb(), LauncherConfig.DEFAULT_MEMORY_MIN_MB);
        int maxMemory = normalizeMemoryValue(config.getMemoryMaxMb(), LauncherConfig.DEFAULT_MEMORY_MAX_MB);
        if (maxMemory < minMemory) {
            maxMemory = minMemory;
        }
        config.setMemoryMinMb(minMemory);
        config.setMemoryMaxMb(maxMemory);
    }

    private static int normalizeMemoryValue(int value, int fallback) {
        if (value < MIN_MEMORY_MB || value > MAX_MEMORY_MB) {
            return fallback;
        }
        return value;
    }
}
