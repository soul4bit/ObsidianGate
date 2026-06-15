package ru.mcrpg.launcher;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public final class ManifestSignatureTool {

    private ManifestSignatureTool() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                "Usage: generate <private.pem> <public.pem> | sign <private.pem> <manifest.json>"
            );
        }

        if ("generate".equals(args[0])) {
            generate(Paths.get(args[1]), Paths.get(args[2]));
            return;
        }
        if ("sign".equals(args[0])) {
            sign(Paths.get(args[1]), Paths.get(args[2]));
            return;
        }
        throw new IllegalArgumentException("Unknown command: " + args[0]);
    }

    private static void generate(Path privateKeyPath, Path publicKeyPath) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        KeyPair keyPair = generator.generateKeyPair();
        writePem(privateKeyPath, "PRIVATE KEY", keyPair.getPrivate().getEncoded());
        writePem(publicKeyPath, "PUBLIC KEY", keyPair.getPublic().getEncoded());
    }

    private static void sign(Path privateKeyPath, Path manifestPath) throws Exception {
        PrivateKey privateKey = readPrivateKey(privateKeyPath);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(privateKey);
        signer.update(Files.readAllBytes(manifestPath));
        String encoded = Base64.getEncoder().encodeToString(signer.sign()) + System.lineSeparator();
        Files.writeString(
            Paths.get(manifestPath.toString() + ".sig"),
            encoded,
            StandardCharsets.US_ASCII
        );
    }

    private static PrivateKey readPrivateKey(Path path) throws Exception {
        String pem = Files.readString(path, StandardCharsets.US_ASCII);
        String encoded = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(encoded);
        return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    private static void writePem(Path path, String type, byte[] content) throws Exception {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String encoded = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(content);
        String pem = "-----BEGIN " + type + "-----\n"
            + encoded + "\n"
            + "-----END " + type + "-----\n";
        Files.writeString(path, pem, StandardCharsets.US_ASCII);
    }
}
