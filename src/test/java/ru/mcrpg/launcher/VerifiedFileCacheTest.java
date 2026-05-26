package ru.mcrpg.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VerifiedFileCacheTest {

    @TempDir
    Path tempDirectory;

    @Test
    void persistsVerifiedFileMetadataAndReusesIt() throws Exception {
        Path file = writeFile("mods/example.jar", "example-mod");
        String hash = ChecksumUtils.sha256(file);

        VerifiedFileCache cache = VerifiedFileCache.open(tempDirectory);
        assertFalse(cache.matches(file, "SHA-256", hash, Long.valueOf(Files.size(file))));

        cache.recordVerified(file, "SHA-256", hash);
        cache.save();

        VerifiedFileCache reopened = VerifiedFileCache.open(tempDirectory);
        assertTrue(reopened.matches(file, "SHA-256", hash, Long.valueOf(Files.size(file))));

        JsonNode document = new ObjectMapper().readTree(tempDirectory.resolve(".launcher-cache/verified-files.json").toFile());
        JsonNode entry = document.path("entries").path("mods/example.jar");
        assertEquals(2, document.path("schemaVersion").asInt());
        assertTrue(entry.path("lastModifiedNanos").asLong() > 0L);
    }

    @Test
    void invalidatesCacheEntryWhenFileMetadataChanges() throws Exception {
        Path file = writeFile("config/rpg.cfg", "difficulty=hard");
        String hash = ChecksumUtils.sha256(file);

        VerifiedFileCache cache = VerifiedFileCache.open(tempDirectory);
        cache.recordVerified(file, "SHA-256", hash);
        cache.save();

        long cachedLastModified = Files.getLastModifiedTime(file).toMillis();
        Files.write(file, "difficulty=easy".getBytes(StandardCharsets.UTF_8));
        Files.setLastModifiedTime(file, FileTime.fromMillis(cachedLastModified + 5000L));

        VerifiedFileCache reopened = VerifiedFileCache.open(tempDirectory);
        assertFalse(reopened.matches(file, "SHA-256", hash, Long.valueOf("difficulty=hard".length())));
    }

    @Test
    void doesNotTrustLegacyMillisOnlyCacheEntries() throws Exception {
        Path file = writeFile("config/rpg.cfg", "difficulty=hard");
        String hash = ChecksumUtils.sha256(file);
        long size = Files.size(file);
        long lastModifiedMillis = Files.getLastModifiedTime(file).toMillis();

        Path cacheDirectory = Files.createDirectories(tempDirectory.resolve(".launcher-cache"));
        String legacyCache = "{\n"
            + "  \"schemaVersion\": 1,\n"
            + "  \"entries\": {\n"
            + "    \"config/rpg.cfg\": {\n"
            + "      \"algorithm\": \"SHA-256\",\n"
            + "      \"hash\": \"" + hash + "\",\n"
            + "      \"size\": " + size + ",\n"
            + "      \"lastModifiedMillis\": " + lastModifiedMillis + "\n"
            + "    }\n"
            + "  }\n"
            + "}\n";
        Files.write(cacheDirectory.resolve("verified-files.json"), legacyCache.getBytes(StandardCharsets.UTF_8));

        VerifiedFileCache cache = VerifiedFileCache.open(tempDirectory);
        assertFalse(cache.matches(file, "SHA-256", hash, Long.valueOf(size)));
    }

    private Path writeFile(String relativePath, String content) throws Exception {
        Path file = tempDirectory.resolve(relativePath);
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }
}
