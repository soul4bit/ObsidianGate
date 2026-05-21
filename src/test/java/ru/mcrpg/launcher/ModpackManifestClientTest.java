package ru.mcrpg.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModpackManifestClientTest {

    @TempDir
    Path tempDirectory;

    @Test
    void loadWrapsConnectionRefusedWithManifestHostingHint() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        IOException exception = org.junit.jupiter.api.Assertions.assertThrows(
            IOException.class,
            () -> new ModpackManifestClient().load("http://127.0.0.1:" + port + "/manifest.json")
        );

        assertTrue(exception.getMessage().contains("manifest.json"));
        assertTrue(exception.getMessage().contains("HTTP(S)"));
        assertTrue(exception.getMessage().contains("25565"));
        assertInstanceOf(ConnectException.class, exception.getCause());
    }

    @Test
    void loadParsesManifestNews() throws Exception {
        Path manifestFile = tempDirectory.resolve("manifest.json");
        Files.writeString(
            manifestFile,
            """
            {
              "schemaVersion": 1,
              "id": "test",
              "version": "2026.05.21",
              "news": {
                "title": "Обновление сборки",
                "date": "2026-05-21",
                "body": "Лаунчер покажет игроку, что изменилось перед синхронизацией.",
                "highlights": ["Добавлен экран прогресса синхронизации"],
                "newMods": ["ExampleNewMod"],
                "removedMods": ["LegacyOldMod"],
                "important": ["Перед запуском дождитесь окончания проверки файлов."]
              },
              "files": []
            }
            """,
            StandardCharsets.UTF_8
        );

        LoadedManifest loadedManifest = new ModpackManifestClient().load(manifestFile.toUri().toURL().toString());
        ModpackNews news = loadedManifest.getManifest().getNews();

        assertEquals("Обновление сборки", news.getTitle());
        assertEquals("2026-05-21", news.getDate());
        assertTrue(news.hasContent());
        assertIterableEquals(List.of("Добавлен экран прогресса синхронизации"), news.getHighlights());
        assertIterableEquals(List.of("ExampleNewMod"), news.getNewMods());
        assertIterableEquals(List.of("LegacyOldMod"), news.getRemovedMods());
        assertIterableEquals(List.of("Перед запуском дождитесь окончания проверки файлов."), news.getImportant());
    }
}
