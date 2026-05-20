package ru.mcrpg.forgeauth.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

final class PlayerRegionService {

    static final int MAX_RADIUS = 64;
    static final int MAX_REGIONS_PER_PLAYER = 3;
    private static final Path DEFAULT_REGIONS_PATH = Paths.get("obsidiangate", "player-regions.properties");
    private static final String REGION_PREFIX = "region.";
    private static final int MAX_NAME_LENGTH = 16;

    private final Logger logger;
    private final Path regionsPath;
    private final Properties regions = new Properties();
    private boolean loaded;

    PlayerRegionService(Logger logger) {
        this(logger, DEFAULT_REGIONS_PATH);
    }

    PlayerRegionService(Logger logger, Path regionsPath) {
        this.logger = logger;
        this.regionsPath = regionsPath;
    }

    synchronized void load() {
        regions.clear();
        if (Files.exists(regionsPath)) {
            try (InputStream input = Files.newInputStream(regionsPath)) {
                regions.load(input);
            } catch (IOException exception) {
                logger.log(Level.WARNING, "Не удалось прочитать приваты игроков. Запускаем пустое хранилище.", exception);
            }
        }
        loaded = true;
        logger.info(String.format("Приваты игроков загружены из %s. Записей=%d", regionsPath, regions.size()));
    }

    synchronized CreateResult createAround(String ownerId, String ownerName, String name, int dimension, double centerX, double centerZ, int radius) {
        return createAround(ownerId, ownerName, name, dimension, centerX, centerZ, radius, MAX_REGIONS_PER_PLAYER);
    }

    synchronized CreateResult createAround(
        String ownerId,
        String ownerName,
        String name,
        int dimension,
        double centerX,
        double centerZ,
        int radius,
        int maxRegions
    ) {
        ensureLoaded();
        String normalizedName = normalizeName(name);
        int safeRadius = clampRadius(radius);
        String key = regionKey(ownerId, normalizedName);
        boolean existing = regions.containsKey(key);
        if (!existing && !RoleLimits.isUnlimited(maxRegions) && list(ownerId).size() >= maxRegions) {
            return CreateResult.limitReached(maxRegions);
        }

        Region next = new Region(
            normalizePlayerId(ownerId),
            ownerName == null ? "" : ownerName,
            normalizedName,
            dimension,
            Math.floor(centerX) - safeRadius,
            0.0D,
            Math.floor(centerZ) - safeRadius,
            Math.floor(centerX) + safeRadius,
            255.0D,
            Math.floor(centerZ) + safeRadius,
            Collections.<String>emptySet()
        );

        Region current = existing ? get(ownerId, normalizedName) : null;
        if (current != null) {
            next = next.withTrusted(current.trustedNames);
        }

        Region overlap = firstOverlap(next, key);
        if (overlap != null) {
            return CreateResult.overlap(overlap);
        }

        regions.setProperty(key, next.serialize());
        save();
        return existing ? CreateResult.updated(next) : CreateResult.created(next);
    }

    synchronized boolean remove(String ownerId, String name) {
        ensureLoaded();
        Object removed = regions.remove(regionKey(ownerId, name));
        if (removed != null) {
            save();
            return true;
        }
        return false;
    }

    synchronized TrustResult trust(String ownerId, String name, String trustedPlayerName) {
        ensureLoaded();
        String key = regionKey(ownerId, name);
        Region region = parseRegion(key, regions.getProperty(key));
        if (region == null) {
            return TrustResult.notFound();
        }
        String trusted = normalizePlayerName(trustedPlayerName);
        if (trusted.equals(normalizePlayerName(region.ownerName))) {
            return TrustResult.owner();
        }
        Region updated = region.withTrustedAdded(trusted);
        regions.setProperty(key, updated.serialize());
        save();
        return TrustResult.ok(updated);
    }

    synchronized TrustResult untrust(String ownerId, String name, String trustedPlayerName) {
        ensureLoaded();
        String key = regionKey(ownerId, name);
        Region region = parseRegion(key, regions.getProperty(key));
        if (region == null) {
            return TrustResult.notFound();
        }
        Region updated = region.withTrustedRemoved(normalizePlayerName(trustedPlayerName));
        regions.setProperty(key, updated.serialize());
        save();
        return TrustResult.ok(updated);
    }

    synchronized List<Region> list(String ownerId) {
        ensureLoaded();
        String prefix = REGION_PREFIX + normalizePlayerId(ownerId) + ".";
        ArrayList<Region> result = new ArrayList<Region>();
        for (String key : regions.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                Region region = parseRegion(key, regions.getProperty(key));
                if (region != null) {
                    result.add(region);
                }
            }
        }
        Collections.sort(result, (left, right) -> left.name.compareTo(right.name));
        return result;
    }

    synchronized Region get(String ownerId, String name) {
        ensureLoaded();
        String key = regionKey(ownerId, name);
        return parseRegion(key, regions.getProperty(key));
    }

    synchronized Region findAt(int dimension, double x, double y, double z) {
        ensureLoaded();
        for (String key : regions.stringPropertyNames()) {
            Region region = parseRegion(key, regions.getProperty(key));
            if (region != null && region.contains(dimension, x, y, z)) {
                return region;
            }
        }
        return null;
    }

    synchronized Region firstOverlap(Region candidate, String ignoredKey) {
        ensureLoaded();
        for (String key : regions.stringPropertyNames()) {
            if (key.equals(ignoredKey)) {
                continue;
            }
            Region region = parseRegion(key, regions.getProperty(key));
            if (region != null && region.overlaps(candidate)) {
                return region;
            }
        }
        return null;
    }

    static String normalizeName(String rawName) {
        String value = rawName == null ? "" : rawName.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Укажите название региона.");
        }
        if (value.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Название региона не должно быть длиннее " + MAX_NAME_LENGTH + " символов.");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
            if (!ok) {
                throw new IllegalArgumentException("Название региона может содержать только латинские буквы, цифры, _ и -.");
            }
        }
        return value;
    }

    static int clampRadius(int radius) {
        if (radius < 1) {
            throw new IllegalArgumentException("Радиус региона должен быть от 1 до " + MAX_RADIUS + ".");
        }
        if (radius > MAX_RADIUS) {
            throw new IllegalArgumentException("Максимальный радиус региона: " + MAX_RADIUS + " блоков.");
        }
        return radius;
    }

    static String normalizePlayerName(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Имя игрока не должно быть пустым.");
        }
        return playerName.trim().toLowerCase(Locale.ROOT);
    }

    private void ensureLoaded() {
        if (!loaded) {
            load();
        }
    }

    private void save() {
        try {
            Path parent = regionsPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(regionsPath)) {
                regions.store(output, "ObsidianGate player regions.");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось сохранить приваты игроков.", exception);
        }
    }

    private static String regionKey(String ownerId, String name) {
        return REGION_PREFIX + normalizePlayerId(ownerId) + "." + normalizeName(name);
    }

    private static String normalizePlayerId(String playerId) {
        if (playerId == null || playerId.trim().isEmpty()) {
            throw new IllegalArgumentException("playerId не должен быть пустым.");
        }
        return playerId.trim().toLowerCase(Locale.ROOT);
    }

    private static Region parseRegion(String key, String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return null;
        }
        String[] keyParts = key.split("\\.");
        if (keyParts.length < 3) {
            return null;
        }
        String ownerId = keyParts[1];
        String name = keyParts[2];
        String[] parts = rawValue.split("\\|", -1);
        if (parts.length < 9) {
            return null;
        }
        try {
            return new Region(
                ownerId,
                parts[0],
                name,
                Integer.parseInt(parts[1]),
                Double.parseDouble(parts[2]),
                Double.parseDouble(parts[3]),
                Double.parseDouble(parts[4]),
                Double.parseDouble(parts[5]),
                Double.parseDouble(parts[6]),
                Double.parseDouble(parts[7]),
                parseTrusted(parts[8])
            );
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Set<String> parseTrusted(String value) {
        LinkedHashSet<String> names = new LinkedHashSet<String>();
        if (value == null || value.trim().isEmpty()) {
            return names;
        }
        String[] parts = value.split(",");
        for (String part : parts) {
            if (!part.trim().isEmpty()) {
                names.add(part.trim().toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }

    static final class Region {
        final String ownerId;
        final String ownerName;
        final String name;
        final int dimension;
        final double minX;
        final double minY;
        final double minZ;
        final double maxX;
        final double maxY;
        final double maxZ;
        final Set<String> trustedNames;

        private Region(
            String ownerId,
            String ownerName,
            String name,
            int dimension,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ,
            Set<String> trustedNames
        ) {
            this.ownerId = ownerId;
            this.ownerName = ownerName;
            this.name = name;
            this.dimension = dimension;
            this.minX = Math.min(minX, maxX);
            this.minY = Math.min(minY, maxY);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxY = Math.max(minY, maxY);
            this.maxZ = Math.max(minZ, maxZ);
            this.trustedNames = Collections.unmodifiableSet(new LinkedHashSet<String>(trustedNames));
        }

        boolean contains(int dimension, double x, double y, double z) {
            return this.dimension == dimension
                && x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
        }

        boolean overlaps(Region other) {
            return dimension == other.dimension
                && minX <= other.maxX && maxX >= other.minX
                && minY <= other.maxY && maxY >= other.minY
                && minZ <= other.maxZ && maxZ >= other.minZ;
        }

        boolean allows(String playerId, String playerName) {
            return ownerId.equals(normalizePlayerId(playerId)) || trustedNames.contains(normalizePlayerName(playerName));
        }

        Region withTrusted(Set<String> nextTrusted) {
            return new Region(ownerId, ownerName, name, dimension, minX, minY, minZ, maxX, maxY, maxZ, nextTrusted);
        }

        Region withTrustedAdded(String trustedName) {
            LinkedHashSet<String> nextTrusted = new LinkedHashSet<String>(trustedNames);
            nextTrusted.add(trustedName);
            return withTrusted(nextTrusted);
        }

        Region withTrustedRemoved(String trustedName) {
            LinkedHashSet<String> nextTrusted = new LinkedHashSet<String>(trustedNames);
            nextTrusted.remove(trustedName);
            return withTrusted(nextTrusted);
        }

        String describeBounds() {
            return "dim " + dimension + " [" + (int) minX + "," + (int) minY + "," + (int) minZ + "]..[" +
                (int) maxX + "," + (int) maxY + "," + (int) maxZ + "]";
        }

        private String serialize() {
            return ownerName + "|" + dimension + "|" + minX + "|" + minY + "|" + minZ + "|" +
                maxX + "|" + maxY + "|" + maxZ + "|" + trustedList();
        }

        private String trustedList() {
            StringBuilder builder = new StringBuilder();
            int index = 0;
            for (String name : trustedNames) {
                if (index > 0) {
                    builder.append(',');
                }
                builder.append(name);
                index++;
            }
            return builder.toString();
        }
    }

    static final class CreateResult {
        final boolean success;
        final boolean updated;
        final int limit;
        final Region region;
        final Region overlap;

        private CreateResult(boolean success, boolean updated, int limit, Region region, Region overlap) {
            this.success = success;
            this.updated = updated;
            this.limit = limit;
            this.region = region;
            this.overlap = overlap;
        }

        private static CreateResult created(Region region) {
            return new CreateResult(true, false, 0, region, null);
        }

        private static CreateResult updated(Region region) {
            return new CreateResult(true, true, 0, region, null);
        }

        private static CreateResult limitReached(int limit) {
            return new CreateResult(false, false, limit, null, null);
        }

        private static CreateResult overlap(Region overlap) {
            return new CreateResult(false, false, 0, null, overlap);
        }
    }

    static final class TrustResult {
        final boolean success;
        final boolean notFound;
        final boolean owner;
        final Region region;

        private TrustResult(boolean success, boolean notFound, boolean owner, Region region) {
            this.success = success;
            this.notFound = notFound;
            this.owner = owner;
            this.region = region;
        }

        private static TrustResult ok(Region region) {
            return new TrustResult(true, false, false, region);
        }

        private static TrustResult notFound() {
            return new TrustResult(false, true, false, null);
        }

        private static TrustResult owner() {
            return new TrustResult(false, false, true, null);
        }
    }
}
