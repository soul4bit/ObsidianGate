package ru.mcrpg.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MinecraftResourcePackOptions {

    private static final Pattern QUOTED_VALUE_PATTERN = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final String LANGUAGE_PREFIX = "lang:";
    private static final String RESOURCE_PACKS_PREFIX = "resourcePacks:";
    private static final String INITIALIZED_MARKER = ".launcher-cache/resourcepacks-initialized";

    private MinecraftResourcePackOptions() {
    }

    static boolean ensureEnabled(Path gameDirectory, String packName) throws IOException {
        return ensureEnabled(gameDirectory, Arrays.asList(packName));
    }

    static boolean ensureEnabled(Path gameDirectory, List<String> packNames) throws IOException {
        if (gameDirectory == null || packNames == null || packNames.isEmpty()) {
            return false;
        }

        List<String> installedPacks = new ArrayList<String>();
        for (String packName : packNames) {
            String normalizedPackName = hasText(packName) ? packName.trim() : "";
            if (
                hasText(normalizedPackName)
                    && isInstalled(gameDirectory, normalizedPackName)
                    && !installedPacks.contains(normalizedPackName)
            ) {
                installedPacks.add(normalizedPackName);
            }
        }

        if (installedPacks.isEmpty()) {
            return false;
        }

        Path optionsFile = gameDirectory.resolve("options.txt");
        List<String> lines = readOptions(optionsFile);

        int resourcePacksLine = findLine(lines, RESOURCE_PACKS_PREFIX);
        List<String> packs = resourcePacksLine >= 0
            ? parseResourcePacks(lines.get(resourcePacksLine))
            : new ArrayList<String>();
        List<String> originalPacks = new ArrayList<String>(packs);

        for (String packName : installedPacks) {
            packs.removeIf(packName::equals);
            packs.add(packName);
        }

        if (originalPacks.equals(packs)) {
            return false;
        }

        String updatedLine = RESOURCE_PACKS_PREFIX + formatResourcePacks(packs);
        if (resourcePacksLine >= 0) {
            lines.set(resourcePacksLine, updatedLine);
        } else {
            lines.add(updatedLine);
        }

        Files.createDirectories(gameDirectory);
        Files.write(optionsFile, lines, StandardCharsets.UTF_8);
        return true;
    }

    static boolean ensureInitialDefaults(Path gameDirectory, List<String> packNames, String language) throws IOException {
        if (gameDirectory == null) {
            return false;
        }

        Path marker = gameDirectory.resolve(INITIALIZED_MARKER);
        if (Files.exists(marker)) {
            return false;
        }

        boolean changed = false;
        changed |= ensureEnabled(gameDirectory, packNames);
        changed |= ensureLanguage(gameDirectory, language);

        Files.createDirectories(marker.getParent());
        Files.write(
            marker,
            Arrays.asList("resourcePacks=true", "language=" + (language == null ? "" : language.trim())),
            StandardCharsets.UTF_8
        );
        return changed;
    }

    static boolean hasInitialDefaultsMarker(Path gameDirectory) {
        return gameDirectory != null && Files.exists(gameDirectory.resolve(INITIALIZED_MARKER));
    }

    static boolean ensureLanguage(Path gameDirectory, String language) throws IOException {
        if (gameDirectory == null || !hasText(language)) {
            return false;
        }

        Path optionsFile = gameDirectory.resolve("options.txt");
        List<String> lines = readOptions(optionsFile);
        String normalizedLanguage = language.trim();
        String updatedLine = LANGUAGE_PREFIX + normalizedLanguage;
        int languageLine = findLine(lines, LANGUAGE_PREFIX);

        if (languageLine >= 0 && updatedLine.equals(lines.get(languageLine))) {
            return false;
        }

        if (languageLine >= 0) {
            lines.set(languageLine, updatedLine);
        } else {
            lines.add(updatedLine);
        }

        Files.createDirectories(gameDirectory);
        Files.write(optionsFile, lines, StandardCharsets.UTF_8);
        return true;
    }

    private static List<String> readOptions(Path optionsFile) throws IOException {
        return Files.isRegularFile(optionsFile)
            ? Files.readAllLines(optionsFile, StandardCharsets.UTF_8)
            : new ArrayList<String>();
    }

    private static boolean isInstalled(Path gameDirectory, String packName) {
        Path resourcePacksDirectory = gameDirectory.resolve("resourcepacks");
        return Files.exists(resourcePacksDirectory.resolve(packName))
            || Files.exists(resourcePacksDirectory.resolve(packName + ".zip"));
    }

    private static int findLine(List<String> lines, String prefix) {
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).startsWith(prefix)) {
                return index;
            }
        }
        return -1;
    }

    private static List<String> parseResourcePacks(String line) {
        List<String> packs = new ArrayList<String>();
        Matcher matcher = QUOTED_VALUE_PATTERN.matcher(line);
        while (matcher.find()) {
            packs.add(unescape(matcher.group(1)));
        }
        return packs;
    }

    private static String formatResourcePacks(List<String> packs) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < packs.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append('"').append(escape(packs.get(index))).append('"');
        }
        return builder.append(']').toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescape(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
