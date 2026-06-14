package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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
