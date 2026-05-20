package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HomeRespawnServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void respawnTeleportsToDefaultHomeWhenItExists() {
        UUID playerId = UUID.fromString("2e5b1e14-a195-361c-8245-3ee75b1144d8");
        HomeService homes = new HomeService(Logger.getLogger("test"), tempDirectory.resolve("homes.properties"));
        homes.load();
        assertTrue(homes.setHome(playerId.toString(), "mine", new HomeService.HomeLocation(0, 10.5D, 70.0D, -5.5D, 90.0F, 0.0F), 3).success);
        assertTrue(homes.setHome(playerId.toString(), "home", new HomeService.HomeLocation(0, 20.5D, 72.0D, -7.5D, 180.0F, 10.0F), 3).success);

        FakePlayer player = new FakePlayer(playerId);
        HomeRespawnService service = new HomeRespawnService(Logger.getLogger("test"), homes);

        assertTrue(service.teleportToPrimaryHome(player, player.server));
        assertEquals(20.5D, player.x);
        assertEquals(72.0D, player.y);
        assertEquals(-7.5D, player.z);
        assertEquals(180.0F, player.yaw);
        assertEquals(10.0F, player.pitch);
    }

    static final class FakePlayer {
        private final UUID uniqueId;
        private final FakeServer server = new FakeServer();
        private int dimension = 0;
        private double motionX = 1.0D;
        private double motionY = 1.0D;
        private double motionZ = 1.0D;
        private double x;
        private double y;
        private double z;
        private float yaw;
        private float pitch;

        FakePlayer(UUID uniqueId) {
            this.uniqueId = uniqueId;
        }

        public UUID getUniqueID() {
            return uniqueId;
        }

        public String getName() {
            return "soul4bit";
        }

        public FakeServer getServer() {
            return server;
        }

        public void dismountRidingEntity() {
        }

        public void setLocationAndAngles(double x, double y, double z, float yaw, float pitch) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    static final class FakeServer {
        private final FakeWorld world = new FakeWorld();

        public FakeWorld getWorld(int dimension) {
            return world;
        }
    }

    static final class FakeWorld {
        private final FakeChunkProvider provider = new FakeChunkProvider();

        public FakeChunkProvider getChunkProvider() {
            return provider;
        }
    }

    static final class FakeChunkProvider {
        public Object loadChunk(int x, int z) {
            return new Object();
        }
    }
}
