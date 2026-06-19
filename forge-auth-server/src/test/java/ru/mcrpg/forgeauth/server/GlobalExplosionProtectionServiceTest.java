package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GlobalExplosionProtectionServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void loadCreatesDefaultConfigEnabled() throws Exception {
        Path configPath = tempDirectory.resolve("obsidiangate-explosion-protection.properties");
        GlobalExplosionProtectionService service = new GlobalExplosionProtectionService(Logger.getLogger("test"), configPath);

        service.load();

        assertTrue(Files.exists(configPath));
        assertTrue(service.config().enabled);
        assertTrue(service.config().preventBlockDamage);
        assertTrue(service.config().preventFireSpread);
    }

    @Test
    void clearAffectedBlocksRemovesEveryBlockFromExplosion() throws Exception {
        Path configPath = tempDirectory.resolve("obsidiangate-explosion-protection.properties");
        GlobalExplosionProtectionService service = new GlobalExplosionProtectionService(Logger.getLogger("test"), configPath);
        service.load();
        FakeExplosionEvent event = new FakeExplosionEvent();
        event.affectedBlocks.add(new FakeBlockPos(1, 64, 1));
        event.affectedBlocks.add(new FakeBlockPos(2, 64, 2));

        int removed = service.clearAffectedBlocks(event);

        assertEquals(2, removed);
        assertTrue(event.affectedBlocks.isEmpty());
    }

    @Test
    void fireNeighborNotifyIsCancelledGlobally() throws Exception {
        Path configPath = tempDirectory.resolve("obsidiangate-explosion-protection.properties");
        GlobalExplosionProtectionService service = new GlobalExplosionProtectionService(Logger.getLogger("test"), configPath);
        service.load();

        assertTrue(service.shouldCancelFireSpread(new FakeNeighborNotifyEvent("minecraft:fire")));
    }

    static final class FakeExplosionEvent {
        private final List<FakeBlockPos> affectedBlocks = new ArrayList<FakeBlockPos>();

        public List<FakeBlockPos> getAffectedBlocks() {
            return affectedBlocks;
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
    }

    static final class FakeNeighborNotifyEvent {
        private final FakeWorld world;
        private final FakeBlockPos pos = new FakeBlockPos(1, 64, 1);

        FakeNeighborNotifyEvent(String blockName) {
            this.world = new FakeWorld(blockName);
        }

        public FakeWorld getWorld() {
            return world;
        }

        public FakeBlockPos getPos() {
            return pos;
        }
    }

    static final class FakeWorld {
        private final FakeBlockState state;

        FakeWorld(String blockName) {
            this.state = new FakeBlockState(blockName);
        }

        public FakeBlockState getBlockState(FakeBlockPos pos) {
            return state;
        }
    }

    static final class FakeBlockState {
        private final FakeBlock block;

        FakeBlockState(String blockName) {
            this.block = new FakeBlock(blockName);
        }

        public FakeBlock getBlock() {
            return block;
        }
    }

    static final class FakeBlock {
        private final String registryName;

        FakeBlock(String registryName) {
            this.registryName = registryName;
        }

        public String getRegistryName() {
            return registryName;
        }
    }
}
