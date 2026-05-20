package ru.mcrpg.forgeauth.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

final class HomeService {

    private static final Path DEFAULT_HOMES_PATH = Paths.get("obsidiangate", "homes.properties");
    private static final String HOME_PREFIX = "home.";
    private static final int MAX_HOME_NAME_LENGTH = 16;

    private final Logger logger;
    private final Path homesPath;
    private final Properties homes = new Properties();
    private boolean loaded;

    HomeService(Logger logger) {
        this(logger, DEFAULT_HOMES_PATH);
    }

    HomeService(Logger logger, Path homesPath) {
        this.logger = logger;
        this.homesPath = homesPath;
    }

    synchronized void load() {
        homes.clear();
        if (Files.exists(homesPath)) {
            try (InputStream input = Files.newInputStream(homesPath)) {
                homes.load(input);
            } catch (IOException exception) {
                logger.log(Level.WARNING, "Не удалось прочитать homes. Запускаем пустое хранилище.", exception);
            }
        }
        loaded = true;
        logger.info(String.format("Homes загружены из %s. Записей=%d", homesPath, homes.size()));
    }

    synchronized List<String> listHomes(String playerId) {
        ensureLoaded();
        String prefix = playerPrefix(playerId);
        ArrayList<String> names = new ArrayList<String>();
        for (String key : homes.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                names.add(key.substring(prefix.length()));
            }
        }
        Collections.sort(names);
        return names;
    }

    synchronized HomeLocation getHome(String playerId, String name) {
        ensureLoaded();
        String value = homes.getProperty(homeKey(playerId, name));
        return value == null ? null : HomeLocation.parse(value);
    }

    synchronized String primaryHomeName(String playerId) {
        List<String> names = listHomes(playerId);
        if (names.isEmpty()) {
            return null;
        }
        if (names.contains("home")) {
            return "home";
        }
        return names.get(0);
    }

    synchronized SetHomeResult setHome(String playerId, String name, HomeLocation location, int maxHomes) {
        ensureLoaded();
        String key = homeKey(playerId, name);
        boolean existing = homes.containsKey(key);
        if (!existing && listHomes(playerId).size() >= maxHomes) {
            return SetHomeResult.limitReached(maxHomes);
        }
        homes.setProperty(key, location.serialize());
        save();
        return existing ? SetHomeResult.updated() : SetHomeResult.created();
    }

    synchronized boolean deleteHome(String playerId, String name) {
        ensureLoaded();
        Object removed = homes.remove(homeKey(playerId, name));
        if (removed != null) {
            save();
            return true;
        }
        return false;
    }

    static String normalizeName(String rawName) {
        String value = rawName == null || rawName.trim().isEmpty() ? "home" : rawName.trim().toLowerCase(Locale.ROOT);
        if (value.length() > MAX_HOME_NAME_LENGTH) {
            throw new IllegalArgumentException("Название дома не должно быть длиннее " + MAX_HOME_NAME_LENGTH + " символов.");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
            if (!ok) {
                throw new IllegalArgumentException("Название дома может содержать только латинские буквы, цифры, _ и -.");
            }
        }
        return value;
    }

    private void ensureLoaded() {
        if (!loaded) {
            load();
        }
    }

    private void save() {
        try {
            Path parent = homesPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(homesPath)) {
                homes.store(output, "ObsidianGate player homes.");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось сохранить homes.", exception);
        }
    }

    private static String playerPrefix(String playerId) {
        return HOME_PREFIX + normalizePlayerId(playerId) + ".";
    }

    private static String homeKey(String playerId, String name) {
        return playerPrefix(playerId) + normalizeName(name);
    }

    private static String normalizePlayerId(String playerId) {
        if (playerId == null || playerId.trim().isEmpty()) {
            throw new IllegalArgumentException("playerId не должен быть пустым.");
        }
        return playerId.trim().toLowerCase(Locale.ROOT);
    }

    static final class HomeLocation {
        final int dimension;
        final double x;
        final double y;
        final double z;
        final float yaw;
        final float pitch;

        HomeLocation(int dimension, double x, double y, double z, float yaw, float pitch) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        private String serialize() {
            return dimension + "," + x + "," + y + "," + z + "," + yaw + "," + pitch;
        }

        private static HomeLocation parse(String value) {
            String[] parts = value == null ? new String[0] : value.split(",");
            if (parts.length != 6) {
                throw new IllegalArgumentException("Некорректная запись home.");
            }
            return new HomeLocation(
                Integer.parseInt(parts[0]),
                Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2]),
                Double.parseDouble(parts[3]),
                Float.parseFloat(parts[4]),
                Float.parseFloat(parts[5])
            );
        }
    }

    static final class SetHomeResult {
        final boolean success;
        final boolean updated;
        final int limit;

        private SetHomeResult(boolean success, boolean updated, int limit) {
            this.success = success;
            this.updated = updated;
            this.limit = limit;
        }

        private static SetHomeResult created() {
            return new SetHomeResult(true, false, 0);
        }

        private static SetHomeResult updated() {
            return new SetHomeResult(true, true, 0);
        }

        private static SetHomeResult limitReached(int limit) {
            return new SetHomeResult(false, false, limit);
        }
    }
}
