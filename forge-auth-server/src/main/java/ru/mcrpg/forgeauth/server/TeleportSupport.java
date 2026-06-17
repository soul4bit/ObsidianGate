package ru.mcrpg.forgeauth.server;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

final class TeleportSupport {

    static final double WORLD_BORDER_LIMIT = 29999984.0D;
    static final double MIN_Y = 0.0D;
    static final double MAX_Y = 256.0D;

    private static final String PLAYER_CLASS_NAME = "net.minecraft.entity.player.EntityPlayerMP";

    private TeleportSupport() {
    }

    static Object resolvePlayer(Object sender) {
        if (isPlayer(sender)) {
            return sender;
        }
        Object entity = invokeZeroArgIfPresent(sender, "getCommandSenderEntity", "func_174793_f");
        return isPlayer(entity) ? entity : null;
    }

    static Object findOnlinePlayer(Object server, String username) {
        if (server == null || username == null || username.trim().isEmpty()) {
            return null;
        }

        Object playerList = invokeZeroArgIfPresent(server, "getPlayerList", "func_184103_al");
        Object player = invokeIfPresent(playerList, new Object[] { username }, "getPlayerByUsername", "func_152612_a");
        if (isPlayer(player)) {
            return player;
        }

        for (Object onlinePlayer : onlinePlayers(playerList)) {
            String onlineName = playerName(onlinePlayer);
            if (onlineName.equalsIgnoreCase(username)) {
                return onlinePlayer;
            }
        }

        for (String onlineName : onlinePlayerNames(server)) {
            if (onlineName.equalsIgnoreCase(username)) {
                player = invokeIfPresent(playerList, new Object[] { onlineName }, "getPlayerByUsername", "func_152612_a");
                return isPlayer(player) ? player : null;
            }
        }
        return null;
    }

    static List<String> onlinePlayerNames(Object server) {
        LinkedHashSet<String> names = new LinkedHashSet<String>();
        Object rawNames = invokeZeroArgIfPresent(server, "getOnlinePlayerNames", "func_71213_z");
        if (rawNames instanceof String[]) {
            String[] onlineNames = (String[]) rawNames;
            for (String name : onlineNames) {
                if (name != null && !name.isEmpty()) {
                    names.add(name);
                }
            }
        }

        Object playerList = invokeZeroArgIfPresent(server, "getPlayerList", "func_184103_al");
        for (Object player : onlinePlayers(playerList)) {
            String name = playerName(player);
            if (!"unknown".equals(name)) {
                names.add(name);
            }
        }

        ArrayList<String> sorted = new ArrayList<String>(names);
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        return sorted;
    }

    static boolean isPlayer(Object value) {
        if (value == null) {
            return false;
        }

        Class<?> type = value.getClass();
        while (type != null) {
            String name = type.getName();
            if (
                PLAYER_CLASS_NAME.equals(name)
                    || "net.minecraft.entity.player.EntityPlayer".equals(name)
                    || "oq".equals(name)
                    || "aed".equals(name)
            ) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    static String playerName(Object player) {
        Object name = invokeZeroArgIfPresent(player, "getName", "func_70005_c_", "h_");
        return name == null ? "unknown" : name.toString();
    }

    static double playerX(Object player) {
        return readDoubleField(player, 0.0D, "posX", "field_70165_t", "p");
    }

    static double playerY(Object player) {
        return readDoubleField(player, 0.0D, "posY", "field_70163_u", "q");
    }

    static double playerZ(Object player) {
        return readDoubleField(player, 0.0D, "posZ", "field_70161_v", "r");
    }

    static float playerYaw(Object player) {
        return readFloatField(player, 0.0F, "rotationYaw", "field_70177_z", "v");
    }

    static float playerPitch(Object player) {
        return readFloatField(player, 0.0F, "rotationPitch", "field_70125_A", "w");
    }

    static int playerDimension(Object player) {
        Object value = readFieldIfPresent(player, "dimension", "field_71093_bK");
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return worldDimension(readFieldIfPresent(player, "world", "field_70170_p", "l"), 0);
    }

    static Object teleportToPlayer(Object movingPlayer, Object destinationPlayer) {
        if (movingPlayer == null || destinationPlayer == null) {
            throw new IllegalArgumentException("Игрок для телепортации не найден.");
        }

        Object currentPlayer = movingPlayer;
        invokeZeroArgIfPresent(currentPlayer, "dismountRidingEntity", "func_184210_p");

        int destinationDimension = playerDimension(destinationPlayer);
        double destinationX = playerX(destinationPlayer);
        double destinationY = playerY(destinationPlayer);
        double destinationZ = playerZ(destinationPlayer);
        float destinationYaw = playerYaw(destinationPlayer);
        float destinationPitch = playerPitch(destinationPlayer);
        if (playerDimension(currentPlayer) != destinationDimension) {
            currentPlayer = changeDimension(
                currentPlayer,
                destinationDimension,
                destinationX,
                destinationY,
                destinationZ,
                destinationYaw,
                destinationPitch
            );
        }

        teleport(
            currentPlayer,
            destinationX,
            destinationY,
            destinationZ,
            destinationYaw,
            destinationPitch
        );
        return currentPlayer;
    }

    static Object teleportToPlayer(Object server, Object movingPlayer, Object destinationPlayer) {
        if (movingPlayer == null || destinationPlayer == null) {
            throw new IllegalArgumentException("Игрок для телепортации не найден.");
        }

        return teleportToDimension(
            server,
            movingPlayer,
            playerDimension(destinationPlayer),
            playerX(destinationPlayer),
            playerY(destinationPlayer),
            playerZ(destinationPlayer),
            playerYaw(destinationPlayer),
            playerPitch(destinationPlayer)
        );
    }

    static Object teleportToDimension(Object server, Object player, int destinationDimension, double x, double y, double z, float yaw, float pitch) {
        if (player == null) {
            throw new IllegalArgumentException("Игрок для телепортации не найден.");
        }

        Object destinationWorld = world(server, destinationDimension);
        prepareDestinationChunk(destinationWorld, x, z);

        Object currentPlayer = player;
        invokeZeroArgIfPresent(currentPlayer, "dismountRidingEntity", "func_184210_p");
        if (playerDimension(currentPlayer) != destinationDimension) {
            String currentName = playerName(currentPlayer);
            currentPlayer = changeDimension(currentPlayer, destinationDimension, x, y, z, yaw, pitch);
            currentPlayer = refreshOnlinePlayer(server, currentPlayer, currentName);
        }

        prepareDestinationChunk(destinationWorld, x, z);
        teleport(currentPlayer, x, y, z, yaw, pitch);
        return currentPlayer;
    }

    static Location worldSpawnLocation(Object world) {
        Object spawn = invokeZeroArgIfPresent(world, "getSpawnPoint", "func_175694_M", "T");
        if (spawn == null) {
            throw new IllegalStateException("Точка спавна мира не найдена.");
        }

        int x = (int) Math.floor(readBlockCoordinate(spawn, "getX", "func_177958_n", "p"));
        int y = (int) Math.floor(readBlockCoordinate(spawn, "getY", "func_177956_o", "q"));
        int z = (int) Math.floor(readBlockCoordinate(spawn, "getZ", "func_177952_p", "r"));
        prepareDestinationChunk(world, x + 0.5D, z + 0.5D);

        Location exact = safeStandingLocation(world, x, y, z);
        if (exact != null) {
            return exact;
        }

        Object top = invokeIfPresent(world, new Object[] { blockPos(x, 0, z) }, "getTopSolidOrLiquidBlock", "func_175672_r");
        if (top != null) {
            int topY = (int) Math.floor(readBlockCoordinate(top, "getY", "func_177956_o", "q"));
            Location surface = safeStandingLocation(world, x, topY, z);
            if (surface != null) {
                return surface;
            }
            return centeredLocation(x, topY, z);
        }

        return centeredLocation(x, y, z);
    }

    static Object changeDimension(Object player, int destinationDimension, double x, double y, double z, float yaw, float pitch) {
        if (player == null) {
            throw new IllegalArgumentException("Игрок для телепортации не найден.");
        }
        if (playerDimension(player) == destinationDimension) {
            return player;
        }

        invokeZeroArgIfPresent(player, "dismountRidingEntity", "func_184210_p");

        Object forgeTeleporter = forgeTeleporter(x, y, z, yaw, pitch);
        if (forgeTeleporter != null) {
            Object transferredPlayer = invokeIfPresent(
                player,
                new Object[] { Integer.valueOf(destinationDimension), forgeTeleporter },
                "changeDimension"
            );
            if (transferredPlayer != null) {
                return transferredPlayer;
            }
            if (playerDimension(player) == destinationDimension) {
                return player;
            }
        }

        Object transferredPlayer = invokeIfPresent(
            player,
            new Object[] { Integer.valueOf(destinationDimension) },
            "changeDimension",
            "func_184204_a"
        );
        if (transferredPlayer != null) {
            return transferredPlayer;
        }
        if (playerDimension(player) == destinationDimension) {
            return player;
        }

        throw new IllegalStateException("Не удалось сменить измерение игрока.");
    }

    static void teleport(Object player, double x, double y, double z, float yaw, float pitch) {
        invokeZeroArgIfPresent(player, "dismountRidingEntity", "func_184210_p");

        Object connection = readFieldIfPresent(player, "connection", "field_71135_a");
        if (connection != null && invokeMethodIfPresent(connection, new Object[] {
            Double.valueOf(x),
            Double.valueOf(y),
            Double.valueOf(z),
            Float.valueOf(yaw),
            Float.valueOf(pitch)
        }, "setPlayerLocation", "func_147364_a")) {
            resetMotion(player);
            return;
        }

        if (invokeMethodIfPresent(player, new Object[] {
            Double.valueOf(x),
            Double.valueOf(y),
            Double.valueOf(z),
            Float.valueOf(yaw),
            Float.valueOf(pitch)
        }, "setLocationAndAngles", "func_70080_a")) {
            resetMotion(player);
            return;
        }

        if (invokeMethodIfPresent(player, new Object[] {
            Double.valueOf(x),
            Double.valueOf(y),
            Double.valueOf(z)
        }, "setPositionAndUpdate", "func_70634_a")) {
            setFloatField(player, yaw, "rotationYaw", "field_70177_z");
            setFloatField(player, pitch, "rotationPitch", "field_70125_A");
            resetMotion(player);
            return;
        }

        throw new IllegalStateException("Не найден метод телепортации.");
    }

    static void resetMotion(Object player) {
        setFloatField(player, 0.0F, "fallDistance", "field_70143_R", "G");
        setDoubleField(player, 0.0D, "motionX", "field_70159_w", "s");
        setDoubleField(player, 0.0D, "motionY", "field_70181_x", "t");
        setDoubleField(player, 0.0D, "motionZ", "field_70179_y", "u");
    }

    static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    static boolean isChunkLoaded(Object world, int blockX, int blockZ) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        Object provider = invokeZeroArgIfPresent(world, "getChunkProvider", "func_72863_F");
        Object loaded = invokeIfPresent(
            provider,
            new Object[] { Integer.valueOf(chunkX), Integer.valueOf(chunkZ) },
            "isChunkLoaded",
            "chunkExists",
            "func_73249_a"
        );
        if (loaded instanceof Boolean) {
            return ((Boolean) loaded).booleanValue();
        }

        Object position = blockPos(blockX, 0, blockZ);
        Object blockLoaded = invokeIfPresent(world, new Object[] { position }, "isBlockLoaded", "func_175667_e");
        return Boolean.TRUE.equals(blockLoaded);
    }

    private static Object world(Object server, int dimension) {
        return invokeIfPresent(server, new Object[] { Integer.valueOf(dimension) }, "getWorld", "func_71218_a");
    }

    private static Object refreshOnlinePlayer(Object server, Object fallback, String playerName) {
        Object onlinePlayer = findOnlinePlayer(server, playerName);
        return onlinePlayer == null ? fallback : onlinePlayer;
    }

    static boolean prepareDestinationChunk(Object world, double x, double z) {
        if (world == null) {
            return false;
        }
        if (isChunkLoaded(world, (int) Math.floor(x), (int) Math.floor(z))) {
            return true;
        }
        int chunkX = ((int) Math.floor(x)) >> 4;
        int chunkZ = ((int) Math.floor(z)) >> 4;
        Object provider = invokeZeroArgIfPresent(world, "getChunkProvider", "func_72863_F");
        if (invokeMethodIfPresent(provider, new Object[] { Integer.valueOf(chunkX), Integer.valueOf(chunkZ) }, "loadChunk", "func_186025_d")) {
            return true;
        }
        return invokeMethodIfPresent(world, new Object[] { Integer.valueOf(chunkX), Integer.valueOf(chunkZ) }, "getChunkFromChunkCoords", "func_72964_e");
    }

    private static Object blockPos(int x, int y, int z) {
        try {
            Class<?> blockPosType = Class.forName("net.minecraft.util.math.BlockPos");
            return blockPosType
                .getConstructor(Integer.TYPE, Integer.TYPE, Integer.TYPE)
                .newInstance(Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(z));
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static Location safeStandingLocation(Object world, int x, int y, int z) {
        if (y < 1 || y > 254) {
            return null;
        }

        Object feet = blockPos(x, y, z);
        Object head = blockPos(x, y + 1, z);
        Object below = blockPos(x, y - 1, z);
        if (isAir(world, feet) && isAir(world, head) && !isAir(world, below) && !isLiquid(world, feet) && !isLiquid(world, below)) {
            return centeredLocation(x, y, z);
        }
        return null;
    }

    private static Location centeredLocation(int x, int y, int z) {
        return new Location(x + 0.5D, y, z + 0.5D);
    }

    private static boolean isAir(Object world, Object position) {
        Object value = invokeIfPresent(world, new Object[] { position }, "isAirBlock", "func_175623_d");
        return Boolean.TRUE.equals(value);
    }

    private static boolean isLiquid(Object world, Object position) {
        Object state = invokeIfPresent(world, new Object[] { position }, "getBlockState", "func_180495_p");
        Object material = invokeIfPresent(state, new Object[0], "getMaterial", "func_185904_a");
        Object liquid = invokeIfPresent(material, new Object[0], "isLiquid", "func_76224_d");
        return Boolean.TRUE.equals(liquid);
    }

    private static double readBlockCoordinate(Object blockPos, String... methodNames) {
        Object value = invokeIfPresent(blockPos, new Object[0], methodNames);
        if (!(value instanceof Number)) {
            throw new IllegalStateException("Координата BlockPos не является числом.");
        }
        return ((Number) value).doubleValue();
    }

    private static Object forgeTeleporter(final double x, final double y, final double z, final float yaw, final float pitch) {
        try {
            Class<?> teleporterType = Class.forName("net.minecraftforge.common.util.ITeleporter");
            return Proxy.newProxyInstance(
                teleporterType.getClassLoader(),
                new Class<?>[] { teleporterType },
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("placeEntity".equals(name)) {
                        Object entity = args != null && args.length > 1 ? args[1] : null;
                        if (entity != null) {
                            placeEntity(entity, x, y, z, yaw, pitch);
                        }
                        return null;
                    }
                    if ("isVanilla".equals(name)) {
                        return Boolean.FALSE;
                    }
                    if ("toString".equals(name)) {
                        return "ObsidianGateTeleporter";
                    }
                    if ("hashCode".equals(name)) {
                        return Integer.valueOf(System.identityHashCode(proxy));
                    }
                    if ("equals".equals(name)) {
                        return Boolean.valueOf(proxy == (args == null || args.length == 0 ? null : args[0]));
                    }
                    return defaultValue(method.getReturnType());
                }
            );
        } catch (ClassNotFoundException | LinkageError exception) {
            return null;
        }
    }

    private static void placeEntity(Object entity, double x, double y, double z, float yaw, float pitch) {
        if (invokeMethodIfPresent(entity, new Object[] {
            Double.valueOf(x),
            Double.valueOf(y),
            Double.valueOf(z),
            Float.valueOf(yaw),
            Float.valueOf(pitch)
        }, "setLocationAndAngles", "func_70080_a")) {
            resetMotion(entity);
            return;
        }

        if (invokeMethodIfPresent(entity, new Object[] {
            Double.valueOf(x),
            Double.valueOf(y),
            Double.valueOf(z)
        }, "setPositionAndUpdate", "func_70634_a")) {
            setFloatField(entity, yaw, "rotationYaw", "field_70177_z");
            setFloatField(entity, pitch, "rotationPitch", "field_70125_A");
            resetMotion(entity);
        }
    }

    private static List<Object> onlinePlayers(Object playerList) {
        if (playerList == null) {
            return Collections.emptyList();
        }

        Object rawPlayers = invokeZeroArgIfPresent(playerList, "getPlayers", "func_181057_v");
        if (!(rawPlayers instanceof Iterable<?>)) {
            return Collections.emptyList();
        }

        ArrayList<Object> players = new ArrayList<Object>();
        for (Object player : (Iterable<?>) rawPlayers) {
            if (isPlayer(player)) {
                players.add(player);
            }
        }
        return players;
    }

    private static Object invokeZeroArgIfPresent(Object target, String... methodNames) {
        return invokeIfPresent(target, new Object[0], methodNames);
    }

    private static Object invokeIfPresent(Object target, Object[] args, String... methodNames) {
        if (target == null) {
            return null;
        }
        Object[] safeArgs = args == null ? new Object[0] : args;
        Class<?> type = target.getClass();
        while (type != null) {
            Method[] methods;
            try {
                methods = type.getDeclaredMethods();
            } catch (LinkageError ignored) {
                type = type.getSuperclass();
                continue;
            }
            for (Method method : methods) {
                if (methodMatches(method, safeArgs, methodNames)) {
                    try {
                        method.setAccessible(true);
                        return method.invoke(target, safeArgs);
                    } catch (ReflectiveOperationException exception) {
                        throw invocationFailure(method, exception);
                    }
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static boolean invokeMethodIfPresent(Object target, Object[] args, String... methodNames) {
        if (target == null) {
            return false;
        }
        Object[] safeArgs = args == null ? new Object[0] : args;
        Class<?> type = target.getClass();
        while (type != null) {
            Method[] methods;
            try {
                methods = type.getDeclaredMethods();
            } catch (LinkageError ignored) {
                type = type.getSuperclass();
                continue;
            }
            for (Method method : methods) {
                if (methodMatches(method, safeArgs, methodNames)) {
                    try {
                        method.setAccessible(true);
                        method.invoke(target, safeArgs);
                        return true;
                    } catch (ReflectiveOperationException exception) {
                        throw invocationFailure(method, exception);
                    }
                }
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static IllegalStateException invocationFailure(Method method, ReflectiveOperationException exception) {
        Throwable cause = exception;
        if (exception instanceof InvocationTargetException) {
            Throwable targetCause = ((InvocationTargetException) exception).getTargetException();
            if (targetCause != null) {
                cause = targetCause;
            }
        }

        String detail = cause.getMessage();
        if (detail == null || detail.trim().isEmpty()) {
            detail = cause.getClass().getSimpleName();
        }
        return new IllegalStateException("Не удалось вызвать " + method.getName() + ": " + detail + ".", cause);
    }

    private static boolean methodMatches(Method method, Object[] args, String... methodNames) {
        if (method.getParameterTypes().length != args.length) {
            return false;
        }
        boolean nameMatches = false;
        for (String methodName : methodNames) {
            if (methodName.equals(method.getName())) {
                nameMatches = true;
                break;
            }
        }
        if (!nameMatches) {
            return false;
        }

        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (!isAssignable(parameterTypes[i], args[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAssignable(Class<?> parameterType, Object value) {
        if (value == null) {
            return !parameterType.isPrimitive();
        }
        if (!parameterType.isPrimitive()) {
            return parameterType.isAssignableFrom(value.getClass());
        }
        if (parameterType == Integer.TYPE) {
            return value instanceof Integer;
        }
        if (parameterType == Double.TYPE) {
            return value instanceof Double;
        }
        if (parameterType == Float.TYPE) {
            return value instanceof Float;
        }
        if (parameterType == Boolean.TYPE) {
            return value instanceof Boolean;
        }
        if (parameterType == Long.TYPE) {
            return value instanceof Long;
        }
        if (parameterType == Short.TYPE) {
            return value instanceof Short;
        }
        if (parameterType == Byte.TYPE) {
            return value instanceof Byte;
        }
        if (parameterType == Character.TYPE) {
            return value instanceof Character;
        }
        return false;
    }

    private static Object readFieldIfPresent(Object target, String... fieldNames) {
        if (target == null) {
            return null;
        }
        Field field = findField(target.getClass(), fieldNames);
        if (field == null) {
            return null;
        }
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Не удалось прочитать поле " + field.getName() + ".", exception);
        }
    }

    private static int worldDimension(Object world, int fallback) {
        Object provider = readFieldIfPresent(world, "provider", "field_73011_w", "s");
        Object value = invokeZeroArgIfPresent(provider, "getDimension", "func_186058_p", "i");
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static double readDoubleField(Object target, double fallback, String... fieldNames) {
        Object value = readFieldIfPresent(target, fieldNames);
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    private static float readFloatField(Object target, float fallback, String... fieldNames) {
        Object value = readFieldIfPresent(target, fieldNames);
        return value instanceof Number ? ((Number) value).floatValue() : fallback;
    }

    private static void setFloatField(Object target, float value, String... fieldNames) {
        setFieldIfPresent(target, Float.valueOf(value), fieldNames);
    }

    private static void setDoubleField(Object target, double value, String... fieldNames) {
        setFieldIfPresent(target, Double.valueOf(value), fieldNames);
    }

    private static void setFieldIfPresent(Object target, Object value, String... fieldNames) {
        if (target == null) {
            return;
        }
        Field field = findField(target.getClass(), fieldNames);
        if (field == null) {
            return;
        }
        try {
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Не удалось записать поле " + field.getName() + ".", exception);
        }
    }

    private static Field findField(Class<?> type, String... fieldNames) {
        Class<?> current = type;
        while (current != null) {
            for (String fieldName : fieldNames) {
                try {
                    return current.getDeclaredField(fieldName);
                } catch (NoSuchFieldException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Object defaultValue(Class<?> type) {
        if (type == Void.TYPE) {
            return null;
        }
        if (type == Boolean.TYPE) {
            return Boolean.FALSE;
        }
        if (type == Integer.TYPE) {
            return Integer.valueOf(0);
        }
        if (type == Float.TYPE) {
            return Float.valueOf(0.0F);
        }
        if (type == Double.TYPE) {
            return Double.valueOf(0.0D);
        }
        if (type == Long.TYPE) {
            return Long.valueOf(0L);
        }
        if (type == Short.TYPE) {
            return Short.valueOf((short) 0);
        }
        if (type == Byte.TYPE) {
            return Byte.valueOf((byte) 0);
        }
        if (type == Character.TYPE) {
            return Character.valueOf('\0');
        }
        return null;
    }

    static final class Location {
        final double x;
        final double y;
        final double z;

        private Location(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
