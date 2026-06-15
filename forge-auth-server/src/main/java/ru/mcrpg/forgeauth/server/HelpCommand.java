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

final class HelpCommand {

    private static final String COMMAND_NAME = "help";
    private static final List<String> ALIASES = Collections.unmodifiableList(Arrays.asList(
        "?",
        "commands",
        "cmd",
        "помощь",
        "команды"
    ));

    private static final List<HelpSection> PLAYER_SECTIONS = Collections.unmodifiableList(Arrays.asList(
        section(
            "Старт",
            "start", "старт", "basic", "основа",
            entry("/kit start", "/kit start", "одноразовый стартовый набор"),
            entry("/ach", "/ach", "коллекция титулов за руды и убийства мобов"),
            entry("/spawn", "/spawn", "вернуться на спавн, перезарядка зависит от роли"),
            entry("/rtp", "/rtp", "случайная безопасная точка, перезарядка зависит от роли")
        ),
        section(
            "Дом",
            "home", "дом", "homes", "дома",
            entry("/sethome [название]", "/sethome ", "сохранить точку дома, лимит зависит от роли"),
            entry("/home [название]", "/home ", "телепортироваться домой, перезарядка зависит от роли"),
            entry("/homes", "/homes", "список ваших домов"),
            entry("/delhome [название]", "/delhome ", "удалить точку дома")
        ),
        section(
            "Приваты",
            "region", "regions", "приват", "приваты", "rg",
            entry("//wand", "//wand", "получить топор для выделения территории"),
            entry("/rg claim <название>", "/rg claim ", "создать приват по двум выбранным точкам"),
            entry("/rg redefine <регион>", "/rg redefine ", "изменить границы своим текущим выделением"),
            entry("/rg transfer <регион> <игрок>", "/rg transfer ", "передать регион игроку онлайн"),
            entry("/rg info [название]", "/rg info ", "информация о регионе"),
            entry("/rg show [название]", "/rg show ", "показать границы частицами"),
            entry("/rg flag <регион> <флаг> <allow|deny>", "/rg flag ", "настроить PvP, мобов, взрывы, огонь, жемчуг и взаимодействия"),
            entry("/rg rollback <регион> [игрок] [количество]", "/rg rollback ", "откатить последние изменения блоков"),
            entry("/rg list", "/rg list", "список ваших регионов"),
            entry("/rg addmember <регион> <игрок>", "/rg addmember ", "добавить участника"),
            entry("/rg removemember <регион> <игрок>", "/rg removemember ", "удалить участника"),
            entry("/rg delete <регион>", "/rg delete ", "удалить свой регион")
        ),
        section(
            "Телепорт",
            "tp", "teleport", "телепорт", "тп",
            entry("/call <игрок>", "/call ", "попроситься к игроку"),
            entry("/call here <игрок>", "/call here ", "позвать игрока к себе"),
            entry("/call accept [игрок]", "/call accept ", "принять запрос"),
            entry("/call deny [игрок]", "/call deny ", "отклонить запрос"),
            entry("/call cancel [игрок]", "/call cancel ", "отменить свой запрос"),
            entry("/back", "/back", "вернуться к месту последней смерти, перезарядка зависит от роли"),
            entry("/wptp <x> <y> <z>", "/wptp ", "телепорт по координатам метки")
        )
    ));

    private static final HelpSection ADMIN_SECTION = section(
        "Админ",
        "admin", "op", "админ", "staff",
        entry("/spawnprotect info", "/spawnprotect info", "статус защиты спавна"),
        entry("/spawnprotect test", "/spawnprotect test", "проверить текущую позицию"),
        entry("/spawnprotect on|off", "/spawnprotect ", "включить или выключить защиту"),
        entry("/spawnprotect radius <блоки>", "/spawnprotect radius ", "радиус защиты"),
        entry("/spawnprotect region here <радиус>", "/spawnprotect region here ", "задать регион вокруг себя"),
        entry("/spawnprotect reload", "/spawnprotect reload", "перезагрузить конфиг"),
        entry("/rg admin find <запрос>", "/rg admin find ", "найти регион по названию или владельцу"),
        entry("/rg admin delete <регион>", "/rg admin delete ", "удалить регион в архив"),
        entry("/rg admin restore <регион>", "/rg admin restore ", "восстановить регион из архива")
    );

    private HelpCommand() {
    }

    static void register(FMLServerStartingEvent event) {
        try {
            Class<?> commandType = Class.forName("net.minecraft.command.ICommand");
            Object command = Proxy.newProxyInstance(
                HelpCommand.class.getClassLoader(),
                new Class<?>[] { commandType },
                new Handler()
            );
            Method registerMethod = event.getClass().getMethod("registerServerCommand", commandType);
            registerMethod.invoke(event, command);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Не удалось зарегистрировать команду /" + COMMAND_NAME + ".", exception);
        }
    }

    static List<HelpSection> visibleSections(boolean includeAdmin) {
        ArrayList<HelpSection> sections = new ArrayList<HelpSection>(PLAYER_SECTIONS);
        if (includeAdmin) {
            sections.add(ADMIN_SECTION);
        }
        return Collections.unmodifiableList(sections);
    }

    private static final class Handler implements InvocationHandler {
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
                execute(args[1], args[2]);
                return null;
            }
            if ("checkPermission".equals(name) || "func_184882_a".equals(name)) {
                return Boolean.TRUE;
            }
            if ("getTabCompletions".equals(name) || "func_184883_a".equals(name)) {
                return tabCompletions(args == null || args.length < 2 ? null : args[1], args == null || args.length < 3 ? null : args[2]);
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

    private static void execute(Object sender, Object arguments) {
        String[] args = arguments instanceof String[] ? (String[]) arguments : new String[0];
        boolean includeAdmin = canUseAdmin(sender);

        if (args.length > 1) {
            ServerChat.usage(sender, usage());
            return;
        }

        if (args.length == 1) {
            HelpSection section = findSection(args[0], includeAdmin);
            if (section == null) {
                ServerChat.status(sender, ServerChat.Tone.WARNING, "Помощь", "раздел не найден. Попробуйте " + ServerChat.command("/help") + ".");
                return;
            }
            sendSectionHelp(sender, section);
            return;
        }

        sendOverview(sender, includeAdmin);
    }

    private static void sendOverview(Object sender, boolean includeAdmin) {
        List<HelpSection> sections = visibleSections(includeAdmin);
        ServerChat.helpTitle(sender, "Команды сервера", "клик по команде вставит ее в чат");
        for (HelpSection section : sections) {
            sendSectionHelp(sender, section);
        }
        ServerChat.helpHint(sender, "Разделы: " + sectionList(sections) + ". Например: " + ServerChat.command("/help дом") + ".");
    }

    private static void sendSectionHelp(Object sender, HelpSection section) {
        ServerChat.helpSection(sender, section.title);
        for (HelpEntry entry : section.entries) {
            ServerChat.helpCommand(sender, entry.command, entry.description, entry.suggestion);
        }
    }

    private static HelpSection findSection(String rawKey, boolean includeAdmin) {
        String key = normalize(rawKey);
        for (HelpSection section : visibleSections(includeAdmin)) {
            if (section.matches(key)) {
                return section;
            }
        }
        return null;
    }

    private static List<String> tabCompletions(Object sender, Object arguments) {
        String[] args = arguments instanceof String[] ? (String[]) arguments : new String[0];
        if (args.length > 1) {
            return Collections.emptyList();
        }
        String prefix = args.length == 0 ? "" : normalize(args[0]);
        ArrayList<String> values = new ArrayList<String>();
        for (HelpSection section : visibleSections(canUseAdmin(sender))) {
            values.add(section.primaryKey());
        }
        return matching(values, prefix);
    }

    private static List<String> matching(List<String> values, String prefix) {
        ArrayList<String> result = new ArrayList<String>();
        for (String value : values) {
            if (normalize(value).startsWith(prefix)) {
                result.add(value);
            }
        }
        return result;
    }

    private static String sectionList(List<HelpSection> sections) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < sections.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append("/help ").append(sections.get(i).primaryKey());
        }
        return builder.toString();
    }

    private static boolean canUseAdmin(Object sender) {
        if (sender == null) {
            return false;
        }
        Object result = invokeIfPresent(sender, new Object[] { Integer.valueOf(2), "spawnprotect" }, "canUseCommand", "func_70003_b");
        return Boolean.TRUE.equals(result);
    }

    private static HelpSection section(String title, String key, String alias, String alias2, String alias3, HelpEntry... entries) {
        return new HelpSection(title, Arrays.asList(key, alias, alias2, alias3), Arrays.asList(entries));
    }

    private static HelpSection section(String title, String key, String alias, String alias2, String alias3, String alias4, HelpEntry... entries) {
        return new HelpSection(title, Arrays.asList(key, alias, alias2, alias3, alias4), Arrays.asList(entries));
    }

    private static HelpEntry entry(String command, String suggestion, String description) {
        return new HelpEntry(command, suggestion, description);
    }

    private static String usage() {
        return "/" + COMMAND_NAME + " [раздел]";
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

    static final class HelpSection {
        final String title;
        final List<String> keys;
        final List<HelpEntry> entries;

        private HelpSection(String title, List<String> keys, List<HelpEntry> entries) {
            this.title = title;
            this.keys = Collections.unmodifiableList(new ArrayList<String>(keys));
            this.entries = Collections.unmodifiableList(new ArrayList<HelpEntry>(entries));
        }

        private boolean matches(String key) {
            for (String candidate : keys) {
                if (normalize(candidate).equals(key)) {
                    return true;
                }
            }
            return false;
        }

        private String primaryKey() {
            return keys.get(0);
        }
    }

    static final class HelpEntry {
        final String command;
        final String suggestion;
        final String description;

        private HelpEntry(String command, String suggestion, String description) {
            this.command = command;
            this.suggestion = suggestion;
            this.description = description;
        }
    }
}
