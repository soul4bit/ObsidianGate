package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import net.minecraft.entity.player.EntityPlayerMP;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerRegionServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void createsPersistsAndLimitsRegions() {
        Path path = tempDirectory.resolve("regions.properties");
        PlayerRegionService service = new PlayerRegionService(Logger.getLogger("test"), path);
        String owner = UUID.randomUUID().toString();

        PlayerRegionService.CreateResult created = service.createAround(owner, "Owner", "base", 0, 100.0D, 200.0D, 16);
        assertTrue(created.success);
        assertEquals("base", created.region.name);
        assertTrue(created.region.contains(0, 116.0D, 64.0D, 200.0D));
        assertFalse(created.region.contains(0, 117.0D, 64.0D, 200.0D));

        assertTrue(service.createAround(owner, "Owner", "mine", 0, 300.0D, 300.0D, 8).success);
        assertTrue(service.createAround(owner, "Owner", "farm", 0, 500.0D, 500.0D, 8).success);

        PlayerRegionService.CreateResult limited = service.createAround(owner, "Owner", "tower", 0, 700.0D, 700.0D, 8);
        assertFalse(limited.success);
        assertEquals(PlayerRegionService.MAX_REGIONS_PER_PLAYER, limited.limit);

        PlayerRegionService restored = new PlayerRegionService(Logger.getLogger("test"), path);
        restored.load();
        assertNotNull(restored.get(owner, "base"));
        assertEquals(3, restored.list(owner).size());
    }

    @Test
    void overlappingRegionsAreRejected() {
        PlayerRegionService service = new PlayerRegionService(Logger.getLogger("test"), tempDirectory.resolve("regions.properties"));
        String owner = UUID.randomUUID().toString();
        String other = UUID.randomUUID().toString();

        assertTrue(service.createAround(owner, "Owner", "base", 0, 0.0D, 0.0D, 16).success);

        PlayerRegionService.CreateResult overlap = service.createAround(other, "Other", "base", 0, 20.0D, 0.0D, 16);
        assertFalse(overlap.success);
        assertNotNull(overlap.overlap);
    }

    @Test
    void customRegionLimitCanBeHigherOrUnlimited() {
        PlayerRegionService service = new PlayerRegionService(Logger.getLogger("test"), tempDirectory.resolve("regions.properties"));
        String owner = UUID.randomUUID().toString();

        assertTrue(service.createAround(owner, "Owner", "one", 0, 0.0D, 0.0D, 4, 5).success);
        assertTrue(service.createAround(owner, "Owner", "two", 0, 20.0D, 0.0D, 4, 5).success);
        assertTrue(service.createAround(owner, "Owner", "three", 0, 40.0D, 0.0D, 4, 5).success);
        assertTrue(service.createAround(owner, "Owner", "four", 0, 60.0D, 0.0D, 4, 5).success);
        assertTrue(service.createAround(owner, "Owner", "five", 0, 80.0D, 0.0D, 4, 5).success);

        PlayerRegionService.CreateResult limited = service.createAround(owner, "Owner", "six", 0, 100.0D, 0.0D, 4, 5);
        assertFalse(limited.success);
        assertEquals(5, limited.limit);

        assertTrue(service.createAround(owner, "Owner", "six", 0, 100.0D, 0.0D, 4, RoleLimits.UNLIMITED).success);
    }

    @Test
    void trustedPlayersCanAccessProtectedRegion() {
        PlayerRegionService service = new PlayerRegionService(Logger.getLogger("test"), tempDirectory.resolve("regions.properties"));
        PlayerRegionProtectionService protection = new PlayerRegionProtectionService(Logger.getLogger("test"), service);
        UUID ownerId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        EntityPlayerMP owner = new EntityPlayerMP(ownerId, "Owner", 0, 0.0D, 64.0D, 0.0D);
        EntityPlayerMP stranger = new EntityPlayerMP(strangerId, "Friend", 0, 0.0D, 64.0D, 0.0D);
        FakeWorld world = new FakeWorld(0);
        FakeBlockPos pos = new FakeBlockPos(0, 64, 0);

        assertTrue(service.createAround(ownerId.toString(), "Owner", "base", 0, 0.0D, 0.0D, 16).success);

        assertFalse(protection.isProtectedFrom(world, pos, owner));
        assertTrue(protection.isProtectedFrom(world, pos, stranger));

        PlayerRegionService.TrustResult trusted = service.trust(ownerId.toString(), "base", "Friend");
        assertTrue(trusted.success);
        assertFalse(protection.isProtectedFrom(world, pos, stranger));
    }

    @Test
    void validatesNamesAndRadius() {
        assertEquals("base_1", PlayerRegionService.normalizeName("Base_1"));
        assertThrows(IllegalArgumentException.class, () -> PlayerRegionService.normalizeName("bad.name"));
        assertThrows(IllegalArgumentException.class, () -> PlayerRegionService.clampRadius(0));
        assertThrows(IllegalArgumentException.class, () -> PlayerRegionService.clampRadius(PlayerRegionService.MAX_RADIUS + 1));
    }

    static final class FakeWorld {
        public final FakeProvider provider;

        FakeWorld(int dimension) {
            this.provider = new FakeProvider(dimension);
        }
    }

    static final class FakeProvider {
        private final int dimension;

        FakeProvider(int dimension) {
            this.dimension = dimension;
        }

        public int getDimension() {
            return dimension;
        }
    }

    static final class FakeBlockPos {
        private final int x;
        private final int y;
        private final int z;

        FakeBlockPos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getZ() {
            return z;
        }
    }
}
