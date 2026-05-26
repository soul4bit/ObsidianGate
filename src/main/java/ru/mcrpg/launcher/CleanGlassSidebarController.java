package ru.mcrpg.launcher;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import ru.mcrpg.launcher.ui.AvatarImages;
import ru.mcrpg.launcher.ui.LauncherIcons;
import ru.mcrpg.launcher.ui.MicroInteractions;

public final class CleanGlassSidebarController {

    @FXML
    private Button homeNavButton;

    @FXML
    private Button settingsNavButton;

    @FXML
    private Button profileNavButton;

    @FXML
    private ImageView sidebarProfileAvatarView;

    @FXML
    private Label sidebarProfileNameLabel;

    @FXML
    private Label sidebarProfileStatusLabel;

    @FXML
    private Region sidebarProfileStatusDot;

    @FXML
    private Label sidebarProfileRoleLabel;

    @FXML
    private HBox sidebarProfileCard;

    private LauncherContext context;
    private ScreenRouter.Screen activeScreen = ScreenRouter.Screen.HOME;

    @FXML
    private void initialize() {
        setGraphic(homeNavButton, "home", 16.0d, "#f8fafc");
        setGraphic(settingsNavButton, "settings", 16.0d, "#f8fafc");
        setGraphic(profileNavButton, "profile", 16.0d, "#f8fafc");
        applyActiveScreen();
        applyProfileState();
        configureMicroInteractions();
    }

    public void bindContext(LauncherContext context) {
        this.context = context;
        applyProfileState();
    }

    public void setActiveScreen(ScreenRouter.Screen activeScreen) {
        this.activeScreen = activeScreen == null ? ScreenRouter.Screen.HOME : activeScreen;
        applyActiveScreen();
    }

    public void refreshProfile() {
        applyProfileState();
    }

    @FXML
    private void openHome() {
        if (context != null) {
            context.getScreenRouter().open(ScreenRouter.Screen.HOME);
        }
    }

    @FXML
    private void openSettings() {
        if (context != null) {
            context.getScreenRouter().open(ScreenRouter.Screen.SETTINGS);
        }
    }

    @FXML
    private void openProfile() {
        if (context != null) {
            context.getScreenRouter().open(profileTargetScreen());
        }
    }

    @FXML
    private void openProfileFromKeyboard(KeyEvent event) {
        if (event == null || (event.getCode() != KeyCode.ENTER && event.getCode() != KeyCode.SPACE)) {
            return;
        }
        event.consume();
        openProfile();
    }

    private ScreenRouter.Screen profileTargetScreen() {
        return context.getState().isAuthenticated() ? ScreenRouter.Screen.PROFILE : ScreenRouter.Screen.AUTH;
    }

    private void applyActiveScreen() {
        setActive(homeNavButton, activeScreen == ScreenRouter.Screen.HOME);
        setActive(settingsNavButton, activeScreen == ScreenRouter.Screen.SETTINGS);
        setActive(profileNavButton, activeScreen == ScreenRouter.Screen.PROFILE);
    }

    private void configureMicroInteractions() {
        MicroInteractions.installHoverLift(homeNavButton, -1.0d, 1.01d);
        MicroInteractions.installHoverLift(settingsNavButton, -1.0d, 1.01d);
        MicroInteractions.installHoverLift(profileNavButton, -1.0d, 1.01d);
        MicroInteractions.installHoverLift(sidebarProfileCard, -2.0d, 1.01d);
    }

    private void applyProfileState() {
        AuthAccount account = context != null && context.getState().isAuthenticated()
            ? context.getState().getSession().getAccount()
            : null;
        LauncherConfig config = context == null ? LauncherConfig.defaults() : context.getState().getConfig();
        String username = account == null
            ? firstText(config.getUsername(), LauncherDefaults.defaultUsername())
            : firstText(account.getUsername(), config.getUsername(), LauncherDefaults.defaultUsername());

        if (sidebarProfileAvatarView != null) {
            sidebarProfileAvatarView.setImage(AvatarImages.forAccount(account));
        }
        setText(sidebarProfileNameLabel, username);
        String statusLabel = account == null ? "Не в сети" : resolveAccountStatusLabel(account.getStatus());
        setText(sidebarProfileStatusLabel, statusLabel);
        setText(sidebarProfileRoleLabel, account == null ? "Player" : resolveAccountRoleLabel(account.getRole()));
        applyUserStateStyle(resolveUserStateStyle(account));
    }

    private void applyUserStateStyle(String stateStyleClass) {
        setStateStyle(sidebarProfileCard, stateStyleClass);
        setStateStyle(sidebarProfileStatusDot, stateStyleClass);
        setStateStyle(sidebarProfileStatusLabel, stateStyleClass);
    }

    private static void setStateStyle(javafx.scene.Node node, String stateStyleClass) {
        if (node == null) {
            return;
        }
        node.getStyleClass().removeAll("user-state-online", "user-state-offline", "user-state-limited");
        node.getStyleClass().add(stateStyleClass);
    }

    private static String resolveUserStateStyle(AuthAccount account) {
        if (account == null) {
            return "user-state-offline";
        }
        String status = account.getStatus();
        if (status == null || status.trim().isEmpty() || "active".equalsIgnoreCase(status.trim())) {
            return "user-state-online";
        }
        if ("disabled".equalsIgnoreCase(status.trim()) || "blocked".equalsIgnoreCase(status.trim())
            || "banned".equalsIgnoreCase(status.trim())) {
            return "user-state-limited";
        }
        return "user-state-online";
    }

    private static void setActive(Button button, boolean active) {
        if (button == null) {
            return;
        }
        button.getStyleClass().remove("active");
        if (active) {
            button.getStyleClass().add("active");
        }
    }

    private static void setGraphic(Button button, String iconName, double size, String color) {
        if (button != null) {
            button.setGraphic(LauncherIcons.icon(iconName, size, color));
            button.setGraphicTextGap(8.0d);
        }
    }

    private static void setText(Label label, String value) {
        if (label != null) {
            label.setText(value);
        }
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String resolveAccountStatusLabel(String status) {
        if (status == null || status.trim().isEmpty() || "active".equalsIgnoreCase(status.trim())) {
            return "В сети";
        }
        if ("disabled".equalsIgnoreCase(status.trim()) || "blocked".equalsIgnoreCase(status.trim())
            || "banned".equalsIgnoreCase(status.trim())) {
            return "Ограничен";
        }
        return status.trim();
    }

    private static String resolveAccountRoleLabel(String role) {
        if (role == null || role.trim().isEmpty()) {
            return "Player";
        }
        String normalized = role.trim();
        String lower = normalized.toLowerCase(java.util.Locale.ROOT);
        if ("admin".equals(lower) || "administrator".equals(lower)) {
            return "Admin";
        }
        if ("moderator".equals(lower) || "mod".equals(lower)) {
            return "Moderator";
        }
        if ("player".equals(lower) || "user".equals(lower)) {
            return "Player";
        }
        return normalized;
    }
}
