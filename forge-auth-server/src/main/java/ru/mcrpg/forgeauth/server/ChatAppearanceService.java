package ru.mcrpg.forgeauth.server;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

final class ChatAppearanceService {

    private static final String RESET = "\u00A7r";
    private static final String DARK_GRAY = "\u00A78";
    private static final String WHITE = "\u00A7f";
    private static final String GRAY = "\u00A77";
    private static final String BRAND = DARK_GRAY + "[" + "\u00A75OG" + DARK_GRAY + "]" + RESET;
    private static final String TEAM_PREFIX = "og";
    private static final int TEAM_NAME_LIMIT = 16;

    private final Logger logger;
    private final Map<String, ? extends ForgeAuthServerLifecycle.PlayerView> players;
    private final TextComponentFactory textComponentFactory;

    ChatAppearanceService(Logger logger, Map<String, ? extends ForgeAuthServerLifecycle.PlayerView> players) {
        this(logger, players, new ReflectiveTextComponentFactory());
    }

    ChatAppearanceService(
        Logger logger,
        Map<String, ? extends ForgeAuthServerLifecycle.PlayerView> players,
        TextComponentFactory textComponentFactory
    ) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.players = Objects.requireNonNull(players, "players");
        this.textComponentFactory = Objects.requireNonNull(textComponentFactory, "textComponentFactory");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerChat(ServerChatEvent event) {
        try {
            Object player = readPlayer(event);
            String username = MinecraftPlayerBridge.extractUsernameStatic(player);
            String message = sanitizeChatMessage(readMessage(event));
            if (username.isEmpty() || message.isEmpty()) {
                return;
            }

            RoleStyle style = styleFor(roleFor(username));
            setComponent(event, textComponentFactory.create(formatChatLine(style, username, message)));
        } catch (RuntimeException exception) {
            logger.log(Level.FINE, "Не удалось применить оформление чата.", exception);
        }
    }

    void applyPlayerAppearance(Object player, String role) {
        try {
            String username = MinecraftPlayerBridge.extractUsernameStatic(player);
            if (username.isEmpty()) {
                return;
            }

            RoleStyle style = styleFor(role);
            String tabName = formatTabName(style, username);
            Object tabComponent = textComponentFactory.create(tabName);

            applyScoreboardTeam(player, username, style);
            if (!invokeAnyMethodIfPresent(player, new Object[] { tabComponent }, "setTabListDisplayName", "func_175396_E")) {
                writeFirstAssignableField(player, tabComponent, "tabListDisplayName", "listName");
            }
        } catch (RuntimeException exception) {
            logger.log(Level.FINE, "Не удалось применить оформление TAB.", exception);
        }
    }

    static String formatChatLine(String role, String username, String message) {
        return formatChatLine(styleFor(role), username, sanitizeChatMessage(message));
    }

    static String formatTabName(String role, String username) {
        return formatTabName(styleFor(role), username);
    }

    private String roleFor(String username) {
        ForgeAuthServerLifecycle.PlayerView view = players.get(normalizeKey(username));
        return view == null ? "" : view.getRole();
    }

    private static String formatChatLine(RoleStyle style, String username, String message) {
        return BRAND
            + " "
            + DARK_GRAY + "[" + style.roleColor + style.label + DARK_GRAY + "]"
            + " "
            + style.nameColor + username
            + " " + DARK_GRAY + "\u00BB" + " "
            + WHITE + message;
    }

    private static String formatTabName(RoleStyle style, String username) {
        return DARK_GRAY + "[" + style.roleColor + style.shortLabel + DARK_GRAY + "] " + style.nameColor + username;
    }

    private static String sanitizeChatMessage(String message) {
        String normalized = message == null ? "" : message.trim();
        return normalized.replaceAll("\u00A7.", "");
    }

    private static RoleStyle styleFor(String role) {
        String normalized = role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
        if ("admin".equals(normalized) || "administrator".equals(normalized) || "owner".equals(normalized)) {
            return new RoleStyle("00admin", "Админ", "Админ", "\u00A74", "\u00A7c");
        }
        if ("moderator".equals(normalized) || "moder".equals(normalized) || "mod".equals(normalized)) {
            return new RoleStyle("10moder", "Модер", "Модер", "\u00A79", "\u00A7b");
        }
        if ("vip".equals(normalized) || "premium".equals(normalized)) {
            return new RoleStyle("20vip", "VIP", "VIP", "\u00A76", "\u00A7e");
        }
        return new RoleStyle("90player", "Игрок", "Игрок", "\u00A77", GRAY);
    }

    private void applyScoreboardTeam(Object player, String username, RoleStyle style) {
        Object world = readFieldIfPresent(player, "world", "field_70170_p");
        Object scoreboard = invokeZeroArgIfPresent(world, "getScoreboard", "func_96441_U");
        if (scoreboard == null) {
            return;
        }

        String teamName = teamName(style);
        Object team = invokeMethodIfPresent(scoreboard, new Object[] { teamName }, "getTeam", "func_96508_e");
        if (team == null) {
            team = invokeMethodIfPresent(scoreboard, new Object[] { teamName }, "createTeam", "func_96527_f");
        }
        if (team == null) {
            return;
        }

        invokeAnyMethodIfPresent(team, new Object[] { DARK_GRAY + "[" + style.roleColor + style.shortLabel + DARK_GRAY + "] " + style.nameColor }, "setPrefix", "func_96666_b");
        invokeAnyMethodIfPresent(team, new Object[] { RESET }, "setSuffix", "func_96662_c");
        invokeAnyMethodIfPresent(scoreboard, new Object[] { username, teamName }, "addPlayerToTeam", "func_151392_a");
    }

    private static String teamName(RoleStyle style) {
        String name = TEAM_PREFIX + style.teamCode;
        return name.length() <= TEAM_NAME_LIMIT ? name : name.substring(0, TEAM_NAME_LIMIT);
    }

    private static Object readPlayer(Object event) {
        Object player = invokeZeroArgIfPresent(event, "getPlayer");
        return player == null ? readFieldIfPresent(event, "player") : player;
    }

    private static String readMessage(Object event) {
        Object message = invokeZeroArgIfPresent(event, "getMessage");
        if (message == null) {
            message = readFieldIfPresent(event, "message");
        }
        return message == null ? "" : message.toString();
    }

    private static void setComponent(Object event, Object component) {
        invokeMethodIfPresent(event, new Object[] { component }, "setComponent");
    }

    private static Object readFieldIfPresent(Object target, String... candidates) {
        if (target == null) {
            return null;
        }
        for (String candidate : candidates) {
            Class<?> type = target.getClass();
            while (type != null) {
                try {
                    Field field = type.getDeclaredField(candidate);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (ReflectiveOperationException ignored) {
                    type = type.getSuperclass();
                }
            }
        }
        return null;
    }

    private static Object invokeZeroArgIfPresent(Object target, String... methodNames) {
        return invokeMethodIfPresent(target, new Object[0], methodNames);
    }

    private static Object invokeMethodIfPresent(Object target, Object[] args, String... methodNames) {
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
                    } catch (ReflectiveOperationException ignored) {
                        return null;
                    }
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static boolean invokeAnyMethodIfPresent(Object target, Object[] args, String... methodNames) {
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
                    } catch (ReflectiveOperationException ignored) {
                        return false;
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
        for (int index = 0; index < parameterTypes.length; index++) {
            if (!isAssignable(parameterTypes[index], args[index])) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAssignable(Class<?> parameterType, Object value) {
        if (value == null) {
            return !parameterType.isPrimitive();
        }
        return !parameterType.isPrimitive() && parameterType.isAssignableFrom(value.getClass());
    }

    private static void writeFirstAssignableField(Object target, Object value, String... preferredFieldNames) {
        for (String fieldName : preferredFieldNames) {
            if (writeFieldIfAssignable(target, value, fieldName)) {
                return;
            }
        }

        Class<?> type = target == null ? null : target.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (isAssignable(field.getType(), value)) {
                    try {
                        field.setAccessible(true);
                        field.set(target, value);
                        return;
                    } catch (ReflectiveOperationException ignored) {
                    }
                }
            }
            type = type.getSuperclass();
        }
    }

    private static boolean writeFieldIfAssignable(Object target, Object value, String fieldName) {
        Class<?> type = target == null ? null : target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                if (!isAssignable(field.getType(), value)) {
                    return false;
                }
                field.setAccessible(true);
                field.set(target, value);
                return true;
            } catch (ReflectiveOperationException ignored) {
                type = type.getSuperclass();
            }
        }
        return false;
    }

    private static String normalizeKey(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    interface TextComponentFactory {
        Object create(String message);
    }

    private static final class ReflectiveTextComponentFactory implements TextComponentFactory {
        @Override
        public Object create(String message) {
            try {
                Class<?> componentClass = Class.forName("net.minecraft.util.text.TextComponentString");
                Constructor<?> constructor = componentClass.getConstructor(String.class);
                return constructor.newInstance(message);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Не удалось создать TextComponentString.", exception);
            }
        }
    }

    private static final class RoleStyle {
        private final String teamCode;
        private final String label;
        private final String shortLabel;
        private final String roleColor;
        private final String nameColor;

        private RoleStyle(String teamCode, String label, String shortLabel, String roleColor, String nameColor) {
            this.teamCode = teamCode;
            this.label = label;
            this.shortLabel = shortLabel;
            this.roleColor = roleColor;
            this.nameColor = nameColor;
        }
    }
}
