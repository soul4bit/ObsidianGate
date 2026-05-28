package ru.mcrpg.launcher;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class MinecraftBootstrapService {

    private static final String DEFAULT_VERSION_MANIFEST_URL =
        "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String DEFAULT_ASSET_BASE_URL = "https://resources.download.minecraft.net/";
    private static final String DEFAULT_LIBRARY_BASE_URL = "https://libraries.minecraft.net/";
    private static final Pattern MINECRAFT_ARGUMENT_PATTERN = Pattern.compile("\\$\\{([a-zA-Z0-9_]+)\\}");
    private static final String NATIVES_MARKER_FILE = ".natives.properties";
    private static final int DOWNLOAD_READ_TIMEOUT_MS = 60000;

    private final ObjectMapper objectMapper;
    private final LaunchCommandBuilder commandTokenizer;

    public MinecraftBootstrapService() {
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        commandTokenizer = new LaunchCommandBuilder();
    }

    public MinecraftBootstrapResult bootstrap(
        MinecraftBootstrapSettings settings,
        Path gameDirectory,
        ModpackSyncService.LogSink logSink
    ) throws IOException {
        VerifiedFileCache verifiedFileCache = VerifiedFileCache.open(gameDirectory);
        try {
            return bootstrap(settings, gameDirectory, logSink, verifiedFileCache);
        } finally {
            saveCache(verifiedFileCache, logSink);
        }
    }

    MinecraftBootstrapResult bootstrap(
        MinecraftBootstrapSettings settings,
        Path gameDirectory,
        ModpackSyncService.LogSink logSink,
        VerifiedFileCache verifiedFileCache
    ) throws IOException {
        if (settings == null || !settings.isEnabled()) {
            return null;
        }

        String minecraftVersion = requireText(settings.getVersion(), "В manifest не указана версия Minecraft.");
        String forgeVersion = requireText(settings.getForgeVersion(), "В manifest не указана версия Forge.");
        String forgeVersionId = minecraftVersion + "-forge-" + forgeVersion;

        Path versionsDirectory = Files.createDirectories(gameDirectory.resolve("versions"));
        Path librariesDirectory = Files.createDirectories(gameDirectory.resolve("libraries"));
        Path assetsDirectory = Files.createDirectories(gameDirectory.resolve("assets"));
        Path cacheDirectory = Files.createDirectories(gameDirectory.resolve(".launcher-cache"));

        VersionMetadata baseVersion = loadBaseVersion(settings, minecraftVersion, logSink);
        writeJsonIfMissing(
            versionsDirectory.resolve(minecraftVersion).resolve(minecraftVersion + ".json"),
            baseVersion
        );

        ForgeInstallData forgeInstallData = ensureForgeVersionInstalled(
            settings,
            minecraftVersion,
            forgeVersion,
            forgeVersionId,
            versionsDirectory,
            librariesDirectory,
            cacheDirectory,
            verifiedFileCache,
            logSink
        );

        Path clientJar = ensureFileDownloaded(
            gameDirectory.resolve("versions").resolve(minecraftVersion).resolve(minecraftVersion + ".jar"),
            baseVersion.downloads == null ? null : baseVersion.downloads.client,
            verifiedFileCache,
            logSink,
            "Клиент Minecraft"
        );

        List<Library> mergedLibraries = mergeLibraries(baseVersion, forgeInstallData.versionMetadata);
        PlatformInfo platform = PlatformInfo.current();
        List<NativeLibrary> nativeLibraries = new ArrayList<NativeLibrary>();
        LinkedHashMap<String, Path> classpathEntries = new LinkedHashMap<String, Path>();
        List<Callable<Void>> libraryDownloadTasks = new ArrayList<Callable<Void>>();
        Set<String> scheduledLibraryTargets = new LinkedHashSet<String>();

        for (Library library : mergedLibraries) {
            if (!isAllowed(library, platform)) {
                continue;
            }

            if (library.downloads != null && library.downloads.artifact != null && hasText(library.downloads.artifact.path)) {
                Path artifactPath = librariesDirectory.resolve(toSystemPath(library.downloads.artifact.path)).normalize();
                Download artifactDownload = library.downloads.artifact;
                if (scheduledLibraryTargets.add(pathKey(artifactPath))) {
                    if (isForgeLibrary(library)) {
                        libraryDownloadTasks.add(() -> {
                            verifyExistingFile(artifactPath, artifactDownload, verifiedFileCache);
                            return null;
                        });
                    } else {
                        libraryDownloadTasks.add(() -> {
                            ensureFileDownloaded(
                                artifactPath,
                                artifactDownload,
                                verifiedFileCache,
                                logSink,
                                "Библиотека " + library.name
                            );
                            return null;
                        });
                    }
                }
                classpathEntries.put(artifactPath.toString(), artifactPath);
            }

            Download nativeDownload = resolveNativeDownload(library, platform);
            if (nativeDownload != null) {
                Path nativeArchive = librariesDirectory.resolve(toSystemPath(nativeDownload.path)).normalize();
                if (scheduledLibraryTargets.add(pathKey(nativeArchive))) {
                    libraryDownloadTasks.add(() -> {
                        ensureFileDownloaded(
                            nativeArchive,
                            nativeDownload,
                            verifiedFileCache,
                            logSink,
                            "Native-библиотека " + library.name
                        );
                        return null;
                    });
                }
                nativeLibraries.add(new NativeLibrary(nativeArchive, library.extract == null
                    ? Collections.<String>emptyList()
                    : library.extract.exclude));
            }
        }
        ParallelIo.run("minecraft-libraries", libraryDownloadTasks);

        classpathEntries.put(clientJar.toString(), clientJar);

        Path assetIndexFile = ensureAssetIndex(assetsDirectory, baseVersion.assetIndex, verifiedFileCache, logSink);
        AssetIndex assetIndex = readJson(assetIndexFile, AssetIndex.class);
        ensureAssets(settings, assetsDirectory, assetIndex, verifiedFileCache, logSink);

        Path loggingConfigFile = ensureLoggingConfig(assetsDirectory, baseVersion.logging, verifiedFileCache, logSink);
        Path nativesDirectory = ensureNatives(gameDirectory, forgeVersionId, platform, nativeLibraries, logSink);

        String launchTemplate = buildLaunchTemplate(
            forgeInstallData.versionMetadata,
            baseVersion,
            forgeVersionId,
            classpathEntries.values(),
            assetsDirectory,
            loggingConfigFile,
            nativesDirectory
        );

        log(logSink, "Официальный bootstrap Minecraft подготовлен: " + forgeVersionId);
        return new MinecraftBootstrapResult(launchTemplate, gameDirectory.toString());
    }

    private VersionMetadata loadBaseVersion(
        MinecraftBootstrapSettings settings,
        String minecraftVersion,
        ModpackSyncService.LogSink logSink
    ) throws IOException {
        String versionManifestUrl = hasText(settings.getVersionManifestUrl())
            ? settings.getVersionManifestUrl().trim()
            : DEFAULT_VERSION_MANIFEST_URL;
        log(logSink, "Загружаем manifest версий Minecraft: " + versionManifestUrl);

        VersionManifest versionManifest = readJson(new URL(versionManifestUrl), VersionManifest.class);
        VersionReference versionReference = null;
        if (versionManifest.versions != null) {
            for (VersionReference candidate : versionManifest.versions) {
                if (minecraftVersion.equals(candidate.id)) {
                    versionReference = candidate;
                    break;
                }
            }
        }
        if (versionReference == null || !hasText(versionReference.url)) {
            throw new IllegalArgumentException("Версия Minecraft не найдена в официальном manifest: " + minecraftVersion);
        }
        return readJson(new URL(versionReference.url), VersionMetadata.class);
    }

    private ForgeInstallData ensureForgeVersionInstalled(
        MinecraftBootstrapSettings settings,
        String minecraftVersion,
        String forgeVersion,
        String forgeVersionId,
        Path versionsDirectory,
        Path librariesDirectory,
        Path cacheDirectory,
        VerifiedFileCache verifiedFileCache,
        ModpackSyncService.LogSink logSink
    ) throws IOException {
        Path versionDirectory = Files.createDirectories(versionsDirectory.resolve(forgeVersionId));
        Path versionJsonPath = versionDirectory.resolve(forgeVersionId + ".json");

        if (Files.isRegularFile(versionJsonPath)) {
            VersionMetadata existingVersion = readJson(versionJsonPath, VersionMetadata.class);
            Download forgeArtifact = findForgeArtifact(existingVersion);
            Path forgeArtifactPath = forgeArtifact == null || !hasText(forgeArtifact.path)
                ? null
                : librariesDirectory.resolve(toSystemPath(forgeArtifact.path)).normalize();
            if (forgeArtifactPath != null && Files.isRegularFile(forgeArtifactPath)
                && matchesExistingFile(forgeArtifactPath, forgeArtifact == null ? null : forgeArtifact.sha1,
                    forgeArtifact == null ? null : forgeArtifact.size, verifiedFileCache)) {
                return new ForgeInstallData(existingVersion, versionJsonPath, forgeArtifactPath);
            }
        }

        String installerUrl = hasText(settings.getForgeInstallerUrl())
            ? settings.getForgeInstallerUrl().trim()
            : defaultForgeInstallerUrl(minecraftVersion, forgeVersion);
        Path installerDirectory = Files.createDirectories(cacheDirectory.resolve("forge"));
        Path installerFile = installerDirectory.resolve("forge-" + forgeVersionId + "-installer.jar");
        downloadIfNeeded(new URL(installerUrl), installerFile, null, null, verifiedFileCache, logSink, "Установщик Forge");

        return extractForgeInstaller(installerFile, versionJsonPath, librariesDirectory, forgeVersionId, verifiedFileCache, logSink);
    }

    private ForgeInstallData extractForgeInstaller(
        Path installerFile,
        Path versionJsonPath,
        Path librariesDirectory,
        String forgeVersionId,
        VerifiedFileCache verifiedFileCache,
        ModpackSyncService.LogSink logSink
    ) throws IOException {
        byte[] versionBytes;
        try (ZipFile zipFile = new ZipFile(installerFile.toFile())) {
            ZipEntry modernVersionEntry = zipFile.getEntry("version.json");
            VersionMetadata versionMetadata;
            byte[] forgeArtifactBytes;
            if (modernVersionEntry != null) {
                versionBytes = readEntry(zipFile, modernVersionEntry);
                versionMetadata = readJson(new ByteArrayInputStream(versionBytes), VersionMetadata.class);
                normalizeLegacyLibraryDownloads(versionMetadata);
            } else {
                LegacyInstallProfile installProfile = readJson(
                    new ByteArrayInputStream(readRequiredEntry(zipFile, "install_profile.json")),
                    LegacyInstallProfile.class
                );
                if (installProfile.versionInfo == null) {
                    throw new IOException("Forge installer не содержит метаданные версии.");
                }

                versionMetadata = installProfile.versionInfo;
                versionMetadata.id = forgeVersionId;
                normalizeLegacyLibraryDownloads(versionMetadata);

                Download legacyForgeArtifact = findForgeArtifact(versionMetadata);
                if (legacyForgeArtifact == null || !hasText(legacyForgeArtifact.path)) {
                    throw new IOException("Forge installer не описывает runtime artifact Forge.");
                }

                String legacyArtifactEntry = installProfile.install == null ? "" : installProfile.install.filePath;
                forgeArtifactBytes = readRequiredEntry(
                    zipFile,
                    requireText(legacyArtifactEntry, "Forge installer не содержит путь к runtime artifact.")
                );

                Path legacyForgeArtifactPath = librariesDirectory.resolve(toSystemPath(legacyForgeArtifact.path)).normalize();
                writeJson(versionJsonPath, versionMetadata);
                writeBytes(legacyForgeArtifactPath, forgeArtifactBytes);
                verifyExistingFile(legacyForgeArtifactPath, legacyForgeArtifact, verifiedFileCache);
                log(logSink, "Метаданные Forge установлены: " + forgeVersionId);
                return new ForgeInstallData(versionMetadata, versionJsonPath, legacyForgeArtifactPath);
            }

            if (!forgeVersionId.equals(versionMetadata.id)) {
                throw new IOException("Неожиданный Forge version id в installer: " + versionMetadata.id);
            }

            Download forgeArtifact = findForgeArtifact(versionMetadata);
            if (forgeArtifact == null || !hasText(forgeArtifact.path)) {
                throw new IOException("Forge installer не описывает runtime artifact Forge.");
            }

            String installerArtifactEntry = "maven/" + forgeArtifact.path.replace('\\', '/');
            forgeArtifactBytes = readRequiredEntry(zipFile, installerArtifactEntry);
            Path forgeArtifactPath = librariesDirectory.resolve(toSystemPath(forgeArtifact.path)).normalize();
            writeBytes(versionJsonPath, versionBytes);
            writeBytes(forgeArtifactPath, forgeArtifactBytes);
            verifyExistingFile(forgeArtifactPath, forgeArtifact, verifiedFileCache);
            log(logSink, "Метаданные Forge установлены: " + forgeVersionId);
            return new ForgeInstallData(versionMetadata, versionJsonPath, forgeArtifactPath);
        }
    }

    private void normalizeLegacyLibraryDownloads(VersionMetadata versionMetadata) {
        if (versionMetadata == null || versionMetadata.libraries == null) {
            return;
        }

        for (Library library : versionMetadata.libraries) {
            if (library == null || !hasText(library.name)) {
                continue;
            }
            if (library.downloads == null) {
                library.downloads = new LibraryDownloads();
            }
            if (library.downloads.artifact != null && hasText(library.downloads.artifact.path)) {
                continue;
            }

            Download artifact = new Download();
            artifact.path = mavenArtifactPath(library.name);
            artifact.url = resolveLibraryDownloadUrl(library, artifact.path);
            artifact.sha1 = firstChecksum(library.checksums);
            library.downloads.artifact = artifact;
        }
    }

    private static String resolveLibraryDownloadUrl(Library library, String artifactPath) {
        String baseUrl = hasText(library.url) ? library.url.trim() : DEFAULT_LIBRARY_BASE_URL;
        return (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + artifactPath;
    }

    private static String firstChecksum(List<String> checksums) {
        if (checksums == null) {
            return "";
        }
        for (String checksum : checksums) {
            if (hasText(checksum)) {
                return checksum.trim();
            }
        }
        return "";
    }

    private static String mavenArtifactPath(String name) {
        String[] parts = name.split(":");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Неподдерживаемая Maven-координата: " + name);
        }

        String groupPath = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length >= 4 && hasText(parts[3]) ? "-" + parts[3].trim() : "";
        String fileName = artifact + "-" + version + classifier + ".jar";
        return groupPath + "/" + artifact + "/" + version + "/" + fileName;
    }

    private Path ensureFileDownloaded(
        Path target,
        Download download,
        VerifiedFileCache verifiedFileCache,
        ModpackSyncService.LogSink logSink,
        String label
    ) throws IOException {
        if (download == null) {
            throw new IllegalArgumentException(label + " download metadata is missing.");
        }
        String rawUrl = requireText(download.url, label + " URL is missing.");
        downloadIfNeeded(new URL(rawUrl), target, download.sha1, download.size, verifiedFileCache, logSink, label);
        return target;
    }

    private static void verifyExistingFile(Path target, Download download, VerifiedFileCache verifiedFileCache)
        throws IOException {
        if (download != null) {
            verifyExistingFile(target, download.sha1, download.size, verifiedFileCache);
        }
    }

    private static void verifyExistingFile(
        Path target,
        String expectedSha1,
        Long expectedSize,
        VerifiedFileCache verifiedFileCache
    ) throws IOException {
        if (!Files.isRegularFile(target)) {
            throw new IOException("Не найден обязательный файл: " + target);
        }
        if (expectedSize != null && expectedSize.longValue() >= 0L) {
            long actualSize = Files.size(target);
            if (actualSize != expectedSize.longValue()) {
                throw new IOException("Размер не совпал для " + target + ". Ожидалось " + expectedSize + ", получено " + actualSize + ".");
            }
        }
        if (hasText(expectedSha1)) {
            if (verifiedFileCache != null && verifiedFileCache.matches(target, "SHA-1", expectedSha1, expectedSize)) {
                return;
            }
            String actualSha1 = ChecksumUtils.sha1(target);
            if (!actualSha1.equalsIgnoreCase(expectedSha1.trim())) {
                if (verifiedFileCache != null) {
                    verifiedFileCache.remove(target);
                }
                throw new IOException(
                    "SHA-1 не совпал для " + target + ". Ожидалось " + expectedSha1 + ", получено " + actualSha1 + "."
                );
            }
            if (verifiedFileCache != null) {
                verifiedFileCache.recordVerified(target, "SHA-1", expectedSha1);
            }
        }
    }

    private static void downloadIfNeeded(
        URL url,
        Path target,
        String expectedSha1,
        Long expectedSize,
        VerifiedFileCache verifiedFileCache,
        ModpackSyncService.LogSink logSink,
        String label
    ) throws IOException {
        if (Files.isRegularFile(target)) {
            if (matchesExistingFile(target, expectedSha1, expectedSize, verifiedFileCache)) {
                log(logSink, "Переиспользовано: " + target.getFileName());
                return;
            }
            verifiedFileCache.remove(target);
            Files.deleteIfExists(target);
        }

        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tempFile = Files.createTempFile(parent == null ? target.toAbsolutePath().getParent() : parent, "download-", ".part");
        try {
            log(logSink, "Скачиваем " + label + ": " + url);
            DownloadUtils.DownloadResult downloadResult = download(
                url,
                tempFile,
                hasText(expectedSha1) ? "SHA-1" : null
            );
            verifyDownloadedFile(tempFile, downloadResult, expectedSha1, expectedSize);
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
            if (verifiedFileCache != null && hasText(expectedSha1)) {
                verifiedFileCache.recordVerified(target, "SHA-1", expectedSha1);
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private static void verifyDownloadedFile(
        Path target,
        DownloadUtils.DownloadResult downloadResult,
        String expectedSha1,
        Long expectedSize
    ) throws IOException {
        if (expectedSize != null && expectedSize.longValue() >= 0L) {
            long actualSize = downloadResult.getBytes();
            if (actualSize != expectedSize.longValue()) {
                throw new IOException(
                    "Size mismatch for " + target + ". Expected " + expectedSize + ", got " + actualSize + "."
                );
            }
        }
        if (hasText(expectedSha1)) {
            String actualSha1 = downloadResult.getDigestHex();
            if (!actualSha1.equalsIgnoreCase(expectedSha1.trim())) {
                throw new IOException(
                    "SHA-1 mismatch for " + target + ". Expected " + expectedSha1 + ", got " + actualSha1 + "."
                );
            }
        }
    }

    private Path ensureAssetIndex(
        Path assetsDirectory,
        AssetIndexReference assetIndex,
        VerifiedFileCache verifiedFileCache,
        ModpackSyncService.LogSink logSink
    ) throws IOException {
        if (assetIndex == null) {
            throw new IllegalArgumentException("Не найдены метаданные asset index Minecraft.");
        }
        String assetIndexId = requireText(assetIndex.id, "Не указан id asset index Minecraft.");
        String assetIndexUrl = requireText(assetIndex.url, "Не указан URL asset index Minecraft.");
        Path target = assetsDirectory.resolve("indexes").resolve(assetIndexId + ".json");
        downloadIfNeeded(
            new URL(assetIndexUrl),
            target,
            assetIndex.sha1,
            assetIndex.size,
            verifiedFileCache,
            logSink,
            "Индекс ресурсов " + assetIndexId
        );
        return target;
    }

    private void ensureAssets(
        MinecraftBootstrapSettings settings,
        Path assetsDirectory,
        AssetIndex assetIndex,
        VerifiedFileCache verifiedFileCache,
        ModpackSyncService.LogSink logSink
    ) throws IOException {
        if (assetIndex == null || assetIndex.objects == null || assetIndex.objects.isEmpty()) {
            return;
        }

        String baseUrl = hasText(settings.getAssetBaseUrl()) ? settings.getAssetBaseUrl().trim() : DEFAULT_ASSET_BASE_URL;
        URL assetsBaseUrl = new URL(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
        log(logSink, "Asset-объектов: " + assetIndex.objects.size());

        List<Callable<Void>> assetDownloadTasks = new ArrayList<Callable<Void>>(assetIndex.objects.size());
        Set<String> scheduledAssetTargets = new LinkedHashSet<String>();
        for (Map.Entry<String, AssetObject> entry : assetIndex.objects.entrySet()) {
            AssetObject assetObject = entry.getValue();
            String hash = requireText(assetObject.hash, "Не указан hash asset для " + entry.getKey() + ".");
            String relativePath = hash.substring(0, 2) + "/" + hash;
            Path target = assetsDirectory.resolve("objects").resolve(toSystemPath(relativePath));
            if (!scheduledAssetTargets.add(pathKey(target))) {
                continue;
            }
            assetDownloadTasks.add(() -> {
                downloadIfNeeded(
                    new URL(assetsBaseUrl, relativePath),
                    target,
                    hash,
                    assetObject.size,
                    verifiedFileCache,
                    logSink,
                    "Ресурс " + entry.getKey()
                );
                return null;
            });
        }
        ParallelIo.run("minecraft-assets", assetDownloadTasks);
    }

    private Path ensureLoggingConfig(
        Path assetsDirectory,
        Logging logging,
        VerifiedFileCache verifiedFileCache,
        ModpackSyncService.LogSink logSink
    ) throws IOException {
        if (logging == null || logging.client == null || logging.client.file == null || !hasText(logging.client.file.id)) {
            return null;
        }

        Download file = logging.client.file;
        Path target = assetsDirectory.resolve("log_configs").resolve(file.id);
        downloadIfNeeded(
            new URL(requireText(file.url, "Не указан URL конфига логирования.")),
            target,
            file.sha1,
            file.size,
            verifiedFileCache,
            logSink,
            "Конфиг логирования"
        );
        return target;
    }

    private Path ensureNatives(
        Path gameDirectory,
        String forgeVersionId,
        PlatformInfo platform,
        List<NativeLibrary> nativeLibraries,
        ModpackSyncService.LogSink logSink
    ) throws IOException {
        if (nativeLibraries.isEmpty()) {
            return null;
        }

        String directoryName = sanitizeDirectoryName(forgeVersionId + "-" + platform.getOs() + "-" + platform.getArch());
        Path nativesDirectory = gameDirectory.resolve("natives").resolve(directoryName).normalize();
        Path markerFile = nativesDirectory.resolve(NATIVES_MARKER_FILE);
        String fingerprint = buildNativeFingerprint(nativeLibraries);

        if (Files.isRegularFile(markerFile) && Files.isDirectory(nativesDirectory)) {
            Properties properties = new Properties();
            try (InputStream inputStream = Files.newInputStream(markerFile)) {
                properties.load(inputStream);
            }
            if (fingerprint.equals(properties.getProperty("fingerprint", ""))) {
                log(logSink, "Переиспользованы native-библиотеки: " + nativesDirectory);
                return nativesDirectory;
            }
        }

        deleteRecursively(nativesDirectory);
        Files.createDirectories(nativesDirectory);
        for (NativeLibrary nativeLibrary : nativeLibraries) {
            extractNativeArchive(nativeLibrary, nativesDirectory);
        }

        Properties properties = new Properties();
        properties.setProperty("fingerprint", fingerprint);
        try (OutputStream outputStream = Files.newOutputStream(
            markerFile,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )) {
            properties.store(outputStream, "Извлеченные native-библиотеки");
        }
        log(logSink, "Native-библиотеки извлечены: " + nativesDirectory);
        return nativesDirectory;
    }

    private String buildLaunchTemplate(
        VersionMetadata forgeVersion,
        VersionMetadata baseVersion,
        String forgeVersionId,
        Iterable<Path> classpathEntries,
        Path assetsDirectory,
        Path loggingConfigFile,
        Path nativesDirectory
    ) {
        String mainClass = requireText(forgeVersion.mainClass, "Не указан Forge mainClass.");
        String minecraftArguments = requireText(
            forgeVersion.minecraftArguments,
            "Не указаны Forge minecraftArguments. Поддерживаются только legacy-метаданные запуска в стиле 1.12."
        );

        List<String> tokens = new ArrayList<String>();
        tokens.add("{java}");

        if (nativesDirectory != null) {
            tokens.add("-Djava.library.path=" + toTemplatePath(nativesDirectory));
        }
        if (loggingConfigFile != null && baseVersion.logging != null && baseVersion.logging.client != null
            && hasText(baseVersion.logging.client.argument)) {
            tokens.add(baseVersion.logging.client.argument.replace("${path}", toTemplatePath(loggingConfigFile)));
        }

        tokens.add("-cp");
        tokens.add(joinPaths(classpathEntries));
        tokens.add(mainClass);

        Map<String, String> replacements = new LinkedHashMap<String, String>();
        replacements.put("auth_player_name", "{username}");
        replacements.put("version_name", forgeVersionId);
        replacements.put("game_directory", "{gameDir}");
        replacements.put("assets_root", toTemplatePath(assetsDirectory));
        replacements.put("assets_index_name", requireText(baseVersion.assetIndex.id, "Не указан id asset index."));
        replacements.put("auth_uuid", "{uuid}");
        replacements.put("auth_access_token", "{accessToken}");
        replacements.put("user_type", "{userType}");
        replacements.put("version_type", "Forge");
        replacements.put("user_properties", "{}");

        for (String token : commandTokenizer.tokenize(minecraftArguments)) {
            tokens.add(replaceMinecraftArguments(token, replacements));
        }

        tokens.add("--server");
        tokens.add("{serverHost}");
        tokens.add("--port");
        tokens.add("{serverPort}");

        return toTemplate(tokens);
    }

    private static String replaceMinecraftArguments(String token, Map<String, String> replacements) {
        Matcher matcher = MINECRAFT_ARGUMENT_PATTERN.matcher(token);
        StringBuffer builder = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            String replacement = replacements.get(name);
            if (replacement == null) {
                throw new IllegalArgumentException("Неподдерживаемый placeholder запуска Minecraft: ${" + name + "}");
            }
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private static String toTemplate(List<String> tokens) {
        StringBuilder builder = new StringBuilder();
        for (String token : tokens) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(quoteIfNeeded(token));
        }
        return builder.toString();
    }

    private static String quoteIfNeeded(String token) {
        if (token == null) {
            return "";
        }
        if (token.indexOf(' ') < 0 && token.indexOf('\t') < 0 && token.indexOf('"') < 0) {
            return token;
        }
        return '"' + token.replace("\"", "\\\"") + '"';
    }

    private static String joinPaths(Iterable<Path> paths) {
        StringBuilder builder = new StringBuilder();
        for (Path path : paths) {
            if (builder.length() > 0) {
                builder.append(File.pathSeparatorChar);
            }
            builder.append(toTemplatePath(path));
        }
        return builder.toString();
    }

    private static String toTemplatePath(Path path) {
        return path.toAbsolutePath().normalize().toString().replace('\\', '/');
    }

    private static String pathKey(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    private static String buildNativeFingerprint(List<NativeLibrary> nativeLibraries) {
        StringBuilder builder = new StringBuilder();
        for (NativeLibrary nativeLibrary : nativeLibraries) {
            builder.append(nativeLibrary.archive.toAbsolutePath().normalize()).append('\n');
            for (String exclude : nativeLibrary.exclude) {
                builder.append(exclude).append('\n');
            }
        }
        return builder.toString();
    }

    private static void extractNativeArchive(NativeLibrary nativeLibrary, Path nativesDirectory) throws IOException {
        try (ZipFile zipFile = new ZipFile(nativeLibrary.archive.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String normalizedName = entry.getName().replace('\\', '/');
                if (entry.isDirectory() || shouldExclude(normalizedName, nativeLibrary.exclude)) {
                    continue;
                }

                Path target = resolveInside(nativesDirectory, normalizedName);
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                try (InputStream inputStream = zipFile.getInputStream(entry);
                     OutputStream outputStream = Files.newOutputStream(
                         target,
                         StandardOpenOption.CREATE,
                         StandardOpenOption.TRUNCATE_EXISTING,
                         StandardOpenOption.WRITE
                     )) {
                    copy(inputStream, outputStream);
                }
            }
        }
    }

    private static boolean matchesExistingFile(
        Path target,
        String expectedSha1,
        Long expectedSize,
        VerifiedFileCache verifiedFileCache
    ) {
        try {
            verifyExistingFile(target, expectedSha1, expectedSize, verifiedFileCache);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static boolean shouldExclude(String entryName, List<String> excludes) {
        if (excludes == null || excludes.isEmpty()) {
            return false;
        }
        String normalized = entryName.replace('\\', '/');
        for (String exclude : excludes) {
            String normalizedExclude = exclude == null ? "" : exclude.replace('\\', '/');
            if (!normalizedExclude.isEmpty() && normalized.startsWith(normalizedExclude)) {
                return true;
            }
        }
        return false;
    }

    private static Download resolveNativeDownload(Library library, PlatformInfo platform) {
        if (library == null || library.downloads == null || library.downloads.classifiers == null
            || library.natives == null || library.natives.isEmpty()) {
            return null;
        }

        String mojangOs = toMojangOsName(platform);
        String classifier = library.natives.get(mojangOs);
        if (!hasText(classifier)) {
            return null;
        }
        classifier = classifier.replace("${arch}", toNativeArch(platform));
        return library.downloads.classifiers.get(classifier);
    }

    private static boolean isAllowed(Library library, PlatformInfo platform) {
        if (library == null) {
            return false;
        }
        if (Boolean.FALSE.equals(library.clientreq)) {
            return false;
        }
        if (library.rules == null || library.rules.isEmpty()) {
            return true;
        }

        boolean allowed = false;
        for (Rule rule : library.rules) {
            if (applies(rule, platform)) {
                allowed = "allow".equalsIgnoreCase(rule.action);
            }
        }
        return allowed;
    }

    private static boolean applies(Rule rule, PlatformInfo platform) {
        if (rule == null) {
            return false;
        }
        if (rule.os == null) {
            return true;
        }

        String mojangOs = toMojangOsName(platform);
        if (hasText(rule.os.name) && !rule.os.name.trim().equalsIgnoreCase(mojangOs)) {
            return false;
        }
        if (hasText(rule.os.arch) && !rule.os.arch.trim().equalsIgnoreCase(platform.getArch())) {
            return false;
        }
        return true;
    }

    private static List<Library> mergeLibraries(VersionMetadata baseVersion, VersionMetadata forgeVersion) {
        List<Library> merged = new ArrayList<Library>();
        if (baseVersion.libraries != null) {
            merged.addAll(baseVersion.libraries);
        }
        if (forgeVersion.libraries != null) {
            merged.addAll(forgeVersion.libraries);
        }
        return merged;
    }

    private static Download findForgeArtifact(VersionMetadata versionMetadata) {
        if (versionMetadata == null || versionMetadata.libraries == null) {
            return null;
        }
        for (Library library : versionMetadata.libraries) {
            if (isForgeLibrary(library) && library.downloads != null) {
                return library.downloads.artifact;
            }
        }
        return null;
    }

    private static boolean isForgeLibrary(Library library) {
        return library != null && hasText(library.name) && library.name.startsWith("net.minecraftforge:forge:");
    }

    private static String defaultForgeInstallerUrl(String minecraftVersion, String forgeVersion) {
        String combined = minecraftVersion + "-" + forgeVersion;
        return "https://maven.minecraftforge.net/net/minecraftforge/forge/" + combined
            + "/forge-" + combined + "-installer.jar";
    }

    private static String toSystemPath(String value) {
        return value.replace('/', File.separatorChar).replace('\\', File.separatorChar);
    }

    private static String sanitizeDirectoryName(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    private static String toMojangOsName(PlatformInfo platform) {
        if ("macos".equals(platform.getOs())) {
            return "osx";
        }
        return platform.getOs();
    }

    private static String toNativeArch(PlatformInfo platform) {
        return "x86".equals(platform.getArch()) ? "32" : "64";
    }

    private <T> T readJson(URL url, Class<T> type) throws IOException {
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        try (InputStream inputStream = connection.getInputStream()) {
            return readJson(inputStream, type);
        }
    }

    private <T> T readJson(Path path, Class<T> type) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return readJson(inputStream, type);
        }
    }

    private <T> T readJson(InputStream inputStream, Class<T> type) throws IOException {
        return objectMapper.readValue(inputStream, type);
    }

    private void writeJsonIfMissing(Path target, Object value) throws IOException {
        if (Files.isRegularFile(target)) {
            return;
        }
        writeJson(target, value);
    }

    private void writeJson(Path target, Object value) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), value);
    }

    private static void writeBytes(Path target, byte[] bytes) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream outputStream = Files.newOutputStream(
            target,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )) {
            outputStream.write(bytes);
        }
    }

    private static byte[] readRequiredEntry(ZipFile zipFile, String entryName) throws IOException {
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null) {
            throw new IOException("В архиве нет записи: " + entryName);
        }

        return readEntry(zipFile, entry);
    }

    private static byte[] readEntry(ZipFile zipFile, ZipEntry entry) throws IOException {
        try (InputStream inputStream = zipFile.getInputStream(entry);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            copy(inputStream, outputStream);
            return outputStream.toByteArray();
        }
    }

    private static DownloadUtils.DownloadResult download(URL url, Path target, String digestAlgorithm)
        throws IOException {
        return DownloadUtils.downloadWithDigest(url, target, DOWNLOAD_READ_TIMEOUT_MS, digestAlgorithm);
    }

    private static void copy(InputStream inputStream, OutputStream outputStream) throws IOException {
        DownloadUtils.copy(inputStream, outputStream);
    }

    private static Path resolveInside(Path root, String entryName) {
        Path resolved = root.resolve(entryName).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Запись архива выходит за пределы целевой папки: " + entryName);
        }
        return resolved;
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        if (Files.isDirectory(path)) {
            try (java.util.stream.Stream<Path> stream = Files.list(path)) {
                Path[] children = stream.toArray(Path[]::new);
                for (Path child : children) {
                    deleteRecursively(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    private static void log(ModpackSyncService.LogSink logSink, String message) {
        if (logSink != null) {
            synchronized (logSink) {
                logSink.log(message);
            }
        }
    }

    private static void saveCache(VerifiedFileCache verifiedFileCache, ModpackSyncService.LogSink logSink) {
        try {
            verifiedFileCache.save();
        } catch (IOException exception) {
            log(logSink, "Кеш проверенных файлов не сохранен: " + exception.getMessage());
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static final class ForgeInstallData {
        private final VersionMetadata versionMetadata;
        private final Path versionJsonPath;
        private final Path forgeArtifactPath;

        private ForgeInstallData(VersionMetadata versionMetadata, Path versionJsonPath, Path forgeArtifactPath) {
            this.versionMetadata = versionMetadata;
            this.versionJsonPath = versionJsonPath;
            this.forgeArtifactPath = forgeArtifactPath;
        }
    }

    private static final class LegacyInstallProfile {
        public LegacyInstall install = new LegacyInstall();
        public VersionMetadata versionInfo;
    }

    private static final class LegacyInstall {
        public String filePath;
    }

    private static final class NativeLibrary {
        private final Path archive;
        private final List<String> exclude;

        private NativeLibrary(Path archive, List<String> exclude) {
            this.archive = archive;
            this.exclude = exclude == null ? Collections.<String>emptyList() : new ArrayList<String>(exclude);
        }
    }

    private static final class VersionManifest {
        public List<VersionReference> versions = new ArrayList<VersionReference>();
    }

    private static final class VersionReference {
        public String id;
        public String url;
    }

    private static final class VersionMetadata {
        public String id;
        public String inheritsFrom;
        public String mainClass;
        public String minecraftArguments;
        public String type;
        public AssetIndexReference assetIndex = new AssetIndexReference();
        public VersionDownloads downloads = new VersionDownloads();
        public List<Library> libraries = new ArrayList<Library>();
        public Logging logging = new Logging();
    }

    private static final class VersionDownloads {
        public Download client;
    }

    private static final class AssetIndexReference {
        public String id;
        public String sha1;
        public Long size;
        public String url;
    }

    private static final class AssetIndex {
        public Map<String, AssetObject> objects = new LinkedHashMap<String, AssetObject>();
    }

    private static final class AssetObject {
        public String hash;
        public Long size;
    }

    private static final class Library {
        public String name;
        public String url;
        public List<String> checksums = new ArrayList<String>();
        public Boolean clientreq;
        public Boolean serverreq;
        public LibraryDownloads downloads = new LibraryDownloads();
        public Map<String, String> natives = new LinkedHashMap<String, String>();
        public Extract extract = new Extract();
        public List<Rule> rules = new ArrayList<Rule>();
    }

    private static final class LibraryDownloads {
        public Download artifact;
        public Map<String, Download> classifiers = new LinkedHashMap<String, Download>();
    }

    private static final class Download {
        public String id;
        public String path;
        public String url;
        public String sha1;
        public Long size;
    }

    private static final class Extract {
        public List<String> exclude = new ArrayList<String>();
    }

    private static final class Rule {
        public String action;
        public RuleOs os = new RuleOs();
    }

    private static final class RuleOs {
        public String name;
        public String arch;
    }

    private static final class Logging {
        public LoggingClient client = new LoggingClient();
    }

    private static final class LoggingClient {
        public String argument;
        public Download file;
    }
}
