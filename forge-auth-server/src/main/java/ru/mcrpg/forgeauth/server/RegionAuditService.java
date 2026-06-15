package ru.mcrpg.forgeauth.server;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

final class RegionAuditService {

    private static final Path DEFAULT_PATH = Paths.get("obsidiangate", "region-audit.log");
    private static final int MAX_RECORDS = 50000;

    private final Logger logger;
    private final Path storagePath;
    private boolean rollingBack;
    private int recordsSinceTrim;

    RegionAuditService(Logger logger) {
        this(logger, DEFAULT_PATH);
    }

    RegionAuditService(Logger logger, Path storagePath) {
        this.logger = logger;
        this.storagePath = storagePath;
    }

    synchronized void record(Object world, Object pos, Object player, RegionProtectionService.Region region) {
        Object state = ServerReflection.invoke(world, new String[] { "getBlockState", "func_180495_p" }, pos);
        record(world, pos, player, region, state, null);
    }

    synchronized void recordSnapshot(Object snapshot, Object player, RegionProtectionService.Region region) {
        Object world = ServerReflection.invoke(snapshot, new String[] { "getWorld" });
        Object pos = ServerReflection.invoke(snapshot, new String[] { "getPos" });
        Object state = ServerReflection.invoke(snapshot, new String[] { "getReplacedBlock" });
        Object nbt = ServerReflection.invoke(snapshot, new String[] { "getNbt" });
        record(world, pos, player, region, state, nbt == null ? null : String.valueOf(nbt));
    }

    private void record(
        Object world,
        Object pos,
        Object player,
        RegionProtectionService.Region region,
        Object state,
        String snapshotNbt
    ) {
        if (rollingBack || world == null || pos == null || player == null || region == null) {
            return;
        }
        try {
            Object block = ServerReflection.invoke(state, new String[] { "getBlock", "func_177230_c" });
            String blockName = String.valueOf(ServerReflection.invoke(block, new String[] { "getRegistryName" }));
            int metadata = ServerReflection.integer(
                ServerReflection.invoke(block, new String[] { "getMetaFromState", "func_176201_c" }, state)
            );
            String nbt = snapshotNbt == null ? tileNbt(world, pos) : snapshotNbt;
            Record record = new Record(
                System.currentTimeMillis(),
                region.name,
                PlayerIdentity.name(player),
                region.dimension,
                coordinate(pos, "getX", "func_177958_n", "p"),
                coordinate(pos, "getY", "func_177956_o", "q"),
                coordinate(pos, "getZ", "func_177952_p", "r"),
                blockName,
                metadata,
                nbt
            );
            append(record);
        } catch (RuntimeException exception) {
            logger.log(Level.FINE, "Could not record region block change.", exception);
        }
    }

    synchronized int rollback(
        Object server,
        Object sender,
        RegionProtectionService.Region region,
        String playerFilter,
        int limit
    ) {
        if (limit < 1 || limit > 10000) {
            throw new IllegalArgumentException("Rollback count must be between 1 and 10000.");
        }
        List<Record> records = readAll();
        List<Integer> selected = new ArrayList<Integer>();
        String normalizedPlayer = playerFilter == null ? null : playerFilter.trim().toLowerCase(Locale.ROOT);
        for (int index = records.size() - 1; index >= 0 && selected.size() < limit; index--) {
            Record record = records.get(index);
            if (!record.region.equals(region.name)) {
                continue;
            }
            if (normalizedPlayer != null && !record.player.toLowerCase(Locale.ROOT).equals(normalizedPlayer)) {
                continue;
            }
            selected.add(Integer.valueOf(index));
        }
        Object manager = ServerReflection.invoke(server, new String[] { "getCommandManager", "func_71187_D" });
        Object rollbackSender = rollbackSender(server, sender, region.dimension);
        int restored = 0;
        List<Integer> restoredIndices = new ArrayList<Integer>();
        rollingBack = true;
        try {
            for (Integer selectedIndex : selected) {
                Record record = records.get(selectedIndex.intValue());
                Object result = ServerReflection.invoke(
                    manager,
                    new String[] { "executeCommand", "func_71556_a" },
                    rollbackSender,
                    record.command()
                );
                if (!(result instanceof Number) || ((Number) result).intValue() > 0) {
                    restored++;
                    restoredIndices.add(selectedIndex);
                }
            }
        } finally {
            rollingBack = false;
        }
        for (Integer selectedIndex : restoredIndices) {
            records.set(selectedIndex.intValue(), null);
        }
        List<Record> remaining = new ArrayList<Record>();
        for (Record record : records) {
            if (record != null) {
                remaining.add(record);
            }
        }
        writeAll(remaining);
        return restored;
    }

    private void append(Record record) {
        try {
            Path parent = storagePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(
                storagePath,
                (record.serialize() + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
            recordsSinceTrim++;
            if (recordsSinceTrim >= 1000) {
                recordsSinceTrim = 0;
                List<String> lines = Files.readAllLines(storagePath, StandardCharsets.UTF_8);
                if (lines.size() > MAX_RECORDS) {
                    Files.write(storagePath, lines.subList(lines.size() - MAX_RECORDS, lines.size()), StandardCharsets.UTF_8);
                }
            }
        } catch (IOException exception) {
            logger.log(Level.WARNING, "Could not persist region audit record.", exception);
        }
    }

    private List<Record> readAll() {
        List<Record> records = new ArrayList<Record>();
        if (!Files.isRegularFile(storagePath)) {
            return records;
        }
        try {
            for (String line : Files.readAllLines(storagePath, StandardCharsets.UTF_8)) {
                Record record = Record.parse(line);
                if (record != null) {
                    records.add(record);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read the region audit log.", exception);
        }
        return records;
    }

    private void writeAll(List<Record> records) {
        List<String> lines = new ArrayList<String>();
        for (Record record : records) {
            lines.add(record.serialize());
        }
        try {
            Path parent = storagePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(storagePath, lines, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not update the region audit log.", exception);
        }
    }

    private static String tileNbt(Object world, Object pos) {
        Object tile = ServerReflection.invoke(world, new String[] { "getTileEntity", "func_175625_s" }, pos);
        if (tile == null) {
            return "";
        }
        try {
            Object compound = Class.forName("net.minecraft.nbt.NBTTagCompound").newInstance();
            Object written = ServerReflection.invoke(tile, new String[] { "writeToNBT", "func_189515_b" }, compound);
            return String.valueOf(written == null ? compound : written);
        } catch (ReflectiveOperationException | LinkageError exception) {
            return "";
        }
    }

    private static int coordinate(Object pos, String... names) {
        return ServerReflection.integer(ServerReflection.invoke(pos, names));
    }

    private static Object rollbackSender(final Object server, final Object original, int dimension) {
        try {
            final Object world = ServerReflection.invoke(server, new String[] { "getWorld", "func_71218_a" }, Integer.valueOf(dimension));
            Class<?> senderType = Class.forName("net.minecraft.command.ICommandSender");
            return Proxy.newProxyInstance(
                senderType.getClassLoader(),
                new Class<?>[] { senderType },
                (proxy, method, args) -> invokeSender(proxy, method, args, original, world)
            );
        } catch (ClassNotFoundException | LinkageError exception) {
            return original;
        }
    }

    private static Object invokeSender(Object proxy, Method method, Object[] args, Object original, Object world) {
        String name = method.getName();
        if ("getEntityWorld".equals(name) || "func_130014_f_".equals(name)) {
            return world;
        }
        if ("canUseCommand".equals(name) || "func_70003_b".equals(name)) {
            return Boolean.TRUE;
        }
        if ("getName".equals(name) || "func_70005_c_".equals(name)) {
            return "ObsidianGateRollback";
        }
        if ("equals".equals(name)) {
            return Boolean.valueOf(args != null && args.length > 0 && proxy == args[0]);
        }
        if ("hashCode".equals(name)) {
            return Integer.valueOf(System.identityHashCode(proxy));
        }
        if ("toString".equals(name)) {
            return "ObsidianGateRollbackSender";
        }
        Object delegated = ServerReflection.invoke(original, new String[] { name }, args == null ? new Object[0] : args);
        return delegated == null ? defaultValue(method.getReturnType()) : delegated;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == Boolean.TYPE) {
            return Boolean.FALSE;
        }
        if (type == Integer.TYPE) {
            return Integer.valueOf(0);
        }
        if (type == Long.TYPE) {
            return Long.valueOf(0L);
        }
        if (type == Float.TYPE) {
            return Float.valueOf(0.0F);
        }
        if (type == Double.TYPE) {
            return Double.valueOf(0.0D);
        }
        return null;
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static final class Record {
        final long timestamp;
        final String region;
        final String player;
        final int dimension;
        final int x;
        final int y;
        final int z;
        final String block;
        final int metadata;
        final String nbt;

        private Record(long timestamp, String region, String player, int dimension, int x, int y, int z, String block, int metadata, String nbt) {
            this.timestamp = timestamp;
            this.region = region;
            this.player = player;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.block = block;
            this.metadata = metadata;
            this.nbt = nbt;
        }

        String serialize() {
            return timestamp + "\t" + encode(region) + "\t" + encode(player) + "\t" + dimension + "\t"
                + x + "\t" + y + "\t" + z + "\t" + encode(block) + "\t" + metadata + "\t" + encode(nbt);
        }

        String command() {
            return "setblock " + x + " " + y + " " + z + " " + block + " " + metadata
                + " replace" + (nbt.isEmpty() ? "" : " " + nbt);
        }

        static Record parse(String line) {
            try {
                String[] parts = line.split("\\t", -1);
                if (parts.length != 10) {
                    return null;
                }
                return new Record(
                    Long.parseLong(parts[0]),
                    decode(parts[1]),
                    decode(parts[2]),
                    Integer.parseInt(parts[3]),
                    Integer.parseInt(parts[4]),
                    Integer.parseInt(parts[5]),
                    Integer.parseInt(parts[6]),
                    decode(parts[7]),
                    Integer.parseInt(parts[8]),
                    decode(parts[9])
                );
            } catch (RuntimeException exception) {
                return null;
            }
        }
    }
}
