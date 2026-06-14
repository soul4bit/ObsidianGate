package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.DataOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class PlayerDataGuardServiceTest {

    @Test
    void readsInventoryCountFromCompressedPlayerdata() throws Exception {
        Path playerdata = Files.createTempFile("playerdata-guard", ".dat");
        try (OutputStream file = Files.newOutputStream(playerdata);
             DataOutputStream output = new DataOutputStream(new GZIPOutputStream(file))) {
            output.writeByte(10);
            output.writeUTF("");
            output.writeByte(3);
            output.writeUTF("DataVersion");
            output.writeInt(1343);
            output.writeByte(9);
            output.writeUTF("Inventory");
            output.writeByte(10);
            output.writeInt(2);
            output.writeByte(0);
            output.writeByte(0);
            output.writeByte(0);
        }

        assertEquals(2, PlayerDataGuardService.readInventoryCount(playerdata));
    }

    @Test
    void countsLoadedMainArmorAndOffhandItems() {
        FakePlayer player = new FakePlayer();
        player.inventory.mainInventory = Arrays.asList(new FakeStack(false), new FakeStack(true));
        player.inventory.armorInventory = Collections.singletonList(new FakeStack(false));
        player.inventory.offHandInventory = Collections.singletonList(new FakeStack(false));

        assertEquals(3, PlayerDataGuardService.countLoadedItems(player));
    }

    static final class FakePlayer {
        public final FakeInventory inventory = new FakeInventory();
    }

    static final class FakeInventory {
        public Iterable<FakeStack> mainInventory = Collections.emptyList();
        public Iterable<FakeStack> armorInventory = Collections.emptyList();
        public Iterable<FakeStack> offHandInventory = Collections.emptyList();
    }

    static final class FakeStack {
        private final boolean empty;

        FakeStack(boolean empty) {
            this.empty = empty;
        }

        public boolean isEmpty() {
            return empty;
        }
    }
}
