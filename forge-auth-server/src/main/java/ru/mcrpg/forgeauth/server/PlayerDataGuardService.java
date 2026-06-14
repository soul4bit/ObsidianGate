package ru.mcrpg.forgeauth.server;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

final class PlayerDataGuardService {

    private static final DateTimeFormatter INCIDENT_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int MAX_DEPTH = 64;
    private static final int RESTORE_DELAY_TICKS = 40;
    private static final String DISCONNECT_REASON =
        "PLAYERDATA_LOAD_FAILED: сервер не загрузил ваш профиль. Файл сохранен; сообщите администратору.";

    private final Logger logger;
    private final MinecraftPlayerBridge playerBridge;
    private final Path serverRoot;
    private final Map<String, PendingRestore> pendingRestores = new ConcurrentHashMap<String, PendingRestore>();
    private long tick;

    PlayerDataGuardService(Logger logger) {
        this(logger, new MinecraftPlayerBridge(), Paths.get("."));
    }

    PlayerDataGuardService(Logger logger, MinecraftPlayerBridge playerBridge, Path serverRoot) {
        this.logger = logger;
        this.playerBridge = playerBridge;
        this.serverRoot = serverRoot.toAbsolutePath().normalize();
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        protectIfLoadFailed(playerBridge.extractPlayerFromEvent(event));
    }

    void protectIfLoadFailed(Object player) {
        String uuid = playerBridge.extractUuid(player);
        String username = playerBridge.extractUsername(player);
        if (uuid.isEmpty() || username.isEmpty()) {
            return;
        }

        Path playerFile = playerDataDirectory().resolve(uuid.toLowerCase() + ".dat");
        if (!Files.isRegularFile(playerFile)) {
            return;
        }

        try {
            int savedItems = readInventoryCount(playerFile);
            int loadedItems = countLoadedItems(player);
            if (savedItems <= 0 || loadedItems > 0) {
                return;
            }

            byte[] original = Files.readAllBytes(playerFile);
            Path incidentFile = incidentDirectory().resolve(
                INCIDENT_TIME.format(LocalDateTime.now()) + "-" + uuid.toLowerCase() + ".dat"
            );
            Files.createDirectories(incidentFile.getParent());
            Files.write(incidentFile, original);
            pendingRestores.put(uuid.toLowerCase(), new PendingRestore(playerFile, original, tick + RESTORE_DELAY_TICKS));

            logger.severe(String.format(
                "Playerdata load guard stopped %s (%s): saved inventory has %d entries, loaded inventory is empty. "
                    + "Original profile: %s",
                username,
                uuid,
                savedItems,
                incidentFile
            ));
            playerBridge.disconnectPlayer(player, DISCONNECT_REASON);
        } catch (Exception exception) {
            logger.log(Level.SEVERE, "Playerdata load guard failed for " + username + ".", exception);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        runEndTick();
    }

    void runEndTick() {
        tick++;
        for (Map.Entry<String, PendingRestore> entry : pendingRestores.entrySet()) {
            PendingRestore pending = entry.getValue();
            if (tick < pending.restoreAtTick || !pendingRestores.remove(entry.getKey(), pending)) {
                continue;
            }
            try {
                Path temp = pending.playerFile.resolveSibling(pending.playerFile.getFileName() + ".guard.tmp");
                Files.write(temp, pending.original);
                Files.move(temp, pending.playerFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                logger.warning("Restored protected playerdata after failed load: " + pending.playerFile);
            } catch (IOException exception) {
                logger.log(Level.SEVERE, "Could not restore protected playerdata: " + pending.playerFile, exception);
                pendingRestores.put(entry.getKey(), new PendingRestore(
                    pending.playerFile,
                    pending.original,
                    tick + RESTORE_DELAY_TICKS
                ));
            }
        }
    }

    private Path playerDataDirectory() {
        Properties properties = new Properties();
        Path propertiesFile = serverRoot.resolve("server.properties");
        try (InputStream input = Files.newInputStream(propertiesFile)) {
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read " + propertiesFile, exception);
        }
        String levelName = properties.getProperty("level-name", "world").trim();
        if (levelName.isEmpty() || levelName.contains("/") || levelName.contains("\\")) {
            throw new IllegalStateException("Unsafe level-name in " + propertiesFile + ": " + levelName);
        }
        return serverRoot.resolve(levelName).resolve("playerdata");
    }

    private Path incidentDirectory() {
        return serverRoot.resolve("backups").resolve("playerdata").resolve("guard");
    }

    static int readInventoryCount(Path playerFile) throws IOException {
        try (
            InputStream file = Files.newInputStream(playerFile);
            DataInputStream input = new DataInputStream(new BufferedInputStream(new GZIPInputStream(file)))
        ) {
            int rootType = input.readUnsignedByte();
            if (rootType != 10) {
                throw new IOException("Playerdata root is not a compound tag");
            }
            readString(input);
            return findInventoryInCompound(input, 0);
        }
    }

    private static int findInventoryInCompound(DataInputStream input, int depth) throws IOException {
        requireDepth(depth);
        while (true) {
            int type = input.readUnsignedByte();
            if (type == 0) {
                return 0;
            }
            String name = readString(input);
            if (type == 9) {
                int elementType = input.readUnsignedByte();
                int length = readLength(input);
                if ("Inventory".equals(name)) {
                    return length;
                }
                for (int index = 0; index < length; index++) {
                    skipPayload(input, elementType, depth + 1);
                }
            } else {
                skipPayload(input, type, depth + 1);
            }
        }
    }

    private static void skipPayload(DataInputStream input, int type, int depth) throws IOException {
        requireDepth(depth);
        switch (type) {
            case 0:
                return;
            case 1:
                input.readByte();
                return;
            case 2:
                input.readShort();
                return;
            case 3:
                input.readInt();
                return;
            case 4:
                input.readLong();
                return;
            case 5:
                input.readFloat();
                return;
            case 6:
                input.readDouble();
                return;
            case 7:
                skipFully(input, readLength(input));
                return;
            case 8:
                readString(input);
                return;
            case 9:
                int elementType = input.readUnsignedByte();
                int listLength = readLength(input);
                for (int index = 0; index < listLength; index++) {
                    skipPayload(input, elementType, depth + 1);
                }
                return;
            case 10:
                while (true) {
                    int childType = input.readUnsignedByte();
                    if (childType == 0) {
                        return;
                    }
                    readString(input);
                    skipPayload(input, childType, depth + 1);
                }
            case 11:
                skipFully(input, Math.multiplyExact(readLength(input), 4));
                return;
            case 12:
                skipFully(input, Math.multiplyExact(readLength(input), 8));
                return;
            default:
                throw new IOException("Unknown NBT tag type: " + type);
        }
    }

    static int countLoadedItems(Object player) {
        Object inventory = readField(player, "inventory", "field_71071_by");
        if (inventory == null) {
            return 0;
        }
        int count = 0;
        count += countNonEmpty(readField(inventory, "mainInventory", "field_70462_a"));
        count += countNonEmpty(readField(inventory, "armorInventory", "field_70460_b"));
        count += countNonEmpty(readField(inventory, "offHandInventory", "field_184439_c"));
        return count;
    }

    private static int countNonEmpty(Object values) {
        if (!(values instanceof Iterable<?>)) {
            return 0;
        }
        int count = 0;
        for (Object value : (Iterable<?>) values) {
            if (value != null && !isEmptyStack(value)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isEmptyStack(Object stack) {
        try {
            Method method = stack.getClass().getMethod("isEmpty");
            return Boolean.TRUE.equals(method.invoke(stack));
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Method method = stack.getClass().getMethod("func_190926_b");
            return Boolean.TRUE.equals(method.invoke(stack));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static Object readField(Object target, String... names) {
        if (target == null) {
            return null;
        }
        for (String name : names) {
            Class<?> type = target.getClass();
            while (type != null) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (ReflectiveOperationException ignored) {
                    type = type.getSuperclass();
                }
            }
        }
        return null;
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readUnsignedShort();
        byte[] value = new byte[length];
        input.readFully(value);
        return new String(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static int readLength(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > 16_777_216) {
            throw new IOException("Invalid NBT collection length: " + length);
        }
        return length;
    }

    private static void skipFully(DataInputStream input, int bytes) throws IOException {
        int remaining = bytes;
        while (remaining > 0) {
            int skipped = input.skipBytes(remaining);
            if (skipped <= 0) {
                if (input.read() < 0) {
                    throw new EOFException("Unexpected end of NBT data");
                }
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    private static void requireDepth(int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("NBT nesting is too deep");
        }
    }

    private static final class PendingRestore {
        private final Path playerFile;
        private final byte[] original;
        private final long restoreAtTick;

        private PendingRestore(Path playerFile, byte[] original, long restoreAtTick) {
            this.playerFile = playerFile;
            this.original = original;
            this.restoreAtTick = restoreAtTick;
        }
    }
}
