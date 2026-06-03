package ru.mcrpg.authapi.service;

import java.time.Instant;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mcrpg.authapi.config.AuthApiProperties;
import ru.mcrpg.authapi.domain.entity.AccountEntity;
import ru.mcrpg.authapi.domain.entity.LauncherSessionEntity;
import ru.mcrpg.authapi.domain.repository.AccountRepository;
import ru.mcrpg.authapi.domain.repository.LauncherSessionRepository;
import ru.mcrpg.authapi.web.error.ApiException;

@Service
public class AuthService {

    private static final String DEFAULT_ROLE = "player";
    private static final String DEFAULT_STATUS = "active";

    private final AccountRepository accountRepository;
    private final LauncherSessionRepository launcherSessionRepository;
    private final AuthApiProperties properties;
    private final JwtService jwtService;
    private final RandomTokenService randomTokenService;
    private final HashingService hashingService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

    public AuthService(
        AccountRepository accountRepository,
        LauncherSessionRepository launcherSessionRepository,
        AuthApiProperties properties,
        JwtService jwtService,
        RandomTokenService randomTokenService,
        HashingService hashingService
    ) {
        this.accountRepository = accountRepository;
        this.launcherSessionRepository = launcherSessionRepository;
        this.properties = properties;
        this.jwtService = jwtService;
        this.randomTokenService = randomTokenService;
        this.hashingService = hashingService;
    }

    @Transactional
    public AuthSessionResult register(String username, String email, String password, String deviceName, String userAgent) {
        String normalizedUsername = normalizeUsername(username);
        String usernameKey = normalizeLookupKey(normalizedUsername);
        String normalizedEmail = normalizeEmail(email);

        accountRepository.findByUsernameNormalizedOrEmailNormalized(usernameKey, normalizedEmail)
            .ifPresent(existing -> {
                if (existing.getUsernameNormalized().equals(usernameKey)) {
                    throw ApiException.conflict("username_taken", "Этот ник уже занят.");
                }
                throw ApiException.conflict("email_taken", "Этот email уже используется.");
            });

        AccountEntity account = new AccountEntity();
        account.setUsername(normalizedUsername);
        account.setUsernameNormalized(usernameKey);
        account.setMinecraftUuid(MinecraftPlayerUuid.offlineUuidForUsername(normalizedUsername));
        account.setEmail(normalizedEmail);
        account.setEmailNormalized(normalizedEmail);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setRole(DEFAULT_ROLE);
        account.setStatus(DEFAULT_STATUS);
        AccountEntity saved = accountRepository.save(account);

        return createSession(saved, deviceName, userAgent);
    }

    @Transactional
    public AuthSessionResult login(String login, String password, String deviceName, String userAgent) {
        String normalizedLogin = normalizeLogin(login);
        String lookupKey = normalizeLookupKey(normalizedLogin);
        AccountEntity account = accountRepository.findByUsernameNormalizedOrEmailNormalized(lookupKey, lookupKey)
            .orElseThrow(() -> ApiException.unauthorized("invalid_credentials", "Неверный ник, email или пароль."));

        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
            throw ApiException.unauthorized("invalid_credentials", "Неверный ник, email или пароль.");
        }
        if (!DEFAULT_STATUS.equalsIgnoreCase(account.getStatus())) {
            throw ApiException.forbidden("account_inactive", "Аккаунт отключен.");
        }

        return createSession(account, deviceName, userAgent);
    }

    @Transactional
    public AuthSessionResult refresh(String refreshToken) {
        LauncherSessionEntity session = launcherSessionRepository.findForUpdateByRefreshTokenHash(hashingService.sha256(requireText(refreshToken, "Нужен refresh token.")))
            .orElseThrow(() -> ApiException.unauthorized("invalid_refresh_token", "Refresh token недействителен."));

        if (session.getRevokedAt() != null) {
            throw ApiException.unauthorized("invalid_refresh_token", "Refresh token уже отозван.");
        }
        if (session.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.unauthorized("invalid_refresh_token", "Refresh token истек.");
        }

        AccountEntity account = session.getAccount();
        if (!DEFAULT_STATUS.equalsIgnoreCase(account.getStatus())) {
            throw ApiException.forbidden("account_inactive", "Аккаунт отключен.");
        }

        String newRefreshToken = randomTokenService.nextToken(48);
        session.setRefreshTokenHash(hashingService.sha256(newRefreshToken));
        session.setExpiresAt(refreshExpiry());
        LauncherSessionEntity savedSession = launcherSessionRepository.save(session);
        JwtService.IssuedAccessToken accessToken = jwtService.issueAccessToken(account);

        return new AuthSessionResult(account, accessToken.token(), newRefreshToken, accessToken.expiresAt());
    }

    @Transactional
    public void logout(String refreshToken) {
        String normalized = requireText(refreshToken, "Нужен refresh token.");
        launcherSessionRepository.findForUpdateByRefreshTokenHash(hashingService.sha256(normalized))
            .ifPresent(session -> {
                if (session.getRevokedAt() == null) {
                    session.setRevokedAt(Instant.now());
                    launcherSessionRepository.save(session);
                }
            });
    }

    @Transactional
    public AccountEntity updateEmail(AccountEntity account, String newEmail) {
        String normalizedEmail = normalizeEmail(newEmail);
        if (!account.getEmailNormalized().equals(normalizedEmail) && accountRepository.existsByEmailNormalized(normalizedEmail)) {
            throw ApiException.conflict("email_taken", "Этот email уже используется.");
        }
        account.setEmail(normalizedEmail);
        account.setEmailNormalized(normalizedEmail);
        return accountRepository.save(account);
    }

    private AuthSessionResult createSession(AccountEntity account, String deviceName, String userAgent) {
        String refreshToken = randomTokenService.nextToken(48);

        LauncherSessionEntity session = new LauncherSessionEntity();
        session.setAccount(account);
        session.setRefreshTokenHash(hashingService.sha256(refreshToken));
        session.setDeviceName(normalizeOptional(deviceName, 128));
        session.setUserAgent(normalizeOptional(userAgent, 255));
        session.setExpiresAt(refreshExpiry());
        launcherSessionRepository.save(session);

        JwtService.IssuedAccessToken accessToken = jwtService.issueAccessToken(account);
        return new AuthSessionResult(account, accessToken.token(), refreshToken, accessToken.expiresAt());
    }

    private Instant refreshExpiry() {
        return Instant.now().plusSeconds(properties.getRefreshTokenTtlDays() * 24L * 60L * 60L);
    }

    private static String normalizeUsername(String raw) {
        String normalized = requireText(raw, "Укажи ник.");
        if (!normalized.matches("^[A-Za-z0-9_]{3,16}$")) {
            throw ApiException.badRequest("invalid_username", "Ник должен содержать 3-16 символов: A-Z, a-z, 0-9 или _.");
        }
        return normalized;
    }

    private static String normalizeEmail(String raw) {
        String normalized = requireText(raw, "Укажи email.").toLowerCase(java.util.Locale.ROOT);
        if (normalized.length() > 255) {
            throw ApiException.badRequest("invalid_email", "Email слишком длинный.");
        }
        return normalized;
    }

    private static String normalizeLogin(String raw) {
        return requireText(raw, "Укажи логин.");
    }

    private static String normalizeLookupKey(String raw) {
        return requireText(raw, "Укажи логин.").toLowerCase(java.util.Locale.ROOT);
    }

    private static String normalizeOptional(String raw, int maxLength) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    private static String requireText(String raw, String message) {
        if (raw == null || raw.trim().isEmpty()) {
            throw ApiException.badRequest("invalid_request", message);
        }
        return raw.trim();
    }

    public record AuthSessionResult(
        AccountEntity account,
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt
    ) {
    }
}
