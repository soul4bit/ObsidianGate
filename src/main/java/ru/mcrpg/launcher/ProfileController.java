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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import ru.mcrpg.launcher.ui.AvatarImages;
import ru.mcrpg.launcher.ui.LauncherIcons;

public final class ProfileController extends AbstractScreenController {

    private static final DateTimeFormatter SESSION_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        .withZone(ZoneId.systemDefault());

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
    private void initialize() {
        configureCleanGlassSidebar(ScreenRouter.Screen.PROFILE);
        configureChrome();
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
            statusLabel.setText("Открыта папка игры: " + gameDirectory);
        } catch (Exception exception) {
            showError("Не удалось открыть папку игры: " + exception.getMessage());
        }
    }

    @FXML
    private void onEditProfile() {
        statusLabel.setText("Редактирование профиля пока не подключено.");
    }

    @FXML
    private void onShowSecurityDetails() {
        statusLabel.setText("Сессия защищена access/refresh token. Пароль в лаунчере не хранится.");
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
        setButtonGraphic(homeNavButton, "home", 17.0d, "#f8fafc");
        setButtonGraphic(settingsNavButton, "settings", 17.0d, "#f8fafc");
        setButtonGraphic(profileNavButton, "profile", 17.0d, "#ffffff");
        editProfileButton.setGraphic(LauncherIcons.icon("edit", 15.0d, "#ffffff"));
        switchAccountButton.setGraphic(LauncherIcons.icon("users", 16.0d, "#dbe4ef"));
        openGameFolderButton.setGraphic(LauncherIcons.icon("folder", 16.0d, "#dbe4ef"));
        logoutButton.setGraphic(LauncherIcons.icon("logout", 18.0d, "#fb7185"));
        securityDetailsButton.setGraphic(LauncherIcons.icon("external", 15.0d, "#dbe4ef"));
        accountInfoIconLabel.setGraphic(LauncherIcons.icon("info", 18.0d, "#c084fc"));
        usernameIconLabel.setGraphic(LauncherIcons.icon("profile", 15.0d, "#b8c3d3"));
        roleIconLabel.setGraphic(LauncherIcons.icon("crown", 15.0d, "#b8c3d3"));
        emailIconLabel.setGraphic(LauncherIcons.icon("mail", 15.0d, "#b8c3d3"));
        accountIdIconLabel.setGraphic(LauncherIcons.icon("id-card", 15.0d, "#b8c3d3"));
        quickActionsIconLabel.setGraphic(LauncherIcons.icon("bolt", 18.0d, "#c084fc"));
        securityIconLabel.setGraphic(LauncherIcons.icon("shield", 24.0d, "#86efac"));
    }

    private void refreshProfileAsync() {
        statusLabel.setText("Обновляем профиль...");
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
            statusLabel.setText("Профиль синхронизирован.");
        });

        task.setOnFailed(event -> {
            Throwable error = task.getException();
            if (handleExpiredSession(error)) {
                return;
            }
            statusLabel.setText(error == null ? "Не удалось обновить профиль." : error.getMessage());
        });

        Thread thread = new Thread(task, "profile-refresh");
        thread.setDaemon(true);
        thread.start();
    }

    private void renderAccount(AuthAccount account) {
        String roleLabel = resolveRoleLabel(account.getRole());
        String roleCode = resolveRoleCodeLabel(account.getRole());
        String accountStatus = resolveAccountStatusLabel(account.getStatus());

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
        sessionExpiresLabel.setText("Сессия активна до " + SESSION_TIME_FORMAT.format(state().getSession().getExpiresAt()));
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

    private static void setButtonGraphic(Button button, String iconName, double size, String color) {
        if (button != null) {
            button.setGraphic(LauncherIcons.icon(iconName, size, color));
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
}
