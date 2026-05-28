package ru.mcrpg.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DownloadUtilsTest {

    @TempDir
    Path tempDirectory;

    @Test
    void downloadCopiesResponseBodyToTargetFile() throws Exception {
        byte[] payload = "launcher-update".getBytes(StandardCharsets.UTF_8);
        HttpServer server = createServer("/launcher.jar", 200, payload);
        try {
            URL url = new URL("http://127.0.0.1:" + server.getAddress().getPort() + "/launcher.jar");
            Path target = tempDirectory.resolve("launcher.jar");

            long downloadedBytes = DownloadUtils.download(url, target, 2000);

            assertEquals(payload.length, downloadedBytes);
            assertEquals("launcher-update", Files.readString(target, StandardCharsets.UTF_8));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void downloadCanReturnDigestFromCopiedBytes() throws Exception {
        byte[] payload = "modpack-file".getBytes(StandardCharsets.UTF_8);
        HttpServer server = createServer("/mod.jar", 200, payload);
        try {
            URL url = new URL("http://127.0.0.1:" + server.getAddress().getPort() + "/mod.jar");
            Path target = tempDirectory.resolve("mod.jar");

            DownloadUtils.DownloadResult result = DownloadUtils.downloadWithDigest(url, target, 2000, "SHA-256");

            assertEquals(payload.length, result.getBytes());
            assertEquals("SHA-256", result.getDigestAlgorithm());
            assertEquals(ChecksumUtils.sha256(new ByteArrayInputStream(payload)), result.getDigestHex());
            assertEquals("modpack-file", Files.readString(target, StandardCharsets.UTF_8));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void downloadReportsHttpStatusAndUrlForMissingFile() throws Exception {
        HttpServer server = createServer("/missing.jar", 404, "missing".getBytes(StandardCharsets.UTF_8));
        try {
            URL url = new URL("http://127.0.0.1:" + server.getAddress().getPort() + "/missing.jar");
            Path target = tempDirectory.resolve("missing.jar");

            IOException exception = assertThrows(IOException.class, () -> DownloadUtils.download(url, target, 2000));

            assertTrue(exception.getMessage().contains("HTTP 404"));
            assertTrue(exception.getMessage().contains(url.toString()));
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer createServer(String path, int statusCode, byte[] body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> writeResponse(exchange, statusCode, body));
        server.start();
        return server;
    }

    private static void writeResponse(HttpExchange exchange, int statusCode, byte[] body) throws IOException {
        try {
            exchange.sendResponseHeaders(statusCode, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        } finally {
            exchange.close();
        }
    }
}
