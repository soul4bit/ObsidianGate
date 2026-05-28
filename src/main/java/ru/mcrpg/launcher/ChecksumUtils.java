package ru.mcrpg.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class ChecksumUtils {

    private static final int BUFFER_SIZE = 64 * 1024;
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private ChecksumUtils() {
    }

    static String sha256(Path path) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return sha256(inputStream);
        }
    }

    static String sha1(Path path) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return sha1(inputStream);
        }
    }

    static String sha256(InputStream inputStream) throws IOException {
        MessageDigest digest = messageDigest("SHA-256");
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        return toHex(digest.digest());
    }

    static String sha1(InputStream inputStream) throws IOException {
        MessageDigest digest = messageDigest("SHA-1");
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        return toHex(digest.digest());
    }

    static MessageDigest messageDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(algorithm + " is not available.", exception);
        }
    }

    static String toHex(byte[] bytes) {
        char[] output = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            output[index * 2] = HEX_DIGITS[value >>> 4];
            output[index * 2 + 1] = HEX_DIGITS[value & 0x0f];
        }
        return new String(output);
    }
}
