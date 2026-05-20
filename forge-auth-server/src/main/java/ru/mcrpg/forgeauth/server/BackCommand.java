package ru.mcrpg.forgeauth.server;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

final class BackCommand {

    private static final String COMMAND_NAME = "back";
    private static final String SUBJECT = "Назад";

    private BackCommand() {
    }

    static void register(
        FMLServerStartingEvent event,
        BackLocationService locations,
        TeleportGuardService guard,
        PlayerRoleLookup roles
    ) {
        try {
            Class<?> commandType = Class.forName("net.minecraft.command.ICommand");
            Object command = Proxy.newProxyInstance(
                BackCommand.class.getClassLoader(),
                new Class<?>[] { commandType },
                new Handler(locations, guard, roles)
            );
            Method registerMethod = event.getClass().getMethod("registerServerCommand", commandType);
            registerMethod.invoke(event, command);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Не удалось зарегистрировать команду /" + COMMAND_NAME + ".", exception);
        }
    }

    private static final class Handler implements InvocationHandler {
        private final BackLocationService locations;
        private final TeleportGuardService guard;
        private final PlayerRoleLookup roles;

        private Handler(BackLocationService locations, TeleportGuardService guard, PlayerRoleLookup roles) {
            this.locations = locations;
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
                return "/" + COMMAND_NAME;
            }
            if ("getAliases".equals(name) || "func_71514_a".equals(name)) {
                return Collections.emptyList();
            }
            if ("execute".equals(name) || "func_184881_a".equals(name)) {
                execute(args[0], args[1], args[2], locations, guard, roles);
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

    private static void execute(
        Object server,
        Object sender,
        Object arguments,
        BackLocationService locations,
        TeleportGuardService guard,
        PlayerRoleLookup roles
    ) {
        Object player = TeleportSupport.resolvePlayer(sender);
        if (player == null) {
            ServerChat.status(sender, ServerChat.Tone.ERROR, SUBJECT, "команду " + ServerChat.command("/" + COMMAND_NAME) + " может использовать только игрок.");
            return;
        }

        String[] args = arguments instanceof String[] ? (String[]) arguments : new String[0];
        if (args.length != 0) {
            ServerChat.usage(player, "/" + COMMAND_NAME);
            return;
        }

        String playerId = PlayerIdentity.id(player);
        RoleLimits limits = RoleLimits.forRole(roles.roleFor(player));
        int combatSeconds = guard.combatRemainingSeconds(player);
        if (combatSeconds > 0) {
            ServerChat.status(player, ServerChat.Tone.WARNING, SUBJECT, "телепорт заблокирован боем. Подождите " + ServerChat.durationText(combatSeconds) + ".");
            return;
        }

        int cooldownSeconds = guard.cooldownRemainingSeconds(playerId, TeleportGuardService.CHANNEL_BACK);
        if (cooldownSeconds > 0) {
            ServerChat.status(player, ServerChat.Tone.WARNING, SUBJECT, "перезарядка: осталось " + ServerChat.durationText(cooldownSeconds) + ".");
            return;
        }

        BackLocationService.DeathLocation location = locations.lastDeath(playerId);
        if (location == null) {
            ServerChat.status(player, ServerChat.Tone.WARNING, SUBJECT, "место последней смерти не найдено.");
            return;
        }

        try {
            Object moved = TeleportSupport.teleportToDimension(
                server,
                player,
                location.dimension,
                location.x,
                location.y,
                location.z,
                location.yaw,
                location.pitch
            );
            guard.startCooldown(playerId, TeleportGuardService.CHANNEL_BACK, limits.backCooldownMillis());
            ServerChat.status(moved, ServerChat.Tone.SUCCESS, SUBJECT, "возврат к месту смерти выполнен.");
        } catch (RuntimeException exception) {
            ServerChat.status(player, ServerChat.Tone.ERROR, SUBJECT, "ошибка: " + exception.getMessage());
        }
    }

    private static int compareTo(Object other) {
        if (other == null) {
            return 1;
        }
        Object otherName = invokeZeroArgIfPresent(other, "getName", "func_71517_b");
        return otherName == null ? 1 : COMMAND_NAME.compareTo(otherName.toString());
    }

    private static Object invokeZeroArgIfPresent(Object target, String... methodNames) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getParameterTypes().length != 0) {
                    continue;
                }
                for (String methodName : methodNames) {
                    if (!methodName.equals(method.getName())) {
                        continue;
                    }
                    try {
                        method.setAccessible(true);
                        return method.invoke(target);
                    } catch (ReflectiveOperationException exception) {
                        throw new IllegalStateException("Не удалось вызвать " + method.getName() + ".", exception);
                    }
                }
            }
            type = type.getSuperclass();
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
