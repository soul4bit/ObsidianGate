package ru.mcrpg.forgeauth.server;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

final class RandomTeleportCommand {

    private static final String COMMAND_NAME = "rtp";
    private static final String SUBJECT = "RTP";
    private static final List<String> ALIASES = Collections.unmodifiableList(Arrays.asList("wild", "randomtp"));
    private static final int OVERWORLD_DIMENSION = 0;
    private static final int MIN_DISTANCE_FROM_SPAWN = 800;
    private static final int MAX_DISTANCE_FROM_SPAWN = 3000;
    private static final int MAX_ATTEMPTS = 48;

    private RandomTeleportCommand() {
    }

    static void register(FMLServerStartingEvent event, TeleportGuardService guard, PlayerRoleLookup roles) {
        try {
            Class<?> commandType = Class.forName("net.minecraft.command.ICommand");
            Object command = Proxy.newProxyInstance(
                RandomTeleportCommand.class.getClassLoader(),
                new Class<?>[] { commandType },
                new Handler(guard, roles)
            );
            Method registerMethod = event.getClass().getMethod("registerServerCommand", commandType);
            registerMethod.invoke(event, command);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Не удалось зарегистрировать команду /" + COMMAND_NAME + ".", exception);
        }
    }

    private static final class Handler implements InvocationHandler {
        private final TeleportGuardService guard;
        private final PlayerRoleLookup roles;

        private Handler(TeleportGuardService guard, PlayerRoleLookup roles) {
            this.guard = guard;
            this.roles = roles;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("getName".equals(name) || "func_71517_b".equals(name)) {
                return COMMAND_NAME;
            }
            if ("getUsage".equals(name) || "func_71518_a".equals(name)) {
                return usage();
            }
            if ("getAliases".equals(name) || "func_71514_a".equals(name)) {
                return ALIASES;
            }
            if ("execute".equals(name) || "func_184881_a".equals(name)) {
                execute(args[0], args[1], args[2], guard, roles);
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
                return Integer.valueOf(compareTo(args == null ? null : args[0]));
            }
            if ("toString".equals(name)) {
                return "/" + COMMAND_NAME;
            }
            if ("hashCode".equals(name)) {
                return Integer.valueOf(COMMAND_NAME.hashCode());
            }
            if ("equals".equals(name)) {
                return Boolean.valueOf(args != null && args.length > 0 && proxy == args[0]);
            }
            return defaultValue(method.getReturnType());
        }
    }

    private static void execute(Object server, Object sender, Object arguments, TeleportGuardService guard, PlayerRoleLookup roles) {
        Object player = TeleportSupport.resolvePlayer(sender);
        if (player == null) {
            ServerChat.status(sender, ServerChat.Tone.ERROR, SUBJECT, "команду " + ServerChat.command("/" + COMMAND_NAME) + " может использовать только игрок.");
            return;
        }

        String[] args = arguments instanceof String[] ? (String[]) arguments : new String[0];
        if (args.length != 0) {
            ServerChat.usage(player, usage());
            return;
        }

        String playerId = PlayerIdentity.id(player);
        RoleLimits limits = RoleLimits.forRole(roles.roleFor(player));
        int combatSeconds = guard.combatRemainingSeconds(player);
        if (combatSeconds > 0) {
            ServerChat.status(player, ServerChat.Tone.WARNING, SUBJECT, "телепорт заблокирован боем. Подождите " + combatSeconds + " сек.");
            return;
        }

        int cooldownSeconds = guard.cooldownRemainingSeconds(playerId, TeleportGuardService.CHANNEL_RTP);
        if (cooldownSeconds > 0) {
            ServerChat.status(player, ServerChat.Tone.WARNING, SUBJECT, "перезарядка: осталось " + ServerChat.durationText(cooldownSeconds) + ".");
            return;
        }

        try {
            Object world = invokeRequired(server, new Object[] { Integer.valueOf(OVERWORLD_DIMENSION) }, "getWorld", "func_71218_a");
            SafeLocation location = findLocation(world);
            if (location == null) {
                ServerChat.status(player, ServerChat.Tone.WARNING, SUBJECT, "не удалось найти безопасную точку. Попробуйте позже.");
                return;
            }

            float yaw = TeleportSupport.playerYaw(player);
            float pitch = TeleportSupport.playerPitch(player);
            Object moved = TeleportSupport.teleportToDimension(server, player, OVERWORLD_DIMENSION, location.x, location.y, location.z, yaw, pitch);
            guard.startCooldown(playerId, TeleportGuardService.CHANNEL_RTP, limits.randomTeleportCooldownMillis());
            ServerChat.status(
                moved,
                ServerChat.Tone.SUCCESS,
                SUBJECT,
                "телепорт: x=" + ServerChat.value(Integer.valueOf((int) Math.floor(location.x))) +
                    " y=" + ServerChat.value(Integer.valueOf((int) Math.floor(location.y))) +
                    " z=" + ServerChat.value(Integer.valueOf((int) Math.floor(location.z))) + "."
            );
        } catch (RuntimeException exception) {
            ServerChat.status(player, ServerChat.Tone.ERROR, SUBJECT, "ошибка: " + exception.getMessage());
        }
    }

    private static SafeLocation findLocation(Object world) {
        Object spawn = invokeRequired(world, new Object[0], "getSpawnPoint", "func_175694_M");
        double spawnX = readBlockCoordinate(spawn, "getX", "func_177958_n", "p");
        double spawnZ = readBlockCoordinate(spawn, "getZ", "func_177952_p", "r");
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            double angle = random.nextDouble(0.0D, Math.PI * 2.0D);
            double distance = Math.sqrt(random.nextDouble(
                (double) MIN_DISTANCE_FROM_SPAWN * (double) MIN_DISTANCE_FROM_SPAWN,
                (double) MAX_DISTANCE_FROM_SPAWN * (double) MAX_DISTANCE_FROM_SPAWN
            ));
            int x = (int) Math.floor(spawnX + Math.cos(angle) * distance);
            int z = (int) Math.floor(spawnZ + Math.sin(angle) * distance);
            if (Math.abs(x) > TeleportSupport.WORLD_BORDER_LIMIT || Math.abs(z) > TeleportSupport.WORLD_BORDER_LIMIT) {
                continue;
            }

            SafeLocation location = safeLocation(world, x, z);
            if (location != null) {
                return location;
            }
        }
        return null;
    }

    private static SafeLocation safeLocation(Object world, int x, int z) {
        if (!TeleportSupport.isChunkLoaded(world, x, z)) {
            return null;
        }

        Object top = invokeIfPresent(world, new Object[] { blockPos(x, 0, z) }, "getTopSolidOrLiquidBlock", "func_175672_r");
        if (top == null) {
            return null;
        }

        int y = (int) Math.floor(readBlockCoordinate(top, "getY", "func_177956_o", "q"));
        if (y < 2 || y > 253) {
            return null;
        }

        Object feet = blockPos(x, y, z);
        Object head = blockPos(x, y + 1, z);
        Object below = blockPos(x, y - 1, z);
        if (!isAir(world, feet) || !isAir(world, head) || isAir(world, below) || isLiquid(world, below) || isLiquid(world, feet)) {
            return null;
        }

        return new SafeLocation(x + 0.5D, y, z + 0.5D);
    }

    private static Object blockPos(int x, int y, int z) {
        try {
            Class<?> blockPosType = Class.forName("net.minecraft.util.math.BlockPos");
            Constructor<?> constructor = blockPosType.getConstructor(Integer.TYPE, Integer.TYPE, Integer.TYPE);
            return constructor.newInstance(Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(z));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Не удалось создать BlockPos.", exception);
        }
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

    private static double readBlockCoordinate(Object blockPos, String deobfuscatedName, String srgName, String obfuscatedName) {
        Object value = invokeIfPresent(blockPos, new Object[0], deobfuscatedName, srgName, obfuscatedName);
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
    }

    private static Object invokeRequired(Object target, Object[] args, String... methodNames) {
        Object value = invokeIfPresent(target, args, methodNames);
        if (value == null) {
            throw new IllegalStateException("Не найден метод " + Arrays.toString(methodNames) + ".");
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
        return false;
    }

    private static String usage() {
        return "/" + COMMAND_NAME;
    }

    private static int compareTo(Object other) {
        if (other == null) {
            return 1;
        }
        Object otherName = invokeIfPresent(other, new Object[0], "getName", "func_71517_b");
        return otherName == null ? 1 : COMMAND_NAME.compareTo(otherName.toString());
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

    private static final class SafeLocation {
        final double x;
        final double y;
        final double z;

        private SafeLocation(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
