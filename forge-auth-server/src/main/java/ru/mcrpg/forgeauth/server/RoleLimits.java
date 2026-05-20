package ru.mcrpg.forgeauth.server;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

final class RoleLimits {

    static final int UNLIMITED = Integer.MAX_VALUE;

    private static final RoleLimits PLAYER = new RoleLimits(
        "player",
        3,
        3,
        TimeUnit.SECONDS.toMillis(30L),
        TimeUnit.SECONDS.toMillis(30L),
        TimeUnit.SECONDS.toMillis(150L),
        TimeUnit.SECONDS.toMillis(60L)
    );
    private static final RoleLimits VIP = new RoleLimits(
        "vip",
        5,
        5,
        TimeUnit.SECONDS.toMillis(15L),
        TimeUnit.SECONDS.toMillis(15L),
        TimeUnit.SECONDS.toMillis(75L),
        TimeUnit.SECONDS.toMillis(30L)
    );
    private static final RoleLimits ADMIN = new RoleLimits(
        "admin",
        UNLIMITED,
        UNLIMITED,
        0L,
        0L,
        0L,
        0L
    );

    private final String role;
    private final int maxHomes;
    private final int maxRegions;
    private final long homeCooldownMillis;
    private final long spawnCooldownMillis;
    private final long randomTeleportCooldownMillis;
    private final long backCooldownMillis;

    private RoleLimits(
        String role,
        int maxHomes,
        int maxRegions,
        long homeCooldownMillis,
        long spawnCooldownMillis,
        long randomTeleportCooldownMillis,
        long backCooldownMillis
    ) {
        this.role = role;
        this.maxHomes = maxHomes;
        this.maxRegions = maxRegions;
        this.homeCooldownMillis = homeCooldownMillis;
        this.spawnCooldownMillis = spawnCooldownMillis;
        this.randomTeleportCooldownMillis = randomTeleportCooldownMillis;
        this.backCooldownMillis = backCooldownMillis;
    }

    static RoleLimits forRole(String role) {
        String normalized = normalizeRole(role);
        if ("admin".equals(normalized) || "administrator".equals(normalized) || "owner".equals(normalized)) {
            return ADMIN;
        }
        if ("vip".equals(normalized) || "premium".equals(normalized)) {
            return VIP;
        }
        return PLAYER;
    }

    static boolean isUnlimited(int limit) {
        return limit >= UNLIMITED;
    }

    static String limitText(int limit) {
        return isUnlimited(limit) ? "без лимита" : Integer.toString(limit);
    }

    String role() {
        return role;
    }

    int maxHomes() {
        return maxHomes;
    }

    int maxRegions() {
        return maxRegions;
    }

    long homeCooldownMillis() {
        return homeCooldownMillis;
    }

    long spawnCooldownMillis() {
        return spawnCooldownMillis;
    }

    long randomTeleportCooldownMillis() {
        return randomTeleportCooldownMillis;
    }

    long backCooldownMillis() {
        return backCooldownMillis;
    }

    private static String normalizeRole(String role) {
        return role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
    }
}
