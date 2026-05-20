package ru.mcrpg.forgeauth.server;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

final class SpawnCommand {

    private static final String COMMAND_NAME = "spawn";
    private static final String PLAYER_CLASS_NAME = "net.minecraft.entity.player.EntityPlayerMP";
    private static final long SPAWN_COOLDOWN_MILLIS = TimeUnit.SECONDS.toMillis(30L);

    private SpawnCommand() {
    }

    static void register(FMLServerStartingEvent event, TeleportGuardService guard) {
        try {
            Class<?> commandType = Class.forName("net.minecraft.command.ICommand");
            Object command = Proxy.newProxyInstance(
                SpawnCommand.class.getClassLoader(),
                new Class<?>[] { commandType },
                new SpawnCommandHandler(guard)
            );
            Method registerMethod = event.getClass().getMethod("registerServerCommand", commandType);
            registerMethod.invoke(event, command);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Не удалось зарегистрировать команду /spawn.", exception);
        }
    }

    private static final class SpawnCommandHandler implements InvocationHandler {
        private final TeleportGuardService guard;

        private SpawnCommandHandler(TeleportGuardService guard) {
            this.guard = guard;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("getName".equals(name) || "func_71517_b".equals(name)) {
                return COMMAND_NAME;
            }
            if ("getUsage".equals(name) || "func_71518_a".equals(name)) {
                return "/" + COMMAND_NAME;
            }
            if ("getAliases".equals(name) || "func_71514_a".equals(name)) {
                return Collections.emptyList();
            }
            if ("execute".equals(name) || "func_184881_a".equals(name)) {
                execute(args[0], args[1], guard);
                return null;
            }
            if ("checkPermission".equals(name) || "func_184882_a".equals(name)) {
                return Boolean.TRUE;
            }
            if ("getTabCompletions".equals(name) || "func_184883_a".equals(name)) {
                return Collections.emptyList();
            }
            if ("isUsernameIndex".equals(name) || "func_82358_a".equals(name)) {
                return Boolean.FALSE;
            }
            if ("compareTo".equals(name)) {
                return compareTo(args == null ? null : args[0]);
            }
            if ("toString".equals(name)) {
                return "/" + COMMAND_NAME;
            }
            if ("hashCode".equals(name)) {
                return Integer.valueOf(COMMAND_NAME.hashCode());
            }
            if ("equals".equals(name)) {
                return Boolean.valueOf(proxy == args[0]);
            }
            return defaultValue(method.getReturnType());
        }

        @SuppressWarnings("unchecked")
        private static int compareTo(Object other) {
            if (other == null) {
                return 1;
            }
            Object otherName = invokeZeroArgIfPresent(other, "getName", "func_71517_b");
            if (otherName == null) {
                return 1;
            }
            return COMMAND_NAME.compareTo(otherName.toString());
        }
    }

    private static void execute(Object server, Object sender, TeleportGuardService guard) {
        Object player = resolvePlayer(sender);
        if (player == null) {
            ServerChat.error(sender, "Команду " + ServerChat.command("/spawn") + " может использовать только игрок.");
            return;
        }

        String playerId = PlayerIdentity.id(player);
        int combatSeconds = guard.combatRemainingSeconds(player);
        if (combatSeconds > 0) {
            ServerChat.status(player, ServerChat.Tone.WARNING, "Спавн", "телепорт заблокирован боем. Подождите " + ServerChat.durationText(combatSeconds) + ".");
            return;
        }

        int cooldownSeconds = guard.cooldownRemainingSeconds(playerId, TeleportGuardService.CHANNEL_SPAWN);
        if (cooldownSeconds > 0) {
            ServerChat.status(player, ServerChat.Tone.WARNING, "Спавн", "перезарядка: осталось " + ServerChat.durationText(cooldownSeconds) + ".");
            return;
        }

        try {
            Object spawnWorld = invoke(server, new Object[] { Integer.valueOf(0) }, "getWorld", "func_71218_a");
            if (spawnWorld == null) {
                ServerChat.error(sender, "Обычный мир ещё не загружен.");
                return;
            }

            Object spawn = invokeZeroArg(spawnWorld, "getSpawnPoint", "func_175694_M");
            double x = readBlockCoordinate(spawn, "getX", "func_177958_n") + 0.5D;
            double y = readBlockCoordinate(spawn, "getY", "func_177956_o");
            double z = readBlockCoordinate(spawn, "getZ", "func_177952_p") + 0.5D;
            if (y <= 0.0D) {
                Object safeSpawn = invokeIfPresent(spawnWorld, new Object[] { spawn }, "getTopSolidOrLiquidBlock", "func_175672_r");
                if (safeSpawn != null) {
                    y = readBlockCoordinate(safeSpawn, "getY", "func_177956_o");
                }
            }

            float yaw = readFloatField(player, 0.0F, "rotationYaw", "field_70177_z");
            float pitch = readFloatField(player, 0.0F, "rotationPitch", "field_70125_A");
            player = TeleportSupport.teleportToDimension(server, player, 0, x, y, z, yaw, pitch);
            guard.startCooldown(playerId, TeleportGuardService.CHANNEL_SPAWN, SPAWN_COOLDOWN_MILLIS);
            ServerChat.success(player, "Вы телепортированы на спавн.");
        } catch (RuntimeException exception) {
            ServerChat.error(sender, "Не удалось телепортировать на спавн: " + exception.getMessage());
        }
    }

    private static Object resolvePlayer(Object sender) {
        if (isPlayer(sender)) {
            return sender;
        }
        Object entity = invokeZeroArgIfPresent(sender, "getCommandSenderEntity", "func_174793_f");
        return isPlayer(entity) ? entity : null;
    }

    private static boolean isPlayer(Object value) {
        if (value == null) {
            return false;
        }

        Class<?> type = value.getClass();
        while (type != null) {
            if (PLAYER_CLASS_NAME.equals(type.getName())) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static void teleport(Object player, double x, double y, double z, float yaw, float pitch) {
        Object connection = readFieldIfPresent(player, "connection", "field_71135_a");
        if (connection != null && invokeMethodIfPresent(connection, new Object[] {
            Double.valueOf(x),
            Double.valueOf(y),
            Double.valueOf(z),
            Float.valueOf(yaw),
            Float.valueOf(pitch)
        }, "setPlayerLocation", "func_147364_a")) {
            return;
        }

        if (invokeMethodIfPresent(player, new Object[] {
            Double.valueOf(x),
            Double.valueOf(y),
            Double.valueOf(z)
        }, "setPositionAndUpdate", "func_70634_a")) {
            return;
        }

        if (invokeMethodIfPresent(player, new Object[] {
            Double.valueOf(x),
            Double.valueOf(y),
            Double.valueOf(z),
            Float.valueOf(yaw),
            Float.valueOf(pitch)
        }, "setLocationAndAngles", "func_70080_a")) {
            return;
        }

        throw new IllegalStateException("Не найден метод телепортации.");
    }

    private static double readBlockCoordinate(Object blockPos, String deobfuscatedName, String srgName) {
        Object value = invokeZeroArg(blockPos, deobfuscatedName, srgName);
        if (!(value instanceof Number)) {
            throw new IllegalStateException("Координата BlockPos не является числом.");
        }
        return ((Number) value).doubleValue();
    }

    private static Object invokeZeroArg(Object target, String... methodNames) {
        Object value = invokeZeroArgIfPresent(target, methodNames);
        if (value == null) {
            throw new IllegalStateException("Не найден метод " + String.join("/", methodNames) + ".");
        }
        return value;
    }

    private static Object invokeZeroArgIfPresent(Object target, String... methodNames) {
        return invokeIfPresent(target, new Object[0], methodNames);
    }

    private static Object invoke(Object target, Object[] args, String... methodNames) {
        Object value = invokeIfPresent(target, args, methodNames);
        if (value == null) {
            throw new IllegalStateException("Не найден метод " + String.join("/", methodNames) + ".");
        }
        return value;
    }

    private static Object invokeIfPresent(Object target, Object[] args, String... methodNames) {
        if (target == null) {
            return null;
        }
        Object[] safeArgs = args == null ? new Object[0] : args;
        Class<?> type = target.getClass();
        while (type != null) {
            for (Method method : type.getDeclaredMethods()) {
                if (methodMatches(method, safeArgs, methodNames)) {
                    try {
                        method.setAccessible(true);
                        return method.invoke(target, safeArgs);
                    } catch (ReflectiveOperationException exception) {
                        throw new IllegalStateException("Не удалось вызвать " + method.getName() + ".", exception);
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
            for (Method method : type.getDeclaredMethods()) {
                if (methodMatches(method, safeArgs, methodNames)) {
                    try {
                        method.setAccessible(true);
                        method.invoke(target, safeArgs);
                        return true;
                    } catch (ReflectiveOperationException exception) {
                        throw new IllegalStateException("Не удалось вызвать " + method.getName() + ".", exception);
                    }
                }
            }
            type = type.getSuperclass();
        }
        return false;
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

    private static int readIntField(Object target, int fallback, String... fieldNames) {
        Object value = readFieldIfPresent(target, fieldNames);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
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
        if (List.class.isAssignableFrom(type)) {
            return Collections.emptyList();
        }
        return null;
    }
}
