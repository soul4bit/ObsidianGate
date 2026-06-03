package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AuthServerConfigTest {

    @Test
    void detectsMissingConfiguration() {
        AuthServerConfig config = new AuthServerConfig("", "", 0);

        assertFalse(config.isReady());
        assertEquals(60, config.getGraceSeconds());
        assertEquals(2, config.getVerifyThreads());
    }

    @Test
    void acceptsConfiguredServerId() {
        AuthServerConfig config = new AuthServerConfig("http://127.0.0.1:8081", "obsidiangate-main", 15);

        assertTrue(config.isReady());
        assertTrue(config.acceptsServerId("obsidiangate-main"));
        assertFalse(config.acceptsServerId("wrong-server"));
    }

    @Test
    void clampsVerifyThreads() {
        assertEquals(1, new AuthServerConfig("http://127.0.0.1:8081", "main", 60, 0).getVerifyThreads());
        assertEquals(8, new AuthServerConfig("http://127.0.0.1:8081", "main", 60, 99).getVerifyThreads());
    }
}
