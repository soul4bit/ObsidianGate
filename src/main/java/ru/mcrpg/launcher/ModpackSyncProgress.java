package ru.mcrpg.launcher;

public final class ModpackSyncProgress {

    public enum Phase {
        PREPARING,
        CHECKING,
        DOWNLOADING,
        RUNTIME,
        MINECRAFT,
        CLEANUP,
        COMPLETE
    }

    private final Phase phase;
    private final String message;
    private final String currentFile;
    private final int totalFiles;
    private final int checkedFiles;
    private final int reusedFiles;
    private final int downloadFiles;
    private final int downloadedFiles;
    private final long totalDownloadBytes;
    private final long downloadedBytes;
    private final long bytesPerSecond;
    private final long estimatedRemainingMillis;
    private final double progress;

    ModpackSyncProgress(
        Phase phase,
        String message,
        String currentFile,
        int totalFiles,
        int checkedFiles,
        int reusedFiles,
        int downloadFiles,
        int downloadedFiles,
        long totalDownloadBytes,
        long downloadedBytes,
        long bytesPerSecond,
        long estimatedRemainingMillis,
        double progress
    ) {
        this.phase = phase;
        this.message = message;
        this.currentFile = currentFile;
        this.totalFiles = totalFiles;
        this.checkedFiles = checkedFiles;
        this.reusedFiles = reusedFiles;
        this.downloadFiles = downloadFiles;
        this.downloadedFiles = downloadedFiles;
        this.totalDownloadBytes = totalDownloadBytes;
        this.downloadedBytes = downloadedBytes;
        this.bytesPerSecond = bytesPerSecond;
        this.estimatedRemainingMillis = estimatedRemainingMillis;
        this.progress = progress;
    }

    public Phase getPhase() {
        return phase;
    }

    public String getMessage() {
        return message;
    }

    public String getCurrentFile() {
        return currentFile;
    }

    public int getTotalFiles() {
        return totalFiles;
    }

    public int getCheckedFiles() {
        return checkedFiles;
    }

    public int getReusedFiles() {
        return reusedFiles;
    }

    public int getDownloadFiles() {
        return downloadFiles;
    }

    public int getDownloadedFiles() {
        return downloadedFiles;
    }

    public long getTotalDownloadBytes() {
        return totalDownloadBytes;
    }

    public long getDownloadedBytes() {
        return downloadedBytes;
    }

    public long getBytesPerSecond() {
        return bytesPerSecond;
    }

    public long getEstimatedRemainingMillis() {
        return estimatedRemainingMillis;
    }

    public double getProgress() {
        return progress;
    }
}
