package ru.mcrpg.launcher;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

final class VerifiedFileCache {

    private static final String CACHE_DIRECTORY = ".launcher-cache";
    private static final String CACHE_FILE = "verified-files.json";
    private static final int SCHEMA_VERSION = 1;

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

        long actualSize = Files.size(path);
        if ((expectedSize != null && expectedSize.longValue() >= 0L && actualSize != expectedSize.longValue())
            || actualSize != entry.size) {
            remove(key);
            return false;
        }

        long actualLastModifiedMillis = Files.getLastModifiedTime(path).toMillis();
        if (actualLastModifiedMillis != entry.lastModifiedMillis) {
            remove(key);
            return false;
        }

        return true;
    }

    void recordVerified(Path path, String algorithm, String expectedHash) throws IOException {
        if (!hasText(expectedHash) || !Files.isRegularFile(path)) {
            return;
        }

        CacheEntry entry = new CacheEntry();
        entry.algorithm = normalizeAlgorithm(algorithm);
        entry.hash = normalizeHash(expectedHash);
        entry.size = Files.size(path);
        entry.lastModifiedMillis = Files.getLastModifiedTime(path).toMillis();
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
            if (document == null || document.schemaVersion != SCHEMA_VERSION || document.entries == null) {
                return new CacheDocument();
            }
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
    }

    private static final class CacheEntrySnapshot {
        private final long size;
        private final long lastModifiedMillis;

        private CacheEntrySnapshot(long size, long lastModifiedMillis) {
            this.size = size;
            this.lastModifiedMillis = lastModifiedMillis;
        }

        private static CacheEntrySnapshot from(CacheEntry entry) {
            return new CacheEntrySnapshot(entry.size, entry.lastModifiedMillis);
        }
    }
}
