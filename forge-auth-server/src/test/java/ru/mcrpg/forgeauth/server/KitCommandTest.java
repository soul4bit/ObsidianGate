package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KitCommandTest {

    @TempDir
    Path tempDirectory;

    @Test
    void startKitRecordsClaimAfterItemsAreGiven() throws Exception {
        UUID playerId = UUID.randomUUID();
        EntityPlayerMP player = new EntityPlayerMP(playerId, "Knight", 0, 0.0D, 64.0D, 0.0D);
        FakeInventory inventory = new FakeInventory();
        player.inventory = inventory;

        KitService service = service();
        executeStart(player, service);

        assertTrue(service.hasClaimedStart(playerId.toString()));
        assertEquals(9, inventory.items.size());
    }

    @Test
    void startKitDoesNotRecordClaimWhenItemsCannotBeGiven() throws Exception {
        UUID playerId = UUID.randomUUID();
        EntityPlayerMP player = new EntityPlayerMP(playerId, "Knight", 0, 0.0D, 64.0D, 0.0D);

        KitService service = service();
        executeStart(player, service);

        assertFalse(service.hasClaimedStart(playerId.toString()));
    }

    private KitService service() {
        KitService service = new KitService(Logger.getLogger("test"), tempDirectory.resolve("kit-claims.properties"));
        service.load();
        return service;
    }

    private static void executeStart(EntityPlayerMP player, KitService service) throws Exception {
        Method execute = KitCommand.class.getDeclaredMethod("execute", Object.class, Object.class, KitService.class);
        execute.setAccessible(true);
        try {
            execute.invoke(null, player, new String[] { "start" }, service);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw exception;
        }
    }

    static final class FakeInventory {

        private final List<ItemStack> items = new ArrayList<ItemStack>();

        public boolean addItemStackToInventory(ItemStack stack) {
            items.add(stack);
            return true;
        }
    }
}
