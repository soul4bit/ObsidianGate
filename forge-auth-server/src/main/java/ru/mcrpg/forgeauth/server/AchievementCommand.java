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

final class AchievementCommand {

    private static final String COMMAND_NAME = "achievements";
    private static final String SUBJECT = "Коллекция";
    private static final List<String> ALIASES = Collections.unmodifiableList(Arrays.asList("ach", "titles", "title", "collection"));
    private static final int PAGE_SIZE = 7;

    private AchievementCommand() {
    }

    static void register(FMLServerStartingEvent event, PlayerAchievementService service) {
        try {
            Class<?> commandType = Class.forName("net.minecraft.command.ICommand");
            Object command = Proxy.newProxyInstance(
                AchievementCommand.class.getClassLoader(),
                new Class<?>[] { commandType },
                new Handler(service)
            );
            Method registerMethod = event.getClass().getMethod("registerServerCommand", commandType);
            registerMethod.invoke(event, command);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Не удалось зарегистрировать команду /" + COMMAND_NAME + ".", exception);
        }
    }

    private static final class Handler implements InvocationHandler {
        private final PlayerAchievementService service;

        private Handler(PlayerAchievementService service) {
            this.service = service;
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
                execute(args[1], args[2], service);
                return null;
            }
            if ("checkPermission".equals(name) || "func_184882_a".equals(name)) {
                return Boolean.TRUE;
            }
            if ("getTabCompletions".equals(name) || "func_184883_a".equals(name)) {
                return tabCompletions(args == null || args.length < 3 ? null : args[2]);
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

    private static void execute(Object sender, Object arguments, PlayerAchievementService service) {
        Object player = TeleportSupport.resolvePlayer(sender);
        if (player == null) {
            ServerChat.status(sender, ServerChat.Tone.ERROR, SUBJECT, "команду " + ServerChat.command("/ach") + " может использовать только игрок.");
            return;
        }

        String[] args = arguments instanceof String[] ? (String[]) arguments : new String[0];
        if (args.length == 0 || "stats".equalsIgnoreCase(args[0])) {
            sendStats(player, service);
            return;
        }
        if ("list".equalsIgnoreCase(args[0])) {
            int page = args.length >= 2 ? parsePage(args[1]) : 1;
            sendList(player, service, page);
            return;
        }
        if ("set".equalsIgnoreCase(args[0])) {
            if (args.length != 2) {
                ServerChat.usage(player, "/ach set <id>");
                return;
            }
            setTitle(player, service, args[1]);
            return;
        }
        if ("clear".equalsIgnoreCase(args[0])) {
            service.clearActiveTitle(player);
            ServerChat.status(player, ServerChat.Tone.SUCCESS, SUBJECT, "активный титул скрыт.");
            return;
        }
        if ("top".equalsIgnoreCase(args[0])) {
            sendTop(player, service, args.length >= 2 ? args[1] : "ores");
            return;
        }
        ServerChat.usage(player, usage());
    }

    private static void sendStats(Object player, PlayerAchievementService service) {
        PlayerAchievementService.PlayerProgress progress = service.snapshotFor(player);
        AchievementTitle active = AchievementTitleCatalog.find(progress.activeTitle());
        ServerChat.helpTitle(player, "Коллекция достижений", "косметические титулы за игру на сервере");
        ServerChat.info(player, "\u00A77Руды: \u00A7f" + progress.ores()
            + "\u00A78 | \u00A77Алмазные руды: \u00A7f" + progress.diamonds()
            + "\u00A78 | \u00A77Мобы: \u00A7f" + progress.mobKills()
            + "\u00A78 | \u00A77Враждебные: \u00A7f" + progress.hostileKills());
        ServerChat.info(player, "\u00A77Открыто титулов: \u00A7f" + progress.unlocked().size()
            + "\u00A78/\u00A7f" + AchievementTitleCatalog.all().size()
            + "\u00A78 | \u00A77Активный: " + (active == null ? "\u00A78нет" : active.coloredLabel()));
        ServerChat.helpCommand(player, "/ach list", "посмотреть все титулы", "/ach list");
        ServerChat.helpCommand(player, "/ach top ores", "топ добытчиков руды", "/ach top ores");
    }

    private static void sendList(Object player, PlayerAchievementService service, int requestedPage) {
        PlayerAchievementService.PlayerProgress progress = service.snapshotFor(player);
        List<AchievementTitle> titles = AchievementTitleCatalog.all();
        int maxPage = Math.max(1, (titles.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(1, Math.min(maxPage, requestedPage));
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(titles.size(), start + PAGE_SIZE);

        ServerChat.helpTitle(player, "Титулы " + page + "/" + maxPage, "клик по открытой команде вставит выбор");
        for (int index = start; index < end; index++) {
            AchievementTitle title = titles.get(index);
            boolean unlocked = progress.unlocked().contains(title.id());
            String prefix = unlocked ? "\u00A7aоткрыт" : "\u00A78закрыт";
            String value = progress.value(title.metric()) + "/" + title.threshold();
            String command = unlocked ? "/ach set " + title.id() : "/ach list " + page;
            ServerChat.helpCommand(
                player,
                (unlocked ? "/ach set " : "") + title.id(),
                prefix + "\u00A7r " + title.coloredLabel() + "\u00A78 - \u00A77" + title.description() + " \u00A78(" + value + ")",
                command
            );
        }
        if (page < maxPage) {
            ServerChat.helpCommand(player, "/ach list " + (page + 1), "следующая страница", "/ach list " + (page + 1));
        }
    }

    private static void setTitle(Object player, PlayerAchievementService service, String titleId) {
        AchievementTitle title = AchievementTitleCatalog.find(titleId);
        if (title == null) {
            ServerChat.status(player, ServerChat.Tone.WARNING, SUBJECT, "такого титула нет. Посмотрите " + ServerChat.command("/ach list") + ".");
            return;
        }
        if (!service.setActiveTitle(player, title.id())) {
            ServerChat.status(player, ServerChat.Tone.WARNING, SUBJECT, "титул " + title.coloredLabel() + "\u00A7e еще не открыт.");
            return;
        }
        ServerChat.status(player, ServerChat.Tone.SUCCESS, SUBJECT, "активный титул: " + title.coloredLabel() + "\u00A7a.");
    }

    private static void sendTop(Object player, PlayerAchievementService service, String rawMetric) {
        AchievementTitle.Metric metric = metric(rawMetric);
        if (metric == null) {
            ServerChat.usage(player, "/ach top <ores|diamonds|kills|hostile>");
            return;
        }
        ServerChat.helpTitle(player, "Топ коллекции", metricLabel(metric));
        List<PlayerAchievementService.PlayerProgress> top = service.top(metric, 8);
        if (top.isEmpty()) {
            ServerChat.info(player, "\u00A77Пока пусто.");
            return;
        }
        int rank = 1;
        for (PlayerAchievementService.PlayerProgress progress : top) {
            ServerChat.info(player, "\u00A78#" + rank + " \u00A7f" + progress.name() + "\u00A78 - \u00A7a" + progress.value(metric));
            rank++;
        }
    }

    private static AchievementTitle.Metric metric(String rawMetric) {
        String normalized = normalize(rawMetric);
        if ("ores".equals(normalized) || "ore".equals(normalized) || "руды".equals(normalized)) {
            return AchievementTitle.Metric.ORES;
        }
        if ("diamonds".equals(normalized) || "diamond".equals(normalized) || "алмазы".equals(normalized)) {
            return AchievementTitle.Metric.DIAMONDS;
        }
        if ("kills".equals(normalized) || "mobs".equals(normalized) || "мобы".equals(normalized)) {
            return AchievementTitle.Metric.MOB_KILLS;
        }
        if ("hostile".equals(normalized) || "hostiles".equals(normalized) || "враждебные".equals(normalized)) {
            return AchievementTitle.Metric.HOSTILE_KILLS;
        }
        return null;
    }

    private static String metricLabel(AchievementTitle.Metric metric) {
        if (metric == AchievementTitle.Metric.ORES) {
            return "добытые руды";
        }
        if (metric == AchievementTitle.Metric.DIAMONDS) {
            return "алмазные руды";
        }
        if (metric == AchievementTitle.Metric.MOB_KILLS) {
            return "убийства мобов";
        }
        if (metric == AchievementTitle.Metric.HOSTILE_KILLS) {
            return "враждебные мобы";
        }
        return "очки";
    }

    private static List<String> tabCompletions(Object arguments) {
        String[] args = arguments instanceof String[] ? (String[]) arguments : new String[0];
        if (args.length <= 1) {
            return matching(Arrays.asList("stats", "list", "set", "clear", "top"), args.length == 0 ? "" : args[0]);
        }
        if (args.length == 2 && "top".equalsIgnoreCase(args[0])) {
            return matching(Arrays.asList("ores", "diamonds", "kills", "hostile"), args[1]);
        }
        if (args.length == 2 && "set".equalsIgnoreCase(args[0])) {
            ArrayList<String> values = new ArrayList<String>();
            for (AchievementTitle title : AchievementTitleCatalog.all()) {
                values.add(title.id());
            }
            return matching(values, args[1]);
        }
        return Collections.emptyList();
    }

    private static List<String> matching(List<String> values, String prefix) {
        String normalizedPrefix = normalize(prefix);
        ArrayList<String> result = new ArrayList<String>();
        for (String value : values) {
            if (normalize(value).startsWith(normalizedPrefix)) {
                result.add(value);
            }
        }
        return result;
    }

    private static int parsePage(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private static String usage() {
        return "/ach [stats|list|set|clear|top]";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static int compareTo(Object other) {
        if (other == null) {
            return 1;
        }
        Object otherName = invokeIfPresent(other, new Object[0], "getName", "func_71517_b");
        return otherName == null ? 1 : COMMAND_NAME.compareTo(otherName.toString());
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
        if (!parameterType.isPrimitive()) {
            return parameterType.isAssignableFrom(value.getClass());
        }
        if (parameterType == Integer.TYPE) {
            return value instanceof Integer;
        }
        if (parameterType == Boolean.TYPE) {
            return value instanceof Boolean;
        }
        return false;
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
        if (List.class.isAssignableFrom(type)) {
            return Collections.emptyList();
        }
        return null;
    }
}
