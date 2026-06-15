package ru.mcrpg.launcher;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.URL;
import java.net.URLConnection;

public final class ModpackManifestClient {

    private final ObjectMapper objectMapper;
    private final ManifestSignatureVerifier signatureVerifier;

    public ModpackManifestClient() {
        this(new ManifestSignatureVerifier());
    }

    ModpackManifestClient(ManifestSignatureVerifier signatureVerifier) {
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.signatureVerifier = signatureVerifier;
    }

    public LoadedManifest load(String manifestUrl) throws IOException {
        String normalizedUrl = requireText(manifestUrl, "Укажи URL manifest.json.");
        URL url = new URL(normalizedUrl);
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);

        byte[] manifestBytes;
        try (InputStream inputStream = connection.getInputStream()) {
            manifestBytes = inputStream.readAllBytes();
        } catch (IOException exception) {
            throw describeLoadFailure(url, exception);
        }

        signatureVerifier.verify(url, manifestBytes);
        try {
            ModpackManifest manifest = objectMapper.readValue(manifestBytes, ModpackManifest.class);
            validate(manifest);
            return new LoadedManifest(url, manifest);
        } catch (IOException exception) {
            throw new IOException("Не удалось прочитать подписанный manifest.json.", exception);
        }
    }

    private static IOException describeLoadFailure(URL url, IOException exception) {
        String location = url.toString();
        if (exception instanceof ConnectException) {
            return new IOException(
                "Не удалось подключиться к " + location
                    + ". Лаунчер ожидает manifest.json по HTTP(S). Проверь, что по этому адресу запущен веб-сервер; сам Minecraft-сервер на порту 25565 manifest не раздаёт.",
                exception
            );
        }
        if (exception instanceof UnknownHostException) {
            return new IOException(
                "Не удалось найти хост для manifest.json: " + location + ". Проверь адрес сервера и DNS/hosts.",
                exception
            );
        }
        if (exception instanceof SocketTimeoutException) {
            return new IOException(
                "Таймаут при загрузке manifest.json: " + location + ". Проверь доступность HTTP-сервера и сеть.",
                exception
            );
        }
        if (exception instanceof FileNotFoundException) {
            return new IOException(
                "manifest.json не найден по адресу " + location + ". Проверь путь и настройку раздачи файлов.",
                exception
            );
        }
        return exception;
    }

    private static void validate(ModpackManifest manifest) {
        if (manifest == null) {
            throw new IllegalArgumentException("manifest.json пустой.");
        }
        if (manifest.getSchemaVersion() != 1) {
            throw new IllegalArgumentException(
                "Неподдерживаемая schemaVersion: " + manifest.getSchemaVersion() + ". Сейчас поддерживается только 1."
            );
        }
        if (manifest.getLauncher() == null) {
            manifest.setLauncher(new LauncherManifestSettings());
        }
        if (manifest.getLauncherUpdate() == null) {
            manifest.setLauncherUpdate(new LauncherUpdateSettings());
        }
        if (manifest.getNews() == null) {
            manifest.setNews(new ModpackNews());
        }
        if (manifest.getChangelog() == null) {
            manifest.setChangelog(new ModpackNews());
        }
        if (manifest.getHistory() == null) {
            manifest.setHistory(null);
        }
        if (manifest.getRuntime() == null) {
            manifest.setRuntime(new ModpackRuntime());
        }
        if (manifest.getMinecraft() == null) {
            manifest.setMinecraft(new MinecraftBootstrapSettings());
        }
        if (manifest.getFiles() == null) {
            manifest.setFiles(null);
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
