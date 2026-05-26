package ru.mcrpg.launcher;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

final class VerifiedFileCache {

    private static final String CACHE_DIRECTORY = ".launcher-cache";
    private static final String CACHE_FILE = "verified-files.json";
    private static final int SCHEMA_VERSION = 2;

    private final ObjectMapper objectMapper;
    private final Path rootDirectory;
    private final Path cacheFile;
    private final CacheDocument document;
    private boolean dirty;

    private VerifiedFileCache(ObjectMapper objectMapper, Path rootDirectory, Path cacheFile, CacheDocument document) {
        this.objectMapper = objectMapper;
        this.rootDirectory = rootDirectory;
        this.cacheFile = cacheFile;
        this.document = document;
    }

    static VerifiedFileCache open(Path rootDirectory) throws IOException {
        Path normalizedRoot = rootDirectory.toAbsolutePath().normalize();
        Path cacheDirectory = normalizedRoot.resolve(CACHE_DIRECTORY);
        Files.createDirectories(cacheDirectory);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        Path cacheFile = cacheDirectory.resolve(CACHE_FILE);
        CacheDocument document = readDocument(objectMapper, cacheFile);
        return new VerifiedFileCache(objectMapper, normalizedRoot, cacheFile, document);
    }

    boolean matches(Path path, String algorithm, String expectedHash, Long expectedSize) throws IOException {
        if (!hasText(expectedHash) || !Files.isRegularFile(path)) {
            return false;
        }
        return matches(path, readMetadata(path), algorithm, expectedHash, expectedSize);
    }

    boolean matches(Path path, FileMetadata metadata, String algorithm, String expectedHash, Long expectedSize)
        throws IOException {
        if (!hasText(expectedHash) || metadata == null || !metadata.isRegularFile()) {
            return false;
        }

        String expectedAlgorithm = normalizeAlgorithm(algorithm);
        String normalizedExpectedHash = normalizeHash(expectedHash);
        String key = key(path);
        CacheEntrySnapshot entry;
        synchronized (this) {
            CacheEntry cachedEntry = document.entries.get(key);
            if (cachedEntry == null) {
                return false;
            }

            if (!expectedAlgorithm.equals(normalizeAlgorithm(cachedEntry.algorithm))
                || !normalizedExpectedHash.equals(normalizeHash(cachedEntry.hash))) {
                return false;
            }
            entry = CacheEntrySnapshot.from(cachedEntry);
        }

        if ((expectedSize != null && expectedSize.longValue() >= 0L && metadata.size() != expectedSize.longValue())
            || metadata.size() != entry.size) {
            remove(key);
            return false;
        }

        if (!entry.hasTrustedTimestamp()) {
            remove(key);
            return false;
        }

        if (metadata.lastModifiedNanos() != entry.lastModifiedNanos.longValue()) {
            remove(key);
            return false;
        }

        if (hasText(entry.fileKey) && !entry.fileKey.equals(metadata.fileKey())) {
            remove(key);
            return false;
        }

        if (!hasText(entry.fileKey) && hasText(metadata.fileKey())) {
            updateMetadata(key, metadata);
        }

        return true;
    }

    void recordVerified(Path path, String algorithm, String expectedHash) throws IOException {
        if (!hasText(expectedHash) || !Files.isRegularFile(path)) {
            return;
        }
        recordVerified(path, algorithm, expectedHash, readMetadata(path));
    }

    void recordVerified(Path path, String algorithm, String expectedHash, FileMetadata metadata) {
        if (!hasText(expectedHash) || metadata == null || !metadata.isRegularFile()) {
            return;
        }
        CacheEntry entry = new CacheEntry();
        entry.algorithm = normalizeAlgorithm(algorithm);
        entry.hash = normalizeHash(expectedHash);
        applyMetadata(entry, metadata);
        synchronized (this) {
            document.entries.put(key(path), entry);
            dirty = true;
        }
    }

    void remove(Path path) {
        remove(key(path));
    }

    synchronized void save() throws IOException {
        if (!dirty) {
            return;
        }

        document.schemaVersion = SCHEMA_VERSION;
        Path parent = cacheFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tempFile = Files.createTempFile(parent, "verified-files-", ".json");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), document);
            moveIntoPlace(tempFile, cacheFile);
        } finally {
            Files.deleteIfExists(tempFile);
        }
        dirty = false;
    }

    private static void moveIntoPlace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static CacheDocument readDocument(ObjectMapper objectMapper, Path cacheFile) {
        if (!Files.isRegularFile(cacheFile)) {
            return new CacheDocument();
        }
        try {
            CacheDocument document = objectMapper.readValue(cacheFile.toFile(), CacheDocument.class);
            if (document == null || document.schemaVersion < 1 || document.schemaVersion > SCHEMA_VERSION
                || document.entries == null) {
                return new CacheDocument();
            }
            document.schemaVersion = SCHEMA_VERSION;
            return document;
        } catch (IOException ignored) {
            return new CacheDocument();
        }
    }

    private String key(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.startsWith(rootDirectory)) {
            return rootDirectory.relativize(normalized).toString().replace('\\', '/');
        }
        return normalized.toString().replace('\\', '/');
    }

    private synchronized void remove(String key) {
        if (document.entries.remove(key) != null) {
            dirty = true;
        }
    }

    private synchronized void updateMetadata(String key, FileMetadata metadata) {
        CacheEntry entry = document.entries.get(key);
        if (entry == null) {
            return;
        }
        applyMetadata(entry, metadata);
        dirty = true;
    }

    static FileMetadata readMetadata(Path path) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
        return FileMetadata.from(attributes);
    }

    private static void applyMetadata(CacheEntry entry, FileMetadata metadata) {
        entry.size = metadata.size();
        entry.lastModifiedMillis = metadata.lastModifiedMillis();
        entry.lastModifiedNanos = Long.valueOf(metadata.lastModifiedNanos());
        entry.fileKey = metadata.fileKey();
    }

    private static String normalizeAlgorithm(String algorithm) {
        return hasText(algorithm) ? algorithm.trim().toUpperCase(Locale.ROOT) : "";
    }

    private static String normalizeHash(String hash) {
        return hasText(hash) ? hash.trim().toLowerCase(Locale.ROOT) : "";
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    static final class CacheDocument {
        public int schemaVersion = SCHEMA_VERSION;
        public Map<String, CacheEntry> entries = new TreeMap<String, CacheEntry>();
    }

    static final class CacheEntry {
        public String algorithm;
        public String hash;
        public long size;
        public long lastModifiedMillis;
        public Long lastModifiedNanos;
        public String fileKey;
    }

    static final class FileMetadata {
        private final boolean regularFile;
        private final long size;
        private final long lastModifiedMillis;
        private final long lastModifiedNanos;
        private final String fileKey;

        private FileMetadata(
            boolean regularFile,
            long size,
            long lastModifiedMillis,
            long lastModifiedNanos,
            String fileKey
        ) {
            this.regularFile = regularFile;
            this.size = size;
            this.lastModifiedMillis = lastModifiedMillis;
            this.lastModifiedNanos = lastModifiedNanos;
            this.fileKey = fileKey;
        }

        private static FileMetadata from(BasicFileAttributes attributes) {
            Object rawFileKey = attributes.fileKey();
            return new FileMetadata(
                attributes.isRegularFile(),
                attributes.size(),
                attributes.lastModifiedTime().toMillis(),
                attributes.lastModifiedTime().to(TimeUnit.NANOSECONDS),
                rawFileKey == null ? "" : rawFileKey.toString()
            );
        }

        boolean isRegularFile() {
            return regularFile;
        }

        long size() {
            return size;
        }

        long lastModifiedMillis() {
            return lastModifiedMillis;
        }

        long lastModifiedNanos() {
            return lastModifiedNanos;
        }

        String fileKey() {
            return fileKey;
        }

        boolean sameFileState(FileMetadata other) {
            if (other == null) {
                return false;
            }
            return regularFile == other.regularFile
                && size == other.size
                && lastModifiedNanos == other.lastModifiedNanos
                && fileKey.equals(other.fileKey);
        }
    }

    private static final class CacheEntrySnapshot {
        private final long size;
        private final Long lastModifiedNanos;
        private final String fileKey;

        private CacheEntrySnapshot(long size, Long lastModifiedNanos, String fileKey) {
            this.size = size;
            this.lastModifiedNanos = lastModifiedNanos;
            this.fileKey = fileKey == null ? "" : fileKey;
        }

        private static CacheEntrySnapshot from(CacheEntry entry) {
            return new CacheEntrySnapshot(entry.size, entry.lastModifiedNanos, entry.fileKey);
        }

        private boolean hasTrustedTimestamp() {
            return lastModifiedNanos != null;
        }
    }
}
