package ru.mcrpg.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Locale;

final class DownloadUtils {

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int BUFFER_SIZE = 64 * 1024;

    private DownloadUtils() {
    }

    interface ProgressSink {
        void transferred(long bytes);
    }

    static long download(URL downloadUrl, Path target, int readTimeoutMs) throws IOException {
        return download(downloadUrl, target, readTimeoutMs, null);
    }

    static long download(URL downloadUrl, Path target, int readTimeoutMs, ProgressSink progressSink) throws IOException {
        return downloadWithDigest(downloadUrl, target, readTimeoutMs, null, progressSink).getBytes();
    }

    static DownloadResult downloadWithDigest(
        URL downloadUrl,
        Path target,
        int readTimeoutMs,
        String digestAlgorithm
    ) throws IOException {
        return downloadWithDigest(downloadUrl, target, readTimeoutMs, digestAlgorithm, null);
    }

    static DownloadResult downloadWithDigest(
        URL downloadUrl,
        Path target,
        int readTimeoutMs,
        String digestAlgorithm,
        ProgressSink progressSink
    ) throws IOException {
        URLConnection connection = downloadUrl.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(readTimeoutMs);

        try {
            ensureSuccessfulResponse(connection, downloadUrl);
        } catch (IOException exception) {
            throw enrichDownloadFailure(downloadUrl, exception);
        }

        try (InputStream inputStream = connection.getInputStream();
             OutputStream outputStream = Files.newOutputStream(
                 target,
                 StandardOpenOption.CREATE,
                 StandardOpenOption.TRUNCATE_EXISTING,
                 StandardOpenOption.WRITE
             )) {
            return copyWithDigest(inputStream, outputStream, digestAlgorithm, progressSink);
        } catch (IOException exception) {
            throw enrichDownloadFailure(downloadUrl, exception);
        }
    }

    static long copy(InputStream inputStream, OutputStream outputStream) throws IOException {
        return copy(inputStream, outputStream, null);
    }

    static long copy(InputStream inputStream, OutputStream outputStream, ProgressSink progressSink) throws IOException {
        return copyWithDigest(inputStream, outputStream, null, progressSink).getBytes();
    }

    private static DownloadResult copyWithDigest(
        InputStream inputStream,
        OutputStream outputStream,
        String digestAlgorithm,
        ProgressSink progressSink
    ) throws IOException {
        long totalBytes = 0L;
        byte[] buffer = new byte[BUFFER_SIZE];
        MessageDigest digest = hasText(digestAlgorithm) ? ChecksumUtils.messageDigest(digestAlgorithm.trim()) : null;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
            if (digest != null) {
                digest.update(buffer, 0, read);
            }
            totalBytes += read;
            if (progressSink != null) {
                progressSink.transferred(read);
            }
        }
        return new DownloadResult(
            totalBytes,
            digest == null ? "" : digest.getAlgorithm(),
            digest == null ? "" : ChecksumUtils.toHex(digest.digest())
        );
    }

    private static void ensureSuccessfulResponse(URLConnection connection, URL downloadUrl) throws IOException {
        if (!(connection instanceof HttpURLConnection)) {
            return;
        }

        HttpURLConnection httpConnection = (HttpURLConnection) connection;
        int statusCode = httpConnection.getResponseCode();
        if (statusCode < 400) {
            return;
        }

        String responseMessage = httpConnection.getResponseMessage();
        StringBuilder message = new StringBuilder("HTTP ").append(statusCode);
        if (hasText(responseMessage)) {
            message.append(" ").append(responseMessage.trim());
        }
        message.append(" при загрузке ").append(downloadUrl);
        throw new IOException(message.toString());
    }

    private static IOException enrichDownloadFailure(URL downloadUrl, IOException exception) {
        String message = exception.getMessage();
        if (!hasText(message)) {
            return new IOException("Не удалось скачать " + downloadUrl + ".", exception);
        }

        String normalizedMessage = message.trim().toLowerCase(Locale.ROOT);
        String normalizedUrl = downloadUrl.toString().toLowerCase(Locale.ROOT);
        if (normalizedMessage.contains(normalizedUrl)
            || normalizedMessage.startsWith("failed to download ")
            || normalizedMessage.startsWith("не удалось скачать ")) {
            return exception;
        }

        return new IOException("Не удалось скачать " + downloadUrl + ": " + message.trim(), exception);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    static final class DownloadResult {

        private final long bytes;
        private final String digestAlgorithm;
        private final String digestHex;

        private DownloadResult(long bytes, String digestAlgorithm, String digestHex) {
            this.bytes = bytes;
            this.digestAlgorithm = digestAlgorithm == null ? "" : digestAlgorithm;
            this.digestHex = digestHex == null ? "" : digestHex;
        }

        long getBytes() {
            return bytes;
        }

        String getDigestAlgorithm() {
            return digestAlgorithm;
        }

        String getDigestHex() {
            return digestHex;
        }
    }
}
