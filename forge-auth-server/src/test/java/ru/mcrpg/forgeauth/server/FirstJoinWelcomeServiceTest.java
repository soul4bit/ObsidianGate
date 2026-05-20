package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import net.minecraft.entity.player.EntityPlayerMP;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FirstJoinWelcomeServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void greetsOnlyOnceAndPersistsMarker() {
        Path path = tempDirectory.resolve("welcome-seen.properties");
        FirstJoinWelcomeService service = new FirstJoinWelcomeService(Logger.getLogger("test"), path);
        EntityPlayerMP player = new EntityPlayerMP(UUID.randomUUID(), "Knight", 0, 0.0D, 64.0D, 0.0D);

        assertTrue(service.greetIfFirstJoin(player));
        assertFalse(service.greetIfFirstJoin(player));

        FirstJoinWelcomeService restored = new FirstJoinWelcomeService(Logger.getLogger("test"), path);
        restored.load();
        assertTrue(restored.hasSeen(PlayerIdentity.id(player)));
        assertFalse(restored.greetIfFirstJoin(player));
    }
}
