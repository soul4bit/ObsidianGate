package ru.mcrpg.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class XaeroUpdateNotificationOptions {

    private static final String OPTION_PREFIX = "update_notifications:";
    private static final String DISABLED_OPTION = OPTION_PREFIX + "false";
    private static final List<String> CONFIG_FILES = Arrays.asList(
        "xaerominimap.txt",
        "xaeroworldmap.txt"
    );

    private XaeroUpdateNotificationOptions() {
    }

    static boolean disable(Path gameDirectory) throws IOException {
        if (gameDirectory == null) {
            return false;
        }

        boolean changed = false;
        Path configDirectory = gameDirectory.resolve("config").resolve("xaero");
        for (String fileName : CONFIG_FILES) {
            changed |= disableInFile(configDirectory.resolve(fileName));
        }
        return changed;
    }

    private static boolean disableInFile(Path configFile) throws IOException {
        List<String> lines = Files.isRegularFile(configFile)
            ? new ArrayList<String>(Files.readAllLines(configFile, StandardCharsets.UTF_8))
            : new ArrayList<String>();

        int optionLine = findOptionLine(lines);
        if (optionLine >= 0 && DISABLED_OPTION.equals(lines.get(optionLine).trim())) {
            return false;
        }

        if (optionLine >= 0) {
            lines.set(optionLine, DISABLED_OPTION);
        } else {
            lines.add(DISABLED_OPTION);
        }

        Files.createDirectories(configFile.getParent());
        Files.write(configFile, lines, StandardCharsets.UTF_8);
        return true;
    }

    private static int findOptionLine(List<String> lines) {
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).trim().startsWith(OPTION_PREFIX)) {
                return index;
            }
        }
        return -1;
    }
}
