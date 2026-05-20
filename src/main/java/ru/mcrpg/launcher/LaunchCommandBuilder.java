package ru.mcrpg.launcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LaunchCommandBuilder {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9]*)}");

    public List<String> build(LauncherConfig config, LaunchIdentity identity) {
        String template = requireText(config.getLaunchTemplate(), "Укажи шаблон команды запуска.");

        Map<String, String> placeholders = new LinkedHashMap<String, String>();
        placeholders.put("java", trim(config.getJavaCommand()));
        placeholders.put("username", trim(identity.getUsername()));
        placeholders.put("gameDir", trim(config.getGameDirectory()));
        placeholders.put("workingDir", trim(config.getWorkingDirectory()));
        placeholders.put("serverHost", trim(config.getServerHost()));
        placeholders.put("serverPort", Integer.toString(config.getServerPort()));
        placeholders.put("uuid", trim(identity.getUuid()));
        placeholders.put("accessToken", trim(identity.getAccessToken()));
        placeholders.put("userType", trim(identity.getUserType()));
        placeholders.put("gameSessionFile", identity.hasSessionFile() ? identity.getSessionFile().toString() : "");
        placeholders.put("memoryMin", formatMemory(config.getMemoryMinMb()));
        placeholders.put("memoryMax", formatMemory(config.getMemoryMaxMb()));
        placeholders.put("memoryMinMb", Integer.toString(config.getMemoryMinMb()));
        placeholders.put("memoryMaxMb", Integer.toString(config.getMemoryMaxMb()));

        List<String> tokens = tokenize(template);
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("Шаблон команды запуска не может быть пустым.");
        }

        List<String> resolved = new ArrayList<String>(tokens.size());
        for (String token : tokens) {
            String value = replacePlaceholders(token, placeholders);
            if (!value.isEmpty()) {
                resolved.add(value);
            }
        }
        maybeInjectMemoryArguments(config, template, resolved);
        maybeInjectSessionFileProperty(config, identity, template, resolved);
        return resolved;
    }

    public String preview(List<String> command) {
        StringBuilder preview = new StringBuilder();
        for (String token : command) {
            if (preview.length() > 0) {
                preview.append(' ');
            }
            preview.append(quoteIfNeeded(token));
        }
        return preview.toString();
    }

    List<String> tokenize(String template) {
        List<String> tokens = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean escaping = false;

        for (char character : template.toCharArray()) {
            if (escaping) {
                current.append(character);
                escaping = false;
                continue;
            }

            if (character == '\\' && !inSingleQuotes) {
                escaping = true;
                continue;
            }

            if (character == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
                continue;
            }

            if (character == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
                continue;
            }

            if (Character.isWhitespace(character) && !inSingleQuotes && !inDoubleQuotes) {
                addToken(tokens, current);
                continue;
            }

            current.append(character);
        }

        if (escaping) {
            current.append('\\');
        }
        if (inSingleQuotes || inDoubleQuotes) {
            throw new IllegalArgumentException("В шаблоне команды незакрытая кавычка.");
        }

        addToken(tokens, current);
        return tokens;
    }

    private static void addToken(List<String> tokens, StringBuilder current) {
        if (current.length() == 0) {
            return;
        }
        tokens.add(current.toString());
        current.setLength(0);
    }

    private static String replacePlaceholders(String token, Map<String, String> placeholders) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(token);
        StringBuffer resolved = new StringBuffer();

        while (matcher.find()) {
            String name = matcher.group(1);
            if (!placeholders.containsKey(name)) {
                throw new IllegalArgumentException(
                    "Неизвестный плейсхолдер {" + name + "}. Доступны: " + Arrays.toString(placeholders.keySet().toArray())
                );
            }
            String value = placeholders.get(name);
            if (value.isEmpty()) {
                throw new IllegalArgumentException(
                    "Плейсхолдер {" + name + "} используется в шаблоне, но значение для него не задано."
                );
            }
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(value));
        }

        matcher.appendTail(resolved);
        return resolved.toString();
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String quoteIfNeeded(String value) {
        if (value.indexOf(' ') < 0 && value.indexOf('\t') < 0 && value.indexOf('"') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\\\"") + '"';
    }

    private static void maybeInjectSessionFileProperty(
        LauncherConfig config,
        LaunchIdentity identity,
        String template,
        List<String> resolved
    ) {
        if (!identity.hasSessionFile() || template.contains("{gameSessionFile}") || resolved.isEmpty()) {
            return;
        }

        String javaCommand = trim(config.getJavaCommand());
        if (javaCommand.isEmpty() || !javaCommand.equals(resolved.get(0))) {
            return;
        }

        String sessionArg = "-Dobsidiangate.sessionFile=" + identity.getSessionFile();
        if (!resolved.contains(sessionArg)) {
            resolved.add(1, sessionArg);
        }
    }

    private static void maybeInjectMemoryArguments(LauncherConfig config, String template, List<String> resolved) {
        if (resolved.isEmpty() || containsMemoryConfiguration(template, resolved)) {
            return;
        }

        String javaCommand = trim(config.getJavaCommand());
        if (javaCommand.isEmpty() || !javaCommand.equals(resolved.get(0))) {
            return;
        }

        List<String> memoryArguments = new ArrayList<String>(2);
        if (config.getMemoryMinMb() > 0) {
            memoryArguments.add("-Xms" + formatMemory(config.getMemoryMinMb()));
        }
        if (config.getMemoryMaxMb() > 0) {
            memoryArguments.add("-Xmx" + formatMemory(config.getMemoryMaxMb()));
        }
        resolved.addAll(1, memoryArguments);
    }

    private static boolean containsMemoryConfiguration(String template, List<String> resolved) {
        if (template != null && (template.contains("{memoryMin") || template.contains("{memoryMax"))) {
            return true;
        }
        for (String token : resolved) {
            if (token.startsWith("-Xms") || token.startsWith("-Xmx")) {
                return true;
            }
        }
        return false;
    }

    private static String formatMemory(int memoryMb) {
        return Math.max(0, memoryMb) + "M";
    }

}
