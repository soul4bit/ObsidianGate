package ru.mcrpg.forgeauth.server;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

final class HomeCommand {

    private static final String SUBJECT = "Дом";
    private HomeCommand() {
    }

    static void register(FMLServerStartingEvent event, HomeService homes, TeleportGuardService guard, PlayerRoleLookup roles) {
        register(event, "sethome", Mode.SET, Collections.emptyList(), homes, guard, roles);
        register(event, "home", Mode.TELEPORT, Collections.singletonList("h"), homes, guard, roles);
        register(event, "delhome", Mode.DELETE, Collections.singletonList("deletehome"), homes, guard, roles);
        register(event, "homes", Mode.LIST, Collections.singletonList("listhomes"), homes, guard, roles);
    }

    private static void register(
        FMLServerStartingEvent event,
        String commandName,
        Mode mode,
        List<String> aliases,
        HomeService homes,
        TeleportGuardService guard,
        PlayerRoleLookup roles
    ) {
        try {
            Class<?> commandType = Class.forName("net.minecraft.command.ICommand");
            Object command = Proxy.newProxyInstance(
                HomeCommand.class.getClassLoader(),
                new Class<?>[] { commandType },
                new Handler(commandName, mode, aliases, homes, guard, roles)
            );
            Method registerMethod = event.getClass().getMethod("registerServerCommand", commandType);
            registerMethod.invoke(event, command);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Не удалось зарегистрировать команду /" + commandName + ".", exception);
        }
    }

    private static final class Handler implements InvocationHandler {
        private final String commandName;
        private final Mode mode;
        private final List<String> aliases;
        private final HomeService homes;
        private final TeleportGuardService guard;
        private final PlayerRoleLookup roles;

        private Handler(
            String commandName,
            Mode mode,
            List<String> aliases,
            HomeService homes,
            TeleportGuardService guard,
            PlayerRoleLookup roles
        ) {
            this.commandName = commandName;
            this.mode = mode;
            this.aliases = aliases;
            this.homes = homes;
            this.guard = guard;
            this.roles = roles;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("getName".equals(name) || "func_71517_b".equals(name)) {
                return commandName;
            }
            if ("getUsage".equals(name) || "func_71518_a".equals(name)) {
                return usage(commandName, mode);
            }
            if ("getAliases".equals(name) || "func_71514_a".equals(name)) {
                return aliases;
            }
            if ("execute".equals(name) || "func_184881_a".equals(name)) {
                execute(commandName, mode, args[0], args[1], args[2], homes, guard, roles);
                return null;
            }
            if ("checkPermission".equals(name) || "func_184882_a".equals(name)) {
                return Boolean.TRUE;
            }
            if ("getTabCompletions".equals(name) || "func_184883_a".equals(name)) {
                return tabCompletions(mode, args == null || args.length < 2 ? null : args[1], args == null || args.length < 3 ? null : args[2], homes);
            }
            if ("isUsernameIndex".equals(name) || "func_82358_a".equals(name)) {
                return Boolean.FALSE;
            }
            if ("compareTo".equals(name)) {
                return Integer.valueOf(compareTo(commandName, args == null ? null : args[0]));
            }
            if ("toString".equals(name)) {
                return "/" + commandName;
            }
            if ("hashCode".equals(name)) {
                return Integer.valueOf(commandName.hashCode());
            }
            if ("equals".equals(name)) {
                return Boolean.valueOf(args != null && args.length > 0 && proxy == args[0]);
            }
            return defaultValue(method.getReturnType());
        }
    }

    private static void execute(
        String commandName,
        Mode mode,
        Object server,
        Object sender,
        Object arguments,
        HomeService homes,
        TeleportGuardService guard,
        PlayerRoleLookup roles
    ) {
        Object player = TeleportSupport.resolvePlayer(sender);
        if (player == null) {
            ServerChat.status(sender, ServerChat.Tone.ERROR, SUBJECT, "команду " + ServerChat.command("/" + commandName) + " может использовать только игрок.");
            return;
        }

        String[] args = arguments instanceof String[] ? (String[]) arguments : new String[0];
        if (args.length > 1) {
            ServerChat.usage(player, usage(commandName, mode));
            return;
        }

        try {
            String playerId = PlayerIdentity.id(player);
            String homeName = args.length == 0 ? "home" : HomeService.normalizeName(args[0]);
            RoleLimits limits = RoleLimits.forRole(roles.roleFor(player));
            if (mode == Mode.SET) {
                setHome(player, playerId, homeName, homes, guard, limits);
                return;
            }
            if (mode == Mode.TELEPORT) {
                teleportHome(server, player, playerId, homeName, homes, guard, limits);
                return;
            }
            if (mode == Mode.DELETE) {
                deleteHome(player, playerId, homeName, homes);
                return;
            }
            listHomes(player, playerId, homes);
        } catch (IllegalArgumentException exception) {
            ServerChat.status(player, ServerChat.Tone.WARNING, SUBJECT, exception.getMessage());
        } catch (RuntimeException exception) {
            ServerChat.status(player, ServerChat.Tone.ERROR, SUBJECT, "ошибка: " + exception.getMessage());
        }
    }

    private static void setHome(
        Object player,
        String playerId,
        String homeName,
        HomeService homes,
        TeleportGuardService guard,
        RoleLimits limits
    ) {
        int combatSeconds = guard.combatRemainingSeconds(player);
        if (combatSeconds > 0) {
            ServerChat.status(player, ServerChat.Tone.WARNING, SUBJECT, "нельзя ставить дом в бою. Подождите " + combatSeconds + " сек.");
            return;
        }

        HomeService.HomeLocation location = new HomeService.HomeLocation(
            TeleportSupport.playerDimension(player),
            TeleportSupport.playerX(player),
            TeleportSupport.playerY(player),
            TeleportSupport.playerZ(player),
            TeleportSupport.playerYaw(player),
            TeleportSupport.playerPitch(player)
        );
        HomeService.SetHomeResult result = homes.setHome(playerId, homeName, location, limits.maxHomes());
        if (!result.success) {
            ServerChat.status(player, ServerChat.Tone.WARNING, SUBJECT, "лимит домов для роли " + ServerChat.value(limits.role()) + ": " + ServerChat.value(RoleLimits.limitText(result.limit)) + ".");
            return;
        }
        ServerChat.status(
            player,
            ServerChat.Tone.SUCCESS,
            SUBJECT,
            (result.updated ? "обновлен " : "создан ") + ServerChat.value(homeName) + "."
        );
    }

    private static void teleportHome(
        Object server,
        Object player,
        String playerId,
        String homeName,
        HomeService homes,
        TeleportGuardService guard,
        RoleLimits limits
    ) {
        int combatSeconds = guard.combatRemainingSeconds(player);
        if (combatSeconds > 0) {
            ServerChat.status(player, ServerChat.Tone.WARNING, SUBJECT, "телепорт заблокирован боем. Подождите " + combatSeconds + " сек.");
            return;
        }

        int cooldownSeconds = guard.cooldownRemainingSeconds(playerId, TeleportGuardService.CHANNEL_HOME);
        if (cooldownSeconds > 0) {
            ServerChat.status(player, ServerChat.Tone.WARNING, SUBJECT, "перезарядка: осталось " + ServerChat.durationText(cooldownSeconds) + ".");
            return;
        }

        HomeService.HomeLocation location = homes.getHome(playerId, homeName);
        if (location == null) {
            ServerChat.status(player, ServerChat.Tone.WARNING, SUBJECT, "точка " + ServerChat.value(homeName) + " не найдена.");
            return;
        }

        Object moved = TeleportSupport.teleportToDimension(server, player, location.dimension, location.x, location.y, location.z, location.yaw, location.pitch);
        guard.startCooldown(playerId, TeleportGuardService.CHANNEL_HOME, limits.homeCooldownMillis());
        ServerChat.status(moved, ServerChat.Tone.SUCCESS, SUBJECT, "телепорт к " + ServerChat.value(homeName) + " выполнен.");
    }

    private static void deleteHome(Object player, String playerId, String homeName, HomeService homes) {
        if (!homes.deleteHome(playerId, homeName)) {
            ServerChat.status(player, ServerChat.Tone.WARNING, SUBJECT, "точка " + ServerChat.value(homeName) + " не найдена.");
            return;
        }
        ServerChat.status(player, ServerChat.Tone.SUCCESS, SUBJECT, "точка " + ServerChat.value(homeName) + " удалена.");
    }

    private static void listHomes(Object player, String playerId, HomeService homes) {
        List<String> names = homes.listHomes(playerId);
        if (names.isEmpty()) {
            ServerChat.status(player, ServerChat.Tone.WARNING, SUBJECT, "домов нет. Используйте " + ServerChat.command("/sethome") + ".");
            return;
        }
        ServerChat.status(player, SUBJECT, "доступные: " + ServerChat.value(join(names)) + ".");
    }

    private static List<String> tabCompletions(Mode mode, Object sender, Object arguments, HomeService homes) {
        if (mode != Mode.TELEPORT && mode != Mode.DELETE) {
            return Collections.emptyList();
        }
        Object player = TeleportSupport.resolvePlayer(sender);
        if (player == null) {
            return Collections.emptyList();
        }
        String[] args = arguments instanceof String[] ? (String[]) arguments : new String[0];
        if (args.length > 1) {
            return Collections.emptyList();
        }
        String prefix = args.length == 0 ? "" : args[0];
        return matching(homes.listHomes(PlayerIdentity.id(player)), prefix);
    }

    private static List<String> matching(List<String> values, String prefix) {
        String normalizedPrefix = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        ArrayList<String> result = new ArrayList<String>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix)) {
                result.add(value);
            }
        }
        return result;
    }

    private static String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(values.get(i));
        }
        return builder.toString();
    }

    private static String usage(String commandName, Mode mode) {
        if (mode == Mode.SET) {
            return "/" + commandName + " [название]";
        }
        if (mode == Mode.TELEPORT) {
            return "/" + commandName + " [название]";
        }
        if (mode == Mode.DELETE) {
            return "/" + commandName + " [название]";
        }
        return "/" + commandName;
    }

    private static int compareTo(String commandName, Object other) {
        if (other == null) {
            return 1;
        }
        Object otherName = invokeZeroArgIfPresent(other, "getName", "func_71517_b");
        return otherName == null ? 1 : commandName.compareTo(otherName.toString());
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

    private enum Mode {
        SET,
        TELEPORT,
        DELETE,
        LIST
    }
}
