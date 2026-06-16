package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
        String accountId = "acc-123";
        EntityPlayerMP player = new EntityPlayerMP(java.util.UUID.randomUUID(), "Knight", 0, 0.0D, 64.0D, 0.0D);
        FakeInventory inventory = new FakeInventory();
        player.inventory = inventory;

        KitService service = service();
        executeStart(player, service, accountId);

        assertTrue(service.hasClaimedStart("account:" + accountId));
        assertEquals(9, inventory.items.size());
    }

    @Test
    void startKitDoesNotRecordClaimWhenItemsCannotBeGiven() throws Exception {
        String accountId = "acc-456";
        EntityPlayerMP player = new EntityPlayerMP(java.util.UUID.randomUUID(), "Knight", 0, 0.0D, 64.0D, 0.0D);

        KitService service = service();
        executeStart(player, service, accountId);

        assertFalse(service.hasClaimedStart("account:" + accountId));
    }

    @Test
    void startKitRequiresLauncherAccount() throws Exception {
        EntityPlayerMP player = new EntityPlayerMP(java.util.UUID.randomUUID(), "Knight", 0, 0.0D, 64.0D, 0.0D);
        FakeInventory inventory = new FakeInventory();
        player.inventory = inventory;

        KitService service = service();
        executeStart(player, service, "");

        assertTrue(inventory.items.isEmpty());
    }

    @Test
    void startKitCanBeClaimedAgainAfterMonthlyCooldown() throws Exception {
        String accountId = "acc-789";
        EntityPlayerMP player = new EntityPlayerMP(java.util.UUID.randomUUID(), "Knight", 0, 0.0D, 64.0D, 0.0D);
        FakeInventory inventory = new FakeInventory();
        player.inventory = inventory;

        KitService service = service();
        service.recordStartClaim(
            "account:" + accountId,
            "Knight",
            accountId,
            player.getUniqueID().toString(),
            Instant.now().minus(Duration.ofDays(31))
        );
        executeStart(player, service, accountId);

        assertEquals(9, inventory.items.size());
    }

    private KitService service() {
        KitService service = new KitService(Logger.getLogger("test"), tempDirectory.resolve("kit-claims.properties"));
        service.load();
        return service;
    }

    private static void executeStart(EntityPlayerMP player, KitService service, String accountId) throws Exception {
        Method execute = KitCommand.class.getDeclaredMethod("execute", Object.class, Object.class, KitService.class, PlayerRoleLookup.class);
        execute.setAccessible(true);
        try {
            execute.invoke(null, player, new String[] { "start" }, service, new FixedAccountLookup(accountId));
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

    private static final class FixedAccountLookup implements PlayerRoleLookup {
        private final String accountId;

        private FixedAccountLookup(String accountId) {
            this.accountId = accountId;
        }

        @Override
        public String roleFor(Object player) {
            return "player";
        }

        @Override
        public String accountIdFor(Object player) {
            return accountId;
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
