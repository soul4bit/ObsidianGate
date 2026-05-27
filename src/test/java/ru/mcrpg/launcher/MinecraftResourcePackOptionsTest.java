package ru.mcrpg.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MinecraftResourcePackOptionsTest {

    @TempDir
    Path tempDirectory;

    @Test
    void appendsInstalledPackAsHighestPriority() throws IOException {
        installPack("ObsidianGate-Fixes-1.12.2");
        Path options = tempDirectory.resolve("options.txt");
        Files.write(
            options,
            "music:1.0\nresourcePacks:[\"Faithful 1.12.2-rv4.zip\"]\n".getBytes(StandardCharsets.UTF_8)
        );

        assertTrue(MinecraftResourcePackOptions.ensureEnabled(tempDirectory, "ObsidianGate-Fixes-1.12.2"));

        assertEquals(
            java.util.List.of(
                "music:1.0",
                "resourcePacks:[\"Faithful 1.12.2-rv4.zip\",\"ObsidianGate-Fixes-1.12.2\"]"
            ),
            Files.readAllLines(options, StandardCharsets.UTF_8)
        );
    }

    @Test
    void doesNothingWhenPackIsAlreadyHighestPriority() throws IOException {
        installPack("ObsidianGate-Fixes-1.12.2");
        Path options = tempDirectory.resolve("options.txt");
        Files.write(
            options,
            "resourcePacks:[\"Faithful 1.12.2-rv4.zip\",\"ObsidianGate-Fixes-1.12.2\"]\n"
                .getBytes(StandardCharsets.UTF_8)
        );

        assertFalse(MinecraftResourcePackOptions.ensureEnabled(tempDirectory, "ObsidianGate-Fixes-1.12.2"));
    }

    @Test
    void skipsMissingPack() throws IOException {
        Path options = tempDirectory.resolve("options.txt");
        Files.write(options, "resourcePacks:[]\n".getBytes(StandardCharsets.UTF_8));

        assertFalse(MinecraftResourcePackOptions.ensureEnabled(tempDirectory, "ObsidianGate-Fixes-1.12.2"));
        assertEquals(java.util.List.of("resourcePacks:[]"), Files.readAllLines(options, StandardCharsets.UTF_8));
    }

    @Test
    void enablesInitialPacksAndLanguageOnce() throws IOException {
        installZipPack("ModdedFaithful 1.12.2-rv1.zip");
        installZipPack("Faithful 1.12.2-rv4.zip");
        installZipPack("bushy-leaves-1.12.2.zip");
        installPack("ObsidianGate-Fixes-1.12.2");

        assertTrue(MinecraftResourcePackOptions.ensureInitialDefaults(
            tempDirectory,
            Arrays.asList(
                "ModdedFaithful 1.12.2-rv1.zip",
                "Faithful 1.12.2-rv4.zip",
                "bushy-leaves-1.12.2.zip",
                "ObsidianGate-Fixes-1.12.2"
            ),
            "ru_ru"
        ));

        assertEquals(
            java.util.List.of(
                "resourcePacks:[\"ModdedFaithful 1.12.2-rv1.zip\",\"Faithful 1.12.2-rv4.zip\",\"bushy-leaves-1.12.2.zip\",\"ObsidianGate-Fixes-1.12.2\"]",
                "lang:ru_ru"
            ),
            Files.readAllLines(tempDirectory.resolve("options.txt"), StandardCharsets.UTF_8)
        );
        assertTrue(MinecraftResourcePackOptions.hasInitialDefaultsMarker(tempDirectory));
    }

    @Test
    void skipsInitialDefaultsAfterMarkerWasCreated() throws IOException {
        installZipPack("Faithful 1.12.2-rv4.zip");
        Files.write(tempDirectory.resolve("options.txt"), "resourcePacks:[]\n".getBytes(StandardCharsets.UTF_8));

        assertTrue(MinecraftResourcePackOptions.ensureInitialDefaults(
            tempDirectory,
            Arrays.asList("Faithful 1.12.2-rv4.zip"),
            "ru_ru"
        ));

        Files.write(tempDirectory.resolve("options.txt"), "resourcePacks:[]\n".getBytes(StandardCharsets.UTF_8));

        assertFalse(MinecraftResourcePackOptions.ensureInitialDefaults(
            tempDirectory,
            Arrays.asList("Faithful 1.12.2-rv4.zip"),
            "ru_ru"
        ));
        assertEquals(
            java.util.List.of("resourcePacks:[]"),
            Files.readAllLines(tempDirectory.resolve("options.txt"), StandardCharsets.UTF_8)
        );
    }

    @Test
    void addsRequiredLanguageWhenMissing() throws IOException {
        Path options = tempDirectory.resolve("options.txt");
        Files.write(options, "music:1.0\n".getBytes(StandardCharsets.UTF_8));

        assertTrue(MinecraftResourcePackOptions.ensureLanguage(tempDirectory, "ru_ru"));

        assertEquals(
            java.util.List.of("music:1.0", "lang:ru_ru"),
            Files.readAllLines(options, StandardCharsets.UTF_8)
        );
    }

    @Test
    void replacesExistingLanguage() throws IOException {
        Path options = tempDirectory.resolve("options.txt");
        Files.write(options, "lang:en_us\nresourcePacks:[]\n".getBytes(StandardCharsets.UTF_8));

        assertTrue(MinecraftResourcePackOptions.ensureLanguage(tempDirectory, "ru_ru"));

        assertEquals(
            java.util.List.of("lang:ru_ru", "resourcePacks:[]"),
            Files.readAllLines(options, StandardCharsets.UTF_8)
        );
    }

    @Test
    void doesNothingWhenLanguageAlreadySet() throws IOException {
        Path options = tempDirectory.resolve("options.txt");
        Files.write(options, "lang:ru_ru\n".getBytes(StandardCharsets.UTF_8));

        assertFalse(MinecraftResourcePackOptions.ensureLanguage(tempDirectory, "ru_ru"));
        assertEquals(java.util.List.of("lang:ru_ru"), Files.readAllLines(options, StandardCharsets.UTF_8));
    }

    private void installPack(String name) throws IOException {
        Files.createDirectories(tempDirectory.resolve("resourcepacks").resolve(name));
    }

    private void installZipPack(String name) throws IOException {
        Files.createDirectories(tempDirectory.resolve("resourcepacks"));
        Files.write(tempDirectory.resolve("resourcepacks").resolve(name), new byte[0]);
    }
}
