package ru.mcrpg.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class XaeroUpdateNotificationOptions {

    private static final String OPTION_KEY = "update_notifications";
    private static final String LEGACY_DISABLED_OPTION = OPTION_KEY + ":false";
    private static final String CARBON_CONFIG_DISABLED_OPTION = OPTION_KEY + " = false";
    private static final List<ConfigFile> CONFIG_FILES = Arrays.asList(
        new ConfigFile("xaero/xaerominimap.txt", LEGACY_DISABLED_OPTION),
        new ConfigFile("xaero/xaeroworldmap.txt", LEGACY_DISABLED_OPTION),
        new ConfigFile("xaero/minimap/client.cfg", CARBON_CONFIG_DISABLED_OPTION),
        new ConfigFile("xaero/world-map/client.cfg", CARBON_CONFIG_DISABLED_OPTION)
    );

    private XaeroUpdateNotificationOptions() {
    }

    static boolean disable(Path gameDirectory) throws IOException {
        if (gameDirectory == null) {
            return false;
        }

        boolean changed = false;
        Path configDirectory = gameDirectory.resolve("config");
        for (ConfigFile configFile : CONFIG_FILES) {
            changed |= disableInFile(configDirectory.resolve(configFile.relativePath), configFile.disabledOption);
        }
        return changed;
    }

    private static boolean disableInFile(Path configFile, String disabledOption) throws IOException {
        List<String> lines = Files.isRegularFile(configFile)
            ? new ArrayList<String>(Files.readAllLines(configFile, StandardCharsets.UTF_8))
            : new ArrayList<String>();

        int optionLine = findOptionLine(lines);
        if (optionLine >= 0 && isDisabledOptionLine(lines.get(optionLine))) {
            return false;
        }

        if (optionLine >= 0) {
            lines.set(optionLine, disabledOption);
        } else {
            lines.add(disabledOption);
        }

        Files.createDirectories(configFile.getParent());
        Files.write(configFile, lines, StandardCharsets.UTF_8);
        return true;
    }

    private static int findOptionLine(List<String> lines) {
        for (int index = 0; index < lines.size(); index++) {
            if (isOptionLine(lines.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isOptionLine(String line) {
        return optionValue(line) != null;
    }

    private static boolean isDisabledOptionLine(String line) {
        String value = optionValue(line);
        return value != null && "false".equalsIgnoreCase(value);
    }

    private static String optionValue(String line) {
        if (line == null) {
            return null;
        }

        String trimmed = line.trim();
        if (!trimmed.startsWith(OPTION_KEY)) {
            return null;
        }

        String remainder = trimmed.substring(OPTION_KEY.length()).trim();
        if (remainder.startsWith(":") || remainder.startsWith("=")) {
            return remainder.substring(1).trim();
        }
        return null;
    }

    private static final class ConfigFile {
        private final String relativePath;
        private final String disabledOption;

        private ConfigFile(String relativePath, String disabledOption) {
            this.relativePath = relativePath;
            this.disabledOption = disabledOption;
        }
    }
}
