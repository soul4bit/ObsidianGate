package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class PlayerDataGuardServiceTest {

    @Test
    void readsInventoryCountFromCompressedPlayerdata() throws Exception {
        Path playerdata = Files.createTempFile("playerdata-guard", ".dat");
        Files.write(playerdata, playerdataWithInventory(2));

        assertEquals(2, PlayerDataGuardService.readInventoryCount(playerdata));
    }

    @Test
    void findsLatestDifferentValidPlayerdataBackup() throws Exception {
        Path backupDirectory = Files.createTempDirectory("playerdata-backups");
        String worldName = "world-season";
        String uuid = "12345678-1234-1234-1234-123456789abc";
        String entryName = worldName + "/playerdata/" + uuid + ".dat";
        byte[] current = playerdataWithInventory(2);
        byte[] expected = playerdataWithInventory(3);

        Files.write(backupDirectory.resolve("playerdata-20260615-120000.tar.gz"), new byte[] {1, 2, 3});
        writeTarArchive(
            backupDirectory.resolve("playerdata-20260615-110000.tar.gz"),
            entryName,
            current
        );
        Path expectedArchive = backupDirectory.resolve("playerdata-20260615-100000.tar.gz");
        writeTarArchive(expectedArchive, entryName, expected);

        PlayerDataGuardService.BackupCandidate candidate = PlayerDataGuardService.findLatestValidBackup(
            backupDirectory,
            worldName,
            uuid,
            current,
            true
        );

        assertEquals(expectedArchive, candidate.archive);
        assertArrayEquals(expected, candidate.playerdata);
    }

    private static byte[] playerdataWithInventory(int count) throws Exception {
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(new GZIPOutputStream(file))) {
            output.writeByte(10);
            output.writeUTF("");
            output.writeByte(3);
            output.writeUTF("DataVersion");
            output.writeInt(1343);
            output.writeByte(9);
            output.writeUTF("Inventory");
            output.writeByte(10);
            output.writeInt(count);
            for (int index = 0; index < count; index++) {
                output.writeByte(0);
            }
            output.writeByte(0);
        }
        return file.toByteArray();
    }

    private static void writeTarArchive(Path archive, String entryName, byte[] payload) throws Exception {
        try (OutputStream file = Files.newOutputStream(archive);
             GZIPOutputStream gzip = new GZIPOutputStream(file)) {
            byte[] header = new byte[512];
            writeAscii(header, 0, 100, entryName);
            writeOctal(header, 100, 8, 0644);
            writeOctal(header, 108, 8, 0);
            writeOctal(header, 116, 8, 0);
            writeOctal(header, 124, 12, payload.length);
            writeOctal(header, 136, 12, 0);
            Arrays.fill(header, 148, 156, (byte) ' ');
            header[156] = '0';
            writeAscii(header, 257, 6, "ustar");
            writeAscii(header, 263, 2, "00");
            long checksum = 0;
            for (byte value : header) {
                checksum += value & 0xff;
            }
            writeOctal(header, 148, 8, checksum);

            gzip.write(header);
            gzip.write(payload);
            int padding = (512 - (payload.length % 512)) % 512;
            gzip.write(new byte[padding]);
            gzip.write(new byte[1024]);
        }
    }

    private static void writeAscii(byte[] target, int offset, int length, String value) {
        byte[] encoded = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(encoded, 0, target, offset, Math.min(encoded.length, length));
    }

    private static void writeOctal(byte[] target, int offset, int length, long value) {
        String encoded = Long.toOctalString(value);
        int start = offset + length - encoded.length() - 1;
        Arrays.fill(target, offset, start, (byte) '0');
        writeAscii(target, start, encoded.length(), encoded);
        target[offset + length - 1] = 0;
    }

    @Test
    void countsLoadedMainArmorAndOffhandItems() {
        FakePlayer player = new FakePlayer();
        player.inventory.mainInventory = Arrays.asList(new FakeStack(false), new FakeStack(true));
        player.inventory.armorInventory = Collections.singletonList(new FakeStack(false));
        player.inventory.offHandInventory = Collections.singletonList(new FakeStack(false));

        assertEquals(3, PlayerDataGuardService.countLoadedItems(player));
    }

    @Test
    void delayedCheckAllowsInventoryThatAppearsAfterLogin() throws Exception {
        UUID uuid = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        Path serverRoot = createServerRoot(uuid, playerdataWithInventory(2));
        PlayerDataGuardService service = newGuard(serverRoot);
        FakePlayer player = new FakePlayer("Knight", uuid);

        service.protectIfLoadFailed(player);
        assertNull(player.connection.lastMessage);

        player.inventory.mainInventory = Collections.singletonList(new FakeStack(false));
        runTicks(service, 100);

        assertNull(player.connection.lastMessage);
    }

    @Test
    void delayedCheckRestoresSnapshotWhenInventoryStaysEmpty() throws Exception {
        UUID uuid = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        byte[] original = playerdataWithInventory(2);
        Path serverRoot = createServerRoot(uuid, original);
        Path playerFile = playerFile(serverRoot, uuid);
        PlayerDataGuardService service = newGuard(serverRoot);
        FakePlayer player = new FakePlayer("Knight", uuid);

        service.protectIfLoadFailed(player);
        runUntilDisconnected(service, player);

        assertNotNull(player.connection.lastMessage);
        assertTrue(player.connection.lastMessage.contains("PLAYERDATA_LOAD_FAILED"));

        Files.write(playerFile, playerdataWithInventory(0));
        runTicks(service, 100);

        assertArrayEquals(original, Files.readAllBytes(playerFile));
    }

    @Test
    void pendingRestoreRejectsEarlyReconnect() throws Exception {
        UUID uuid = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        Path serverRoot = createServerRoot(uuid, playerdataWithInventory(2));
        PlayerDataGuardService service = newGuard(serverRoot);
        FakePlayer firstLogin = new FakePlayer("Knight", uuid);

        service.protectIfLoadFailed(firstLogin);
        runUntilDisconnected(service, firstLogin);

        FakePlayer earlyReconnect = new FakePlayer("Knight", uuid);
        service.protectIfLoadFailed(earlyReconnect);

        assertNotNull(earlyReconnect.connection.lastMessage);
        assertTrue(earlyReconnect.connection.lastMessage.contains("PLAYERDATA_RESTORE_PENDING"));
    }

    @Test
    void unreadablePlayerdataRestoresBackupEvenIfInventoryAppearsLater() throws Exception {
        UUID uuid = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        byte[] backupPlayerdata = playerdataWithInventory(3);
        Path serverRoot = createServerRoot(uuid, new byte[0]);
        Path backupDirectory = serverRoot.resolve("backups").resolve("playerdata");
        Files.createDirectories(backupDirectory);
        writeTarArchive(
            backupDirectory.resolve("playerdata-20260618-120000.tar.gz"),
            "world/playerdata/" + uuid.toString().toLowerCase() + ".dat",
            backupPlayerdata
        );
        PlayerDataGuardService service = newGuard(serverRoot);
        FakePlayer player = new FakePlayer("Knight", uuid);

        service.protectIfLoadFailed(player);
        player.inventory.mainInventory = Collections.singletonList(new FakeStack(false));
        runUntilDisconnected(service, player);

        assertNotNull(player.connection.lastMessage);
        assertTrue(player.connection.lastMessage.contains("PLAYERDATA_AUTO_RESTORED"));

        runTicks(service, 100);

        assertArrayEquals(backupPlayerdata, Files.readAllBytes(playerFile(serverRoot, uuid)));
    }

    private static PlayerDataGuardService newGuard(Path serverRoot) {
        return new PlayerDataGuardService(
            Logger.getLogger("test"),
            new MinecraftPlayerBridge(message -> message),
            serverRoot
        );
    }

    private static Path createServerRoot(UUID uuid, byte[] playerdata) throws Exception {
        Path serverRoot = Files.createTempDirectory("playerdata-guard-server");
        Files.write(serverRoot.resolve("server.properties"), Collections.singletonList("level-name=world"));
        Path playerFile = playerFile(serverRoot, uuid);
        Files.createDirectories(playerFile.getParent());
        Files.write(playerFile, playerdata);
        return serverRoot;
    }

    private static Path playerFile(Path serverRoot, UUID uuid) {
        return serverRoot.resolve("world").resolve("playerdata").resolve(uuid.toString().toLowerCase() + ".dat");
    }

    private static void runTicks(PlayerDataGuardService service, int ticks) {
        for (int index = 0; index < ticks; index++) {
            service.runEndTick();
        }
    }

    private static void runUntilDisconnected(PlayerDataGuardService service, FakePlayer player) {
        for (int index = 0; index < 200 && player.connection.lastMessage == null; index++) {
            service.runEndTick();
        }
    }

    static final class FakePlayer {
        private final String username;
        private final UUID uuid;
        public final FakeInventory inventory = new FakeInventory();
        public final FakeConnection connection = new FakeConnection();

        FakePlayer() {
            this("Knight", UUID.fromString("12345678-1234-1234-1234-123456789abc"));
        }

        FakePlayer(String username, UUID uuid) {
            this.username = username;
            this.uuid = uuid;
        }

        public String getName() {
            return username;
        }

        public UUID getUniqueID() {
            return uuid;
        }
    }

    static final class FakeInventory {
        public Iterable<FakeStack> mainInventory = Collections.emptyList();
        public Iterable<FakeStack> armorInventory = Collections.emptyList();
        public Iterable<FakeStack> offHandInventory = Collections.emptyList();
    }

    static final class FakeConnection {
        private String lastMessage;

        public void disconnect(Object textComponent) {
            this.lastMessage = String.valueOf(textComponent);
        }
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
