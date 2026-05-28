package ru.mcrpg.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class RuntimeSyncService {

    private static final String RUNTIME_MARKER_FILE = ".runtime-package.properties";
    private static final int RUNTIME_DOWNLOAD_READ_TIMEOUT_MS = 60000;

    public RuntimeResolution sync(
        LoadedManifest loadedManifest,
        ModpackManifest manifest,
        Path gameDirectory,
        ModpackSyncService.LogSink logSink
    ) throws IOException {
        RuntimePackage runtimePackage = resolvePackage(manifest.getRuntime(), PlatformInfo.current());
        if (runtimePackage == null) {
            return null;
        }

        Path installDirectory = resolveInstallDirectory(gameDirectory, runtimePackage);
        Path javaExecutable = resolveJavaExecutable(installDirectory, runtimePackage);
        if (isInstalled(installDirectory, javaExecutable, runtimePackage)) {
            log(logSink, "Портативная Java уже установлена: " + javaExecutable);
            return new RuntimeResolution(runtimePackage, javaExecutable);
        }

        URL downloadUrl = resolveDownloadUrl(loadedManifest, manifest, runtimePackage.getUrl());
        log(logSink, "Скачиваем runtime Java: " + downloadUrl);

        Path parent = installDirectory.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path archiveFile = Files.createTempFile(gameDirectory, "runtime-", ".zip");
        Path tempInstallDirectory = Files.createTempDirectory(gameDirectory, "runtime-install-");
        try {
            DownloadUtils.DownloadResult downloadResult = download(downloadUrl, archiveFile);
            verifyArchive(runtimePackage, downloadResult);
            unzip(archiveFile, tempInstallDirectory);

            Path extractedJava = resolveJavaExecutable(tempInstallDirectory, runtimePackage);
            if (!Files.isRegularFile(extractedJava)) {
                throw new IOException("Архив runtime не содержит исполняемый файл Java: " + runtimePackage.getJavaPath());
            }

            deleteRecursively(installDirectory);
            Files.move(tempInstallDirectory, installDirectory, StandardCopyOption.REPLACE_EXISTING);

            javaExecutable = resolveJavaExecutable(installDirectory, runtimePackage);
            javaExecutable.toFile().setExecutable(true, false);
            writeMarker(installDirectory, runtimePackage);
            log(logSink, "Портативная Java установлена: " + javaExecutable);
            return new RuntimeResolution(runtimePackage, javaExecutable);
        } finally {
            Files.deleteIfExists(archiveFile);
            deleteRecursively(tempInstallDirectory);
        }
    }

    private static RuntimePackage resolvePackage(ModpackRuntime runtime, PlatformInfo platform) {
        if (runtime == null) {
            return null;
        }

        List<RuntimePackage> packages = runtime.getPackages();
        if (packages == null || packages.isEmpty()) {
            return null;
        }

        for (RuntimePackage runtimePackage : packages) {
            if (matches(runtimePackage, platform)) {
                validatePackage(runtimePackage);
                return runtimePackage;
            }
        }

        throw new IllegalArgumentException(
            "Для платформы " + platform.getOs() + "/" + platform.getArch() + " не найден пакет runtime."
        );
    }

    private static boolean matches(RuntimePackage runtimePackage, PlatformInfo platform) {
        return matchesValue(runtimePackage.getOs(), platform.getOs())
            && matchesValue(runtimePackage.getArch(), platform.getArch());
    }

    private static boolean matchesValue(String expected, String actual) {
        return expected == null || expected.trim().isEmpty() || expected.trim().equalsIgnoreCase(actual);
    }

    private static void validatePackage(RuntimePackage runtimePackage) {
        requireText(runtimePackage.getUrl(), "В runtime-пакете не указан url.");
        requireText(runtimePackage.getSha256(), "В runtime-пакете не указан sha256.");
        requireText(runtimePackage.getExtractDir(), "В runtime-пакете не указан extractDir.");
        requireText(runtimePackage.getJavaPath(), "В runtime-пакете не указан javaPath.");
    }

    private static boolean isInstalled(Path installDirectory, Path javaExecutable, RuntimePackage runtimePackage)
        throws IOException {
        if (!Files.isRegularFile(javaExecutable)) {
            return false;
        }

        Path markerFile = installDirectory.resolve(RUNTIME_MARKER_FILE);
        if (!Files.isRegularFile(markerFile)) {
            return false;
        }

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(markerFile)) {
            properties.load(inputStream);
        }

        return runtimePackage.getSha256().equalsIgnoreCase(properties.getProperty("sha256", ""))
            && runtimePackage.getJavaPath().equals(properties.getProperty("javaPath", ""));
    }

    private static void writeMarker(Path installDirectory, RuntimePackage runtimePackage) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("sha256", runtimePackage.getSha256());
        properties.setProperty("javaPath", runtimePackage.getJavaPath());
        properties.setProperty("url", runtimePackage.getUrl());

        try (OutputStream outputStream = Files.newOutputStream(
            installDirectory.resolve(RUNTIME_MARKER_FILE),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )) {
            properties.store(outputStream, "Установленный портативный runtime");
        }
    }

    private static URL resolveDownloadUrl(LoadedManifest loadedManifest, ModpackManifest manifest, String rawUrl)
        throws IOException {
        return DownloadUrlResolver.resolve(
            loadedManifest.getSourceUrl(),
            manifest.getBaseUrl(),
            requireText(rawUrl, "В runtime-пакете не указан url.")
        );
    }

    private static void verifyArchive(RuntimePackage runtimePackage, DownloadUtils.DownloadResult downloadResult)
        throws IOException {
        if (runtimePackage.getSize() != null && runtimePackage.getSize().longValue() >= 0L) {
            long actualSize = downloadResult.getBytes();
            if (actualSize != runtimePackage.getSize().longValue()) {
                throw new IOException(
                    "Размер архива runtime не совпал. Ожидалось " + runtimePackage.getSize()
                        + ", получено " + actualSize + "."
                );
            }
        }

        String actualSha256 = downloadResult.getDigestHex();
        if (!actualSha256.equalsIgnoreCase(runtimePackage.getSha256())) {
            throw new IOException(
                "SHA-256 архива runtime не совпал. Ожидалось " + runtimePackage.getSha256()
                    + ", получено " + actualSha256 + "."
            );
        }
    }

    private static void unzip(Path archiveFile, Path targetDirectory) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(archiveFile))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path targetPath = resolveZipEntry(targetDirectory, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Path parent = targetPath.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    try (OutputStream outputStream = Files.newOutputStream(
                        targetPath,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE
                    )) {
                        DownloadUtils.copy(zipInputStream, outputStream);
                    }
                }
                zipInputStream.closeEntry();
            }
        }
    }

    private static Path resolveZipEntry(Path targetDirectory, String entryName) {
        String normalized = entryName.replace('\\', '/');
        Path resolved = targetDirectory.resolve(normalized).normalize();
        if (!resolved.startsWith(targetDirectory)) {
            throw new IllegalArgumentException("Архив runtime содержит некорректный путь: " + entryName);
        }
        return resolved;
    }

    private static DownloadUtils.DownloadResult download(URL downloadUrl, Path target) throws IOException {
        return DownloadUtils.downloadWithDigest(downloadUrl, target, RUNTIME_DOWNLOAD_READ_TIMEOUT_MS, "SHA-256");
    }

    private static Path resolveInstallDirectory(Path gameDirectory, RuntimePackage runtimePackage) {
        Path relative = Paths.get(requireText(runtimePackage.getExtractDir(), "В runtime-пакете не указан extractDir."));
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("runtime extractDir должен быть относительным.");
        }
        Path resolved = gameDirectory.resolve(relative).normalize();
        if (!resolved.startsWith(gameDirectory)) {
            throw new IllegalArgumentException("runtime extractDir выходит за пределы папки игры.");
        }
        return resolved;
    }

    private static Path resolveJavaExecutable(Path installDirectory, RuntimePackage runtimePackage) {
        Path relative = Paths.get(requireText(runtimePackage.getJavaPath(), "В runtime-пакете не указан javaPath."));
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("runtime javaPath должен быть относительным.");
        }
        Path resolved = installDirectory.resolve(relative).normalize();
        if (!resolved.startsWith(installDirectory)) {
            throw new IllegalArgumentException("runtime javaPath выходит за пределы папки runtime.");
        }
        return resolved;
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        if (Files.isDirectory(path)) {
            try (Stream<Path> stream = Files.list(path)) {
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
            logSink.log(message);
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
}
