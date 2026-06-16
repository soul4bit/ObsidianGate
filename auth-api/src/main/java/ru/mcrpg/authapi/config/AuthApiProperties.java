package ru.mcrpg.authapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth")
public class AuthApiProperties {

    private String jwtSecret = "change-me";
    private long accessTokenTtlSeconds = 900L;
    private long refreshTokenTtlDays = 30L;
    private long gameTicketTtlSeconds = 900L;
    private boolean cleanupEnabled = true;
    private long cleanupIntervalMillis = 3_600_000L;
    private long usedGameTicketRetentionSeconds = 86_400L;
    private long revokedSessionRetentionDays = 7L;
    private String serverId = "obsidiangate-main";
    private boolean rateLimitEnabled = true;
    private long rateLimitWindowSeconds = 60L;
    private int loginRateLimit = 10;
    private int registerRateLimit = 5;
    private int refreshRateLimit = 60;

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) {
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public long getRefreshTokenTtlDays() {
        return refreshTokenTtlDays;
    }

    public void setRefreshTokenTtlDays(long refreshTokenTtlDays) {
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    public long getGameTicketTtlSeconds() {
        return gameTicketTtlSeconds;
    }

    public void setGameTicketTtlSeconds(long gameTicketTtlSeconds) {
        this.gameTicketTtlSeconds = gameTicketTtlSeconds;
    }

    public boolean isCleanupEnabled() {
        return cleanupEnabled;
    }

    public void setCleanupEnabled(boolean cleanupEnabled) {
        this.cleanupEnabled = cleanupEnabled;
    }

    public long getCleanupIntervalMillis() {
        return cleanupIntervalMillis;
    }

    public void setCleanupIntervalMillis(long cleanupIntervalMillis) {
        this.cleanupIntervalMillis = cleanupIntervalMillis;
    }

    public long getUsedGameTicketRetentionSeconds() {
        return usedGameTicketRetentionSeconds;
    }

    public void setUsedGameTicketRetentionSeconds(long usedGameTicketRetentionSeconds) {
        this.usedGameTicketRetentionSeconds = usedGameTicketRetentionSeconds;
    }

    public long getRevokedSessionRetentionDays() {
        return revokedSessionRetentionDays;
    }

    public void setRevokedSessionRetentionDays(long revokedSessionRetentionDays) {
        this.revokedSessionRetentionDays = revokedSessionRetentionDays;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public boolean isRateLimitEnabled() {
        return rateLimitEnabled;
    }

    public void setRateLimitEnabled(boolean rateLimitEnabled) {
        this.rateLimitEnabled = rateLimitEnabled;
    }

    public long getRateLimitWindowSeconds() {
        return rateLimitWindowSeconds;
    }

    public void setRateLimitWindowSeconds(long rateLimitWindowSeconds) {
        this.rateLimitWindowSeconds = rateLimitWindowSeconds;
    }

    public int getLoginRateLimit() {
        return loginRateLimit;
    }

    public void setLoginRateLimit(int loginRateLimit) {
        this.loginRateLimit = loginRateLimit;
    }

    public int getRegisterRateLimit() {
        return registerRateLimit;
    }

    public void setRegisterRateLimit(int registerRateLimit) {
        this.registerRateLimit = registerRateLimit;
    }

    public int getRefreshRateLimit() {
        return refreshRateLimit;
    }

    public void setRefreshRateLimit(int refreshRateLimit) {
        this.refreshRateLimit = refreshRateLimit;
    }
}
