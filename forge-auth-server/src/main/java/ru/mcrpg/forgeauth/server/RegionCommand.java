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

    static void register(FMLServerStartingEvent event, RegionProtectionService regions, RegionAuditService audit, PlayerRoleLookup roles) {
        register(event, "/wand", Collections.singletonList("wand"), new WandHandler());
        register(event, "/expand", Arrays.asList("expand", "/exp", "exp"), new ExpandHandler(regions));
        register(event, "rg", Arrays.asList("region", "регион"), new RegionHandler(regions, audit, roles));
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
                "give " + PlayerIdentity.name(player)
                    + " minecraft:wooden_axe 1 0 {ObsidianGateRegionWand:1b,display:{Name:\"§6Топор привата\",Lore:[\"§7Исчезает при выбрасывании\"]}}"
            );
            ServerChat.status(player, ServerChat.Tone.SUCCESS, SUBJECT, "деревянный топор выдан. ЛКМ — точка 1, ПКМ — точка 2.");
        }

        @Override
        public String usage() {
            return "//wand";
        }
    }

    private static final class ExpandHandler implements CommandExecutor {
        private final RegionProtectionService regions;

        private ExpandHandler(RegionProtectionService regions) {
            this.regions = regions;
        }

        @Override
        public void execute(Object server, Object sender, String[] args) {
            Object player = player(sender);
            if (player == null) {
                return;
            }
            if (args.length < 2) {
                ServerChat.usage(player, "//expand <блоки> <up|down|north|south|east|west> [...]");
                return;
            }
            try {
                int amount = Integer.parseInt(args[0]);
                String[] directions = Arrays.copyOfRange(args, 1, args.length);
                RegionProtectionService.Selection selection = regions.expandSelection(
                    PlayerIdentity.id(player),
                    amount,
                    directions
                );
                showSelection(player, selection);
                ServerChat.status(
                    player,
                    ServerChat.Tone.SUCCESS,
                    SUBJECT,
                    "выделение расширено на " + amount + ": " + selectionSummary(selection) + "."
                );
            } catch (NumberFormatException exception) {
                ServerChat.status(player, ServerChat.Tone.WARNING, SUBJECT, "Укажите целое количество блоков.");
            } catch (IllegalArgumentException exception) {
                ServerChat.status(player, ServerChat.Tone.WARNING, SUBJECT, exception.getMessage());
            }
        }

        @Override
        public String usage() {
            return "//expand <блоки> <направление> [...]";
        }
    }

    private static final class RegionHandler implements CommandExecutor {
        private final RegionProtectionService regions;
        private final RegionAuditService audit;
        private final PlayerRoleLookup roles;

        private RegionHandler(RegionProtectionService regions, RegionAuditService audit, PlayerRoleLookup roles) {
            this.regions = regions;
            this.audit = audit;
            this.roles = roles;
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
                } else if ("redefine".equals(action) && args.length == 2) {
                    RegionProtectionService.Region region = regions.redefine(args[1], PlayerIdentity.id(player), isOperator(player));
                    ServerChat.status(player, ServerChat.Tone.SUCCESS, SUBJECT, "Границы региона " + ServerChat.value(region.name) + " изменены.");
                } else if ("transfer".equals(action) && args.length == 3) {
                    transfer(server, player, args[1], args[2]);
                } else if ("setpriority".equals(action) && args.length == 3 && isOperator(player)) {
                    RegionProtectionService.Region region = regions.setPriority(args[1], Integer.parseInt(args[2]));
                    ServerChat.status(player, ServerChat.Tone.SUCCESS, SUBJECT, "приоритет региона: " + region.priority + ".");
                } else if ("rollback".equals(action) && args.length >= 2 && args.length <= 4) {
                    rollback(server, player, args);
                } else if ("flag".equals(action) && args.length == 4) {
                    boolean allowed = parseState(args[3]);
                    regions.setFlag(args[1], PlayerIdentity.id(player), args[2], allowed, isOperator(player));
                    ServerChat.status(player, ServerChat.Tone.SUCCESS, SUBJECT, "флаг " + args[2] + " = " + (allowed ? "allow" : "deny") + ".");
                } else if ("show".equals(action) && args.length <= 2) {
                    show(server, player, args.length == 2 ? args[1] : null);
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
                } else if ("admin".equals(action) && args.length >= 3 && isOperator(player)) {
                    admin(player, args);
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
                PlayerIdentity.name(player),
                RoleLimits.forRole(roles.roleFor(player)).maxRegions(),
                isOperator(player)
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

        private void transfer(Object server, Object player, String regionName, String targetName) {
            Object target = TeleportSupport.findOnlinePlayer(server, targetName);
            if (target == null) {
                throw new IllegalArgumentException("Новый владелец должен быть онлайн.");
            }
            RegionProtectionService.Region region = regions.transfer(
                regionName,
                PlayerIdentity.id(player),
                PlayerIdentity.id(target),
                PlayerIdentity.name(target),
                RoleLimits.forRole(roles.roleFor(target)).maxRegions(),
                isOperator(player)
            );
            ServerChat.status(player, ServerChat.Tone.SUCCESS, SUBJECT, "регион передан игроку " + ServerChat.value(region.ownerName) + ".");
            ServerChat.status(target, ServerChat.Tone.SUCCESS, SUBJECT, "вам передан регион " + ServerChat.value(region.name) + ".");
        }

        private void rollback(Object server, Object player, String[] args) {
            RegionProtectionService.Region region = regions.region(args[1]);
            if (region == null) {
                throw new IllegalArgumentException("Регион не найден.");
            }
            if (!isOperator(player) && !region.ownerId.equals(PlayerIdentity.id(player))) {
                throw new IllegalArgumentException("Откат доступен только владельцу региона.");
            }
            String playerFilter = null;
            int limit = 100;
            if (args.length >= 3) {
                if (args[2].matches("\\d+")) {
                    limit = Integer.parseInt(args[2]);
                } else {
                    playerFilter = args[2];
                }
            }
            if (args.length == 4) {
                limit = Integer.parseInt(args[3]);
            }
            int restored = audit.rollback(server, player, region, playerFilter, limit);
            ServerChat.status(player, ServerChat.Tone.SUCCESS, SUBJECT, "восстановлено изменений: " + ServerChat.value(restored) + ".");
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
                    + ", флаги " + ServerChat.value(flagSummary(region))
                    + ", приоритет " + region.priority
                    + ", границы X " + region.minX + ".." + region.maxX
                    + ", Y " + region.minY + ".." + region.maxY
                    + ", Z " + region.minZ + ".." + region.maxZ + "."
            );
        }

        private void show(Object server, Object player, String name) {
            RegionProtectionService.Region region = name == null
                ? regions.regionAt(
                    TeleportSupport.playerDimension(player),
                    (int) TeleportSupport.playerX(player),
                    (int) TeleportSupport.playerY(player),
                    (int) TeleportSupport.playerZ(player)
                )
                : regions.region(name);
            if (region == null) {
                throw new IllegalArgumentException("Регион не найден.");
            }
            int y = Math.max(1, Math.min(254, (int) Math.floor(TeleportSupport.playerY(player))));
            Object manager = ServerReflection.invoke(server, new String[] { "getCommandManager", "func_71187_D" });
            Object sender = silentCommandSender(player);
            int step = Math.max(1, Math.max(region.maxX - region.minX, region.maxZ - region.minZ) / 32);
            for (int x = region.minX; x <= region.maxX; x += step) {
                particle(manager, sender, player, x, y, region.minZ);
                particle(manager, sender, player, x, y, region.maxZ);
            }
            for (int z = region.minZ; z <= region.maxZ; z += step) {
                particle(manager, sender, player, region.minX, y, z);
                particle(manager, sender, player, region.maxX, y, z);
            }
            ServerChat.status(player, ServerChat.Tone.SUCCESS, SUBJECT, "границы " + ServerChat.value(region.name) + " показаны частицами.");
        }

        private void admin(Object player, String[] args) {
            String adminAction = args[1].toLowerCase(Locale.ROOT);
            if ("find".equals(adminAction)) {
                List<RegionProtectionService.Region> found = regions.find(args[2]);
                ServerChat.status(player, ServerChat.Tone.INFO, SUBJECT, "найдено: " + ServerChat.value(regionNames(found)) + ".");
                return;
            }
            if ("delete".equals(adminAction)) {
                regions.delete(args[2], PlayerIdentity.id(player), true);
                ServerChat.status(player, ServerChat.Tone.SUCCESS, SUBJECT, "регион удален в архив.");
                return;
            }
            if ("restore".equals(adminAction)) {
                regions.restore(args[2]);
                ServerChat.status(player, ServerChat.Tone.SUCCESS, SUBJECT, "регион восстановлен.");
                return;
            }
            throw new IllegalArgumentException("Админ-команды: find, delete, restore.");
        }

        @Override
        public String usage() {
            return "/rg <claim|redefine|transfer|setpriority|rollback|flag|show|info|list|addmember|removemember|delete|admin>";
        }
    }

    private static boolean parseState(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if ("allow".equals(normalized) || "true".equals(normalized) || "on".equals(normalized)) {
            return true;
        }
        if ("deny".equals(normalized) || "false".equals(normalized) || "off".equals(normalized)) {
            return false;
        }
        throw new IllegalArgumentException("Значение флага: allow или deny.");
    }

    private static String flagSummary(RegionProtectionService.Region region) {
        StringBuilder result = new StringBuilder();
        for (RegionProtectionService.RegionFlag flag : RegionProtectionService.RegionFlag.values()) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(flag.id).append('=').append(region.flag(flag) ? "allow" : "deny");
        }
        return result.toString();
    }

    private static String regionNames(List<RegionProtectionService.Region> regions) {
        if (regions.isEmpty()) {
            return "нет";
        }
        StringBuilder result = new StringBuilder();
        for (RegionProtectionService.Region region : regions) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(region.name).append('(').append(region.ownerName).append(')');
        }
        return result.toString();
    }

    private static void particle(Object manager, Object sender, Object player, int x, int y, int z) {
        ServerReflection.invoke(
            manager,
            new String[] { "executeCommand", "func_71556_a" },
            sender,
            "particle reddust " + x + " " + y + " " + z + " 0 0 0 0 1 force " + PlayerIdentity.name(player)
        );
    }

    static void showSelection(Object player, RegionProtectionService.Selection selection) {
        if (player == null) {
            return;
        }
        if (selection == null || selection.first == null) {
            hideSelection(player);
            return;
        }
        if (ForgeAuthServerMod.networkChannel() == null) {
            return;
        }
        sendToPlayer(player, RegionSelectionMessage.visible(selection.first, selection.second));
    }

    static void hideSelection(Object player) {
        if (player != null && ForgeAuthServerMod.networkChannel() != null) {
            sendToPlayer(player, RegionSelectionMessage.hidden());
        }
    }

    static void showRegionHud(Object player, String regionName) {
        if (player != null && ForgeAuthServerMod.networkChannel() != null) {
            sendToPlayer(player, RegionHudMessage.show(regionName));
        }
    }

    static void hideRegionHud(Object player) {
        if (player != null && ForgeAuthServerMod.networkChannel() != null) {
            sendToPlayer(player, RegionHudMessage.hidden());
        }
    }

    private static void sendToPlayer(Object player, Object message) {
        Object channel = ForgeAuthServerMod.networkChannel();
        for (Method method : channel.getClass().getMethods()) {
            if ("sendTo".equals(method.getName()) && method.getParameterTypes().length == 2) {
                try {
                    method.invoke(channel, message, player);
                    return;
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Unable to send region selection to player.", exception);
                }
            }
        }
        throw new IllegalStateException("Forge network channel does not expose sendTo.");
    }

    private static Object silentCommandSender(final Object player) {
        try {
            Class<?> senderType = Class.forName("net.minecraft.command.ICommandSender");
            return Proxy.newProxyInstance(
                senderType.getClassLoader(),
                new Class<?>[] { senderType },
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("sendMessage".equals(name) || "func_145747_a".equals(name)) {
                        return null;
                    }
                    if ("sendCommandFeedback".equals(name) || "func_174792_t".equals(name)) {
                        return Boolean.FALSE;
                    }
                    if ("equals".equals(name)) {
                        return Boolean.valueOf(args != null && args.length > 0 && proxy == args[0]);
                    }
                    if ("hashCode".equals(name)) {
                        return Integer.valueOf(System.identityHashCode(proxy));
                    }
                    if ("toString".equals(name)) {
                        return "ObsidianGateSelectionSender";
                    }
                    try {
                        method.setAccessible(true);
                        return method.invoke(player, args == null ? new Object[0] : args);
                    } catch (ReflectiveOperationException exception) {
                        return defaultValue(method.getReturnType());
                    }
                }
            );
        } catch (ClassNotFoundException | LinkageError exception) {
            return player;
        }
    }

    private static String selectionSummary(RegionProtectionService.Selection selection) {
        int minX = Math.min(selection.first.x, selection.second.x);
        int minY = Math.min(selection.first.y, selection.second.y);
        int minZ = Math.min(selection.first.z, selection.second.z);
        int maxX = Math.max(selection.first.x, selection.second.x);
        int maxY = Math.max(selection.first.y, selection.second.y);
        int maxZ = Math.max(selection.first.z, selection.second.z);
        return "X " + minX + ".." + maxX + ", Y " + minY + ".." + maxY + ", Z " + minZ + ".." + maxZ;
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
        ServerChat.usage(player, "//expand <блоки> <направление> [...]");
        ServerChat.usage(player, "/rg redefine <регион>");
        ServerChat.usage(player, "/rg transfer <регион> <игрок>");
        ServerChat.usage(player, "/rg setpriority <регион> <число>");
        ServerChat.usage(player, "/rg rollback <регион> [игрок] [количество]");
        ServerChat.usage(player, "/rg info [название]");
        ServerChat.usage(player, "/rg show [название]");
        ServerChat.usage(player, "/rg flag <регион> <флаг> <allow|deny>");
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
