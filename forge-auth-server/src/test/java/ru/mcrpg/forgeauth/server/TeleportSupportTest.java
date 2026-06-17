package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class TeleportSupportTest {

    @Test
    void changeDimensionUsesForgeTeleporterWhenAvailable() throws Exception {
        FakeDimensionPlayer player = new FakeDimensionPlayer();

        Object moved = TeleportSupport.changeDimension(player, 0, 12.5D, 70.0D, -4.5D, 90.0F, 15.0F);

        assertSame(player, moved);
        assertEquals(0, player.dimension);
        assertFalse(player.vanillaChangeDimensionCalled);
        assertNotNull(player.forgeTeleporter);
        assertEquals(Boolean.FALSE, invokeZeroArg(player.forgeTeleporter, "isVanilla"));
    }

    @Test
    void forgeTeleporterPlacesEntityAtRequestedLocation() throws Exception {
        FakeDimensionPlayer player = new FakeDimensionPlayer();
        TeleportSupport.changeDimension(player, 0, 12.5D, 70.0D, -4.5D, 90.0F, 15.0F);

        Class<?> worldType = Class.forName("amu");
        Class<?> entityType = Class.forName("vg");
        Object world = worldType.getDeclaredConstructor().newInstance();
        Object entity = entityType.getDeclaredConstructor().newInstance();
        Method placeEntity = player.forgeTeleporter.getClass().getMethod(
            "placeEntity",
            worldType,
            entityType,
            Float.TYPE
        );

        placeEntity.invoke(player.forgeTeleporter, world, entity, Float.valueOf(90.0F));

        assertTrue((Boolean) readField(entity, "locationAndAnglesCalled"));
        assertEquals(12.5D, (Double) readField(entity, "x"));
        assertEquals(70.0D, (Double) readField(entity, "y"));
        assertEquals(-4.5D, (Double) readField(entity, "z"));
        assertEquals(90.0F, (Float) readField(entity, "yaw"));
        assertEquals(15.0F, (Float) readField(entity, "pitch"));
    }

    @Test
    void teleportToDimensionLoadsDestinationChunkAndTeleports() {
        FakeTeleportServer server = new FakeTeleportServer();
        FakeTeleportPlayer player = new FakeTeleportPlayer();

        Object moved = TeleportSupport.teleportToDimension(server, player, 0, 33.5D, 72.0D, -17.5D, 45.0F, 5.0F);

        assertSame(player, moved);
        assertEquals(2, server.world.provider.loadedChunkX);
        assertEquals(-2, server.world.provider.loadedChunkZ);
        assertEquals(1, server.world.provider.loadCount);
        assertEquals(33.5D, player.x);
        assertEquals(72.0D, player.y);
        assertEquals(-17.5D, player.z);
        assertEquals(45.0F, player.yaw);
        assertEquals(5.0F, player.pitch);
        assertTrue(player.motionReset());
    }

    @Test
    void worldSpawnLocationKeepsSafeSpawnPointCentered() {
        FakeSpawnWorld world = new FakeSpawnWorld(10, 64, -3, 80, true);

        TeleportSupport.Location location = TeleportSupport.worldSpawnLocation(world);

        assertEquals(10.5D, location.x);
        assertEquals(64.0D, location.y);
        assertEquals(-2.5D, location.z);
    }

    @Test
    void worldSpawnLocationFallsBackToTopSurfaceWhenSpawnPointIsBlocked() {
        FakeSpawnWorld world = new FakeSpawnWorld(10, 64, -3, 80, false);

        TeleportSupport.Location location = TeleportSupport.worldSpawnLocation(world);

        assertEquals(10.5D, location.x);
        assertEquals(80.0D, location.y);
        assertEquals(-2.5D, location.z);
    }

    private static Object invokeZeroArg(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private static Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getField(fieldName);
        return field.get(target);
    }

    static final class FakeDimensionPlayer {
        private int dimension = 1;
        private Object forgeTeleporter;
        private boolean vanillaChangeDimensionCalled;

        public void dismountRidingEntity() {
        }

        public Object changeDimension(int destinationDimension, Object forgeTeleporter) {
            this.dimension = destinationDimension;
            this.forgeTeleporter = forgeTeleporter;
            return this;
        }

        public Object changeDimension(int destinationDimension) {
            this.vanillaChangeDimensionCalled = true;
            this.dimension = destinationDimension;
            return this;
        }
    }

    static final class FakeTeleportServer {
        private final FakeTeleportWorld world = new FakeTeleportWorld();

        public FakeTeleportWorld getWorld(int dimension) {
            return world;
        }
    }

    static final class FakeTeleportWorld {
        private final FakeChunkProvider provider = new FakeChunkProvider();

        public FakeChunkProvider getChunkProvider() {
            return provider;
        }
    }

    static final class FakeChunkProvider {
        private int loadedChunkX;
        private int loadedChunkZ;
        private int loadCount;

        public Object loadChunk(int x, int z) {
            this.loadedChunkX = x;
            this.loadedChunkZ = z;
            this.loadCount++;
            return new Object();
        }

        public boolean isChunkLoaded(int x, int z) {
            return loadCount > 0 && loadedChunkX == x && loadedChunkZ == z;
        }
    }

    static final class FakeTeleportPlayer {
        private int dimension = 0;
        private double motionX = 1.0D;
        private double motionY = 1.0D;
        private double motionZ = 1.0D;
        private double x;
        private double y;
        private double z;
        private float yaw;
        private float pitch;
        public void dismountRidingEntity() {
        }

        public void setLocationAndAngles(double x, double y, double z, float yaw, float pitch) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        boolean motionReset() {
            return motionX == 0.0D && motionY == 0.0D && motionZ == 0.0D;
        }
    }

    static final class FakeSpawnWorld {
        private final net.minecraft.util.math.BlockPos spawn;
        private final int topY;
        private final boolean spawnSafe;
        private final FakeChunkProvider provider = new FakeChunkProvider();

        private FakeSpawnWorld(int x, int y, int z, int topY, boolean spawnSafe) {
            this.spawn = new net.minecraft.util.math.BlockPos(x, y, z);
            this.topY = topY;
            this.spawnSafe = spawnSafe;
        }

        public net.minecraft.util.math.BlockPos getSpawnPoint() {
            return spawn;
        }

        public FakeChunkProvider getChunkProvider() {
            return provider;
        }

        public net.minecraft.util.math.BlockPos getTopSolidOrLiquidBlock(net.minecraft.util.math.BlockPos position) {
            return new net.minecraft.util.math.BlockPos(position.getX(), topY, position.getZ());
        }

        public boolean isAirBlock(net.minecraft.util.math.BlockPos position) {
            if (position.getX() != spawn.getX() || position.getZ() != spawn.getZ()) {
                return false;
            }
            if (spawnSafe && (position.getY() == spawn.getY() || position.getY() == spawn.getY() + 1)) {
                return true;
            }
            return position.getY() == topY || position.getY() == topY + 1;
        }
    }
}
