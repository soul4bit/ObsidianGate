package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import net.minecraft.entity.player.EntityPlayerMP;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackLocationServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void storesAndRestoresLastDeathLocation() {
        Path path = tempDirectory.resolve("back-locations.properties");
        BackLocationService service = new BackLocationService(Logger.getLogger("test"), path);
        EntityPlayerMP player = new EntityPlayerMP(UUID.randomUUID(), "Knight", 7, 12.5D, 45.0D, -30.5D);

        service.recordDeath(player);

        BackLocationService restored = new BackLocationService(Logger.getLogger("test"), path);
        restored.load();
        BackLocationService.DeathLocation location = restored.lastDeath(PlayerIdentity.id(player));

        assertNotNull(location);
        assertEquals(7, location.dimension);
        assertEquals(12.5D, location.x);
        assertEquals(45.0D, location.y);
        assertEquals(-30.5D, location.z);
    }

    @Test
    void raisesVoidDeathLocationAboveBottom() {
        BackLocationService service = new BackLocationService(Logger.getLogger("test"), tempDirectory.resolve("back-locations.properties"));
        EntityPlayerMP player = new EntityPlayerMP(UUID.randomUUID(), "Knight", 0, 0.0D, -10.0D, 0.0D);

        service.recordDeath(player);

        BackLocationService.DeathLocation location = service.lastDeath(PlayerIdentity.id(player));
        assertNotNull(location);
        assertEquals(1.0D, location.y);
    }
}
