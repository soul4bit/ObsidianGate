package ru.mcrpg.launcher;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import ru.mcrpg.launcher.ui.AvatarImages;
import ru.mcrpg.launcher.ui.LauncherIcons;
import ru.mcrpg.launcher.ui.MicroInteractions;

public final class ProfileController extends AbstractScreenController {

    private static final DateTimeFormatter SESSION_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter SESSION_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM HH:mm")
        .withZone(ZoneId.systemDefault());

    private final ModpackManifestClient manifestClient = new ModpackManifestClient();

    @FXML
    private Label versionLabel;

    @FXML
    private Button homeNavButton;

    @FXML
    private Button settingsNavButton;

    @FXML
    private Button profileNavButton;

    @FXML
    private Button logoutButton;

    @FXML
    private Button editProfileButton;

    @FXML
    private Button switchAccountButton;

    @FXML
    private Button openGameFolderButton;

    @FXML
    private Button securityDetailsButton;

    @FXML
    private Label sidebarProfileNameLabel;

    @FXML
    private ImageView sidebarProfileAvatarView;

    @FXML
    private Label sidebarProfileStatusLabel;

    @FXML
    private Label sidebarProfileRoleLabel;

    @FXML
    private Label profileNameLabel;

    @FXML
    private ImageView profileAvatarView;

    @FXML
    private Label profileRoleBadgeLabel;

    @FXML
    private Label profileStatusLabel;

    @FXML
    private Label profileWelcomeLabel;

    @FXML
    private Label accountUsernameLabel;

    @FXML
    private Label accountRoleLabel;

    @FXML
    private Label profileEmailLabel;

    @FXML
    private Label accountIdLabel;

    @FXML
    private Label sessionExpiresLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Label accountInfoIconLabel;

    @FXML
    private Label usernameIconLabel;

    @FXML
    private Label roleIconLabel;

    @FXML
    private Label emailIconLabel;

    @FXML
    private Label accountIdIconLabel;

    @FXML
    private Label quickActionsIconLabel;

    @FXML
    private Label securityIconLabel;

    @FXML
    private Label roleLimitsIconLabel;

    @FXML
    private Label playerBriefIconLabel;

    @FXML
    private Label maxHomesLabel;

    @FXML
    private Label maxRegionsLabel;

    @FXML
    private Label homeCooldownLabel;

    @FXML
    private Label rtpCooldownLabel;

    @FXML
    private Label roleLimitsHintLabel;

    @FXML
    private Label avatarSummaryLabel;

    @FXML
    private Label sessionSummaryLabel;

    @FXML
    private Label playerNewsDateLabel;

    @FXML
    private Label playerNewsTitleLabel;

    @FXML
    private Label playerNewsBodyLabel;

    @FXML
    private Node profileHeroCard;

    @FXML
    private Node accountInfoCard;

    @FXML
    private Node quickActionsCard;

    @FXML
    private Node securityCard;

    @FXML
    private Node roleLimitsCard;

    @FXML
    private Node playerBriefCard;

    @FXML
    private void initialize() {
        configureCleanGlassSidebar(ScreenRouter.Screen.PROFILE);
        configureChrome();
        configureMicroInteractions();
    }

    @Override
    protected void onContextBound(LauncherContext context) {
        if (!state().isAuthenticated()) {
            router().open(ScreenRouter.Screen.AUTH);
            return;
        }
        configureCleanGlassSidebar(ScreenRouter.Screen.PROFILE);
        renderAccount(state().getSession().getAccount());
        refreshProfileAsync();
        refreshPlayerNewsAsync();
    }

    private void configureMicroInteractions() {
        MicroInteractions.installHoverLift(accountInfoCard);
        MicroInteractions.installHoverLift(quickActionsCard);
        MicroInteractions.installHoverLift(roleLimitsCard);
        MicroInteractions.installHoverLift(playerBriefCard);
        MicroInteractions.installHoverLift(securityCard);
        MicroInteractions.installHoverLift(editProfileButton, -1.0d, 1.005d);
        MicroInteractions.installHoverLift(switchAccountButton, -1.0d, 1.005d);
        MicroInteractions.installHoverLift(openGameFolderButton, -1.0d, 1.005d);
        MicroInteractions.installHoverLift(logoutButton, -1.0d, 1.005d);
        MicroInteractions.playEntrance(profileHeroCard, accountInfoCard, quickActionsCard, roleLimitsCard, playerBriefCard, securityCard);
    }

    @FXML
    private void onOpenHome() {
        router().open(ScreenRouter.Screen.HOME);
    }

    @FXML
    private void onLogout() {
        logoutAndOpenAuth();
    }

    @FXML
    private void onSwitchAccount() {
        logoutAndOpenAuth();
    }

    @FXML
    private void onOpenGameFolder() {
        try {
            String configuredDirectory = state().getConfig().getGameDirectory();
            Path gameDirectory = Paths.get(valueOrFallback(configuredDirectory, LauncherDefaults.defaultGameDirectory()))
                .toAbsolutePath()
                .normalize();
            Files.createDirectories(gameDirectory);
            openDesktopPath(gameDirectory);
            setProfileStatus("Открыта папка игры: " + gameDirectory);
        } catch (Exception exception) {
            showError("Не удалось открыть папку игры: " + exception.getMessage());
        }
    }

    @FXML
    private void onEditProfile() {
        setProfileStatus("Редактирование профиля пока не подключено.");
    }

    @FXML
    private void onShowSecurityDetails() {
        setProfileStatus("Сессия защищена access/refresh token. Пароль в лаунчере не хранится.");
    }

    @FXML
    private void openSettings() {
        router().open(ScreenRouter.Screen.SETTINGS);
    }

    private void logoutAndOpenAuth() {
        context().getAuthService().logoutQuietly(state().getConfig(), state().getSession());
        state().setSession(null);
        router().open(ScreenRouter.Screen.AUTH);
    }

    private void configureChrome() {
        configureWindowButtons();
        versionLabel.setText("Лаунчер " + LauncherBrand.displayVersion());
        setButtonGraphic(homeNavButton, "home", 16.0d, "#f8fafc");
        setButtonGraphic(settingsNavButton, "settings", 16.0d, "#f8fafc");
        setButtonGraphic(profileNavButton, "profile", 16.0d, "#ffffff");
        setButtonGraphic(editProfileButton, "edit", 16.0d, "#ffffff");
        setButtonGraphic(switchAccountButton, "users", 16.0d, "#dbe4ef");
        setButtonGraphic(openGameFolderButton, "folder", 16.0d, "#dbe4ef");
        setButtonGraphic(logoutButton, "logout", 16.0d, "#fb7185");
        setButtonGraphic(securityDetailsButton, "external", 16.0d, "#dbe4ef");
        accountInfoIconLabel.setGraphic(LauncherIcons.icon("info", 16.0d, "#c084fc"));
        usernameIconLabel.setGraphic(LauncherIcons.icon("profile", 16.0d, "#b8c3d3"));
        roleIconLabel.setGraphic(LauncherIcons.icon("crown", 16.0d, "#b8c3d3"));
        emailIconLabel.setGraphic(LauncherIcons.icon("mail", 16.0d, "#b8c3d3"));
        accountIdIconLabel.setGraphic(LauncherIcons.icon("id-card", 16.0d, "#b8c3d3"));
        quickActionsIconLabel.setGraphic(LauncherIcons.icon("bolt", 16.0d, "#c084fc"));
        securityIconLabel.setGraphic(LauncherIcons.icon("shield", 16.0d, "#86efac"));
        roleLimitsIconLabel.setGraphic(LauncherIcons.icon("crown", 16.0d, "#c084fc"));
        playerBriefIconLabel.setGraphic(LauncherIcons.icon("bell", 16.0d, "#86efac"));
    }

    private void refreshProfileAsync() {
        setProfileStatus("Обновляем профиль...");
        Task<AuthAccount> task = new Task<AuthAccount>() {
            @Override
            protected AuthAccount call() throws Exception {
                return context().getAuthService().fetchProfile(state().getConfig(), state().getSession());
            }
        };

        task.setOnSucceeded(event -> {
            AuthAccount account = task.getValue();
            state().setSession(state().getSession().withAccount(account));
            renderAccount(account);
            setProfileStatus("Профиль синхронизирован.");
        });

        task.setOnFailed(event -> {
            Throwable error = task.getException();
            if (handleExpiredSession(error)) {
                return;
            }
            setProfileStatus(error == null ? "Не удалось обновить профиль." : error.getMessage());
        });

        Thread thread = new Thread(task, "profile-refresh");
        thread.setDaemon(true);
        thread.start();
    }

    private void refreshPlayerNewsAsync() {
        setLabelText(playerNewsDateLabel, "Новости");
        setLabelText(playerNewsTitleLabel, "Загружаем новости...");
        setLabelText(playerNewsBodyLabel, "");

        Task<ModpackNews> task = new Task<ModpackNews>() {
            @Override
            protected ModpackNews call() throws Exception {
                LoadedManifest loadedManifest = manifestClient.load(state().getConfig().getManifestUrl());
                ModpackManifest manifest = loadedManifest.getManifest();
                return chooseProfileNews(manifest);
            }
        };

        task.setOnSucceeded(event -> renderPlayerNews(task.getValue()));
        task.setOnFailed(event -> {
            setLabelText(playerNewsDateLabel, "Новости");
            setLabelText(playerNewsTitleLabel, "Новости временно недоступны");
            setLabelText(playerNewsBodyLabel, "Играть можно и без них, если сборка уже синхронизирована.");
        });

        Thread thread = new Thread(task, "profile-news-refresh");
        thread.setDaemon(true);
        thread.start();
    }

    private void setProfileStatus(String message) {
        if (statusLabel == null) {
            return;
        }
        statusLabel.setText(message == null ? "" : message.trim());
        MicroInteractions.playStatusSwap(statusLabel);
    }

    private void renderAccount(AuthAccount account) {
        String roleLabel = resolveRoleLabel(account.getRole());
        String roleCode = resolveRoleCodeLabel(account.getRole());
        String accountStatus = resolveAccountStatusLabel(account.getStatus());
        RoleSummary roleSummary = RoleSummary.forRole(account.getRole());
        String sessionTime = SESSION_TIME_FORMAT.format(state().getSession().getExpiresAt());
        String shortSessionTime = SESSION_DATE_FORMAT.format(state().getSession().getExpiresAt());

        profileAvatarView.setImage(AvatarImages.forAccount(account));
        profileNameLabel.setText(account.getUsername());
        profileRoleBadgeLabel.setText(roleLabel.equals(roleCode) ? roleLabel : roleLabel + " / " + roleCode);
        profileStatusLabel.setText(accountStatus);
        profileWelcomeLabel.setText("Добро пожаловать на ObsidianGate");

        setLabelText(sidebarProfileNameLabel, account.getUsername());
        if (sidebarProfileAvatarView != null) {
            sidebarProfileAvatarView.setImage(AvatarImages.forAccount(account));
        }
        setLabelText(sidebarProfileStatusLabel, accountStatus);
        setLabelText(sidebarProfileRoleLabel, roleCode);
        refreshCleanGlassSidebar();

        accountUsernameLabel.setText(account.getUsername());
        accountRoleLabel.setText(roleLabel);
        profileEmailLabel.setText(valueOrFallback(account.getEmail(), "Email не указан"));
        accountIdLabel.setText(valueOrFallback(account.getId(), "—"));
        sessionExpiresLabel.setText("Сессия активна до " + sessionTime);
        avatarSummaryLabel.setText(avatarSummary(account));
        sessionSummaryLabel.setText("до " + shortSessionTime);
        maxHomesLabel.setText(roleSummary.maxHomesText());
        maxRegionsLabel.setText(roleSummary.maxRegionsText());
        homeCooldownLabel.setText(roleSummary.homeCooldownText());
        rtpCooldownLabel.setText(roleSummary.randomTeleportCooldownText());
        roleLimitsHintLabel.setText(roleSummary.description());
    }

    private void renderPlayerNews(ModpackNews news) {
        if (news == null || !news.hasContent()) {
            setLabelText(playerNewsDateLabel, "Новости");
            setLabelText(playerNewsTitleLabel, "Свежих новостей пока нет");
            setLabelText(playerNewsBodyLabel, "Здесь появится важное для игрока перед запуском.");
            return;
        }
        setLabelText(playerNewsDateLabel, valueOrFallback(news.getDate(), valueOrFallback(news.getVersion(), "Новости")));
        setLabelText(playerNewsTitleLabel, valueOrFallback(news.getTitle(), "Обновление ObsidianGate"));
        setLabelText(playerNewsBodyLabel, summarizeNews(news));
    }

    private static ModpackNews chooseProfileNews(ModpackManifest manifest) {
        if (manifest == null) {
            return null;
        }
        if (manifest.getNews() != null && manifest.getNews().hasContent()) {
            return manifest.getNews();
        }
        if (manifest.getChangelog() != null && manifest.getChangelog().hasContent()) {
            return manifest.getChangelog();
        }
        if (manifest.getHistory() != null && !manifest.getHistory().isEmpty()) {
            return manifest.getHistory().get(0);
        }
        return null;
    }

    private static String summarizeNews(ModpackNews news) {
        if (news == null) {
            return "";
        }
        if (hasText(news.getBody())) {
            String body = news.getBody().trim();
            return body.length() > 130 ? body.substring(0, 127).trim() + "..." : body;
        }
        if (news.getImportant() != null && !news.getImportant().isEmpty()) {
            return news.getImportant().get(0);
        }
        if (news.getHighlights() != null && !news.getHighlights().isEmpty()) {
            return news.getHighlights().get(0);
        }
        return "Откройте главную страницу, чтобы посмотреть подробности обновления.";
    }

    private boolean handleExpiredSession(Throwable error) {
        if (!(error instanceof AuthSessionExpiredException)) {
            return false;
        }

        state().setSession(null);
        state().setAuthNotice(error.getMessage());
        context().persistStateQuietly();
        router().open(ScreenRouter.Screen.AUTH);
        return true;
    }

    private static void openDesktopPath(Path target) throws IOException {
        if (!Desktop.isDesktopSupported()) {
            throw new IOException("Открытие через рабочий стол не поддерживается.");
        }
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.OPEN)) {
            throw new IOException("Открытие файлов не поддерживается.");
        }
        desktop.open(target.toFile());
    }

    private static String resolveRoleLabel(String role) {
        if (!hasText(role)) {
            return "Игрок";
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        if ("admin".equals(normalized) || "administrator".equals(normalized)) {
            return "Администратор";
        }
        if ("moderator".equals(normalized)) {
            return "Модератор";
        }
        if ("vip".equals(normalized)) {
            return "VIP";
        }
        if ("player".equals(normalized)) {
            return "Игрок";
        }
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private static String resolveRoleCodeLabel(String role) {
        if (!hasText(role)) {
            return "Игрок";
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        if ("admin".equals(normalized) || "administrator".equals(normalized)) {
            return "Админ";
        }
        if ("moderator".equals(normalized)) {
            return "Модератор";
        }
        if ("vip".equals(normalized)) {
            return "VIP";
        }
        if ("player".equals(normalized)) {
            return "Игрок";
        }
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private static String resolveAccountStatusLabel(String status) {
        if (!hasText(status) || "active".equalsIgnoreCase(status.trim())) {
            return "В сети";
        }
        if ("inactive".equalsIgnoreCase(status.trim())) {
            return "Неактивен";
        }
        if ("banned".equalsIgnoreCase(status.trim())) {
            return "Заблокирован";
        }
        return status.trim();
    }

    private static String valueOrFallback(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private static String avatarSummary(AuthAccount account) {
        if (account == null) {
            return "по умолчанию";
        }
        if (hasText(account.getAvatar())) {
            return account.getAvatar();
        }
        if (hasText(account.getAvatarUrl())) {
            return "внешний";
        }
        return "авто";
    }

    private static void setButtonGraphic(Button button, String iconName, double size, String color) {
        if (button != null) {
            button.setGraphic(LauncherIcons.icon(iconName, size, color));
            button.setGraphicTextGap(8.0d);
        }
    }

    private static void setLabelText(Label label, String value) {
        if (label != null) {
            label.setText(value);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static final class RoleSummary {
        private final String label;
        private final int maxHomes;
        private final int maxRegions;
        private final long homeCooldownSeconds;
        private final long randomTeleportCooldownSeconds;

        private RoleSummary(
            String label,
            int maxHomes,
            int maxRegions,
            long homeCooldownSeconds,
            long randomTeleportCooldownSeconds
        ) {
            this.label = label;
            this.maxHomes = maxHomes;
            this.maxRegions = maxRegions;
            this.homeCooldownSeconds = homeCooldownSeconds;
            this.randomTeleportCooldownSeconds = randomTeleportCooldownSeconds;
        }

        private static RoleSummary forRole(String role) {
            String normalized = role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
            if ("admin".equals(normalized) || "administrator".equals(normalized) || "owner".equals(normalized)) {
                return new RoleSummary("администратора", Integer.MAX_VALUE, Integer.MAX_VALUE, 0L, 0L);
            }
            if ("vip".equals(normalized) || "premium".equals(normalized)) {
                return new RoleSummary("VIP", 5, 5, 15L, 75L);
            }
            return new RoleSummary("игрока", 3, 3, 30L, 150L);
        }

        private String maxHomesText() {
            return limitText(maxHomes);
        }

        private String maxRegionsText() {
            return limitText(maxRegions);
        }

        private String homeCooldownText() {
            return cooldownText(homeCooldownSeconds);
        }

        private String randomTeleportCooldownText() {
            return cooldownText(randomTeleportCooldownSeconds);
        }

        private String description() {
            return "Лимиты роли " + label + ": дома, регионы и перезарядки серверных телепортов.";
        }

        private static String limitText(int value) {
            return value == Integer.MAX_VALUE ? "∞" : Integer.toString(value);
        }

        private static String cooldownText(long seconds) {
            if (seconds <= 0L) {
                return "нет";
            }
            if (seconds >= 60L && seconds % 60L == 0L) {
                return seconds / 60L + " мин";
            }
            return seconds + " сек";
        }
    }
}
