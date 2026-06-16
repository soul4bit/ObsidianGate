package ru.mcrpg.launcher;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class AuthService {

    private static final Duration ACCESS_TOKEN_REFRESH_WINDOW = Duration.ofSeconds(45);
    private static final String EXPIRED_SESSION_MESSAGE = "Сессия истекла. Войдите в аккаунт снова.";

    private final AuthApiClient authApiClient;
    private final AuthSessionStore authSessionStore;

    public AuthService(AuthApiClient authApiClient, AuthSessionStore authSessionStore) {
        this.authApiClient = authApiClient;
        this.authSessionStore = authSessionStore;
    }

    public AuthSession login(LauncherConfig config, String login, String password, boolean rememberSession) throws IOException {
        AuthSession session = authApiClient.login(config.getAuthBaseUrl(), login, password, deviceName());
        return remember(session, rememberSession);
    }

    public AuthSession register(LauncherConfig config, String username, String email, String password, boolean rememberSession) throws IOException {
        AuthSession session = authApiClient.register(config.getAuthBaseUrl(), username, email, password, deviceName());
        return remember(session, rememberSession);
    }

    public AuthSession refreshIfNeeded(LauncherConfig config, AuthSession session) throws IOException {
        if (session == null) {
            return null;
        }
        if (!session.isAccessTokenExpiringWithin(ACCESS_TOKEN_REFRESH_WINDOW)) {
            return session;
        }

        AuthSession refreshed = withSessionRecovery(() -> authApiClient.refresh(config.getAuthBaseUrl(), session));
        AuthSession enriched = refreshed.withAccount(session.getAccount()).withPersistence(session.isPersisted());
        persist(enriched);
        return enriched;
    }

    public AuthAccount fetchProfile(LauncherConfig config, AuthSession session) throws IOException {
        AuthSession refreshed = refreshIfNeeded(config, session);
        AuthAccount account = withSessionRecovery(() -> authApiClient.me(config.getAuthBaseUrl(), refreshed.getAccessToken()));
        AuthSession enriched = refreshed.withAccount(account);
        persist(enriched);
        return account;
    }

    public GameTicket createGameTicket(LauncherConfig config, AuthSession session) throws IOException {
        return createGameTickets(config, session, 1).get(0);
    }

    public List<GameTicket> createGameTickets(LauncherConfig config, AuthSession session, int count) throws IOException {
        int resolvedCount = Math.max(1, count);
        AuthSession refreshed = refreshIfNeeded(config, session);
        persist(refreshed);
        List<GameTicket> tickets = new ArrayList<GameTicket>(resolvedCount);
        for (int index = 0; index < resolvedCount; index++) {
            tickets.add(withSessionRecovery(() -> authApiClient.createGameTicket(
                config.getAuthBaseUrl(),
                refreshed.getAccessToken(),
                config.getServerId()
            )));
        }
        return Collections.unmodifiableList(tickets);
    }

    public void logoutQuietly(LauncherConfig config, AuthSession session) {
        if (session != null) {
            try {
                authApiClient.logout(config.getAuthBaseUrl(), session);
            } catch (IOException ignored) {
            }
        }
        try {
            authSessionStore.clear();
        } catch (IOException ignored) {
        }
    }

    public void persist(AuthSession session) throws IOException {
        if (session == null) {
            authSessionStore.clear();
            return;
        }
        if (session.isPersisted()) {
            authSessionStore.save(session);
        } else {
            authSessionStore.clear();
        }
    }

    private AuthSession remember(AuthSession session, boolean rememberSession) throws IOException {
        AuthSession persisted = session.withPersistence(rememberSession);
        persist(persisted);
        return persisted;
    }

    private <T> T withSessionRecovery(IoSupplier<T> action) throws IOException {
        try {
            return action.get();
        } catch (IOException exception) {
            throw normalizeSessionFailure(exception);
        }
    }

    private IOException normalizeSessionFailure(IOException exception) {
        if (!isExpiredSession(exception)) {
            return exception;
        }
        clearStoredSessionQuietly();
        return new AuthSessionExpiredException(EXPIRED_SESSION_MESSAGE, exception);
    }

    private void clearStoredSessionQuietly() {
        try {
            authSessionStore.clear();
        } catch (IOException ignored) {
        }
    }

    private static boolean isExpiredSession(IOException exception) {
        if (exception instanceof AuthSessionExpiredException) {
            return true;
        }
        if (!(exception instanceof AuthClientException)) {
            return false;
        }

        AuthClientException authException = (AuthClientException) exception;
        if (authException.getStatusCode() == 401) {
            return true;
        }

        String errorCode = normalizeLower(authException.getErrorCode());
        String message = normalizeLower(authException.getMessage());
        return errorCode.contains("refresh_token")
            || errorCode.contains("access_token")
            || errorCode.contains("invalid_token")
            || errorCode.contains("token_expired")
            || errorCode.contains("unauthorized")
            || message.contains("refresh token")
            || message.contains("access token")
            || message.contains("token expired")
            || message.contains("unauthorized");
    }

    private static String normalizeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String deviceName() {
        String host = "unknown-host";
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
        }
        PlatformInfo platform = PlatformInfo.current();
        return LauncherBrand.APP_NAME + "@" + host + " (" + platform.getOs() + "/" + platform.getArch() + ")";
    }

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws IOException;
    }
}
