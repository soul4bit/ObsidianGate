package ru.mcrpg.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuthServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void refreshIfNeededClearsStoredSessionWhenRefreshTokenIsInvalid() throws Exception {
        HttpServer server = startRefreshServer(401, "{\"error\":\"invalid_refresh_token\",\"message\":\"Refresh token is invalid.\"}");
        try {
            AuthSessionStore store = new AuthSessionStore(tempDirectory.resolve("session.json"));
            AuthService service = new AuthService(new AuthApiClient(HttpClient.newHttpClient()), store);
            LauncherConfig config = LauncherConfig.defaults();
            config.setAuthBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());

            AuthSession session = expiringSession(true);
            store.save(session);

            IOException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IOException.class,
                () -> service.refreshIfNeeded(config, session)
            );

            assertInstanceOf(AuthSessionExpiredException.class, exception);
            assertEquals("Сессия истекла. Войдите в аккаунт снова.", exception.getMessage());
            assertFalse(Files.exists(store.getSessionFile()));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void refreshIfNeededKeepsStoredSessionForNonAuthFailures() throws Exception {
        HttpServer server = startRefreshServer(500, "{\"error\":\"server_error\",\"message\":\"Backend is down.\"}");
        try {
            AuthSessionStore store = new AuthSessionStore(tempDirectory.resolve("session.json"));
            AuthService service = new AuthService(new AuthApiClient(HttpClient.newHttpClient()), store);
            LauncherConfig config = LauncherConfig.defaults();
            config.setAuthBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());

            AuthSession session = expiringSession(true);
            store.save(session);

            IOException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IOException.class,
                () -> service.refreshIfNeeded(config, session)
            );

            AuthClientException authException = assertInstanceOf(AuthClientException.class, exception);
            assertEquals(500, authException.getStatusCode());
            assertTrue(Files.exists(store.getSessionFile()));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void createGameTicketsRequestsConfiguredAmount() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/game/tickets", exchange -> {
            assertEquals("Bearer access-1", exchange.getRequestHeaders().getFirst("Authorization"));
            int number = requests.incrementAndGet();
            byte[] body = (
                "{\"ticket\":\"ticket-" + number + "\","
                    + "\"username\":\"Knight\","
                    + "\"uuid\":\"uuid-1\","
                    + "\"serverId\":\"obsidiangate-main\","
                    + "\"expiresAt\":\"2026-05-07T13:06:01Z\"}"
            ).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            AuthSessionStore store = new AuthSessionStore(tempDirectory.resolve("session.json"));
            AuthService service = new AuthService(new AuthApiClient(HttpClient.newHttpClient()), store);
            LauncherConfig config = LauncherConfig.defaults();
            config.setAuthBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            config.setServerId("obsidiangate-main");

            List<GameTicket> tickets = service.createGameTickets(config, activeSession(false), 3);

            assertEquals(3, requests.get());
            assertEquals(3, tickets.size());
            assertEquals("ticket-1", tickets.get(0).getTicket());
            assertEquals("ticket-2", tickets.get(1).getTicket());
            assertEquals("ticket-3", tickets.get(2).getTicket());
        } finally {
            server.stop(0);
        }
    }

    private HttpServer startRefreshServer(int statusCode, String responseBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth/refresh", exchange -> {
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static AuthSession expiringSession(boolean persisted) {
        AuthAccount account = new AuthAccount("acc-1", "Knight", "knight@example.com", "player", "active");
        return new AuthSession(account, "access-1", "refresh-1", Instant.now().plusSeconds(5), persisted);
    }

    private static AuthSession activeSession(boolean persisted) {
        AuthAccount account = new AuthAccount("acc-1", "Knight", "knight@example.com", "player", "active");
        return new AuthSession(account, "access-1", "refresh-1", Instant.now().plusSeconds(300), persisted);
    }
}
