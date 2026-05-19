package ru.mcrpg.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModpackSyncServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void syncDownloadsFilesAndAppliesManifestLauncherSettings() throws Exception {
        Path sourceDirectory = Files.createDirectories(tempDirectory.resolve("source"));
        Path forgeJar = writeFile(sourceDirectory, "forge-1.12.2-14.23.5.2847.jar", "forge-client");
        Path modJar = writeFile(sourceDirectory, "mods/examplemod.jar", "example-mod");
        Path configFile = writeFile(sourceDirectory, "config/rpg.cfg", "difficulty=hard");

        Path manifest = tempDirectory.resolve("manifest.json");
        Files.write(
            manifest,
            buildManifest(sourceDirectory, forgeJar, modJar, configFile).getBytes(StandardCharsets.UTF_8)
        );

        LauncherConfig config = LauncherConfig.defaults();
        config.setManifestUrl(manifest.toUri().toURL().toString());
        config.setGameDirectory(tempDirectory.resolve("client").toString());
        config.setServerHost("");
        config.setLaunchTemplate("");

        List<String> logLines = new ArrayList<String>();
        ModpackSyncService service = new ModpackSyncService(new ModpackManifestClient());
        ModpackSyncResult result = service.sync(config, logLines::add);

        assertTrue(Files.exists(tempDirectory.resolve("client/forge-1.12.2-14.23.5.2847.jar")));
        assertTrue(Files.exists(tempDirectory.resolve("client/mods/examplemod.jar")));
        assertTrue(Files.exists(tempDirectory.resolve("client/config/rpg.cfg")));
        assertEquals(LauncherConfig.DEFAULT_SERVER_HOST, result.getResolvedConfig().getServerHost());
        assertEquals(25565, result.getResolvedConfig().getServerPort());
        assertEquals(
            "{java} -jar forge-1.12.2-14.23.5.2847.jar --username {username} --gameDir {gameDir} --server {serverHost} --port {serverPort}",
            result.getResolvedConfig().getLaunchTemplate()
        );
        assertEquals("http://" + LauncherConfig.DEFAULT_SERVER_HOST + ":8081", result.getResolvedConfig().getAuthBaseUrl());
        assertEquals("obsidiangate-main", result.getResolvedConfig().getServerId());
        assertTrue(result.getResolvedConfig().getWorkingDirectory().endsWith("client"));
        assertEquals(3, result.getDownloadedFiles());
        assertEquals(0, result.getReusedFiles());
        assertTrue(result.getDownloadedBytes() > 0L);
        assertTrue(logLines.size() >= 4);

        ModpackSyncResult secondRun = service.sync(result.getResolvedConfig(), null);
        assertEquals(0, secondRun.getDownloadedFiles());
        assertEquals(3, secondRun.getReusedFiles());
        assertEquals(0, secondRun.getRemovedFiles());
        assertTrue(Files.isRegularFile(tempDirectory.resolve("client/.launcher-cache/verified-files.json")));
    }

    @Test
    void syncMovesObsoleteModEntriesOutOfModsDirectory() throws Exception {
        Path sourceDirectory = Files.createDirectories(tempDirectory.resolve("source"));
        Path currentMod = writeFile(sourceDirectory, "mods/current.jar", "current-mod");

        Path manifest = tempDirectory.resolve("manifest.json");
        String manifestJson = "{\n"
            + "  \"schemaVersion\": 1,\n"
            + "  \"id\": \"mc-rpg\",\n"
            + "  \"version\": \"2026.05.19\",\n"
            + "  \"baseUrl\": \"" + sourceDirectory.toUri().toURL().toString() + "\",\n"
            + "  \"files\": [\n"
            + fileJson("mods/current.jar", currentMod) + "\n"
            + "  ]\n"
            + "}\n";
        Files.write(manifest, manifestJson.getBytes(StandardCharsets.UTF_8));

        Path clientDirectory = Files.createDirectories(tempDirectory.resolve("client"));
        writeFile(clientDirectory, "mods/old.jar", "old-mod");
        writeFile(clientDirectory, "mods/memory_repo/nested.jar", "nested-old-mod");
        writeFile(clientDirectory, "config/local.cfg", "keep=true");

        LauncherConfig config = LauncherConfig.defaults();
        config.setManifestUrl(manifest.toUri().toURL().toString());
        config.setGameDirectory(clientDirectory.toString());

        ModpackSyncResult result = new ModpackSyncService(new ModpackManifestClient()).sync(config, null);

        assertTrue(Files.exists(clientDirectory.resolve("mods/current.jar")));
        assertFalse(Files.exists(clientDirectory.resolve("mods/old.jar")));
        assertFalse(Files.exists(clientDirectory.resolve("mods/memory_repo")));
        assertTrue(Files.exists(clientDirectory.resolve("config/local.cfg")));
        assertEquals(1, result.getDownloadedFiles());
        assertEquals(2, result.getRemovedFiles());

        Path backupDirectory = onlyChild(clientDirectory.resolve(".obsolete-mods"));
        assertTrue(Files.exists(backupDirectory.resolve("old.jar")));
        assertTrue(Files.exists(backupDirectory.resolve("memory_repo/nested.jar")));
    }

    @Test
    void previewLeavesObsoleteModEntriesInPlace() throws Exception {
        Path sourceDirectory = Files.createDirectories(tempDirectory.resolve("source"));
        Path currentMod = writeFile(sourceDirectory, "mods/current.jar", "current-mod");

        Path manifest = tempDirectory.resolve("manifest.json");
        String manifestJson = "{\n"
            + "  \"schemaVersion\": 1,\n"
            + "  \"baseUrl\": \"" + sourceDirectory.toUri().toURL().toString() + "\",\n"
            + "  \"files\": [\n"
            + fileJson("mods/current.jar", currentMod) + "\n"
            + "  ]\n"
            + "}\n";
        Files.write(manifest, manifestJson.getBytes(StandardCharsets.UTF_8));

        Path clientDirectory = Files.createDirectories(tempDirectory.resolve("client"));
        writeFile(clientDirectory, "mods/old.jar", "old-mod");

        LauncherConfig config = LauncherConfig.defaults();
        config.setManifestUrl(manifest.toUri().toURL().toString());
        config.setGameDirectory(clientDirectory.toString());

        ModpackSyncPreviewResult previewResult = new ModpackSyncService(new ModpackManifestClient()).preview(config, null);

        assertEquals(1, previewResult.getDownloadFiles());
        assertTrue(Files.exists(clientDirectory.resolve("mods/old.jar")));
        assertFalse(Files.exists(clientDirectory.resolve(".obsolete-mods")));
    }

    @Test
    void syncRejectsPathsOutsideGameDirectory() throws Exception {
        Path sourceDirectory = Files.createDirectories(tempDirectory.resolve("source"));
        Path payload = writeFile(sourceDirectory, "mods/examplemod.jar", "example-mod");

        Path manifest = tempDirectory.resolve("manifest.json");
        String manifestJson = "{\n"
            + "  \"schemaVersion\": 1,\n"
            + "  \"baseUrl\": \"" + sourceDirectory.toUri().toURL().toString() + "\",\n"
            + "  \"files\": [\n"
            + "    {\n"
            + "      \"path\": \"../escape.txt\",\n"
            + "      \"url\": \"mods/examplemod.jar\",\n"
            + "      \"sha256\": \"" + ChecksumUtils.sha256(payload) + "\"\n"
            + "    }\n"
            + "  ]\n"
            + "}\n";
        Files.write(manifest, manifestJson.getBytes(StandardCharsets.UTF_8));

        LauncherConfig config = LauncherConfig.defaults();
        config.setManifestUrl(manifest.toUri().toURL().toString());
        config.setGameDirectory(tempDirectory.resolve("client").toString());

        ModpackSyncService service = new ModpackSyncService(new ModpackManifestClient());

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> service.sync(config, null)
        );

        assertEquals("Путь файла выходит за пределы папки игры: ../escape.txt", exception.getMessage());
    }

    @Test
    void previewReportsMissingAndOutdatedFilesWithoutDownloading() throws Exception {
        Path sourceDirectory = Files.createDirectories(tempDirectory.resolve("source"));
        Path forgeJar = writeFile(sourceDirectory, "forge-1.12.2-14.23.5.2847.jar", "forge-client");
        Path modJar = writeFile(sourceDirectory, "mods/examplemod.jar", "example-mod");
        Path configFile = writeFile(sourceDirectory, "config/rpg.cfg", "difficulty=hard");

        Path manifest = tempDirectory.resolve("manifest.json");
        Files.write(
            manifest,
            buildManifest(sourceDirectory, forgeJar, modJar, configFile).getBytes(StandardCharsets.UTF_8)
        );

        Path clientDirectory = Files.createDirectories(tempDirectory.resolve("client"));
        Files.copy(forgeJar, clientDirectory.resolve("forge-1.12.2-14.23.5.2847.jar"));
        writeFile(clientDirectory, "mods/examplemod.jar", "stale-value");

        LauncherConfig config = LauncherConfig.defaults();
        config.setManifestUrl(manifest.toUri().toURL().toString());
        config.setGameDirectory(clientDirectory.toString());

        ModpackSyncService service = new ModpackSyncService(new ModpackManifestClient());
        ModpackSyncPreviewResult previewResult = service.preview(config, null);

        assertEquals(2, previewResult.getDownloadFiles());
        assertEquals(1, previewResult.getReusedFiles());
        assertEquals(Files.size(modJar) + Files.size(configFile), previewResult.getDownloadBytes());
        assertFalse(Files.exists(clientDirectory.resolve("config/rpg.cfg")));

        ModpackSyncPreviewEntry forgeEntry = findPreviewEntry(previewResult, "forge-1.12.2-14.23.5.2847.jar");
        assertEquals(ModpackSyncPreviewEntry.State.REUSED, forgeEntry.getState());
        assertEquals("up-to-date", forgeEntry.getReason());

        ModpackSyncPreviewEntry modEntry = findPreviewEntry(previewResult, "mods/examplemod.jar");
        assertEquals(ModpackSyncPreviewEntry.State.DOWNLOAD, modEntry.getState());
        assertEquals("sha256-mismatch", modEntry.getReason());

        ModpackSyncPreviewEntry configEntry = findPreviewEntry(previewResult, "config/rpg.cfg");
        assertEquals(ModpackSyncPreviewEntry.State.DOWNLOAD, configEntry.getState());
        assertEquals("missing", configEntry.getReason());
    }

    @Test
    void previewUsesSizeMismatchBeforeHashingOutdatedFiles() throws Exception {
        Path sourceDirectory = Files.createDirectories(tempDirectory.resolve("source"));
        Path expectedFile = writeFile(sourceDirectory, "mods/examplemod.jar", "expected-content");

        Path manifest = tempDirectory.resolve("manifest.json");
        String manifestJson = "{\n"
            + "  \"schemaVersion\": 1,\n"
            + "  \"baseUrl\": \"" + sourceDirectory.toUri().toURL().toString() + "\",\n"
            + "  \"files\": [\n"
            + fileJson("mods/examplemod.jar", expectedFile) + "\n"
            + "  ]\n"
            + "}\n";
        Files.write(manifest, manifestJson.getBytes(StandardCharsets.UTF_8));

        Path clientDirectory = Files.createDirectories(tempDirectory.resolve("client"));
        writeFile(clientDirectory, "mods/examplemod.jar", "short");

        LauncherConfig config = LauncherConfig.defaults();
        config.setManifestUrl(manifest.toUri().toURL().toString());
        config.setGameDirectory(clientDirectory.toString());

        ModpackSyncPreviewResult previewResult = new ModpackSyncService(new ModpackManifestClient()).preview(config, null);
        ModpackSyncPreviewEntry entry = findPreviewEntry(previewResult, "mods/examplemod.jar");

        assertEquals(ModpackSyncPreviewEntry.State.DOWNLOAD, entry.getState());
        assertEquals("size-mismatch", entry.getReason());
    }

    @Test
    void previewAppliesManifestLauncherSettingsToResolvedConfig() throws Exception {
        Path sourceDirectory = Files.createDirectories(tempDirectory.resolve("source"));
        Path forgeJar = writeFile(sourceDirectory, "forge-1.12.2-14.23.5.2847.jar", "forge-client");
        Path modJar = writeFile(sourceDirectory, "mods/examplemod.jar", "example-mod");
        Path configFile = writeFile(sourceDirectory, "config/rpg.cfg", "difficulty=hard");

        Path manifest = tempDirectory.resolve("manifest.json");
        Files.write(
            manifest,
            buildManifest(sourceDirectory, forgeJar, modJar, configFile).getBytes(StandardCharsets.UTF_8)
        );

        LauncherConfig config = LauncherConfig.defaults();
        config.setManifestUrl(manifest.toUri().toURL().toString());
        config.setGameDirectory(tempDirectory.resolve("client").toString());
        config.setServerHost("");
        config.setAuthBaseUrl("");
        config.setServerId("");

        ModpackSyncService service = new ModpackSyncService(new ModpackManifestClient());
        ModpackSyncPreviewResult previewResult = service.preview(config, null);

        assertEquals(LauncherConfig.DEFAULT_SERVER_HOST, previewResult.getResolvedConfig().getServerHost());
        assertEquals("http://" + LauncherConfig.DEFAULT_SERVER_HOST + ":8081", previewResult.getResolvedConfig().getAuthBaseUrl());
        assertEquals("obsidiangate-main", previewResult.getResolvedConfig().getServerId());
    }

    @Test
    void syncInstallsPortableRuntimeAndOverridesJavaCommand() throws Exception {
        PlatformInfo platform = PlatformInfo.current();
        String javaPath = "windows".equals(platform.getOs()) ? "bin/java.exe" : "bin/java";

        Path sourceDirectory = Files.createDirectories(tempDirectory.resolve("source"));
        Path forgeJar = writeFile(sourceDirectory, "forge-1.12.2-14.23.5.2847.jar", "forge-client");
        Path runtimeArchive = sourceDirectory.resolve("runtime/windows-x64/jre8.zip");
        createRuntimeArchive(runtimeArchive, javaPath, "portable-java");

        Path manifest = tempDirectory.resolve("manifest.json");
        Files.write(
            manifest,
            buildManifestWithRuntime(sourceDirectory, forgeJar, runtimeArchive, platform, javaPath)
                .getBytes(StandardCharsets.UTF_8)
        );

        LauncherConfig config = LauncherConfig.defaults();
        config.setManifestUrl(manifest.toUri().toURL().toString());
        config.setGameDirectory(tempDirectory.resolve("client").toString());
        config.setJavaCommand("");

        ModpackSyncService service = new ModpackSyncService(new ModpackManifestClient());
        ModpackSyncResult result = service.sync(config, null);

        Path resolvedJava = Paths.get(result.getResolvedConfig().getJavaCommand());
        assertTrue(Files.exists(resolvedJava));
        assertTrue(result.getResolvedConfig().getJavaCommand().contains("runtime"));

        Files.delete(runtimeArchive);
        ModpackSyncResult secondRun = service.sync(result.getResolvedConfig(), null);
        assertEquals(result.getResolvedConfig().getJavaCommand(), secondRun.getResolvedConfig().getJavaCommand());
        assertTrue(Files.exists(Paths.get(secondRun.getResolvedConfig().getJavaCommand())));
    }

    @Test
    void syncBootstrapsOfficialMinecraftAndForge() throws Exception {
        PlatformInfo platform = PlatformInfo.current();
        String mojangOs = toMojangOs(platform);
        String nativeClassifier = "natives-" + mojangOs;

        Path modpackSource = Files.createDirectories(tempDirectory.resolve("modpack-source"));
        Path modJar = writeFile(modpackSource, "mods/examplemod.jar", "example-mod");

        Path officialSource = Files.createDirectories(tempDirectory.resolve("official-source"));
        Path clientJar = writeFile(officialSource, "downloads/client.jar", "client-jar");
        Path baseLibrary = writeFile(officialSource, "downloads/base-lib.jar", "base-library");
        Path forgeLibrary = writeFile(officialSource, "downloads/forge-lib.jar", "forge-library");
        Path loggingConfig = writeFile(officialSource, "downloads/client-1.12.xml", "<Configuration/>");
        Path nativeLibrary = officialSource.resolve("downloads/native-lib-" + nativeClassifier + ".jar");
        createNativeArchive(nativeLibrary, "native/example.bin", "native-library");

        Path assetPayload = writeFile(officialSource, "downloads/asset-payload.bin", "asset-payload");
        String assetHash = ChecksumUtils.sha1(assetPayload);
        Path assetObject = officialSource.resolve("assets-objects/" + assetHash.substring(0, 2) + "/" + assetHash);
        Files.createDirectories(assetObject.getParent());
        Files.copy(assetPayload, assetObject);

        Path assetIndex = officialSource.resolve("downloads/1.12-assets.json");
        Files.write(
            assetIndex,
            ("{\n"
                + "  \"objects\": {\n"
                + "    \"minecraft/sounds/test.ogg\": {\n"
                + "      \"hash\": \"" + assetHash + "\",\n"
                + "      \"size\": " + Files.size(assetObject) + "\n"
                + "    }\n"
                + "  }\n"
                + "}\n").getBytes(StandardCharsets.UTF_8)
        );

        Path baseVersionJson = officialSource.resolve("1.12.2.json");
        Files.write(
            baseVersionJson,
            buildBaseVersionJson(
                clientJar,
                assetIndex,
                loggingConfig,
                baseLibrary,
                nativeLibrary,
                nativeClassifier,
                mojangOs
            ).getBytes(StandardCharsets.UTF_8)
        );

        Path versionManifest = officialSource.resolve("version_manifest_v2.json");
        Files.write(
            versionManifest,
            ("{\n"
                + "  \"versions\": [\n"
                + "    {\n"
                + "      \"id\": \"1.12.2\",\n"
                + "      \"url\": \"" + baseVersionJson.toUri().toURL().toString() + "\"\n"
                + "    }\n"
                + "  ]\n"
                + "}\n").getBytes(StandardCharsets.UTF_8)
        );

        Path forgeInstaller = officialSource.resolve("forge-installer.jar");
        createForgeInstaller(forgeInstaller, forgeLibrary);

        Path manifest = tempDirectory.resolve("manifest.json");
        Files.write(
            manifest,
            buildManifestWithOfficialBootstrap(modpackSource, modJar, versionManifest, forgeInstaller, officialSource)
                .getBytes(StandardCharsets.UTF_8)
        );

        LauncherConfig config = LauncherConfig.defaults();
        config.setManifestUrl(manifest.toUri().toURL().toString());
        Path clientDirectory = tempDirectory.resolve("client");
        config.setGameDirectory(clientDirectory.toString());
        config.setLaunchTemplate("");

        ModpackSyncService service = new ModpackSyncService(new ModpackManifestClient());
        ModpackSyncResult result = service.sync(config, null);

        assertTrue(Files.exists(clientDirectory.resolve("mods/examplemod.jar")));
        assertTrue(Files.exists(clientDirectory.resolve("versions/1.12.2/1.12.2.jar")));
        assertTrue(Files.exists(clientDirectory.resolve("versions/1.12.2-forge-14.23.5.2847/1.12.2-forge-14.23.5.2847.json")));
        assertTrue(Files.exists(
            clientDirectory.resolve("libraries/net/minecraftforge/forge/1.12.2-14.23.5.2847/forge-1.12.2-14.23.5.2847.jar")
        ));
        assertTrue(Files.exists(clientDirectory.resolve("libraries/com/example/base-lib/1.0/base-lib-1.0.jar")));
        assertTrue(Files.exists(clientDirectory.resolve("libraries/com/example/forge-lib/1.0/forge-lib-1.0.jar")));
        assertTrue(Files.exists(clientDirectory.resolve("assets/indexes/1.12.json")));
        assertTrue(Files.exists(clientDirectory.resolve("assets/objects/" + assetHash.substring(0, 2) + "/" + assetHash)));
        assertTrue(Files.exists(clientDirectory.resolve("assets/log_configs/client-1.12.xml")));
        assertTrue(Files.exists(clientDirectory.resolve("natives")));

        assertEquals(clientDirectory.toString(), result.getResolvedConfig().getWorkingDirectory());
        assertTrue(result.getResolvedConfig().getLaunchTemplate().contains("net.minecraft.launchwrapper.Launch"));
        assertTrue(result.getResolvedConfig().getLaunchTemplate().contains("--tweakClass"));
        assertFalse(result.getResolvedConfig().getLaunchTemplate().contains("\\"));

        List<String> command = new LaunchCommandBuilder().build(
            result.getResolvedConfig(),
            LaunchIdentity.authenticated("Soul4", "uuid-Soul4", "token-Soul4", null)
        );
        assertTrue(command.contains("net.minecraft.launchwrapper.Launch"));
        assertTrue(command.contains("--tweakClass"));
        assertTrue(command.contains("net.minecraftforge.fml.common.launcher.FMLTweaker"));
        assertTrue(command.contains("--server"));
        assertTrue(command.contains(LauncherConfig.DEFAULT_SERVER_HOST));
        assertTrue(command.contains("--port"));
        assertTrue(command.contains("25565"));
    }

    @Test
    void syncBootstrapsLegacyForgeInstallerFromInstallProfile() throws Exception {
        PlatformInfo platform = PlatformInfo.current();
        String mojangOs = toMojangOs(platform);
        String nativeClassifier = "natives-" + mojangOs;

        Path modpackSource = Files.createDirectories(tempDirectory.resolve("legacy-modpack-source"));
        Path modJar = writeFile(modpackSource, "mods/examplemod.jar", "example-mod");

        Path officialSource = Files.createDirectories(tempDirectory.resolve("legacy-official"));
        Path clientJar = writeFile(officialSource, "downloads/client.jar", "client-jar");
        Path baseLibrary = writeFile(officialSource, "downloads/base-lib.jar", "base-library");
        Path forgeLibrary = writeFile(officialSource, "forge-1.12.2-14.23.5.2847-universal.jar", "forge-library");
        Path legacyLibrary = writeFile(officialSource, "com/example/legacy-lib/1.0/legacy-lib-1.0.jar", "legacy-library");
        Path loggingConfig = writeFile(officialSource, "downloads/client-1.12.xml", "<Configuration/>");
        Path nativeLibrary = officialSource.resolve("downloads/native-lib-" + nativeClassifier + ".jar");
        createNativeArchive(nativeLibrary, "native/example.bin", "native-library");

        Path assetPayload = writeFile(officialSource, "downloads/asset-payload.bin", "asset-payload");
        String assetHash = ChecksumUtils.sha1(assetPayload);
        Path assetObject = officialSource.resolve("assets-objects/" + assetHash.substring(0, 2) + "/" + assetHash);
        Files.createDirectories(assetObject.getParent());
        Files.copy(assetPayload, assetObject);

        Path assetIndex = officialSource.resolve("downloads/1.12-assets.json");
        Files.write(
            assetIndex,
            ("{\n"
                + "  \"objects\": {\n"
                + "    \"minecraft/sounds/test.ogg\": {\n"
                + "      \"hash\": \"" + assetHash + "\",\n"
                + "      \"size\": " + Files.size(assetObject) + "\n"
                + "    }\n"
                + "  }\n"
                + "}\n").getBytes(StandardCharsets.UTF_8)
        );

        Path baseVersionJson = officialSource.resolve("1.12.2.json");
        Files.write(
            baseVersionJson,
            buildBaseVersionJson(
                clientJar,
                assetIndex,
                loggingConfig,
                baseLibrary,
                nativeLibrary,
                nativeClassifier,
                mojangOs
            ).getBytes(StandardCharsets.UTF_8)
        );

        Path versionManifest = officialSource.resolve("version_manifest_v2.json");
        Files.write(
            versionManifest,
            ("{\n"
                + "  \"versions\": [\n"
                + "    {\n"
                + "      \"id\": \"1.12.2\",\n"
                + "      \"url\": \"" + baseVersionJson.toUri().toURL().toString() + "\"\n"
                + "    }\n"
                + "  ]\n"
                + "}\n").getBytes(StandardCharsets.UTF_8)
        );

        Path forgeInstaller = officialSource.resolve("legacy-forge-installer.jar");
        createLegacyForgeInstaller(forgeInstaller, forgeLibrary, legacyLibrary, officialSource.toUri().toURL().toString());

        Path manifest = tempDirectory.resolve("legacy-manifest.json");
        Files.write(
            manifest,
            buildManifestWithOfficialBootstrap(modpackSource, modJar, versionManifest, forgeInstaller, officialSource)
                .getBytes(StandardCharsets.UTF_8)
        );

        LauncherConfig config = LauncherConfig.defaults();
        config.setManifestUrl(manifest.toUri().toURL().toString());
        Path clientDirectory = tempDirectory.resolve("legacy-client");
        config.setGameDirectory(clientDirectory.toString());
        config.setLaunchTemplate("");

        ModpackSyncService service = new ModpackSyncService(new ModpackManifestClient());
        ModpackSyncResult result = service.sync(config, null);

        Path versionJson = clientDirectory.resolve("versions/1.12.2-forge-14.23.5.2847/1.12.2-forge-14.23.5.2847.json");
        assertTrue(Files.exists(versionJson));
        String installedVersionJson = new String(Files.readAllBytes(versionJson), StandardCharsets.UTF_8);
        assertTrue(installedVersionJson.contains("1.12.2-forge-14.23.5.2847"));
        assertFalse(installedVersionJson.contains("1.12.2-forge1.12.2-14.23.5.2847"));
        assertTrue(Files.exists(
            clientDirectory.resolve("libraries/net/minecraftforge/forge/1.12.2-14.23.5.2847/forge-1.12.2-14.23.5.2847.jar")
        ));
        assertTrue(Files.exists(clientDirectory.resolve("libraries/com/example/legacy-lib/1.0/legacy-lib-1.0.jar")));
        assertFalse(Files.exists(clientDirectory.resolve("libraries/com/example/server-only/1.0/server-only-1.0.jar")));
        assertTrue(result.getResolvedConfig().getLaunchTemplate().contains("14.23.5.2847"));
    }

    private static String buildManifest(Path sourceDirectory, Path forgeJar, Path modJar, Path configFile) throws IOException {
        return "{\n"
            + "  \"schemaVersion\": 1,\n"
            + "  \"id\": \"mc-rpg\",\n"
            + "  \"version\": \"2026.05.05\",\n"
            + "  \"baseUrl\": \"" + sourceDirectory.toUri().toURL().toString() + "\",\n"
            + "  \"launcher\": {\n"
            + "    \"serverHost\": \"" + LauncherConfig.DEFAULT_SERVER_HOST + "\",\n"
            + "    \"serverPort\": 25565,\n"
            + "    \"authBaseUrl\": \"http://" + LauncherConfig.DEFAULT_SERVER_HOST + ":8081\",\n"
            + "    \"serverId\": \"obsidiangate-main\",\n"
            + "    \"workingDirectory\": \".\",\n"
            + "    \"launchTemplate\": \"{java} -jar forge-1.12.2-14.23.5.2847.jar --username {username} --gameDir {gameDir} --server {serverHost} --port {serverPort}\"\n"
            + "  },\n"
            + "  \"files\": [\n"
            + fileJson("forge-1.12.2-14.23.5.2847.jar", forgeJar) + ",\n"
            + fileJson("mods/examplemod.jar", modJar) + ",\n"
            + fileJson("config/rpg.cfg", configFile) + "\n"
            + "  ]\n"
            + "}\n";
    }

    private static String buildManifestWithRuntime(
        Path sourceDirectory,
        Path forgeJar,
        Path runtimeArchive,
        PlatformInfo platform,
        String javaPath
    ) throws IOException {
        return "{\n"
            + "  \"schemaVersion\": 1,\n"
            + "  \"id\": \"mc-rpg\",\n"
            + "  \"version\": \"2026.05.05\",\n"
            + "  \"baseUrl\": \"" + sourceDirectory.toUri().toURL().toString() + "\",\n"
            + "  \"launcher\": {\n"
            + "    \"serverHost\": \"" + LauncherConfig.DEFAULT_SERVER_HOST + "\",\n"
            + "    \"serverPort\": 25565,\n"
            + "    \"workingDirectory\": \".\",\n"
            + "    \"launchTemplate\": \"{java} -jar forge-1.12.2-14.23.5.2847.jar --username {username} --gameDir {gameDir} --server {serverHost} --port {serverPort}\"\n"
            + "  },\n"
            + "  \"runtime\": {\n"
            + "    \"packages\": [\n"
            + "      {\n"
            + "        \"os\": \"" + platform.getOs() + "\",\n"
            + "        \"arch\": \"" + platform.getArch() + "\",\n"
            + "        \"url\": \"runtime/windows-x64/jre8.zip\",\n"
            + "        \"sha256\": \"" + ChecksumUtils.sha256(runtimeArchive) + "\",\n"
            + "        \"size\": " + Files.size(runtimeArchive) + ",\n"
            + "        \"extractDir\": \"runtime/jre8\",\n"
            + "        \"javaPath\": \"" + javaPath + "\"\n"
            + "      }\n"
            + "    ]\n"
            + "  },\n"
            + "  \"files\": [\n"
            + fileJson("forge-1.12.2-14.23.5.2847.jar", forgeJar) + "\n"
            + "  ]\n"
            + "}\n";
    }

    private static String buildManifestWithOfficialBootstrap(
        Path sourceDirectory,
        Path modJar,
        Path versionManifest,
        Path forgeInstaller,
        Path officialSource
    ) throws IOException {
        return "{\n"
            + "  \"schemaVersion\": 1,\n"
            + "  \"id\": \"mc-rpg\",\n"
            + "  \"version\": \"2026.05.05\",\n"
            + "  \"baseUrl\": \"" + sourceDirectory.toUri().toURL().toString() + "\",\n"
            + "  \"launcher\": {\n"
            + "    \"serverHost\": \"" + LauncherConfig.DEFAULT_SERVER_HOST + "\",\n"
            + "    \"serverPort\": 25565,\n"
            + "    \"workingDirectory\": \".\",\n"
            + "    \"launchTemplate\": \"\"\n"
            + "  },\n"
            + "  \"minecraft\": {\n"
            + "    \"version\": \"1.12.2\",\n"
            + "    \"forgeVersion\": \"14.23.5.2847\",\n"
            + "    \"versionManifestUrl\": \"" + versionManifest.toUri().toURL().toString() + "\",\n"
            + "    \"forgeInstallerUrl\": \"" + forgeInstaller.toUri().toURL().toString() + "\",\n"
            + "    \"assetBaseUrl\": \"" + officialSource.resolve("assets-objects").toUri().toURL().toString() + "\"\n"
            + "  },\n"
            + "  \"files\": [\n"
            + fileJson("mods/examplemod.jar", modJar) + "\n"
            + "  ]\n"
            + "}\n";
    }

    private static String buildBaseVersionJson(
        Path clientJar,
        Path assetIndex,
        Path loggingConfig,
        Path baseLibrary,
        Path nativeLibrary,
        String nativeClassifier,
        String mojangOs
    ) throws IOException {
        return "{\n"
            + "  \"id\": \"1.12.2\",\n"
            + "  \"assetIndex\": {\n"
            + "    \"id\": \"1.12\",\n"
            + "    \"sha1\": \"" + ChecksumUtils.sha1(assetIndex) + "\",\n"
            + "    \"size\": " + Files.size(assetIndex) + ",\n"
            + "    \"url\": \"" + assetIndex.toUri().toURL().toString() + "\"\n"
            + "  },\n"
            + "  \"downloads\": {\n"
            + "    \"client\": {\n"
            + "      \"sha1\": \"" + ChecksumUtils.sha1(clientJar) + "\",\n"
            + "      \"size\": " + Files.size(clientJar) + ",\n"
            + "      \"url\": \"" + clientJar.toUri().toURL().toString() + "\"\n"
            + "    }\n"
            + "  },\n"
            + "  \"libraries\": [\n"
            + "    {\n"
            + "      \"name\": \"com.example:base-lib:1.0\",\n"
            + "      \"downloads\": {\n"
            + "        \"artifact\": {\n"
            + "          \"path\": \"com/example/base-lib/1.0/base-lib-1.0.jar\",\n"
            + "          \"sha1\": \"" + ChecksumUtils.sha1(baseLibrary) + "\",\n"
            + "          \"size\": " + Files.size(baseLibrary) + ",\n"
            + "          \"url\": \"" + baseLibrary.toUri().toURL().toString() + "\"\n"
            + "        }\n"
            + "      }\n"
            + "    },\n"
            + "    {\n"
            + "      \"name\": \"com.example:native-lib:1.0\",\n"
            + "      \"downloads\": {\n"
            + "        \"classifiers\": {\n"
            + "          \"" + nativeClassifier + "\": {\n"
            + "            \"path\": \"com/example/native-lib/1.0/native-lib-1.0-" + nativeClassifier + ".jar\",\n"
            + "            \"sha1\": \"" + ChecksumUtils.sha1(nativeLibrary) + "\",\n"
            + "            \"size\": " + Files.size(nativeLibrary) + ",\n"
            + "            \"url\": \"" + nativeLibrary.toUri().toURL().toString() + "\"\n"
            + "          }\n"
            + "        }\n"
            + "      },\n"
            + "      \"extract\": {\n"
            + "        \"exclude\": [\"META-INF/\"]\n"
            + "      },\n"
            + "      \"natives\": {\n"
            + "        \"" + mojangOs + "\": \"" + nativeClassifier + "\"\n"
            + "      }\n"
            + "    }\n"
            + "  ],\n"
            + "  \"logging\": {\n"
            + "    \"client\": {\n"
            + "      \"argument\": \"-Dlog4j.configurationFile=${path}\",\n"
            + "      \"file\": {\n"
            + "        \"id\": \"client-1.12.xml\",\n"
            + "        \"sha1\": \"" + ChecksumUtils.sha1(loggingConfig) + "\",\n"
            + "        \"size\": " + Files.size(loggingConfig) + ",\n"
            + "        \"url\": \"" + loggingConfig.toUri().toURL().toString() + "\"\n"
            + "      }\n"
            + "    }\n"
            + "  },\n"
            + "  \"mainClass\": \"net.minecraft.client.main.Main\",\n"
            + "  \"minecraftArguments\": \"--username ${auth_player_name} --version ${version_name} --gameDir ${game_directory} --assetsDir ${assets_root} --assetIndex ${assets_index_name} --uuid ${auth_uuid} --accessToken ${auth_access_token} --userType ${user_type} --versionType ${version_type}\"\n"
            + "}\n";
    }

    private static String fileJson(String relativePath, Path file) throws IOException {
        return "    {\n"
            + "      \"path\": \"" + relativePath + "\",\n"
            + "      \"sha256\": \"" + ChecksumUtils.sha256(file) + "\",\n"
            + "      \"size\": " + Files.size(file) + "\n"
            + "    }";
    }

    private static ModpackSyncPreviewEntry findPreviewEntry(ModpackSyncPreviewResult previewResult, String path) {
        for (ModpackSyncPreviewEntry entry : previewResult.getEntries()) {
            if (path.equals(entry.getPath())) {
                return entry;
            }
        }
        throw new AssertionError("Preview entry was not found: " + path);
    }

    private static Path onlyChild(Path directory) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            Path onlyChild = null;
            for (Path child : stream) {
                if (onlyChild != null) {
                    throw new AssertionError("Expected one child in " + directory);
                }
                onlyChild = child;
            }
            if (onlyChild == null) {
                throw new AssertionError("Expected child in " + directory);
            }
            return onlyChild;
        }
    }

    private static Path writeFile(Path root, String relativePath, String content) throws IOException {
        Path path = root.resolve(relativePath);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void createForgeInstaller(Path archive, Path forgeLibrary) throws IOException {
        Path parent = archive.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        byte[] forgeBytes = Files.readAllBytes(forgeLibrary);
        String versionJson = "{\n"
            + "  \"id\": \"1.12.2-forge-14.23.5.2847\",\n"
            + "  \"mainClass\": \"net.minecraft.launchwrapper.Launch\",\n"
            + "  \"minecraftArguments\": \"--username ${auth_player_name} --version ${version_name} --gameDir ${game_directory} --assetsDir ${assets_root} --assetIndex ${assets_index_name} --uuid ${auth_uuid} --accessToken ${auth_access_token} --userType ${user_type} --tweakClass net.minecraftforge.fml.common.launcher.FMLTweaker --versionType Forge\",\n"
            + "  \"libraries\": [\n"
            + "    {\n"
            + "      \"name\": \"net.minecraftforge:forge:1.12.2-14.23.5.2847\",\n"
            + "      \"downloads\": {\n"
            + "        \"artifact\": {\n"
            + "          \"path\": \"net/minecraftforge/forge/1.12.2-14.23.5.2847/forge-1.12.2-14.23.5.2847.jar\",\n"
            + "          \"sha1\": \"" + ChecksumUtils.sha1(forgeLibrary) + "\",\n"
            + "          \"size\": " + Files.size(forgeLibrary) + "\n"
            + "        }\n"
            + "      }\n"
            + "    },\n"
            + "    {\n"
            + "      \"name\": \"com.example:forge-lib:1.0\",\n"
            + "      \"downloads\": {\n"
            + "        \"artifact\": {\n"
            + "          \"path\": \"com/example/forge-lib/1.0/forge-lib-1.0.jar\",\n"
            + "          \"sha1\": \"" + ChecksumUtils.sha1(forgeLibrary) + "\",\n"
            + "          \"size\": " + Files.size(forgeLibrary) + ",\n"
            + "          \"url\": \"" + forgeLibrary.toUri().toURL().toString() + "\"\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}\n";

        try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(archive))) {
            outputStream.putNextEntry(new ZipEntry("version.json"));
            outputStream.write(versionJson.getBytes(StandardCharsets.UTF_8));
            outputStream.closeEntry();

            addZipDirectory(outputStream, "maven/");
            addZipDirectory(outputStream, "maven/net/");
            addZipDirectory(outputStream, "maven/net/minecraftforge/");
            addZipDirectory(outputStream, "maven/net/minecraftforge/forge/");
            addZipDirectory(outputStream, "maven/net/minecraftforge/forge/1.12.2-14.23.5.2847/");
            outputStream.putNextEntry(new ZipEntry(
                "maven/net/minecraftforge/forge/1.12.2-14.23.5.2847/forge-1.12.2-14.23.5.2847.jar"
            ));
            outputStream.write(forgeBytes);
            outputStream.closeEntry();
        }
    }

    private static void createLegacyForgeInstaller(
        Path archive,
        Path forgeLibrary,
        Path legacyLibrary,
        String legacyLibraryBaseUrl
    ) throws IOException {
        Path parent = archive.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        byte[] forgeBytes = Files.readAllBytes(forgeLibrary);
        String installProfile = "{\n"
            + "  \"install\": {\n"
            + "    \"target\": \"1.12.2-forge1.12.2-14.23.5.2847\",\n"
            + "    \"path\": \"net.minecraftforge:forge:1.12.2-14.23.5.2847\",\n"
            + "    \"filePath\": \"forge-1.12.2-14.23.5.2847-universal.jar\"\n"
            + "  },\n"
            + "  \"versionInfo\": {\n"
            + "    \"id\": \"1.12.2-forge1.12.2-14.23.5.2847\",\n"
            + "    \"mainClass\": \"net.minecraft.launchwrapper.Launch\",\n"
            + "    \"minecraftArguments\": \"--username ${auth_player_name} --version ${version_name} --gameDir ${game_directory} --assetsDir ${assets_root} --assetIndex ${assets_index_name} --uuid ${auth_uuid} --accessToken ${auth_access_token} --userType ${user_type} --tweakClass net.minecraftforge.fml.common.launcher.FMLTweaker --versionType Forge\",\n"
            + "    \"libraries\": [\n"
            + "      {\n"
            + "        \"name\": \"net.minecraftforge:forge:1.12.2-14.23.5.2847\"\n"
            + "      },\n"
            + "      {\n"
            + "        \"name\": \"com.example:legacy-lib:1.0\",\n"
            + "        \"url\": \"" + legacyLibraryBaseUrl + "\",\n"
            + "        \"checksums\": [\"" + ChecksumUtils.sha1(legacyLibrary) + "\"],\n"
            + "        \"clientreq\": true,\n"
            + "        \"serverreq\": true\n"
            + "      },\n"
            + "      {\n"
            + "        \"name\": \"com.example:server-only:1.0\",\n"
            + "        \"url\": \"" + legacyLibraryBaseUrl + "\",\n"
            + "        \"clientreq\": false,\n"
            + "        \"serverreq\": true\n"
            + "      }\n"
            + "    ]\n"
            + "  }\n"
            + "}\n";

        try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(archive))) {
            outputStream.putNextEntry(new ZipEntry("install_profile.json"));
            outputStream.write(installProfile.getBytes(StandardCharsets.UTF_8));
            outputStream.closeEntry();

            outputStream.putNextEntry(new ZipEntry("forge-1.12.2-14.23.5.2847-universal.jar"));
            outputStream.write(forgeBytes);
            outputStream.closeEntry();
        }
    }

    private static void createRuntimeArchive(Path archive, String javaPath, String payload) throws IOException {
        Path parent = archive.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(archive))) {
            addZipDirectory(outputStream, "bin/");
            outputStream.putNextEntry(new ZipEntry(javaPath));
            outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
            outputStream.closeEntry();
        }
    }

    private static void createNativeArchive(Path archive, String entryName, String payload) throws IOException {
        Path parent = archive.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(archive))) {
            addZipDirectory(outputStream, "META-INF/");
            addZipDirectory(outputStream, "native/");
            outputStream.putNextEntry(new ZipEntry(entryName));
            outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
            outputStream.closeEntry();
        }
    }

    private static void addZipDirectory(ZipOutputStream outputStream, String name) throws IOException {
        outputStream.putNextEntry(new ZipEntry(name));
        outputStream.closeEntry();
    }

    private static String toMojangOs(PlatformInfo platform) {
        if ("windows".equals(platform.getOs())) {
            return "windows";
        }
        if ("linux".equals(platform.getOs())) {
            return "linux";
        }
        return "osx";
    }
}
