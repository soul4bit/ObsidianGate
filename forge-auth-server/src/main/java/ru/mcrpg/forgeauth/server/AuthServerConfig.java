package ru.mcrpg.forgeauth.server;

final class AuthServerConfig {

    static final String AUTH_BASE_URL_PROPERTY = "obsidiangate.authBaseUrl";
    static final String SERVER_ID_PROPERTY = "obsidiangate.serverId";
    static final String AUTH_GRACE_SECONDS_PROPERTY = "obsidiangate.authGraceSeconds";
    static final String AUTH_VERIFY_THREADS_PROPERTY = "obsidiangate.authVerifyThreads";

    private static final String AUTH_BASE_URL_ENV = "OBSIDIANGATE_AUTH_BASE_URL";
    private static final String SERVER_ID_ENV = "OBSIDIANGATE_SERVER_ID";
    private static final String AUTH_GRACE_SECONDS_ENV = "OBSIDIANGATE_AUTH_GRACE_SECONDS";
    private static final String AUTH_VERIFY_THREADS_ENV = "OBSIDIANGATE_AUTH_VERIFY_THREADS";
    private static final int MIN_GRACE_SECONDS = 60;
    private static final int DEFAULT_VERIFY_THREADS = 2;
    private static final int MIN_VERIFY_THREADS = 1;
    private static final int MAX_VERIFY_THREADS = 8;

    private final String authBaseUrl;
    private final String serverId;
    private final int graceSeconds;
    private final int verifyThreads;

    AuthServerConfig(String authBaseUrl, String serverId, int graceSeconds) {
        this(authBaseUrl, serverId, graceSeconds, DEFAULT_VERIFY_THREADS);
    }

    AuthServerConfig(String authBaseUrl, String serverId, int graceSeconds, int verifyThreads) {
        this.authBaseUrl = normalize(authBaseUrl);
        this.serverId = normalize(serverId);
        this.graceSeconds = Math.max(MIN_GRACE_SECONDS, graceSeconds);
        this.verifyThreads = clamp(verifyThreads, MIN_VERIFY_THREADS, MAX_VERIFY_THREADS);
    }

    static AuthServerConfig fromSystem() {
        String authBaseUrl = firstNonBlank(
            System.getProperty(AUTH_BASE_URL_PROPERTY),
            System.getenv(AUTH_BASE_URL_ENV)
        );
        String serverId = firstNonBlank(
            System.getProperty(SERVER_ID_PROPERTY),
            System.getenv(SERVER_ID_ENV)
        );
        int graceSeconds = parseInt(
            firstNonBlank(
                System.getProperty(AUTH_GRACE_SECONDS_PROPERTY),
                System.getenv(AUTH_GRACE_SECONDS_ENV)
            ),
            MIN_GRACE_SECONDS
        );
        int verifyThreads = parseInt(
            firstNonBlank(
                System.getProperty(AUTH_VERIFY_THREADS_PROPERTY),
                System.getenv(AUTH_VERIFY_THREADS_ENV)
            ),
            DEFAULT_VERIFY_THREADS
        );
        return new AuthServerConfig(authBaseUrl, serverId, graceSeconds, verifyThreads);
    }

    String getAuthBaseUrl() {
        return authBaseUrl;
    }

    String getServerId() {
        return serverId;
    }

    int getGraceSeconds() {
        return graceSeconds;
    }

    int getVerifyThreads() {
        return verifyThreads;
    }

    boolean isReady() {
        return !authBaseUrl.isEmpty() && !serverId.isEmpty();
    }

    boolean acceptsServerId(String requestedServerId) {
        return !serverId.isEmpty() && serverId.equals(normalize(requestedServerId));
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static String firstNonBlank(String primary, String fallback) {
        String normalizedPrimary = normalize(primary);
        return normalizedPrimary.isEmpty() ? normalize(fallback) : normalizedPrimary;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
