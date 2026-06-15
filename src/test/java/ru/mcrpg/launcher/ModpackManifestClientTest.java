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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
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
        String manifestJson = """
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
              "history": [
                {
                  "version": "2026.05.21.4",
                  "date": "2026-05-21",
                  "title": "News layout",
                  "highlights": ["News panel now reaches the build file card."]
                },
                {
                  "version": "2026.05.21.3",
                  "title": "Compact news",
                  "highlights": ["News text is shorter."]
                }
              ],
              "files": []
            }
            """;
        KeyPair keyPair = generateKeyPair();
        writeSignedManifest(manifestFile, manifestJson, keyPair);

        LoadedManifest loadedManifest = new ModpackManifestClient(
            new ManifestSignatureVerifier(keyPair.getPublic())
        ).load(manifestFile.toUri().toURL().toString());
        ModpackNews news = loadedManifest.getManifest().getNews();

        assertEquals("Обновление сборки", news.getTitle());
        assertEquals("2026-05-21", news.getDate());
        assertTrue(news.hasContent());
        assertIterableEquals(List.of("Добавлен экран прогресса синхронизации"), news.getHighlights());
        assertIterableEquals(List.of("ExampleNewMod"), news.getNewMods());
        assertIterableEquals(List.of("LegacyOldMod"), news.getRemovedMods());
        assertIterableEquals(List.of("Перед запуском дождитесь окончания проверки файлов."), news.getImportant());
        assertEquals(2, loadedManifest.getManifest().getHistory().size());
        assertEquals("2026.05.21.4", loadedManifest.getManifest().getHistory().get(0).getVersion());
        assertEquals("News layout", loadedManifest.getManifest().getHistory().get(0).getTitle());
        assertIterableEquals(
            List.of("News panel now reaches the build file card."),
            loadedManifest.getManifest().getHistory().get(0).getHighlights()
        );
    }

    @Test
    void loadRejectsManifestChangedAfterSigning() throws Exception {
        Path manifestFile = tempDirectory.resolve("manifest.json");
        KeyPair keyPair = generateKeyPair();
        writeSignedManifest(
            manifestFile,
            "{\"schemaVersion\":1,\"id\":\"test\",\"version\":\"1\",\"files\":[]}",
            keyPair
        );
        Files.writeString(
            manifestFile,
            "{\"schemaVersion\":1,\"id\":\"test\",\"version\":\"2\",\"files\":[]}",
            StandardCharsets.UTF_8
        );

        IOException exception = org.junit.jupiter.api.Assertions.assertThrows(
            IOException.class,
            () -> new ModpackManifestClient(new ManifestSignatureVerifier(keyPair.getPublic()))
                .load(manifestFile.toUri().toURL().toString())
        );

        assertTrue(exception.getMessage().contains("недействительна"));
    }

    private static KeyPair generateKeyPair() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private static void writeSignedManifest(Path path, String content, KeyPair keyPair) throws Exception {
        byte[] manifestBytes = content.getBytes(StandardCharsets.UTF_8);
        Files.write(path, manifestBytes);

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(manifestBytes);
        Files.writeString(
            Path.of(path.toString() + ".sig"),
            Base64.getEncoder().encodeToString(signer.sign()),
            StandardCharsets.US_ASCII
        );
    }
}
