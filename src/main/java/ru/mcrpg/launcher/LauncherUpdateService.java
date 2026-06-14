package ru.mcrpg.launcher;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

final class LauncherUpdateService {

    private static final int LAUNCHER_DOWNLOAD_READ_TIMEOUT_MS = 60000;

    interface LogSink {
        void log(String message);
    }

    interface ProgressSink {
        void progress(long downloadedBytes, long totalBytes, long elapsedMillis);
    }

    private final Path currentLauncherPath;

    LauncherUpdateService() {
        this(detectCurrentLauncherPath());
    }

    LauncherUpdateService(Path currentLauncherPath) {
        this.currentLauncherPath = currentLauncherPath;
    }

    LauncherUpdateCandidate findUpdate(LoadedManifest loadedManifest, String currentVersion) throws IOException {
        if (loadedManifest == null || loadedManifest.getManifest() == null) {
            return null;
        }

        LauncherUpdateSettings settings = loadedManifest.getManifest().getLauncherUpdate();
        if (!isComplete(settings)) {
            return null;
        }

        URL downloadUrl = DownloadUrlResolver.resolve(loadedManifest.getSourceUrl(), settings.getUrl());
        if (!shouldOfferUpdate(settings, currentVersion)) {
            return null;
        }

        return new LauncherUpdateCandidate(settings, currentVersion, downloadUrl, currentLauncherPath);
    }

    void installAndRestart(LauncherUpdateCandidate candidate, LogSink logSink) throws IOException {
        installAndRestart(candidate, logSink, null);
    }

    void installAndRestart(LauncherUpdateCandidate candidate, LogSink logSink, ProgressSink progressSink) throws IOException {
        if (candidate == null) {
            throw new IllegalArgumentException("Обновление лаунчера недоступно.");
        }
        if (!candidate.isInstallSupported()) {
            throw new IOException("Автообновление доступно только при запуске лаунчера из jar-файла.");
        }

        Path updateFile = downloadUpdate(candidate, logSink, progressSink);
        Path script = createRestartScript(updateFile, candidate.getCurrentLauncherPath());
        log(logSink, "Запускаем скрипт обновления лаунчера: " + script);
        startUpdaterScript(script);
    }

    private Path downloadUpdate(LauncherUpdateCandidate candidate, LogSink logSink, ProgressSink progressSink) throws IOException {
        Path updatesDirectory = Paths.get(
            System.getProperty("user.home"),
            ".obsidian-gate-launcher",
            "updates"
        ).toAbsolutePath().normalize();
        Files.createDirectories(updatesDirectory);

        Path fileName = Paths.get(candidate.getCurrentLauncherPath().getFileName().toString());
        Path target = updatesDirectory.resolve(fileName).normalize();
        log(logSink, "Скачиваем обновление лаунчера " + candidate.getVersion() + " из " + candidate.getDownloadUrl());
        long startedAt = System.nanoTime();
        long totalBytes = candidate.getSize() == null ? -1L : candidate.getSize().longValue();
        final long[] downloadedBytes = {0L};
        DownloadUtils.download(candidate.getDownloadUrl(), target, LAUNCHER_DOWNLOAD_READ_TIMEOUT_MS, bytes -> {
            downloadedBytes[0] += bytes;
            reportProgress(progressSink, downloadedBytes[0], totalBytes, startedAt);
        });
        reportProgress(progressSink, downloadedBytes[0], totalBytes, startedAt);
        verifyUpdateFile(target, candidate);
        return target;
    }

    private void verifyUpdateFile(Path updateFile, LauncherUpdateCandidate candidate) throws IOException {
        if (candidate.getSize() != null && candidate.getSize().longValue() >= 0L) {
            long actualSize = Files.size(updateFile);
            if (actualSize != candidate.getSize().longValue()) {
                throw new IOException(
                    "Размер обновления лаунчера не совпал. Ожидалось " + candidate.getSize()
                        + ", получено " + actualSize + "."
                );
            }
        }

        String actualSha256 = ChecksumUtils.sha256(updateFile);
        if (!actualSha256.equalsIgnoreCase(candidate.getSha256())) {
            throw new IOException(
                "SHA-256 обновления лаунчера не совпал. Ожидалось " + candidate.getSha256()
                    + ", получено " + actualSha256 + "."
            );
        }
    }

    private static Path createRestartScript(Path updateFile, Path currentLauncherPath) throws IOException {
        Path script = Files.createTempFile("obsidiangate-launcher-update-", isWindows() ? ".ps1" : ".sh");
        String javaCommand = resolveJavaCommand();
        String content = isWindows()
            ? windowsScript(updateFile, currentLauncherPath, javaCommand)
            : unixScript(updateFile, currentLauncherPath, javaCommand);
        Files.write(script, content.getBytes(StandardCharsets.UTF_8));
        script.toFile().setExecutable(true, false);
        return script;
    }

    private static void startUpdaterScript(Path script) throws IOException {
        ProcessBuilder builder = isWindows()
            ? new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-WindowStyle",
                "Hidden",
                "-File",
                script.toString()
            )
            : new ProcessBuilder("sh", script.toString());
        builder.start();
    }

    private static String windowsScript(Path updateFile, Path currentLauncherPath, String javaCommand) {
        String targetDirectory = currentLauncherPath.toAbsolutePath().getParent().toString();
        return "$ErrorActionPreference = 'Stop'\r\n"
            + "$src = " + powerShellQuote(updateFile.toAbsolutePath().toString()) + "\r\n"
            + "$dst = " + powerShellQuote(currentLauncherPath.toAbsolutePath().toString()) + "\r\n"
            + "$java = " + powerShellQuote(javaCommand) + "\r\n"
            + "$workdir = " + powerShellQuote(targetDirectory) + "\r\n"
            + "Start-Sleep -Seconds 2\r\n"
            + "Copy-Item -LiteralPath $src -Destination $dst -Force\r\n"
            + "Start-Process -FilePath $java -ArgumentList @('-jar', $dst, '--update-complete') -WorkingDirectory $workdir\r\n";
    }

    private static String unixScript(Path updateFile, Path currentLauncherPath, String javaCommand) {
        return "#!/bin/sh\n"
            + "sleep 2\n"
            + "mv -f " + shellQuote(updateFile.toAbsolutePath().toString()) + " "
            + shellQuote(currentLauncherPath.toAbsolutePath().toString()) + "\n"
            + "chmod +x " + shellQuote(currentLauncherPath.toAbsolutePath().toString()) + "\n"
            + shellQuote(javaCommand) + " -jar " + shellQuote(currentLauncherPath.toAbsolutePath().toString())
            + " --update-complete"
            + " >/dev/null 2>&1 &\n"
            + "rm -- \"$0\"\n";
    }

    private boolean shouldOfferUpdate(LauncherUpdateSettings settings, String currentVersion) throws IOException {
        if (currentLauncherPath != null && Files.isRegularFile(currentLauncherPath)) {
            String currentSha256 = ChecksumUtils.sha256(currentLauncherPath);
            return !currentSha256.equalsIgnoreCase(settings.getSha256());
        }
        return isNewerVersion(settings.getVersion(), currentVersion);
    }

    static boolean isNewerVersion(String candidateVersion, String currentVersion) {
        String candidate = normalizeVersion(candidateVersion);
        String current = normalizeVersion(currentVersion);
        if (!hasText(candidate) || !hasText(current) || "dev".equalsIgnoreCase(current)) {
            return false;
        }
        if (candidate.equalsIgnoreCase(current)) {
            return false;
        }

        String[] candidateParts = candidate.split("[^A-Za-z0-9]+");
        String[] currentParts = current.split("[^A-Za-z0-9]+");
        int max = Math.max(candidateParts.length, currentParts.length);
        for (int index = 0; index < max; index++) {
            String left = index < candidateParts.length ? candidateParts[index] : "0";
            String right = index < currentParts.length ? currentParts[index] : "0";
            int comparison = compareVersionPart(left, right);
            if (comparison != 0) {
                return comparison > 0;
            }
        }
        return false;
    }

    private static int compareVersionPart(String left, String right) {
        boolean leftNumeric = left.matches("\\d+");
        boolean rightNumeric = right.matches("\\d+");
        if (leftNumeric && rightNumeric) {
            long leftValue = Long.parseLong(left);
            long rightValue = Long.parseLong(right);
            return Long.compare(leftValue, rightValue);
        }
        return left.compareToIgnoreCase(right);
    }

    private static String normalizeVersion(String value) {
        return value == null ? "" : value.trim().replace("-SNAPSHOT", "");
    }

    private static boolean isComplete(LauncherUpdateSettings settings) {
        return settings != null
            && hasText(settings.getVersion())
            && hasText(settings.getUrl())
            && hasText(settings.getSha256());
    }

    private static Path detectCurrentLauncherPath() {
        try {
            Path path = Paths.get(LauncherUpdateService.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI()).toAbsolutePath().normalize();
            if (Files.isRegularFile(path) && path.getFileName().toString().toLowerCase().endsWith(".jar")) {
                return path;
            }
        } catch (URISyntaxException | RuntimeException ignored) {
        }
        return null;
    }

    private static String resolveJavaCommand() {
        String executable = isWindows() ? "javaw.exe" : "java";
        Path javaPath = Paths.get(System.getProperty("java.home"), "bin", executable);
        if (Files.isRegularFile(javaPath)) {
            return javaPath.toAbsolutePath().toString();
        }
        Path javaFallback = Paths.get(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java");
        if (Files.isRegularFile(javaFallback)) {
            return javaFallback.toAbsolutePath().toString();
        }
        return isWindows() ? "javaw.exe" : "java";
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static String powerShellQuote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static void log(LogSink logSink, String message) {
        if (logSink != null) {
            logSink.log(message);
        }
    }

    private static void reportProgress(ProgressSink progressSink, long downloadedBytes, long totalBytes, long startedAt) {
        if (progressSink == null) {
            return;
        }
        long elapsedMillis = Math.max(1L, (System.nanoTime() - startedAt) / 1_000_000L);
        progressSink.progress(downloadedBytes, totalBytes, elapsedMillis);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
