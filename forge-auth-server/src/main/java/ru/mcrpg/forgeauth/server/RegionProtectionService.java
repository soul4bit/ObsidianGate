package ru.mcrpg.forgeauth.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

final class RegionProtectionService {

    private static final Path DEFAULT_PATH = Paths.get("obsidiangate", "regions.properties");
    static final int MAX_REGIONS_PER_PLAYER = 3;
    static final long MAX_HORIZONTAL_AREA = 65536L;

    private final Logger logger;
    private final Path storagePath;
    private final Map<String, Region> regions = new LinkedHashMap<String, Region>();
    private final Map<String, Selection> selections = new ConcurrentHashMap<String, Selection>();

    RegionProtectionService(Logger logger) {
        this(logger, DEFAULT_PATH);
    }

    RegionProtectionService(Logger logger, Path storagePath) {
        this.logger = logger;
        this.storagePath = storagePath;
    }

    synchronized void load() {
        regions.clear();
        if (!Files.isRegularFile(storagePath)) {
            save();
            return;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(storagePath)) {
            properties.load(input);
            for (String key : properties.stringPropertyNames()) {
                if (!key.startsWith("region.") || !key.endsWith(".ownerId")) {
                    continue;
                }
                String name = key.substring("region.".length(), key.length() - ".ownerId".length());
                Region region = readRegion(name, properties);
                if (region != null) {
                    regions.put(name, region);
                }
            }
        } catch (IOException exception) {
            logger.log(Level.WARNING, "Не удалось загрузить приваты.", exception);
        }
        logger.info("Приваты загружены. Регионов=" + regions.size());
    }

    void setSelectionPoint(String playerId, int point, Position position) {
        Selection selection = selections.computeIfAbsent(normalizePlayerId(playerId), ignored -> new Selection());
        if (point == 1) {
            selection.first = position;
        } else if (point == 2) {
            selection.second = position;
        } else {
            throw new IllegalArgumentException("Номер точки должен быть 1 или 2.");
        }
    }

    Selection selection(String playerId) {
        return selections.get(normalizePlayerId(playerId));
    }

    synchronized ClaimResult claim(String rawName, String ownerId, String ownerName) {
        String name = normalizeRegionName(rawName);
        String normalizedOwnerId = normalizePlayerId(ownerId);
        Selection selection = selections.get(normalizedOwnerId);
        if (selection == null || selection.first == null || selection.second == null) {
            return ClaimResult.failure("Сначала выделите две точки деревянным топором.");
        }
        if (selection.first.dimension != selection.second.dimension) {
            return ClaimResult.failure("Обе точки должны быть в одном измерении.");
        }
        if (regions.containsKey(name)) {
            return ClaimResult.failure("Регион с таким названием уже существует.");
        }
        if (ownedRegions(normalizedOwnerId).size() >= MAX_REGIONS_PER_PLAYER) {
            return ClaimResult.failure("Достигнут лимит регионов: " + MAX_REGIONS_PER_PLAYER + ".");
        }

        Region candidate = Region.fromSelection(name, normalizedOwnerId, ownerName, selection);
        if (candidate.horizontalArea() > MAX_HORIZONTAL_AREA) {
            return ClaimResult.failure("Площадь слишком большая. Максимум: " + MAX_HORIZONTAL_AREA + " блоков.");
        }
        Region overlap = firstOverlap(candidate);
        if (overlap != null) {
            return ClaimResult.failure("Территория пересекается с регионом " + overlap.name + ".");
        }

        regions.put(name, candidate);
        save();
        return ClaimResult.success(candidate);
    }

    synchronized boolean addMember(String regionName, String actorId, String memberName, boolean operator) {
        Region region = requireManagedRegion(regionName, actorId, operator);
        boolean changed = region.members.add(normalizePlayerName(memberName));
        if (changed) {
            save();
        }
        return changed;
    }

    synchronized boolean removeMember(String regionName, String actorId, String memberName, boolean operator) {
        Region region = requireManagedRegion(regionName, actorId, operator);
        boolean changed = region.members.remove(normalizePlayerName(memberName));
        if (changed) {
            save();
        }
        return changed;
    }

    synchronized boolean delete(String regionName, String actorId, boolean operator) {
        Region region = requireManagedRegion(regionName, actorId, operator);
        boolean removed = regions.remove(region.name) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    synchronized Region region(String rawName) {
        return regions.get(normalizeRegionName(rawName));
    }

    synchronized Region regionAt(int dimension, int x, int y, int z) {
        for (Region region : regions.values()) {
            if (region.contains(dimension, x, y, z)) {
                return region;
            }
        }
        return null;
    }

    synchronized List<Region> ownedRegions(String ownerId) {
        String normalizedOwnerId = normalizePlayerId(ownerId);
        List<Region> result = new ArrayList<Region>();
        for (Region region : regions.values()) {
            if (region.ownerId.equals(normalizedOwnerId)) {
                result.add(region);
            }
        }
        return Collections.unmodifiableList(result);
    }

    synchronized boolean canBuild(String playerId, String playerName, boolean operator, int dimension, int x, int y, int z) {
        Region region = regionAt(dimension, x, y, z);
        return region == null || operator || region.allows(playerId, playerName);
    }

    private Region requireManagedRegion(String rawName, String actorId, boolean operator) {
        Region region = regions.get(normalizeRegionName(rawName));
        if (region == null) {
            throw new IllegalArgumentException("Регион не найден.");
        }
        if (!operator && !region.ownerId.equals(normalizePlayerId(actorId))) {
            throw new IllegalArgumentException("Управлять регионом может только владелец.");
        }
        return region;
    }

    private Region firstOverlap(Region candidate) {
        for (Region region : regions.values()) {
            if (candidate.overlaps(region)) {
                return region;
            }
        }
        return null;
    }

    private Region readRegion(String name, Properties properties) {
        try {
            String prefix = "region." + name + ".";
            Set<String> members = new LinkedHashSet<String>();
            String rawMembers = properties.getProperty(prefix + "members", "");
            for (String member : rawMembers.split(",")) {
                if (!member.trim().isEmpty()) {
                    members.add(normalizePlayerName(member));
                }
            }
            return new Region(
                normalizeRegionName(name),
                normalizePlayerId(properties.getProperty(prefix + "ownerId")),
                properties.getProperty(prefix + "ownerName", ""),
                Integer.parseInt(properties.getProperty(prefix + "dimension")),
                Integer.parseInt(properties.getProperty(prefix + "minX")),
                Integer.parseInt(properties.getProperty(prefix + "minZ")),
                Integer.parseInt(properties.getProperty(prefix + "maxX")),
                Integer.parseInt(properties.getProperty(prefix + "maxZ")),
                members
            );
        } catch (RuntimeException exception) {
            logger.warning("Пропущен поврежденный регион " + name + ": " + exception.getMessage());
            return null;
        }
    }

    private synchronized void save() {
        Properties properties = new Properties();
        for (Region region : regions.values()) {
            String prefix = "region." + region.name + ".";
            properties.setProperty(prefix + "ownerId", region.ownerId);
            properties.setProperty(prefix + "ownerName", region.ownerName);
            properties.setProperty(prefix + "dimension", Integer.toString(region.dimension));
            properties.setProperty(prefix + "minX", Integer.toString(region.minX));
            properties.setProperty(prefix + "minZ", Integer.toString(region.minZ));
            properties.setProperty(prefix + "maxX", Integer.toString(region.maxX));
            properties.setProperty(prefix + "maxZ", Integer.toString(region.maxZ));
            properties.setProperty(prefix + "members", String.join(",", region.members));
        }
        try {
            Path parent = storagePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(storagePath)) {
                properties.store(output, "ObsidianGate player regions");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось сохранить приваты.", exception);
        }
    }

    static String normalizeRegionName(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_\\-]{1,24}")) {
            throw new IllegalArgumentException("Название: 1-24 символа, латиница, цифры, _ или -.");
        }
        return normalized;
    }

    private static String normalizePlayerId(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Не удалось определить UUID игрока.");
        }
        return normalized;
    }

    private static String normalizePlayerName(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_]{1,16}")) {
            throw new IllegalArgumentException("Некорректное имя игрока.");
        }
        return normalized;
    }

    static final class Position {
        final int dimension;
        final int x;
        final int y;
        final int z;

        Position(int dimension, int x, int y, int z) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    static final class Selection {
        volatile Position first;
        volatile Position second;
    }

    static final class Region {
        final String name;
        final String ownerId;
        final String ownerName;
        final int dimension;
        final int minX;
        final int minZ;
        final int maxX;
        final int maxZ;
        final Set<String> members;

        Region(
            String name,
            String ownerId,
            String ownerName,
            int dimension,
            int minX,
            int minZ,
            int maxX,
            int maxZ,
            Set<String> members
        ) {
            this.name = name;
            this.ownerId = ownerId;
            this.ownerName = ownerName == null ? "" : ownerName;
            this.dimension = dimension;
            this.minX = Math.min(minX, maxX);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxZ = Math.max(minZ, maxZ);
            this.members = new LinkedHashSet<String>(members);
        }

        private static Region fromSelection(String name, String ownerId, String ownerName, Selection selection) {
            return new Region(
                name,
                ownerId,
                ownerName,
                selection.first.dimension,
                selection.first.x,
                selection.first.z,
                selection.second.x,
                selection.second.z,
                Collections.<String>emptySet()
            );
        }

        boolean contains(int targetDimension, int x, int y, int z) {
            return targetDimension == dimension && x >= minX && x <= maxX && z >= minZ && z <= maxZ && y >= 0 && y <= 255;
        }

        boolean overlaps(Region other) {
            return dimension == other.dimension
                && minX <= other.maxX
                && maxX >= other.minX
                && minZ <= other.maxZ
                && maxZ >= other.minZ;
        }

        boolean allows(String playerId, String playerName) {
            return ownerId.equals(normalizePlayerId(playerId)) || members.contains(normalizePlayerName(playerName));
        }

        long horizontalArea() {
            return (long) (maxX - minX + 1) * (long) (maxZ - minZ + 1);
        }
    }

    static final class ClaimResult {
        final boolean success;
        final String message;
        final Region region;

        private ClaimResult(boolean success, String message, Region region) {
            this.success = success;
            this.message = message;
            this.region = region;
        }

        static ClaimResult success(Region region) {
            return new ClaimResult(true, "", region);
        }

        static ClaimResult failure(String message) {
            return new ClaimResult(false, message, null);
        }
    }
}
