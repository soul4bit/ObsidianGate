package ru.mcrpg.launcher;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;

final class ModpackManifestStateCache {

    private static final String CACHE_DIRECTORY = ".launcher-cache";
    private static final String CACHE_FILE = "manifest-state.json";
    private static final int SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;
    private final Path cacheFile;
    private final CacheDocument document;

    private ModpackManifestStateCache(ObjectMapper objectMapper, Path cacheFile, CacheDocument document) {
        this.objectMapper = objectMapper;
        this.cacheFile = cacheFile;
        this.document = document;
    }

    static ModpackManifestStateCache open(Path rootDirectory) throws IOException {
        Path normalizedRoot = rootDirectory.toAbsolutePath().normalize();
        Path cacheDirectory = normalizedRoot.resolve(CACHE_DIRECTORY);
        Files.createDirectories(cacheDirectory);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        Path cacheFile = cacheDirectory.resolve(CACHE_FILE);
        return new ModpackManifestStateCache(objectMapper, cacheFile, readDocument(objectMapper, cacheFile));
    }

    boolean matches(LoadedManifest loadedManifest, ModpackManifest manifest) throws IOException {
        if (loadedManifest == null || loadedManifest.getSourceUrl() == null || manifest == null) {
            return false;
        }
        return SCHEMA_VERSION == document.schemaVersion
            && valueOrEmpty(loadedManifest.getSourceUrl().toString()).equals(valueOrEmpty(document.manifestUrl))
            && fingerprint(manifest).equals(valueOrEmpty(document.manifestFingerprint));
    }

    void recordSynced(LoadedManifest loadedManifest, ModpackManifest manifest) throws IOException {
        if (loadedManifest == null || loadedManifest.getSourceUrl() == null || manifest == null) {
            return;
        }
        document.schemaVersion = SCHEMA_VERSION;
        document.manifestUrl = loadedManifest.getSourceUrl().toString();
        document.manifestFingerprint = fingerprint(manifest);
        document.syncedAt = Instant.now().toString();
        save();
    }

    private void save() throws IOException {
        Path parent = cacheFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tempFile = Files.createTempFile(parent, "manifest-state-", ".json");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), document);
            moveIntoPlace(tempFile, cacheFile);
        } finally {
            Files.deleteIfExists(tempFile);
        }
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
            if (document == null || document.schemaVersion != SCHEMA_VERSION) {
                return new CacheDocument();
            }
            return document;
        } catch (IOException ignored) {
            return new CacheDocument();
        }
    }

    private String fingerprint(ModpackManifest manifest) throws IOException {
        byte[] manifestBytes = objectMapper.writeValueAsBytes(manifest);
        MessageDigest digest = ChecksumUtils.messageDigest("SHA-256");
        byte[] hash = digest.digest(manifestBytes);
        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (byte value : hash) {
            int unsigned = value & 0xff;
            if (unsigned < 16) {
                builder.append('0');
            }
            builder.append(Integer.toHexString(unsigned));
        }
        return builder.toString();
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    static final class CacheDocument {
        public int schemaVersion = SCHEMA_VERSION;
        public String manifestUrl = "";
        public String manifestFingerprint = "";
        public String syncedAt = "";
    }
}
