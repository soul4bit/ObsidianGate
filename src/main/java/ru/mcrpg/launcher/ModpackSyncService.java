package ru.mcrpg.launcher;

import java.io.IOException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class ModpackSyncService {

    private static final int FILE_DOWNLOAD_READ_TIMEOUT_MS = 30000;

    public interface LogSink {
        void log(String message);
    }

    public interface ProgressSink {
        void progress(ModpackSyncProgress progress);
    }

    private final ModpackManifestClient manifestClient;
    private final RuntimeSyncService runtimeSyncService;
    private final MinecraftBootstrapService minecraftBootstrapService;

    public ModpackSyncService(ModpackManifestClient manifestClient) {
        this(manifestClient, new RuntimeSyncService(), new MinecraftBootstrapService());
    }

    ModpackSyncService(
        ModpackManifestClient manifestClient,
        RuntimeSyncService runtimeSyncService,
        MinecraftBootstrapService minecraftBootstrapService
    ) {
        this.manifestClient = manifestClient;
        this.runtimeSyncService = runtimeSyncService;
        this.minecraftBootstrapService = minecraftBootstrapService;
    }

    public ModpackSyncPreviewResult preview(LauncherConfig baseConfig, LogSink logSink) throws IOException {
        return preview(baseConfig, logSink, null);
    }

    public ModpackSyncPreviewResult preview(LauncherConfig baseConfig, LogSink logSink, ProgressSink progressSink)
        throws IOException {
        SyncProgressTracker progressTracker = SyncProgressTracker.start(progressSink);
        progressTracker.phase(ModpackSyncProgress.Phase.PREPARING, "Загружаем manifest для предпросмотра", true);
        PreparedSyncContext prepared = prepareSync(baseConfig, logSink, "Предпросмотр manifest");
        log(logSink, "Файлов в manifest для предпросмотра: " + prepared.manifest.getFiles().size());
        progressTracker.startChecking(prepared.manifest.getFiles().size(), "Проверяем локальные файлы");

        int downloadFiles = 0;
        int reusedFiles = 0;
        long downloadBytes = 0L;
        List<ModpackSyncPreviewEntry> entries = new ArrayList<ModpackSyncPreviewEntry>(prepared.manifest.getFiles().size());
        VerifiedFileCache verifiedFileCache = VerifiedFileCache.open(prepared.gameDirectory);

        try {
            List<FileInspection> inspections = inspectFiles(
                prepared.gameDirectory,
                prepared.manifest.getFiles(),
                verifiedFileCache,
                progressTracker
            );

            for (int index = 0; index < prepared.manifest.getFiles().size(); index++) {
                ModpackFile file = prepared.manifest.getFiles().get(index);
                FileInspection inspection = inspections.get(index);
                entries.add(toPreviewEntry(file, inspection));
                if (inspection.isReused()) {
                    reusedFiles++;
                } else {
                    downloadFiles++;
                    if (file.getSize() != null && file.getSize().longValue() > 0L) {
                        downloadBytes += file.getSize().longValue();
                    }
                }
            }
        } finally {
            saveCache(verifiedFileCache, logSink);
        }

        progressTracker.complete("Предпросмотр завершен");

        log(
            logSink,
            "Предпросмотр завершен. Нужно синхронизировать: " + downloadFiles
                + ", актуальны: " + reusedFiles
                + ", байт к скачиванию: " + downloadBytes
        );

        return new ModpackSyncPreviewResult(
            prepared.resolvedConfig,
            prepared.manifest,
            entries,
            downloadFiles,
            reusedFiles,
            downloadBytes
        );
    }

    public ModpackSyncResult sync(LauncherConfig baseConfig, LogSink logSink) throws IOException {
        return sync(baseConfig, logSink, null);
    }

    public ModpackSyncResult sync(LauncherConfig baseConfig, LogSink logSink, ProgressSink progressSink)
        throws IOException {
        SyncProgressTracker progressTracker = SyncProgressTracker.start(progressSink);
        progressTracker.phase(ModpackSyncProgress.Phase.PREPARING, "Загружаем manifest", true);
        PreparedSyncContext prepared = prepareSync(baseConfig, logSink, "Загружаем manifest");
        LauncherConfig resolvedConfig = prepared.resolvedConfig;
        LoadedManifest loadedManifest = prepared.loadedManifest;
        ModpackManifest manifest = prepared.manifest;
        Path gameDirectory = prepared.gameDirectory;

        log(logSink, "Версия manifest: " + valueOrFallback(manifest.getVersion(), "неизвестно"));
        log(logSink, "Файлов в manifest: " + manifest.getFiles().size());

        int downloadedFiles = 0;
        int reusedFiles = 0;
        long downloadedBytes = 0L;
        VerifiedFileCache verifiedFileCache = VerifiedFileCache.open(gameDirectory);

        try {
            progressTracker.startChecking(manifest.getFiles().size(), "Проверяем manifest.files[]");
            List<FileInspection> inspections = inspectFiles(
                gameDirectory,
                manifest.getFiles(),
                verifiedFileCache,
                progressTracker
            );

            List<PendingDownload> pendingDownloads = new ArrayList<PendingDownload>();
            long plannedDownloadBytes = 0L;
            for (int index = 0; index < manifest.getFiles().size(); index++) {
                ModpackFile file = manifest.getFiles().get(index);
                FileInspection inspection = inspections.get(index);
                if (inspection.isReused()) {
                    reusedFiles++;
                } else {
                    pendingDownloads.add(new PendingDownload(file, inspection));
                    if (file.getSize() != null && file.getSize().longValue() > 0L) {
                        plannedDownloadBytes += file.getSize().longValue();
                    }
                }
            }

            progressTracker.startDownloading(pendingDownloads.size(), plannedDownloadBytes);
            List<FileSyncOutcome> outcomes = downloadFiles(
                gameDirectory,
                loadedManifest,
                manifest,
                pendingDownloads,
                verifiedFileCache,
                progressTracker,
                logSink
            );

            downloadedFiles = outcomes.size();
            for (FileSyncOutcome outcome : outcomes) {
                downloadedBytes += outcome.getDownloadedBytes();
            }

            if (hasRuntime(manifest.getRuntime())) {
                progressTracker.phase(ModpackSyncProgress.Phase.RUNTIME, "Проверяем portable Java", true);
            }
            RuntimeResolution runtimeResolution = runtimeSyncService.sync(loadedManifest, manifest, gameDirectory, logSink);
            if (runtimeResolution != null) {
                resolvedConfig.setJavaCommand(runtimeResolution.getJavaExecutable().toString());
            }

            if (isMinecraftBootstrapEnabled(manifest.getMinecraft())) {
                progressTracker.phase(ModpackSyncProgress.Phase.MINECRAFT, "Подготавливаем Minecraft и Forge", true);
            }
            MinecraftBootstrapResult bootstrapResult = minecraftBootstrapService.bootstrap(
                manifest.getMinecraft(),
                gameDirectory,
                logSink,
                verifiedFileCache
            );
            if (bootstrapResult != null) {
                if (hasText(bootstrapResult.getLaunchTemplate())) {
                    resolvedConfig.setLaunchTemplate(bootstrapResult.getLaunchTemplate());
                }
                if (hasText(bootstrapResult.getWorkingDirectory())) {
                    resolvedConfig.setWorkingDirectory(bootstrapResult.getWorkingDirectory());
                }
            }
        } finally {
            saveCache(verifiedFileCache, logSink);
        }

        progressTracker.phase(ModpackSyncProgress.Phase.CLEANUP, "Проверяем устаревшие моды", true);
        int removedFiles = cleanupObsoleteModEntries(gameDirectory, manifest, logSink);
        if (removedFiles > 0) {
            log(logSink, "Устаревших модов убрано: " + removedFiles);
        }

        log(
            logSink,
            "Синхронизация завершена. Скачано: " + downloadedFiles
                + ", переиспользовано: " + reusedFiles
                + ", байт: " + downloadedBytes
        );
        progressTracker.complete("Синхронизация завершена");

        return new ModpackSyncResult(resolvedConfig, manifest, downloadedFiles, reusedFiles, removedFiles, downloadedBytes);
    }

    private PreparedSyncContext prepareSync(LauncherConfig baseConfig, LogSink logSink, String manifestLogPrefix)
        throws IOException {
        LauncherConfig resolvedConfig = LauncherDefaults.applyMissingValues(baseConfig.copy());
        String manifestUrl = requireText(resolvedConfig.getManifestUrl(), "Укажи URL manifest.json.");
        Path gameDirectory = resolveGameDirectory(resolvedConfig.getGameDirectory());

        log(logSink, manifestLogPrefix + ": " + manifestUrl);
        LoadedManifest loadedManifest = manifestClient.load(manifestUrl);
        ModpackManifest manifest = loadedManifest.getManifest();
        applyManifestSettings(resolvedConfig, manifest, gameDirectory);
        return new PreparedSyncContext(resolvedConfig, loadedManifest, manifest, gameDirectory);
    }

    private static List<FileInspection> inspectFiles(
        Path gameDirectory,
        List<ModpackFile> files,
        VerifiedFileCache verifiedFileCache,
        SyncProgressTracker progressTracker
    ) throws IOException {
        List<Callable<FileInspection>> tasks = new ArrayList<Callable<FileInspection>>(files.size());
        for (ModpackFile file : files) {
            tasks.add(() -> {
                FileInspection inspection = inspectFile(gameDirectory, file, verifiedFileCache);
                progressTracker.fileInspected(file, inspection);
                return inspection;
            });
        }
        return ParallelIo.run("modpack-preview", tasks);
    }

    private List<FileSyncOutcome> downloadFiles(
        Path gameDirectory,
        LoadedManifest loadedManifest,
        ModpackManifest manifest,
        List<PendingDownload> pendingDownloads,
        VerifiedFileCache verifiedFileCache,
        SyncProgressTracker progressTracker,
        LogSink logSink
    ) throws IOException {
        List<Callable<FileSyncOutcome>> tasks = new ArrayList<Callable<FileSyncOutcome>>(pendingDownloads.size());
        for (PendingDownload pendingDownload : pendingDownloads) {
            tasks.add(() -> downloadFile(
                gameDirectory,
                loadedManifest,
                manifest,
                pendingDownload,
                verifiedFileCache,
                progressTracker,
                logSink
            ));
        }
        return ParallelIo.run("modpack-sync", tasks);
    }

    private FileSyncOutcome downloadFile(
        Path gameDirectory,
        LoadedManifest loadedManifest,
        ModpackManifest manifest,
        PendingDownload pendingDownload,
        VerifiedFileCache verifiedFileCache,
        SyncProgressTracker progressTracker,
        LogSink logSink
    ) throws IOException {
        ModpackFile file = pendingDownload.getFile();
        FileInspection inspection = pendingDownload.getInspection();
        Path target = inspection.getTarget();

        URL downloadUrl = resolveDownloadUrl(loadedManifest, manifest, file);
        progressTracker.downloadStarted(file);
        log(logSink, "Скачиваем: " + file.getPath() + " <- " + downloadUrl);

        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tempFile = Files.createTempFile(parent, target.getFileName().toString(), ".part");
        try {
            long downloadedBytes = download(downloadUrl, tempFile, progressTracker);
            verifyDownloadedFile(tempFile, file, inspection.getExpectedSha256());
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
            verifiedFileCache.recordVerified(target, "SHA-256", inspection.getExpectedSha256());

            if (file.isExecutable()) {
                target.toFile().setExecutable(true, false);
            }

            progressTracker.downloadCompleted(file);
            return FileSyncOutcome.downloaded(downloadedBytes);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private static FileInspection inspectFile(
        Path gameDirectory,
        ModpackFile file,
        VerifiedFileCache verifiedFileCache
    ) throws IOException {
        Path target = resolveTargetPath(gameDirectory, file.getPath());
        String expectedSha256 = requireText(file.getSha256(), "Для файла " + file.getPath() + " не указан sha256.");

        if (Files.exists(target) && !Files.isRegularFile(target)) {
            throw new IllegalArgumentException("Ожидался файл, но найден не файл: " + target);
        }

        if (!Files.isRegularFile(target)) {
            verifiedFileCache.remove(target);
            return FileInspection.download(target, expectedSha256, "missing");
        }

        if (file.getSize() != null && file.getSize().longValue() >= 0L) {
            long existingSize = Files.size(target);
            if (existingSize != file.getSize().longValue()) {
                verifiedFileCache.remove(target);
                return FileInspection.download(target, expectedSha256, "size-mismatch");
            }
        }

        if (verifiedFileCache.matches(target, "SHA-256", expectedSha256, file.getSize())) {
            return FileInspection.reused(target, expectedSha256);
        }

        String existingSha256 = ChecksumUtils.sha256(target);
        if (existingSha256.equalsIgnoreCase(expectedSha256)) {
            verifiedFileCache.recordVerified(target, "SHA-256", expectedSha256);
            return FileInspection.reused(target, expectedSha256);
        }

        verifiedFileCache.remove(target);
        return FileInspection.download(target, expectedSha256, "sha256-mismatch");
    }

    private static ModpackSyncPreviewEntry toPreviewEntry(ModpackFile file, FileInspection inspection) {
        return new ModpackSyncPreviewEntry(
            file.getPath(),
            inspection.getTarget().toString(),
            inspection.getExpectedSha256(),
            file.getSize(),
            inspection.isReused() ? ModpackSyncPreviewEntry.State.REUSED : ModpackSyncPreviewEntry.State.DOWNLOAD,
            inspection.getReason()
        );
    }

    private static long download(URL downloadUrl, Path target, SyncProgressTracker progressTracker) throws IOException {
        return DownloadUtils.download(downloadUrl, target, FILE_DOWNLOAD_READ_TIMEOUT_MS, progressTracker::bytesDownloaded);
    }

    private static void verifyDownloadedFile(Path path, ModpackFile file, String expectedSha256) throws IOException {
        if (file.getSize() != null && file.getSize().longValue() >= 0L) {
            long actualSize = Files.size(path);
            if (actualSize != file.getSize().longValue()) {
                throw new IOException(
                    "Размер файла " + file.getPath() + " не совпал. Ожидалось "
                        + file.getSize() + ", получено " + actualSize + "."
                );
            }
        }

        String actualSha256 = ChecksumUtils.sha256(path);
        if (!actualSha256.equalsIgnoreCase(expectedSha256)) {
            throw new IOException(
                "SHA-256 файла " + file.getPath() + " не совпал. Ожидалось "
                    + expectedSha256 + ", получено " + actualSha256 + "."
            );
        }
    }

    private static URL resolveDownloadUrl(LoadedManifest loadedManifest, ModpackManifest manifest, ModpackFile file)
        throws IOException {
        String relativeUrl = hasText(file.getUrl()) ? file.getUrl().trim() : normalizeUrlPath(file.getPath());
        return DownloadUrlResolver.resolve(loadedManifest.getSourceUrl(), manifest.getBaseUrl(), relativeUrl);
    }

    private static int cleanupObsoleteModEntries(Path gameDirectory, ModpackManifest manifest, LogSink logSink)
        throws IOException {
        Path modsDirectory = gameDirectory.resolve("mods").normalize();
        if (!modsDirectory.startsWith(gameDirectory)) {
            throw new IllegalArgumentException("mods directory is outside game directory: " + modsDirectory);
        }
        if (!Files.isDirectory(modsDirectory)) {
            return 0;
        }

        Set<String> expectedEntries = collectExpectedManagedEntries(manifest, "mods");
        int removedFiles = 0;
        Path backupDirectory = null;

        try (DirectoryStream<Path> entries = Files.newDirectoryStream(modsDirectory)) {
            for (Path entry : entries) {
                Path fileName = entry.getFileName();
                if (fileName == null || expectedEntries.contains(fileName.toString())) {
                    continue;
                }

                if (backupDirectory == null) {
                    backupDirectory = createObsoleteBackupDirectory(gameDirectory, manifest);
                }

                Path destination = uniqueDestination(backupDirectory, fileName.toString());
                Files.move(entry, destination);
                removedFiles++;
                log(
                    logSink,
                    "Убран устаревший файл сборки: mods/" + fileName + " -> "
                        + gameDirectory.relativize(destination).toString().replace('\\', '/')
                );
            }
        }

        return removedFiles;
    }

    private static Set<String> collectExpectedManagedEntries(ModpackManifest manifest, String rootName) {
        Set<String> expectedEntries = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        String prefix = rootName + "/";

        for (ModpackFile file : manifest.getFiles()) {
            if (!hasText(file.getPath())) {
                continue;
            }

            String normalizedPath = normalizeUrlPath(file.getPath());
            if (!normalizedPath.regionMatches(true, 0, prefix, 0, prefix.length())) {
                continue;
            }

            String remainder = normalizedPath.substring(prefix.length());
            int slashIndex = remainder.indexOf('/');
            String topLevelEntry = slashIndex >= 0 ? remainder.substring(0, slashIndex) : remainder;
            if (hasText(topLevelEntry)) {
                expectedEntries.add(topLevelEntry);
            }
        }

        return expectedEntries;
    }

    private static Path createObsoleteBackupDirectory(Path gameDirectory, ModpackManifest manifest) throws IOException {
        String manifestVersion = manifest == null ? "unknown" : valueOrFallback(manifest.getVersion(), "unknown");
        String directoryName = sanitizePathSegment(manifestVersion) + "-" + System.currentTimeMillis();
        Path backupDirectory = gameDirectory.resolve(".obsolete-mods").resolve(directoryName).normalize();
        if (!backupDirectory.startsWith(gameDirectory)) {
            throw new IllegalArgumentException("obsolete mods backup is outside game directory: " + backupDirectory);
        }
        Files.createDirectories(backupDirectory);
        return backupDirectory;
    }

    private static Path uniqueDestination(Path backupDirectory, String fileName) {
        Path destination = backupDirectory.resolve(fileName);
        if (!Files.exists(destination)) {
            return destination;
        }

        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        String extension = dotIndex > 0 ? fileName.substring(dotIndex) : "";
        int counter = 1;
        while (true) {
            Path candidate = backupDirectory.resolve(baseName + "-" + counter + extension);
            if (!Files.exists(candidate)) {
                return candidate;
            }
            counter++;
        }
    }

    private static String sanitizePathSegment(String value) {
        String sanitized = valueOrFallback(value, "unknown").replaceAll("[^A-Za-z0-9._-]", "_");
        return hasText(sanitized) ? sanitized : "unknown";
    }

    private static void applyManifestSettings(LauncherConfig config, ModpackManifest manifest, Path gameDirectory) {
        LauncherManifestSettings settings = manifest.getLauncher();
        if (settings == null) {
            return;
        }

        if (hasText(settings.getServerHost())) {
            config.setServerHost(settings.getServerHost().trim());
        }
        if (settings.getServerPort() != null) {
            config.setServerPort(settings.getServerPort().intValue());
        }
        if (hasText(settings.getLaunchTemplate())) {
            config.setLaunchTemplate(settings.getLaunchTemplate().trim());
        }
        if (hasText(settings.getWorkingDirectory())) {
            Path resolvedWorkingDirectory = resolveManifestWorkingDirectory(gameDirectory, settings.getWorkingDirectory());
            config.setWorkingDirectory(resolvedWorkingDirectory.toString());
        }
        if (hasText(settings.getAuthBaseUrl())) {
            config.setAuthBaseUrl(settings.getAuthBaseUrl().trim());
        }
        if (hasText(settings.getServerId())) {
            config.setServerId(settings.getServerId().trim());
        }
    }

    private static Path resolveGameDirectory(String rawGameDirectory) throws IOException {
        String gameDirectory = requireText(rawGameDirectory, "Укажи папку игры для синхронизации файлов.");
        Path path = Paths.get(gameDirectory).toAbsolutePath().normalize();
        Files.createDirectories(path);
        return path;
    }

    private static Path resolveManifestWorkingDirectory(Path gameDirectory, String workingDirectory) {
        Path path = Paths.get(requireText(workingDirectory, "workingDirectory в manifest пустой."));
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("workingDirectory в manifest должен быть относительным.");
        }

        Path resolved = gameDirectory.resolve(path).normalize();
        if (!resolved.startsWith(gameDirectory)) {
            throw new IllegalArgumentException("workingDirectory в manifest выходит за пределы папки игры.");
        }
        return resolved;
    }

    private static Path resolveTargetPath(Path gameDirectory, String relativePath) {
        String normalizedPath = requireText(relativePath, "В manifest найден файл без path.").replace('\\', '/');
        if (normalizedPath.startsWith("/")) {
            throw new IllegalArgumentException("Путь файла должен быть относительным: " + relativePath);
        }

        Path candidate = Paths.get(normalizedPath);
        if (candidate.isAbsolute()) {
            throw new IllegalArgumentException("Путь файла должен быть относительным: " + relativePath);
        }

        Path resolved = gameDirectory.resolve(candidate).normalize();
        if (!resolved.startsWith(gameDirectory)) {
            throw new IllegalArgumentException("Путь файла выходит за пределы папки игры: " + relativePath);
        }
        return resolved;
    }

    private static void log(LogSink logSink, String message) {
        if (logSink != null) {
            synchronized (logSink) {
                logSink.log(message);
            }
        }
    }

    private static void saveCache(VerifiedFileCache verifiedFileCache, LogSink logSink) {
        try {
            verifiedFileCache.save();
        } catch (IOException exception) {
            log(logSink, "Кеш проверенных файлов не сохранен: " + exception.getMessage());
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String valueOrFallback(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private static String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String normalizeUrlPath(String value) {
        return value.replace('\\', '/');
    }

    private static boolean hasRuntime(ModpackRuntime runtime) {
        return runtime != null && runtime.getPackages() != null && !runtime.getPackages().isEmpty();
    }

    private static boolean isMinecraftBootstrapEnabled(MinecraftBootstrapSettings settings) {
        return settings != null && settings.isEnabled();
    }

    private static final class FileSyncOutcome {

        private final long downloadedBytes;

        private FileSyncOutcome(long downloadedBytes) {
            this.downloadedBytes = downloadedBytes;
        }

        static FileSyncOutcome downloaded(long downloadedBytes) {
            return new FileSyncOutcome(downloadedBytes);
        }

        long getDownloadedBytes() {
            return downloadedBytes;
        }
    }

    private static final class PendingDownload {

        private final ModpackFile file;
        private final FileInspection inspection;

        private PendingDownload(ModpackFile file, FileInspection inspection) {
            this.file = file;
            this.inspection = inspection;
        }

        ModpackFile getFile() {
            return file;
        }

        FileInspection getInspection() {
            return inspection;
        }
    }

    private static final class FileInspection {

        private final Path target;
        private final String expectedSha256;
        private final boolean reused;
        private final String reason;

        private FileInspection(Path target, String expectedSha256, boolean reused, String reason) {
            this.target = target;
            this.expectedSha256 = expectedSha256;
            this.reused = reused;
            this.reason = reason;
        }

        static FileInspection reused(Path target, String expectedSha256) {
            return new FileInspection(target, expectedSha256, true, "up-to-date");
        }

        static FileInspection download(Path target, String expectedSha256, String reason) {
            return new FileInspection(target, expectedSha256, false, reason);
        }

        Path getTarget() {
            return target;
        }

        String getExpectedSha256() {
            return expectedSha256;
        }

        boolean isReused() {
            return reused;
        }

        String getReason() {
            return reason;
        }
    }

    private static final class SyncProgressTracker {

        private static final long EMIT_INTERVAL_MILLIS = 150L;
        private static final double CHECK_PROGRESS_WEIGHT = 0.35d;
        private static final double DOWNLOAD_PROGRESS_WEIGHT = 0.55d;

        private final ProgressSink progressSink;
        private final AtomicInteger totalFiles = new AtomicInteger();
        private final AtomicInteger checkedFiles = new AtomicInteger();
        private final AtomicInteger reusedFiles = new AtomicInteger();
        private final AtomicInteger downloadFiles = new AtomicInteger();
        private final AtomicInteger downloadedFiles = new AtomicInteger();
        private final AtomicLong totalDownloadBytes = new AtomicLong();
        private final AtomicLong downloadedBytes = new AtomicLong();
        private final AtomicReference<String> currentFile = new AtomicReference<String>("");

        private volatile ModpackSyncProgress.Phase phase = ModpackSyncProgress.Phase.PREPARING;
        private volatile String message = "";
        private volatile long downloadStartedAtMillis;
        private volatile long lastEmitAtMillis;

        private SyncProgressTracker(ProgressSink progressSink) {
            this.progressSink = progressSink;
        }

        static SyncProgressTracker start(ProgressSink progressSink) {
            return new SyncProgressTracker(progressSink);
        }

        void phase(ModpackSyncProgress.Phase phase, String message, boolean force) {
            this.phase = phase;
            this.message = valueOrFallback(message, "");
            currentFile.set("");
            emit(force);
        }

        void startChecking(int totalFiles, String message) {
            this.phase = ModpackSyncProgress.Phase.CHECKING;
            this.message = valueOrFallback(message, "Проверяем файлы");
            this.totalFiles.set(Math.max(0, totalFiles));
            checkedFiles.set(0);
            reusedFiles.set(0);
            downloadFiles.set(0);
            downloadedFiles.set(0);
            totalDownloadBytes.set(0L);
            downloadedBytes.set(0L);
            currentFile.set("");
            emit(true);
        }

        void fileInspected(ModpackFile file, FileInspection inspection) {
            currentFile.set(file == null ? "" : valueOrFallback(file.getPath(), ""));
            checkedFiles.incrementAndGet();
            if (inspection != null && inspection.isReused()) {
                reusedFiles.incrementAndGet();
            }
            emit(false);
        }

        void startDownloading(int downloadFiles, long totalDownloadBytes) {
            this.phase = ModpackSyncProgress.Phase.DOWNLOADING;
            this.message = downloadFiles > 0 ? "Скачиваем файлы сборки" : "Файлы сборки актуальны";
            this.downloadFiles.set(Math.max(0, downloadFiles));
            this.totalDownloadBytes.set(Math.max(0L, totalDownloadBytes));
            downloadedFiles.set(0);
            downloadedBytes.set(0L);
            currentFile.set("");
            downloadStartedAtMillis = System.currentTimeMillis();
            emit(true);
        }

        void downloadStarted(ModpackFile file) {
            currentFile.set(file == null ? "" : valueOrFallback(file.getPath(), ""));
            emit(true);
        }

        void bytesDownloaded(long bytes) {
            if (bytes <= 0L) {
                return;
            }
            downloadedBytes.addAndGet(bytes);
            emit(false);
        }

        void downloadCompleted(ModpackFile file) {
            currentFile.set(file == null ? "" : valueOrFallback(file.getPath(), ""));
            downloadedFiles.incrementAndGet();
            emit(true);
        }

        void complete(String message) {
            this.phase = ModpackSyncProgress.Phase.COMPLETE;
            this.message = valueOrFallback(message, "Готово");
            currentFile.set("");
            emit(true);
        }

        private void emit(boolean force) {
            if (progressSink == null) {
                return;
            }

            long now = System.currentTimeMillis();
            if (!force && now - lastEmitAtMillis < EMIT_INTERVAL_MILLIS) {
                return;
            }
            lastEmitAtMillis = now;
            progressSink.progress(snapshot(now));
        }

        private ModpackSyncProgress snapshot(long now) {
            long speed = bytesPerSecond(now);
            long eta = estimatedRemainingMillis(speed);
            return new ModpackSyncProgress(
                phase,
                message,
                currentFile.get(),
                totalFiles.get(),
                checkedFiles.get(),
                reusedFiles.get(),
                downloadFiles.get(),
                downloadedFiles.get(),
                totalDownloadBytes.get(),
                downloadedBytes.get(),
                speed,
                eta,
                progress()
            );
        }

        private double progress() {
            if (phase == ModpackSyncProgress.Phase.PREPARING
                || phase == ModpackSyncProgress.Phase.RUNTIME
                || phase == ModpackSyncProgress.Phase.MINECRAFT
                || phase == ModpackSyncProgress.Phase.CLEANUP) {
                return -1.0d;
            }
            if (phase == ModpackSyncProgress.Phase.COMPLETE) {
                return 1.0d;
            }
            int total = totalFiles.get();
            if (phase == ModpackSyncProgress.Phase.CHECKING) {
                return total <= 0
                    ? CHECK_PROGRESS_WEIGHT
                    : Math.min(CHECK_PROGRESS_WEIGHT, checkedFiles.get() / (double) total * CHECK_PROGRESS_WEIGHT);
            }

            int downloads = downloadFiles.get();
            if (downloads <= 0) {
                return CHECK_PROGRESS_WEIGHT + DOWNLOAD_PROGRESS_WEIGHT;
            }

            long totalBytes = totalDownloadBytes.get();
            double downloadProgress = totalBytes > 0L
                ? Math.min(1.0d, downloadedBytes.get() / (double) totalBytes)
                : Math.min(1.0d, downloadedFiles.get() / (double) downloads);
            return CHECK_PROGRESS_WEIGHT + downloadProgress * DOWNLOAD_PROGRESS_WEIGHT;
        }

        private long bytesPerSecond(long now) {
            if (phase != ModpackSyncProgress.Phase.DOWNLOADING || downloadStartedAtMillis <= 0L) {
                return 0L;
            }
            long elapsedMillis = Math.max(1L, now - downloadStartedAtMillis);
            return downloadedBytes.get() * 1000L / elapsedMillis;
        }

        private long estimatedRemainingMillis(long bytesPerSecond) {
            long totalBytes = totalDownloadBytes.get();
            if (phase != ModpackSyncProgress.Phase.DOWNLOADING || totalBytes <= 0L || bytesPerSecond <= 0L) {
                return -1L;
            }
            long remainingBytes = Math.max(0L, totalBytes - downloadedBytes.get());
            return remainingBytes * 1000L / bytesPerSecond;
        }
    }

    private static final class PreparedSyncContext {

        private final LauncherConfig resolvedConfig;
        private final LoadedManifest loadedManifest;
        private final ModpackManifest manifest;
        private final Path gameDirectory;

        private PreparedSyncContext(
            LauncherConfig resolvedConfig,
            LoadedManifest loadedManifest,
            ModpackManifest manifest,
            Path gameDirectory
        ) {
            this.resolvedConfig = resolvedConfig;
            this.loadedManifest = loadedManifest;
            this.manifest = manifest;
            this.gameDirectory = gameDirectory;
        }
    }
}
