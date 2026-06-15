package ru.mcrpg.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

final class ManifestSignatureVerifier {

    private static final String PUBLIC_KEY_RESOURCE =
        "/ru/mcrpg/launcher/security/manifest-ed25519-public.pem";

    private final PublicKey publicKey;

    ManifestSignatureVerifier() {
        this(loadEmbeddedPublicKey());
    }

    ManifestSignatureVerifier(PublicKey publicKey) {
        if (publicKey == null) {
            throw new IllegalArgumentException("Manifest public key is required.");
        }
        this.publicKey = publicKey;
    }

    void verify(URL manifestUrl, byte[] manifestBytes) throws IOException {
        URL signatureUrl = signatureUrl(manifestUrl);
        URLConnection connection = signatureUrl.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);

        byte[] signatureBytes;
        try (InputStream inputStream = connection.getInputStream()) {
            signatureBytes = decodeSignature(inputStream.readAllBytes());
        } catch (IOException exception) {
            throw new IOException(
                "Не удалось загрузить подпись manifest: " + signatureUrl + ".",
                exception
            );
        }

        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(manifestBytes);
            if (!verifier.verify(signatureBytes)) {
                throw new IOException("Подпись manifest.json недействительна. Обновление отменено.");
            }
        } catch (GeneralSecurityException exception) {
            throw new IOException("Не удалось проверить Ed25519-подпись manifest.json.", exception);
        }
    }

    private static URL signatureUrl(URL manifestUrl) throws IOException {
        try {
            URI manifestUri = manifestUrl.toURI();
            return new URI(
                manifestUri.getScheme(),
                manifestUri.getUserInfo(),
                manifestUri.getHost(),
                manifestUri.getPort(),
                manifestUri.getPath() + ".sig",
                manifestUri.getQuery(),
                null
            ).toURL();
        } catch (URISyntaxException exception) {
            throw new IOException("Некорректный URL manifest.json: " + manifestUrl + ".", exception);
        }
    }

    static PublicKey decodePublicKey(byte[] pemBytes) {
        try {
            String pem = new String(pemBytes, StandardCharsets.US_ASCII);
            String encoded = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(encoded);
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Embedded manifest public key is invalid.", exception);
        }
    }

    private static PublicKey loadEmbeddedPublicKey() {
        try (InputStream inputStream = ManifestSignatureVerifier.class.getResourceAsStream(PUBLIC_KEY_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException("Embedded manifest public key is missing.");
            }
            return decodePublicKey(inputStream.readAllBytes());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read embedded manifest public key.", exception);
        }
    }

    private static byte[] decodeSignature(byte[] content) throws IOException {
        try {
            String encoded = new String(content, StandardCharsets.US_ASCII).trim();
            return Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Файл подписи manifest.json.sig имеет неверный Base64-формат.", exception);
        }
    }
}
