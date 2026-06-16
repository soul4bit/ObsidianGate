package ru.mcrpg.forgeauth.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcrpg.gameauth.GameAuthConstants;
import ru.mcrpg.gameauth.LauncherSession;
import ru.mcrpg.gameauth.LauncherSessionFiles;

class ForgeAuthClientLifecycleTest {

    @TempDir
    Path tempDirectory;

    @Test
    void retriesSameProofWithoutConsumingAdditionalTickets() throws Exception {
        Path sessionFile = tempDirectory.resolve("session.json");
        LauncherSessionFiles sessionFiles = new LauncherSessionFiles();
        sessionFiles.write(
            sessionFile,
            new LauncherSession(
                Arrays.asList("ticket-1", "ticket-2"),
                "Knight",
                "uuid-1",
                "obsidiangate-main",
                Instant.now().plusSeconds(180)
            )
        );

        String previousSessionFile = System.getProperty(GameAuthConstants.SESSION_FILE_PROPERTY);
        System.setProperty(GameAuthConstants.SESSION_FILE_PROPERTY, sessionFile.toString());
        RecordingTicketSender sender = new RecordingTicketSender();
        try {
            ForgeAuthClientLifecycle lifecycle = new ForgeAuthClientLifecycle(
                sender,
                Logger.getLogger("test"),
                sessionFiles
            );

            lifecycle.onConnected(null);
            for (int tick = 0; tick < 100; tick++) {
                lifecycle.runClientEndTick();
            }

            assertEquals(5, sender.messages.size());
            for (AuthTicketMessage message : sender.messages) {
                assertEquals("ticket-1", message.getTicket());
                assertEquals("obsidiangate-main", message.getServerId());
            }
            assertEquals(Arrays.asList("ticket-2"), sessionFiles.read(sessionFile).getTickets());
        } finally {
            if (previousSessionFile == null) {
                System.clearProperty(GameAuthConstants.SESSION_FILE_PROPERTY);
            } else {
                System.setProperty(GameAuthConstants.SESSION_FILE_PROPERTY, previousSessionFile);
            }
        }
    }

    @Test
    void reconnectUsesNextStoredTicket() throws Exception {
        Path sessionFile = tempDirectory.resolve("session.json");
        LauncherSessionFiles sessionFiles = new LauncherSessionFiles();
        sessionFiles.write(
            sessionFile,
            new LauncherSession(
                Arrays.asList("ticket-1", "ticket-2"),
                "Knight",
                "uuid-1",
                "obsidiangate-main",
                Instant.now().plusSeconds(180)
            )
        );

        String previousSessionFile = System.getProperty(GameAuthConstants.SESSION_FILE_PROPERTY);
        System.setProperty(GameAuthConstants.SESSION_FILE_PROPERTY, sessionFile.toString());
        RecordingTicketSender sender = new RecordingTicketSender();
        try {
            ForgeAuthClientLifecycle lifecycle = new ForgeAuthClientLifecycle(
                sender,
                Logger.getLogger("test"),
                sessionFiles
            );

            lifecycle.onConnected(null);
            lifecycle.runClientEndTick();
            lifecycle.onDisconnected(null);
            lifecycle.onConnected(null);
            lifecycle.runClientEndTick();

            assertEquals(2, sender.messages.size());
            assertEquals("ticket-1", sender.messages.get(0).getTicket());
            assertEquals("ticket-2", sender.messages.get(1).getTicket());
            assertThrows(java.io.IOException.class, () -> sessionFiles.read(sessionFile));
        } finally {
            if (previousSessionFile == null) {
                System.clearProperty(GameAuthConstants.SESSION_FILE_PROPERTY);
            } else {
                System.setProperty(GameAuthConstants.SESSION_FILE_PROPERTY, previousSessionFile);
            }
        }
    }

    private static final class RecordingTicketSender implements ForgeAuthClientLifecycle.TicketSender {
        private final List<AuthTicketMessage> messages = new ArrayList<AuthTicketMessage>();

        @Override
        public void send(AuthTicketMessage message) {
            messages.add(message);
        }
    }
}
