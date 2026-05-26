package ru.mcrpg.forgeauth.server;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import ru.mcrpg.gameauth.TicketVerificationClient;
import ru.mcrpg.gameauth.TicketVerificationResult;

final class ForgeAuthServerLifecycle implements PlayerRoleLookup {

    private static final String DISCONNECT_MESSAGE = "Для входа нужен лаунчер ObsidianGate.";
    private static final String MARKER_TIMEOUT_NO_TICKET = "AUTH_TIMEOUT_NO_TICKET";
    private static final String MARKER_TICKET_EXPIRED = "AUTH_TICKET_EXPIRED";
    private static final String MARKER_TICKET_SERVER_MISMATCH = "AUTH_TICKET_SERVER_MISMATCH";
    private static final String MARKER_API_UNREACHABLE = "AUTH_API_UNREACHABLE";
    private static final String MARKER_TICKET_REJECTED = "AUTH_TICKET_REJECTED";
    private static final String MARKER_AUTH_UNAVAILABLE = "AUTH_UNAVAILABLE";
    private static final String MARKER_INVALID_TICKET_PACKET = "AUTH_INVALID_TICKET_PACKET";
    private static final String MARKER_TICKET_USERNAME_MISMATCH = "AUTH_TICKET_USERNAME_MISMATCH";
    private static final String MARKER_VERIFY_ERROR = "AUTH_VERIFY_ERROR";

    private final Logger logger;
    private final AuthServerConfig config;
    private final MinecraftPlayerBridge playerBridge;
    private final TicketVerificationClient verificationClient;
    private final ExecutorService verificationExecutor;
    private final Map<String, PlayerAuthState> authStates;
    private final Queue<Runnable> mainThreadActions;
    private final ChatAppearanceService chatAppearance;

    private int serverTickCounter;

    ForgeAuthServerLifecycle(Logger logger) {
        this(
            logger,
            AuthServerConfig.fromSystem(),
            new MinecraftPlayerBridge(),
            new TicketVerificationClient(),
            newSingleThreadExecutor(),
            new ConcurrentHashMap<String, PlayerAuthState>(),
            new ConcurrentLinkedQueue<Runnable>()
        );
    }

    ForgeAuthServerLifecycle(
        Logger logger,
        AuthServerConfig config,
        MinecraftPlayerBridge playerBridge,
        TicketVerificationClient verificationClient,
        ExecutorService verificationExecutor,
        Map<String, PlayerAuthState> authStates,
        Queue<Runnable> mainThreadActions
    ) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.config = Objects.requireNonNull(config, "config");
        this.playerBridge = Objects.requireNonNull(playerBridge, "playerBridge");
        this.verificationClient = Objects.requireNonNull(verificationClient, "verificationClient");
        this.verificationExecutor = Objects.requireNonNull(verificationExecutor, "verificationExecutor");
        this.authStates = Objects.requireNonNull(authStates, "authStates");
        this.mainThreadActions = Objects.requireNonNull(mainThreadActions, "mainThreadActions");
        this.chatAppearance = new ChatAppearanceService(logger, this.authStates);
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        trackPlayerLogin(playerBridge.extractPlayerFromEvent(event));
    }

    void trackPlayerLogin(Object player) {
        String username = playerBridge.extractUsername(player);
        if (username.isEmpty()) {
            logger.warning("Игрок вошел без читаемого ника. Таймаут авторизации через лаунчер нельзя отследить.");
            return;
        }

        String key = normalizeKey(username);
        PlayerAuthState existing = authStates.get(key);
        if (existing != null) {
            existing.player = player;
            chatAppearance.applyPlayerAppearance(player, existing.role);
            logger.info(String.format(
                "Player %s logged in. Preserving existing launcher auth state: verified=%s inFlight=%s.",
                username,
                existing.verified,
                existing.verificationInFlight
            ));
            return;
        }

        authStates.put(key, new PlayerAuthState(player, username, Instant.now()));
        chatAppearance.applyPlayerAppearance(player, "player");
        logger.info(String.format(
            "Игрок %s вошел. Ждем подтверждение авторизации лаунчера до %d секунд.",
            username,
            config.getGraceSeconds()
        ));
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Object player = playerBridge.extractPlayerFromEvent(event);
        String username = playerBridge.extractUsername(player);
        if (!username.isEmpty()) {
            authStates.remove(normalizeKey(username));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerChat(ServerChatEvent event) {
        chatAppearance.onServerChat(event);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        runServerEndTick();
    }

    void runServerEndTick() {
        Runnable action;
        while ((action = mainThreadActions.poll()) != null) {
            action.run();
        }

        serverTickCounter++;
        if (serverTickCounter % 20 != 0) {
            return;
        }

        Instant now = Instant.now();
        for (PlayerAuthState state : authStates.values()) {
            if (state.verified) {
                continue;
            }
            if (state.verificationInFlight) {
                continue;
            }
            long elapsedSeconds = Duration.between(state.connectedAt, now).getSeconds();
            if (elapsedSeconds >= config.getGraceSeconds()) {
                failAndDisconnect(state.username, MARKER_TIMEOUT_NO_TICKET, "Лаунчер не прислал ticket вовремя.");
            }
        }
    }

    void onTicketMessage(AuthTicketMessage message, Object ctx) {
        Object player;
        String username;
        try {
            player = playerBridge.extractPlayerFromMessageContext(ctx);
            username = playerBridge.extractUsername(player);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Could not resolve player from launcher auth packet.", exception);
            return;
        }
        if (username.isEmpty()) {
            logger.warning("Получен пакет авторизации лаунчера от игрока с нечитаемым ником.");
            return;
        }

        String key = normalizeKey(username);
        PlayerAuthState state = authStates.computeIfAbsent(key, unused -> new PlayerAuthState(player, username, Instant.now()));
        state.player = player;

        if (!config.isReady()) {
            failAndDisconnect(username, MARKER_AUTH_UNAVAILABLE, "Серверная авторизация не настроена.");
            return;
        }

        if (!message.isComplete()) {
            failAndDisconnect(username, MARKER_INVALID_TICKET_PACKET, "Пакет launcher ticket неполный.");
            return;
        }

        if (!config.acceptsServerId(message.getServerId())) {
            failAndDisconnect(username, MARKER_TICKET_SERVER_MISMATCH, "Launcher ticket выдан для другого serverId.");
            return;
        }

        if (state.verified) {
            logger.info(String.format("Повторный пакет авторизации лаунчера от %s пропущен.", username));
            return;
        }

        if (state.verificationInFlight) {
            logger.info(String.format("Параллельный пакет авторизации лаунчера от %s пропущен.", username));
            return;
        }

        state.verificationInFlight = true;
        verificationExecutor.submit(() -> verifyTicketAsync(key, state.username, message));
    }

    void shutdown() {
        verificationExecutor.shutdownNow();
        authStates.clear();
        mainThreadActions.clear();
    }

    @Override
    public String roleFor(Object player) {
        String username = playerBridge.extractUsername(player);
        if (username.isEmpty()) {
            return "player";
        }
        PlayerAuthState state = authStates.get(normalizeKey(username));
        return state == null ? "player" : state.role;
    }

    private void verifyTicketAsync(String key, String expectedUsername, AuthTicketMessage message) {
        try {
            TicketVerificationResult result = verificationClient.verify(
                config.getAuthBaseUrl(),
                message.getTicket(),
                config.getServerId()
            );
            mainThreadActions.add(() -> applyVerificationResult(key, expectedUsername, result));
        } catch (IOException exception) {
            logger.log(Level.WARNING, "Запрос проверки launcher ticket не удался для " + expectedUsername + ".", exception);
            mainThreadActions.add(() -> failAndDisconnect(expectedUsername, MARKER_API_UNREACHABLE, "Auth API не проверил launcher ticket."));
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Неожиданная ошибка проверки авторизации лаунчера для " + expectedUsername + ".", exception);
            mainThreadActions.add(() -> failAndDisconnect(expectedUsername, MARKER_VERIFY_ERROR, "Неожиданная ошибка проверки авторизации лаунчера."));
        }
    }

    private void applyVerificationResult(String key, String expectedUsername, TicketVerificationResult result) {
        PlayerAuthState state = authStates.get(key);
        if (state == null) {
            return;
        }

        state.verificationInFlight = false;
        if (!result.isValid()) {
            failAndDisconnect(
                expectedUsername,
                markerForVerificationReason(result.getReason()),
                "Ticket отклонен: " + normalizeReason(result.getReason())
            );
            return;
        }

        if (!expectedUsername.equalsIgnoreCase(result.getUsername())) {
            failAndDisconnect(expectedUsername, MARKER_TICKET_USERNAME_MISMATCH, "Ticket принадлежит другому нику.");
            return;
        }

        state.verified = true;
        state.role = result.getRole();
        chatAppearance.applyPlayerAppearance(state.player, state.role);
        logger.info(String.format(
            "Авторизация лаунчера принята для %s. Роль=%s accountId=%s",
            expectedUsername,
            result.getRole(),
            result.getAccountId()
        ));
    }

    private void failAndDisconnect(String username, String marker, String reason) {
        String key = normalizeKey(username);
        PlayerAuthState state = authStates.remove(key);
        if (state == null) {
            return;
        }

        state.verificationInFlight = false;
        String markedReason = formatMarkedReason(marker, reason);
        logger.warning(String.format("%s Disconnecting %s: %s", normalizeMarker(marker), state.username, reason));
        try {
            playerBridge.disconnectPlayer(state.player, DISCONNECT_MESSAGE + " Причина: " + markedReason);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Не удалось отключить неавторизованного игрока " + state.username + ".", exception);
        }
    }

    private static String normalizeKey(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private static String normalizeReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.isEmpty()) {
            return "неизвестная причина";
        }
        if ("invalid".equalsIgnoreCase(normalized)) {
            return "ticket недействителен";
        }
        if ("used".equalsIgnoreCase(normalized)) {
            return "ticket уже использован";
        }
        if ("expired".equalsIgnoreCase(normalized)) {
            return "ticket истек";
        }
        if ("server_mismatch".equalsIgnoreCase(normalized)) {
            return "ticket выдан для другого сервера";
        }
        if ("account_inactive".equalsIgnoreCase(normalized)) {
            return "аккаунт отключен";
        }
        return normalized;
    }

    private static String markerForVerificationReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if ("expired".equalsIgnoreCase(normalized)) {
            return MARKER_TICKET_EXPIRED;
        }
        if ("server_mismatch".equalsIgnoreCase(normalized)) {
            return MARKER_TICKET_SERVER_MISMATCH;
        }
        return MARKER_TICKET_REJECTED;
    }

    private static String formatMarkedReason(String marker, String reason) {
        return "[" + normalizeMarker(marker) + "] " + (reason == null ? "" : reason.trim());
    }

    private static String normalizeMarker(String marker) {
        String normalized = marker == null ? "" : marker.trim();
        return normalized.isEmpty() ? "AUTH_UNKNOWN" : normalized;
    }

    private static ExecutorService newSingleThreadExecutor() {
        AtomicInteger counter = new AtomicInteger(1);
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "obsidiangate-auth-verify-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newSingleThreadExecutor(factory);
    }

    interface PlayerView {
        String getRole();
    }

    private static final class PlayerAuthState implements PlayerView {
        private volatile Object player;
        private final String username;
        private final Instant connectedAt;
        private volatile boolean verificationInFlight;
        private volatile boolean verified;
        private volatile String role = "player";

        private PlayerAuthState(Object player, String username, Instant connectedAt) {
            this.player = player;
            this.username = username;
            this.connectedAt = connectedAt;
        }

        @Override
        public String getRole() {
            return role;
        }
    }
}
