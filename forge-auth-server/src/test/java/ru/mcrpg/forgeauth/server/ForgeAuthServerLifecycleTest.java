package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import ru.mcrpg.gameauth.TicketVerificationClient;
import ru.mcrpg.gameauth.TicketVerificationResult;

class ForgeAuthServerLifecycleTest {

    @Test
    void loginPreservesExistingVerifiedState() throws Exception {
        Map authStates = new ConcurrentHashMap();
        Queue<Runnable> mainThreadActions = new ConcurrentLinkedQueue<Runnable>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            FakePlayer earlyPlayer = new FakePlayer("Knight");
            Object state = newState(earlyPlayer, "Knight", Instant.now());
            setBoolean(state, "verified", true);
            authStates.put("knight", state);
            ForgeAuthServerLifecycle lifecycle = newLifecycle(authStates, mainThreadActions, executor);

            FakePlayer loggedInPlayer = new FakePlayer("Knight");
            lifecycle.trackPlayerLogin(loggedInPlayer);

            Object restored = authStates.get("knight");
            assertSame(state, restored);
            assertSame(loggedInPlayer, getField(restored, "player"));
            assertTrue(getBoolean(restored, "verified"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void accountIdForReturnsVerifiedLauncherAccount() throws Exception {
        Map authStates = new ConcurrentHashMap();
        Queue<Runnable> mainThreadActions = new ConcurrentLinkedQueue<Runnable>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            FakePlayer player = new FakePlayer("Knight");
            Object state = newState(player, "Knight", Instant.now());
            setBoolean(state, "verified", true);
            setString(state, "accountId", "account-123");
            authStates.put("knight", state);
            ForgeAuthServerLifecycle lifecycle = newLifecycle(authStates, mainThreadActions, executor);

            assertTrue("account-123".equals(lifecycle.accountIdFor(player)));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void timeoutDoesNotDisconnectWhileVerificationIsInFlight() throws Exception {
        Map authStates = new ConcurrentHashMap();
        Queue<Runnable> mainThreadActions = new ConcurrentLinkedQueue<Runnable>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            FakePlayer player = new FakePlayer("Knight");
            Object state = newState(player, "Knight", Instant.now().minusSeconds(30));
            setBoolean(state, "verificationInFlight", true);
            authStates.put("knight", state);
            ForgeAuthServerLifecycle lifecycle = newLifecycle(authStates, mainThreadActions, executor);

            for (int tick = 0; tick < 20; tick++) {
                lifecycle.runServerEndTick();
            }

            assertNull(player.connection.lastMessage);
            assertFalse(authStates.isEmpty());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void timeoutDisconnectIncludesNoTicketMarker() throws Exception {
        Map authStates = new ConcurrentHashMap();
        Queue<Runnable> mainThreadActions = new ConcurrentLinkedQueue<Runnable>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            FakePlayer player = new FakePlayer("Knight");
            authStates.put("knight", newState(player, "Knight", Instant.now().minusSeconds(90)));
            ForgeAuthServerLifecycle lifecycle = newLifecycle(authStates, mainThreadActions, executor);

            for (int tick = 0; tick < 20; tick++) {
                lifecycle.runServerEndTick();
            }

            assertTrue(player.connection.lastMessage != null && player.connection.lastMessage.contains("AUTH_TIMEOUT_NO_TICKET"));
            assertTrue(authStates.isEmpty());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void verificationDisconnectsWhenTicketUuidDiffersFromPlayerUuid() throws Exception {
        Map authStates = new ConcurrentHashMap();
        Queue<Runnable> mainThreadActions = new ConcurrentLinkedQueue<Runnable>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            FakePlayer player = new FakePlayer("Knight", UUID.fromString("00000000-0000-0000-0000-000000000001"));
            authStates.put("knight", newState(player, "Knight", Instant.now()));
            ForgeAuthServerLifecycle lifecycle = newLifecycle(authStates, mainThreadActions, executor);

            applyVerificationResult(
                lifecycle,
                "knight",
                "Knight",
                new TicketVerificationResult(
                    true,
                    "account-123",
                    "Knight",
                    "00000000-0000-0000-0000-000000000002",
                    "player",
                    null
                )
            );

            assertTrue(player.connection.lastMessage != null && player.connection.lastMessage.contains("AUTH_TICKET_UUID_MISMATCH"));
            assertTrue(authStates.isEmpty());
        } finally {
            executor.shutdownNow();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ForgeAuthServerLifecycle newLifecycle(
        Map authStates,
        Queue<Runnable> mainThreadActions,
        ExecutorService executor
    ) {
        return new ForgeAuthServerLifecycle(
            Logger.getLogger("test"),
            new AuthServerConfig("http://127.0.0.1:1", "obsidiangate-main", 1),
            new MinecraftPlayerBridge(message -> message),
            new TicketVerificationClient(1, 1),
            executor,
            authStates,
            mainThreadActions
        );
    }

    private static Object newState(Object player, String username, Instant connectedAt) throws Exception {
        Class<?> stateClass = Class.forName("ru.mcrpg.forgeauth.server.ForgeAuthServerLifecycle$PlayerAuthState");
        Constructor<?> constructor = stateClass.getDeclaredConstructor(Object.class, String.class, Instant.class);
        constructor.setAccessible(true);
        return constructor.newInstance(player, username, connectedAt);
    }

    private static void applyVerificationResult(
        ForgeAuthServerLifecycle lifecycle,
        String key,
        String expectedUsername,
        TicketVerificationResult result
    ) throws Exception {
        java.lang.reflect.Method method = ForgeAuthServerLifecycle.class.getDeclaredMethod(
            "applyVerificationResult",
            String.class,
            String.class,
            TicketVerificationResult.class
        );
        method.setAccessible(true);
        method.invoke(lifecycle, key, expectedUsername, result);
    }

    private static void setBoolean(Object target, String name, boolean value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private static void setString(Object target, String name, String value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static boolean getBoolean(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    static final class FakePlayer {
        private final String username;
        private final UUID uuid;
        public final FakeConnection connection = new FakeConnection();

        FakePlayer(String username) {
            this(username, UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }

        FakePlayer(String username, UUID uuid) {
            this.username = username;
            this.uuid = uuid;
        }

        public String func_70005_c_() {
            return username;
        }

        public UUID getUniqueID() {
            return uuid;
        }
    }

    static final class FakeConnection {
        private String lastMessage;

        public void disconnect(Object textComponent) {
            this.lastMessage = String.valueOf(textComponent);
        }
    }
}
