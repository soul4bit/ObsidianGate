package ru.mcrpg.authapi.service;

import org.junit.jupiter.api.Test;
import ru.mcrpg.authapi.config.AuthApiProperties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    @Test
    void rejectsDefaultJwtSecret() {
        AuthApiProperties properties = new AuthApiProperties();

        assertThrows(IllegalStateException.class, () -> new JwtService(properties));
    }

    @Test
    void rejectsShortJwtSecret() {
        AuthApiProperties properties = new AuthApiProperties();
        properties.setJwtSecret("short-secret");

        assertThrows(IllegalStateException.class, () -> new JwtService(properties));
    }

    @Test
    void acceptsStrongHexJwtSecret() {
        AuthApiProperties properties = new AuthApiProperties();
        properties.setJwtSecret("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

        assertDoesNotThrow(() -> new JwtService(properties));
    }
}
