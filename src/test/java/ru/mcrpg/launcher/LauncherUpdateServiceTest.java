package ru.mcrpg.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LauncherUpdateServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void findUpdateUsesCurrentJarShaToAvoidRepeatedUpdates() throws Exception {
        Path currentJar = Files.write(tempDirectory.resolve("launcher.jar"), "same-launcher".getBytes(StandardCharsets.UTF_8));
        LoadedManifest loadedManifest = manifestWithUpdate(
            "2026.05.13.2",
            "launcher/obsidian-gate-launcher.jar",
            ChecksumUtils.sha256(currentJar),
            Files.size(currentJar)
        );

        LauncherUpdateCandidate update = new LauncherUpdateService(currentJar).findUpdate(loadedManifest, "0.1.0-SNAPSHOT");

        assertNull(update);
    }

    @Test
    void findUpdateResolvesRelativeLauncherUrlWhenShaDiffers() throws Exception {
        Path currentJar = Files.write(tempDirectory.resolve("launcher.jar"), "old-launcher".getBytes(StandardCharsets.UTF_8));
        Path newJar = Files.write(tempDirectory.resolve("new-launcher.jar"), "new-launcher".getBytes(StandardCharsets.UTF_8));
        LoadedManifest loadedManifest = manifestWithUpdate(
            "2026.05.13.2",
            "launcher/obsidian-gate-launcher.jar",
            ChecksumUtils.sha256(newJar),
            Files.size(newJar)
        );

        LauncherUpdateCandidate update = new LauncherUpdateService(currentJar).findUpdate(loadedManifest, "0.1.0-SNAPSHOT");

        assertNotNull(update);
        assertEquals("2026.05.13.2", update.getVersion());
        assertEquals("http://example.com/releases/launcher/obsidian-gate-launcher.jar", update.getDownloadUrl().toString());
        assertTrue(update.isInstallSupported());
    }

    @Test
    void findUpdateFallsBackToVersionComparisonOutsidePackagedJar() throws Exception {
        Path newJar = Files.write(tempDirectory.resolve("new-launcher.jar"), "new-launcher".getBytes(StandardCharsets.UTF_8));
        LoadedManifest loadedManifest = manifestWithUpdate(
            "0.2.0",
            "http://cdn.example.com/launcher.jar",
            ChecksumUtils.sha256(newJar),
            Files.size(newJar)
        );

        LauncherUpdateCandidate update = new LauncherUpdateService(null).findUpdate(loadedManifest, "0.1.0");

        assertNotNull(update);
        assertEquals("http://cdn.example.com/launcher.jar", update.getDownloadUrl().toString());
        assertTrue(!update.isInstallSupported());
    }

    @Test
    void restartScriptContinuesUpdateExperienceInNewLauncher() throws Exception {
        Method method = LauncherUpdateService.class.getDeclaredMethod(
            System.getProperty("os.name", "").toLowerCase().contains("win") ? "windowsScript" : "unixScript",
            Path.class,
            Path.class,
            String.class
        );
        method.setAccessible(true);

        String script = (String) method.invoke(
            null,
            tempDirectory.resolve("update.jar"),
            tempDirectory.resolve("launcher.jar"),
            "java"
        );

        assertTrue(script.contains("--update-complete"));
    }

    private static LoadedManifest manifestWithUpdate(String version, String url, String sha256, long size) throws Exception {
        LauncherUpdateSettings update = new LauncherUpdateSettings();
        update.setVersion(version);
        update.setUrl(url);
        update.setSha256(sha256);
        update.setSize(Long.valueOf(size));

        ModpackManifest manifest = new ModpackManifest();
        manifest.setLauncherUpdate(update);
        return new LoadedManifest(new URL("http://example.com/releases/manifest.json"), manifest);
    }
}
