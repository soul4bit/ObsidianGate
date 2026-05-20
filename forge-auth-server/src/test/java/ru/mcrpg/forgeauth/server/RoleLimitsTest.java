package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RoleLimitsTest {

    @Test
    void playerHasDefaultLimits() {
        RoleLimits limits = RoleLimits.forRole("player");

        assertEquals(3, limits.maxHomes());
        assertEquals(3, limits.maxRegions());
        assertEquals(TimeUnit.SECONDS.toMillis(30L), limits.homeCooldownMillis());
        assertEquals(TimeUnit.SECONDS.toMillis(30L), limits.spawnCooldownMillis());
        assertEquals(TimeUnit.SECONDS.toMillis(150L), limits.randomTeleportCooldownMillis());
    }

    @Test
    void vipHasExpandedLimitsAndShorterCooldowns() {
        RoleLimits limits = RoleLimits.forRole("VIP");

        assertEquals(5, limits.maxHomes());
        assertEquals(5, limits.maxRegions());
        assertEquals(TimeUnit.SECONDS.toMillis(15L), limits.homeCooldownMillis());
        assertEquals(TimeUnit.SECONDS.toMillis(15L), limits.spawnCooldownMillis());
        assertEquals(TimeUnit.SECONDS.toMillis(75L), limits.randomTeleportCooldownMillis());
    }

    @Test
    void adminHasNoLimitsOrCooldowns() {
        RoleLimits limits = RoleLimits.forRole("admin");

        assertTrue(RoleLimits.isUnlimited(limits.maxHomes()));
        assertTrue(RoleLimits.isUnlimited(limits.maxRegions()));
        assertEquals(0L, limits.homeCooldownMillis());
        assertEquals(0L, limits.spawnCooldownMillis());
        assertEquals(0L, limits.randomTeleportCooldownMillis());
    }

    @Test
    void unknownRoleFallsBackToPlayer() {
        assertEquals(3, RoleLimits.forRole("unknown").maxHomes());
        assertEquals(3, RoleLimits.forRole(null).maxRegions());
    }
}
