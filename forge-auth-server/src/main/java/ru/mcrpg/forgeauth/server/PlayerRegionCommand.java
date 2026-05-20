package ru.mcrpg.forgeauth.server;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

final class PlayerRegionCommand {

    private static final String COMMAND_NAME = "claim";
    private static final String SUBJECT = "Приват";
    private static final List<String> ALIASES = Collections.unmodifiableList(Arrays.asList("region", "rg"));

    private PlayerRegionCommand() {
    }

    static void register(FMLServerStartingEvent event, PlayerRegionService service, PlayerRoleLookup roles) {
        try {
            Class<?> commandType = Class.forName("net.minecraft.command.ICommand");
            Object command = Proxy.newProxyInstance(
                PlayerRegionCommand.class.getClassLoader(),
                new Class<?>[] { commandType },
                new Handler(service, roles)
            );
            Method registerMethod = event.getClass().getMethod("registerServerCommand", commandType);
            registerMethod.invoke(event, command);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Не удалось зарегистрировать команду /" + COMMAND_NAME + ".", exception);
        }
    }

    private static final class Handler implements InvocationHandler {
        private final PlayerRegionService service;
        private final PlayerRoleLookup roles;

        private Handler(PlayerRegionService service, PlayerRoleLookup roles) {
            this.service = service;
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
                execute(args[1], args[2], service, roles);
                return null;
            }
            if ("checkPermission".equals(name) || "func_184882_a".equals(name)) {
                return Boolean.TRUE;
            }
            if ("getTabCompletions".equals(name) || "func_184883_a".equals(name)) {
                return tabCompletions(args == null || args.length < 2 ? null : args[1], args == null || args.length < 3 ? null : args[2], service);
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

    private static void execute(Object sender, Object arguments, PlayerRegionService service, PlayerRoleLookup roles) {
        Object player = TeleportSupport.resolvePlayer(sender);
        if (player == null) {
            ServerChat.status(sender, ServerChat.Tone.ERROR, SUBJECT, "команду " + ServerChat.command("/" + COMMAND_NAME) + " может использовать только игрок.");
            return;
        }

        String[] args = arguments instanceof String[] ? (String[]) arguments : new String[0];
        if (args.length == 0) {
            ServerChat.usage(player, usage());
            return;
        }

        String action = args[0].trim().toLowerCase(Locale.ROOT);
        try {
            if ("create".equals(action) || "here".equals(action)) {
                create(player, args, service, RoleLimits.forRole(roles.roleFor(player)));
                return;
            }
            if ("remove".equals(action) || "delete".equals(action) || "del".equals(action)) {
                remove(player, args, service);
                return;
            }
            if ("list".equals(action)) {
                list(player, service);
                return;
            }
            if ("info".equals(action)) {
                info(player, args, service);
                return;
            }
            if ("trust".equals(action)) {
                trust(player, args, service, true);
                return;
            }
            if ("untrust".equals(action)) {
                trust(player, args, service, false);
                return;
            }
            ServerChat.usage(player, usage());
        } catch (IllegalArgumentException exception) {
            ServerChat.status(player, ServerChat.Tone.WARNING, SUBJECT, exception.getMessage());
        } catch (RuntimeException exception) {
            ServerChat.status(player, ServerChat.Tone.ERROR, SUBJECT, "ошибка: " + exception.getMessage());
        }
    }

    private static void create(Object player, String[] args, PlayerRegionService service, RoleLimits limits) {
        if (args.length != 3) {
            ServerChat.usage(player, "/" + COMMAND_NAME + " create <название> <радиус>");
            return;
        }
        int radius = parseInt(args[2], "радиус");
        PlayerRegionService.CreateResult result = service.createAround(
            PlayerIdentity.id(player),
            PlayerIdentity.name(player),
            args[1],
            TeleportSupport.playerDimension(player),
            TeleportSupport.playerX(player),
            TeleportSupport.playerZ(player),
            radius,
            limits.maxRegions()
        );
        if (!result.success) {
            if (result.overlap != null) {
                ServerChat.status(player, ServerChat.Tone.WARNING, SUBJECT, "пересекается с регионом " + regionName(result.overlap) + ".");
                return;
            }
            ServerChat.status(player, ServerChat.Tone.WARNING, SUBJECT, "лимит регионов для роли " + ServerChat.value(limits.role()) + ": " + ServerChat.value(RoleLimits.limitText(result.limit)) + ".");
            return;
        }
        ServerChat.status(
            player,
            ServerChat.Tone.SUCCESS,
            SUBJECT,
            (result.updated ? "обновлен " : "создан ") + regionName(result.region) + ", " + result.region.describeBounds() + "."
        );
    }

    private static void remove(Object player, String[] args, PlayerRegionService service) {
        if (args.length != 2) {
            ServerChat.usage(player, "/" + COMMAND_NAME + " remove <название>");
            return;
        }
        String name = PlayerRegionService.normalizeName(args[1]);
        if (!service.remove(PlayerIdentity.id(player), name)) {
            ServerChat.status(player, ServerChat.Tone.WARNING, SUBJECT, "регион " + ServerChat.value(name) + " не найден.");
            return;
        }
        ServerChat.status(player, ServerChat.Tone.SUCCESS, SUBJECT, "регион " + ServerChat.value(name) + " удален.");
    }

    private static void list(Object player, PlayerRegionService service) {
        List<PlayerRegionService.Region> regions = service.list(PlayerIdentity.id(player));
        if (regions.isEmpty()) {
            ServerChat.status(player, ServerChat.Tone.WARNING, SUBJECT, "регионов нет. Используйте " + ServerChat.command("/claim create base 32") + ".");
            return;
        }
        ArrayList<String> names = new ArrayList<String>();
        for (PlayerRegionService.Region region : regions) {
            names.add(region.name);
        }
        ServerChat.status(player, SUBJECT, "ваши регионы: " + ServerChat.value(join(names)) + ".");
    }

    private static void info(Object player, String[] args, PlayerRegionService service) {
        if (args.length > 2) {
            ServerChat.usage(player, "/" + COMMAND_NAME + " info [название]");
            return;
        }
        PlayerRegionService.Region region;
        if (args.length == 2) {
            region = service.get(PlayerIdentity.id(player), PlayerRegionService.normalizeName(args[1]));
        } else {
            region = service.findAt(
                TeleportSupport.playerDimension(player),
                TeleportSupport.playerX(player),
                TeleportSupport.playerY(player),
                TeleportSupport.playerZ(player)
            );
        }
        if (region == null) {
            ServerChat.status(player, ServerChat.Tone.WARNING, SUBJECT, "регион не найден.");
            return;
        }
        ServerChat.status(
            player,
            SUBJECT,
            regionName(region) + ", владелец " + ServerChat.value(region.ownerName) + ", " + region.describeBounds() +
                ", trusted " + ServerChat.value(region.trustedNames.isEmpty() ? "-" : join(new ArrayList<String>(region.trustedNames))) + "."
        );
    }

    private static void trust(Object player, String[] args, PlayerRegionService service, boolean add) {
        if (args.length != 3) {
            ServerChat.usage(player, "/" + COMMAND_NAME + (add ? " trust" : " untrust") + " <регион> <игрок>");
            return;
        }
        PlayerRegionService.TrustResult result = add
            ? service.trust(PlayerIdentity.id(player), args[1], args[2])
            : service.untrust(PlayerIdentity.id(player), args[1], args[2]);
        if (result.notFound) {
            ServerChat.status(player, ServerChat.Tone.WARNING, SUBJECT, "регион " + ServerChat.value(args[1]) + " не найден.");
            return;
        }
        if (result.owner) {
            ServerChat.status(player, ServerChat.Tone.WARNING, SUBJECT, "владелец уже имеет доступ.");
            return;
        }
        ServerChat.status(
            player,
            ServerChat.Tone.SUCCESS,
            SUBJECT,
            (add ? "доступ выдан " : "доступ удален ") + ServerChat.value(args[2]) + " для " + regionName(result.region) + "."
        );
    }

    private static List<String> tabCompletions(Object sender, Object arguments, PlayerRegionService service) {
        Object player = TeleportSupport.resolvePlayer(sender);
        String[] args = arguments instanceof String[] ? (String[]) arguments : new String[0];
        if (args.length <= 1) {
            return matching(Arrays.asList("create", "remove", "list", "info", "trust", "untrust"), args.length == 0 ? "" : args[0]);
        }
        if (player == null) {
            return Collections.emptyList();
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && ("remove".equals(action) || "info".equals(action) || "trust".equals(action) || "untrust".equals(action))) {
            ArrayList<String> names = new ArrayList<String>();
            for (PlayerRegionService.Region region : service.list(PlayerIdentity.id(player))) {
                names.add(region.name);
            }
            return matching(names, args[1]);
        }
        return Collections.emptyList();
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

    private static int parseInt(String value, String name) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " должен быть числом.");
        }
    }

    private static String regionName(PlayerRegionService.Region region) {
        return ServerChat.value(region.name);
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

    private static String usage() {
        return "/" + COMMAND_NAME + " create <название> <радиус> | /" + COMMAND_NAME + " remove <название> | /" +
            COMMAND_NAME + " list | /" + COMMAND_NAME + " info [название] | /" +
            COMMAND_NAME + " trust <регион> <игрок> | /" + COMMAND_NAME + " untrust <регион> <игрок>";
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
