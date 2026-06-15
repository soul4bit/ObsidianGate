package ru.mcrpg.forgeauth.server;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

final class RegionCommand {

    private static final String SUBJECT = "Приват";

    private RegionCommand() {
    }

    static void register(FMLServerStartingEvent event, RegionProtectionService regions) {
        register(event, "/wand", Collections.singletonList("wand"), new WandHandler());
        register(event, "rg", Arrays.asList("region", "регион"), new RegionHandler(regions));
    }

    private static void register(
        FMLServerStartingEvent event,
        String name,
        List<String> aliases,
        CommandExecutor executor
    ) {
        try {
            Class<?> commandType = Class.forName("net.minecraft.command.ICommand");
            Object command = Proxy.newProxyInstance(
                RegionCommand.class.getClassLoader(),
                new Class<?>[] { commandType },
                new Handler(name, aliases, executor)
            );
            Method registerMethod = event.getClass().getMethod("registerServerCommand", commandType);
            registerMethod.invoke(event, command);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Не удалось зарегистрировать команду " + name + ".", exception);
        }
    }

    private interface CommandExecutor {
        void execute(Object server, Object sender, String[] args);
        String usage();
    }

    private static final class Handler implements InvocationHandler {
        private final String name;
        private final List<String> aliases;
        private final CommandExecutor executor;

        private Handler(String name, List<String> aliases, CommandExecutor executor) {
            this.name = name;
            this.aliases = aliases;
            this.executor = executor;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String methodName = method.getName();
            if ("getName".equals(methodName) || "func_71517_b".equals(methodName)) {
                return name;
            }
            if ("getUsage".equals(methodName) || "func_71518_a".equals(methodName)) {
                return executor.usage();
            }
            if ("getAliases".equals(methodName) || "func_71514_a".equals(methodName)) {
                return aliases;
            }
            if ("execute".equals(methodName) || "func_184881_a".equals(methodName)) {
                executor.execute(args[0], args[1], args[2] instanceof String[] ? (String[]) args[2] : new String[0]);
                return null;
            }
            if ("checkPermission".equals(methodName) || "func_184882_a".equals(methodName)) {
                return Boolean.TRUE;
            }
            if ("getTabCompletions".equals(methodName) || "func_184883_a".equals(methodName)) {
                return Collections.emptyList();
            }
            if ("isUsernameIndex".equals(methodName) || "func_82358_a".equals(methodName)) {
                return Boolean.FALSE;
            }
            if ("compareTo".equals(methodName)) {
                return Integer.valueOf(name.compareTo(String.valueOf(args[0])));
            }
            if ("toString".equals(methodName)) {
                return name;
            }
            if ("hashCode".equals(methodName)) {
                return Integer.valueOf(name.hashCode());
            }
            if ("equals".equals(methodName)) {
                return Boolean.valueOf(args != null && args.length > 0 && proxy == args[0]);
            }
            return defaultValue(method.getReturnType());
        }
    }

    private static final class WandHandler implements CommandExecutor {
        @Override
        public void execute(Object server, Object sender, String[] args) {
            Object player = player(sender);
            if (player == null) {
                return;
            }
            Object manager = ServerReflection.invoke(server, new String[] { "getCommandManager", "func_71187_D" });
            ServerReflection.invoke(
                manager,
                new String[] { "executeCommand", "func_71556_a" },
                sender,
                "give " + PlayerIdentity.name(player) + " minecraft:wooden_axe 1"
            );
            ServerChat.status(player, ServerChat.Tone.SUCCESS, SUBJECT, "деревянный топор выдан. ЛКМ — точка 1, ПКМ — точка 2.");
        }

        @Override
        public String usage() {
            return "//wand";
        }
    }

    private static final class RegionHandler implements CommandExecutor {
        private final RegionProtectionService regions;

        private RegionHandler(RegionProtectionService regions) {
            this.regions = regions;
        }

        @Override
        public void execute(Object server, Object sender, String[] args) {
            Object player = player(sender);
            if (player == null) {
                return;
            }
            String action = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
            try {
                if ("claim".equals(action) && args.length == 2) {
                    claim(player, args[1]);
                } else if (("addmember".equals(action) || "add".equals(action)) && args.length == 3) {
                    boolean changed = regions.addMember(args[1], PlayerIdentity.id(player), args[2], isOperator(player));
                    result(player, changed, "Игрок добавлен.", "Игрок уже является участником.");
                } else if (("removemember".equals(action) || "remove".equals(action)) && args.length == 3) {
                    boolean changed = regions.removeMember(args[1], PlayerIdentity.id(player), args[2], isOperator(player));
                    result(player, changed, "Игрок удален из участников.", "Игрок не был участником.");
                } else if (("delete".equals(action) || "remove".equals(action)) && args.length == 2) {
                    regions.delete(args[1], PlayerIdentity.id(player), isOperator(player));
                    ServerChat.status(player, ServerChat.Tone.SUCCESS, SUBJECT, "регион удален.");
                } else if ("list".equals(action)) {
                    list(player);
                } else if ("info".equals(action)) {
                    info(player, args.length > 1 ? args[1] : null);
                } else {
                    RegionCommand.usage(player);
                }
            } catch (IllegalArgumentException exception) {
                ServerChat.status(player, ServerChat.Tone.WARNING, SUBJECT, exception.getMessage());
            } catch (RuntimeException exception) {
                ServerChat.status(player, ServerChat.Tone.ERROR, SUBJECT, "ошибка: " + exception.getMessage());
            }
        }

        private void claim(Object player, String name) {
            RegionProtectionService.ClaimResult result = regions.claim(
                name,
                PlayerIdentity.id(player),
                PlayerIdentity.name(player)
            );
            if (!result.success) {
                ServerChat.status(player, ServerChat.Tone.WARNING, SUBJECT, result.message);
                return;
            }
            ServerChat.status(
                player,
                ServerChat.Tone.SUCCESS,
                SUBJECT,
                "регион " + ServerChat.value(result.region.name) + " создан, площадь "
                    + ServerChat.value(result.region.horizontalArea()) + "."
            );
        }

        private void list(Object player) {
            List<RegionProtectionService.Region> owned = regions.ownedRegions(PlayerIdentity.id(player));
            if (owned.isEmpty()) {
                ServerChat.status(player, ServerChat.Tone.INFO, SUBJECT, "у вас пока нет регионов.");
                return;
            }
            StringBuilder names = new StringBuilder();
            for (RegionProtectionService.Region region : owned) {
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(region.name);
            }
            ServerChat.status(player, ServerChat.Tone.INFO, SUBJECT, "ваши регионы: " + ServerChat.value(names) + ".");
        }

        private void info(Object player, String name) {
            RegionProtectionService.Region region = name == null
                ? regions.regionAt(
                    TeleportSupport.playerDimension(player),
                    (int) TeleportSupport.playerX(player),
                    (int) TeleportSupport.playerY(player),
                    (int) TeleportSupport.playerZ(player)
                )
                : regions.region(name);
            if (region == null) {
                ServerChat.status(player, ServerChat.Tone.INFO, SUBJECT, "в этой точке региона нет.");
                return;
            }
            ServerChat.status(
                player,
                ServerChat.Tone.INFO,
                SUBJECT,
                ServerChat.value(region.name) + ", владелец " + ServerChat.value(region.ownerName)
                    + ", участники " + ServerChat.value(region.members.isEmpty() ? "нет" : region.members)
                    + ", границы X " + region.minX + ".." + region.maxX + ", Z " + region.minZ + ".." + region.maxZ + "."
            );
        }

        @Override
        public String usage() {
            return "/rg <claim|info|list|addmember|removemember|delete>";
        }
    }

    private static Object player(Object sender) {
        Object resolved = TeleportSupport.resolvePlayer(sender);
        if (resolved != null) {
            return resolved;
        }
        ServerChat.status(sender, ServerChat.Tone.ERROR, SUBJECT, "команда доступна только игроку.");
        return null;
    }

    private static boolean isOperator(Object player) {
        return ServerReflection.bool(ServerReflection.invoke(
            player,
            new String[] { "canUseCommand", "func_70003_b" },
            Integer.valueOf(2),
            "rg"
        ));
    }

    private static void result(Object player, boolean success, String yes, String no) {
        ServerChat.status(player, success ? ServerChat.Tone.SUCCESS : ServerChat.Tone.INFO, SUBJECT, success ? yes : no);
    }

    private static void usage(Object player) {
        ServerChat.usage(player, "/rg claim <название>");
        ServerChat.usage(player, "/rg info [название]");
        ServerChat.usage(player, "/rg list");
        ServerChat.usage(player, "/rg addmember <регион> <игрок>");
        ServerChat.usage(player, "/rg removemember <регион> <игрок>");
        ServerChat.usage(player, "/rg delete <регион>");
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
}
