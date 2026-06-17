package ru.mcrpg.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class XaeroUserDataReset {

    private static final String RESET_MARKER = ".launcher-cache/xaero-user-data-reset-2026-06-17";
    private static final String BACKUP_ROOT = ".xaero-reset-backups";
    private static final List<String> DATA_PATHS = Collections.unmodifiableList(Arrays.asList(
        "XaeroWaypoints",
        "xaerowaypoints.txt",
        "xaero/minimap",
        "XaeroWorldMap",
        "xaero/world-map"
    ));

    private XaeroUserDataReset() {
    }

    static Result resetOnce(Path gameDirectory) throws IOException {
        if (gameDirectory == null) {
            return Result.empty();
        }

        Path root = gameDirectory.toAbsolutePath().normalize();
        Path marker = resolveInside(root, RESET_MARKER);
        if (Files.exists(marker)) {
            return Result.empty();
        }

        List<String> movedEntries = new ArrayList<String>();
        Path backupDirectory = null;
        for (String dataPath : DATA_PATHS) {
            Path source = resolveInside(root, dataPath);
            if (!Files.exists(source)) {
                continue;
            }

            if (backupDirectory == null) {
                backupDirectory = createBackupDirectory(root);
            }

            Path destination = uniqueDestination(backupDirectory, dataPath);
            Path parent = destination.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.move(source, destination);
            movedEntries.add(dataPath + " -> " + root.relativize(destination).toString().replace('\\', '/'));
        }

        Files.createDirectories(marker.getParent());
        List<String> markerLines = new ArrayList<String>();
        markerLines.add("reset=xaero-user-data");
        markerLines.add("createdAtMillis=" + System.currentTimeMillis());
        markerLines.add("moved=" + movedEntries.size());
        markerLines.addAll(movedEntries);
        Files.write(marker, markerLines, StandardCharsets.UTF_8);

        return new Result(movedEntries, backupDirectory);
    }

    static boolean hasResetMarker(Path gameDirectory) {
        if (gameDirectory == null) {
            return false;
        }
        return Files.exists(gameDirectory.toAbsolutePath().normalize().resolve(RESET_MARKER).normalize());
    }

    private static Path createBackupDirectory(Path gameDirectory) throws IOException {
        Path backupDirectory = gameDirectory.resolve(BACKUP_ROOT)
            .resolve("xaero-user-data-" + System.currentTimeMillis())
            .normalize();
        if (!backupDirectory.startsWith(gameDirectory)) {
            throw new IllegalArgumentException("Xaero backup is outside game directory: " + backupDirectory);
        }
        Files.createDirectories(backupDirectory);
        return backupDirectory;
    }

    private static Path uniqueDestination(Path backupDirectory, String relativePath) {
        Path relative = Paths.get(relativePath).normalize();
        if (relative.isAbsolute() || startsWithParent(relative)) {
            throw new IllegalArgumentException("Xaero data path is outside backup directory: " + relativePath);
        }

        Path destination = backupDirectory.resolve(relative).normalize();
        if (!destination.startsWith(backupDirectory)) {
            throw new IllegalArgumentException("Xaero backup path is outside backup directory: " + destination);
        }
        if (!Files.exists(destination)) {
            return destination;
        }

        Path parent = destination.getParent();
        String fileName = destination.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        String extension = dotIndex > 0 ? fileName.substring(dotIndex) : "";
        int counter = 1;
        while (true) {
            Path candidate = parent.resolve(baseName + "-" + counter + extension);
            if (!Files.exists(candidate)) {
                return candidate;
            }
            counter++;
        }
    }

    private static Path resolveInside(Path root, String relativePath) {
        Path path = root.resolve(relativePath).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Xaero data path is outside game directory: " + relativePath);
        }
        return path;
    }

    private static boolean startsWithParent(Path path) {
        return path.getNameCount() > 0 && "..".equals(path.getName(0).toString());
    }

    static final class Result {
        private final List<String> movedEntries;
        private final Path backupDirectory;

        private Result(List<String> movedEntries, Path backupDirectory) {
            this.movedEntries = Collections.unmodifiableList(new ArrayList<String>(movedEntries));
            this.backupDirectory = backupDirectory;
        }

        private static Result empty() {
            return new Result(Collections.emptyList(), null);
        }

        boolean hasMovedEntries() {
            return !movedEntries.isEmpty();
        }

        int movedCount() {
            return movedEntries.size();
        }

        Path getBackupDirectory() {
            return backupDirectory;
        }
    }
}
