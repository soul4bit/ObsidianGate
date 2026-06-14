package ru.mcrpg.launcher;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import ru.mcrpg.launcher.ui.AvatarImages;
import ru.mcrpg.launcher.ui.LauncherIcons;
import ru.mcrpg.launcher.ui.MicroInteractions;

public final class SettingsController extends AbstractScreenController {

    private static final int MIN_MEMORY_MB = 512;
    private static final int MAX_MEMORY_MB = 65536;
    private static final int DEFAULT_MEMORY_MIN_MB = 1024;
    private static final int MEMORY_PRESET_2_GB_MB = 2048;
    private static final int MEMORY_PRESET_4_GB_MB = 4096;
    private static final int MEMORY_PRESET_6_GB_MB = 6144;
    private static final int MEMORY_PRESET_8_GB_MB = 8192;
    private static final String SETTINGS_STATUS_IDLE = "settings-status-idle";
    private static final String SETTINGS_STATUS_DIRTY = "settings-status-dirty";
    private static final String SETTINGS_STATUS_SUCCESS = "settings-status-success";
    private static final String SETTINGS_STATUS_ERROR = "settings-status-error";

    private final ToggleGroup memoryToggleGroup = new ToggleGroup();
    private boolean applyingConfigToFields;
    private boolean settingsDirty;

    @FXML
    private Label versionLabel;

    @FXML
    private Button homeNavButton;

    @FXML
    private Button settingsNavButton;

    @FXML
    private Button profileNavButton;

    @FXML
    private Button saveButton;

    @FXML
    private Button resetButton;

    @FXML
    private Button openConfigButton;

    @FXML
    private Button browseGameDirectoryButton;

    @FXML
    private Button browseJavaButton;

    @FXML
    private Button browseWorkingDirectoryButton;

    @FXML
    private Button openGameDirectoryButton;

    @FXML
    private TextField gameDirectoryField;

    @FXML
    private TextField javaCommandField;

    @FXML
    private TextField workingDirectoryField;

    @FXML
    private TextField memoryMinField;

    @FXML
    private TextField memoryMaxField;

    @FXML
    private ToggleButton memory2Button;

    @FXML
    private ToggleButton memory4Button;

    @FXML
    private ToggleButton memory6Button;

    @FXML
    private ToggleButton memory8Button;

    @FXML
    private ToggleButton memoryManualButton;

    @FXML
    private HBox manualMemoryBox;

    @FXML
    private Label memorySummaryLabel;

    @FXML
    private TextField serverHostField;

    @FXML
    private TextField serverPortField;

    @FXML
    private TextField manifestUrlField;

    @FXML
    private TextField authBaseUrlField;

    @FXML
    private TextField serverIdField;

    @FXML
    private TextArea launchTemplateArea;

    @FXML
    private CheckBox launcherUpdatesEnabledCheck;

    @FXML
    private CheckBox updateFilesBeforeLaunchCheck;

    @FXML
    private CheckBox closeLauncherAfterGameStartCheck;

    @FXML
    private Label statusLabel;

    @FXML
    private Label sidebarProfileNameLabel;

    @FXML
    private ImageView sidebarProfileAvatarView;

    @FXML
    private Label sidebarProfileStatusLabel;

    @FXML
    private Label sidebarProfileRoleLabel;

    @FXML
    private Label clientIconLabel;

    @FXML
    private Label serverIconLabel;

    @FXML
    private Label launchIconLabel;

    @FXML
    private Node settingsHeroCard;

    @FXML
    private Node settingsPrimaryCard;

    @FXML
    private Node settingsConnectionCard;

    @FXML
    private Node settingsActionsCard;

    @FXML
    private Node settingsQuickActionsCard;

    @FXML
    private Node settingsAdvancedCard;

    @FXML
    private void initialize() {
        configureCleanGlassSidebar(ScreenRouter.Screen.SETTINGS);
        configureChrome();
        configureMemoryPresets();
        configureDirtyTracking();
        setSettingsDirty(false);
        setSettingsStatus("Готово", SETTINGS_STATUS_IDLE);
        configureMicroInteractions();
    }

    @Override
    protected void onContextBound(LauncherContext context) {
        configureCleanGlassSidebar(ScreenRouter.Screen.SETTINGS);
        applyProfileState();
        applyConfigToFields(LauncherDefaults.applyMissingValues(state().getConfig().copy()));
        setSettingsStatus("Готово", SETTINGS_STATUS_IDLE);
    }

    private void configureMicroInteractions() {
        MicroInteractions.installHoverLift(settingsPrimaryCard);
        MicroInteractions.installHoverLift(settingsConnectionCard);
        MicroInteractions.installHoverLift(settingsActionsCard);
        MicroInteractions.installHoverLift(settingsQuickActionsCard);
        MicroInteractions.installHoverLift(settingsAdvancedCard);
        MicroInteractions.installHoverLift(saveButton, -1.0d, 1.005d);
        MicroInteractions.installHoverLift(resetButton, -1.0d, 1.005d);
        MicroInteractions.installHoverLift(openConfigButton, -1.0d, 1.005d);
        MicroInteractions.installHoverLift(openGameDirectoryButton, -1.0d, 1.005d);
        MicroInteractions.playEntrance(
            settingsHeroCard,
            settingsPrimaryCard,
            settingsConnectionCard,
            settingsActionsCard,
            settingsQuickActionsCard
        );
    }

    @FXML
    private void onOpenHome() {
        router().open(ScreenRouter.Screen.HOME);
    }

    @FXML
    private void onOpenProfile() {
        router().open(state().isAuthenticated() ? ScreenRouter.Screen.PROFILE : ScreenRouter.Screen.AUTH);
    }

    @FXML
    private void onOpenProfileFromKeyboard(KeyEvent event) {
        if (event == null || (event.getCode() != KeyCode.ENTER && event.getCode() != KeyCode.SPACE)) {
            return;
        }
        event.consume();
        onOpenProfile();
    }

    @FXML
    private void onSaveSettings() {
        try {
            LauncherConfig config = readConfigFromFields();
            context().saveConfig(config);
            applyConfigToFields(state().getConfig());
            setSettingsStatus("Настройки сохранены.", SETTINGS_STATUS_SUCCESS);
        } catch (Exception exception) {
            setSettingsStatus(exception.getMessage(), SETTINGS_STATUS_ERROR);
            showError(exception.getMessage());
        }
    }

    @FXML
    private void onResetDefaults() {
        LauncherConfig defaults = LauncherConfig.defaults();
        if (state().isAuthenticated() && state().getSession().getAccount() != null) {
            defaults.setUsername(state().getSession().getAccount().getUsername());
        }
        applyConfigToFields(defaults);
        markSettingsDirty();
    }

    @FXML
    private void onOpenConfigFile() {
        openLauncherConfigLocation();
    }

    @FXML
    private void onBrowseGameDirectory() {
        chooseDirectory(gameDirectoryField, "Папка игры");
    }

    @FXML
    private void onBrowseWorkingDirectory() {
        chooseDirectory(workingDirectoryField, "Рабочая папка");
    }

    @FXML
    private void onBrowseJavaCommand() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Java executable");
        Path initialDirectory = existingParent(javaCommandField.getText());
        if (initialDirectory != null) {
            chooser.setInitialDirectory(initialDirectory.toFile());
        }
        File selected = chooser.showOpenDialog(stage());
        if (selected != null) {
            javaCommandField.setText(selected.toPath().toAbsolutePath().normalize().toString());
        }
    }

    @FXML
    private void onOpenGameDirectory() {
        try {
            Path gameDirectory = Paths.get(requireText(gameDirectoryField.getText(), "Укажите папку игры."))
                .toAbsolutePath()
                .normalize();
            Files.createDirectories(gameDirectory);
            openDesktopPath(gameDirectory);
            setSettingsStatus(
                settingsDirty ? "Есть несохранённые изменения" : "Открыта папка игры: " + gameDirectory,
                settingsDirty ? SETTINGS_STATUS_DIRTY : SETTINGS_STATUS_SUCCESS
            );
        } catch (Exception exception) {
            setSettingsStatus(exception.getMessage(), SETTINGS_STATUS_ERROR);
            showError("Не удалось открыть папку игры: " + exception.getMessage());
        }
    }

    private void configureChrome() {
        configureWindowButtons();
        versionLabel.setText("Лаунчер " + LauncherBrand.displayVersion());
        setButtonGraphic(homeNavButton, "home", 16.0d, "#f8fafc");
        setButtonGraphic(settingsNavButton, "settings", 16.0d, "#ffffff");
        setButtonGraphic(profileNavButton, "profile", 16.0d, "#f8fafc");
        setButtonGraphic(saveButton, "check-circle", 16.0d, "#ffffff");
        setButtonGraphic(resetButton, "refresh", 16.0d, "#d8b4fe");
        setButtonGraphic(openConfigButton, "external", 16.0d, "#d8b4fe");
        setButtonGraphic(browseGameDirectoryButton, "folder", 16.0d, "#f5f7fa");
        setButtonGraphic(browseJavaButton, "folder", 16.0d, "#f5f7fa");
        setButtonGraphic(browseWorkingDirectoryButton, "folder", 16.0d, "#f5f7fa");
        setButtonGraphic(openGameDirectoryButton, "external", 16.0d, "#f5f7fa");
        clientIconLabel.setGraphic(LauncherIcons.icon("download", 16.0d, "#c084fc"));
        serverIconLabel.setGraphic(LauncherIcons.icon("server", 16.0d, "#c084fc"));
        launchIconLabel.setGraphic(LauncherIcons.icon("play", 16.0d, "#c084fc"));
        setButtonTooltip(browseGameDirectoryButton, "Выбрать папку игры");
        setButtonTooltip(browseJavaButton, "Выбрать Java");
        setButtonTooltip(browseWorkingDirectoryButton, "Выбрать рабочую папку");
    }

    private LauncherConfig readConfigFromFields() {
        LauncherConfig config = state().getConfig().copy();
        config.setGameDirectory(requireText(gameDirectoryField.getText(), "Укажите папку игры."));
        config.setJavaCommand(requireText(javaCommandField.getText(), "Укажите команду Java."));
        config.setWorkingDirectory(optionalText(workingDirectoryField.getText()));
        Integer presetMemoryMaxMb = selectedMemoryMaxMb();
        if (presetMemoryMaxMb == null) {
            config.setMemoryMinMb(parseMemory(memoryMinField.getText(), "Минимальная RAM"));
            config.setMemoryMaxMb(parseMemory(memoryMaxField.getText(), "Максимальная RAM"));
        } else {
            config.setMemoryMinMb(DEFAULT_MEMORY_MIN_MB);
            config.setMemoryMaxMb(presetMemoryMaxMb);
        }
        if (config.getMemoryMaxMb() < config.getMemoryMinMb()) {
            throw new IllegalArgumentException("Максимальная RAM не может быть меньше минимальной.");
        }

        config.setServerHost(requireText(serverHostField.getText(), "Укажите адрес Minecraft-сервера."));
        config.setServerPort(parsePort(serverPortField.getText()));
        config.setManifestUrl(requireText(manifestUrlField.getText(), "Укажите URL manifest.json."));
        config.setAuthBaseUrl(requireText(authBaseUrlField.getText(), "Укажите URL Auth API."));
        config.setServerId(requireText(serverIdField.getText(), "Укажите server id."));
        config.setLaunchTemplate(requireText(launchTemplateArea.getText(), "Укажите шаблон запуска."));
        config.setUpdateFilesBeforeLaunch(updateFilesBeforeLaunchCheck.isSelected());
        config.setLauncherUpdatesEnabled(launcherUpdatesEnabledCheck.isSelected());
        config.setCloseLauncherAfterGameStart(closeLauncherAfterGameStartCheck.isSelected());

        if (state().isAuthenticated() && state().getSession().getAccount() != null) {
            config.setUsername(state().getSession().getAccount().getUsername());
        }
        return LauncherDefaults.applyMissingValues(config);
    }

    private void applyConfigToFields(LauncherConfig config) {
        applyingConfigToFields = true;
        try {
            LauncherConfig resolved = LauncherDefaults.applyMissingValues(config.copy());
            gameDirectoryField.setText(resolved.getGameDirectory());
            javaCommandField.setText(resolved.getJavaCommand());
            workingDirectoryField.setText(resolved.getWorkingDirectory());
            memoryMinField.setText(Integer.toString(resolved.getMemoryMinMb()));
            memoryMaxField.setText(Integer.toString(resolved.getMemoryMaxMb()));
            selectMemoryPreset(resolved.getMemoryMinMb(), resolved.getMemoryMaxMb());
            updateMemoryPresetState();
            serverHostField.setText(resolved.getServerHost());
            serverPortField.setText(Integer.toString(resolved.getServerPort()));
            manifestUrlField.setText(resolved.getManifestUrl());
            authBaseUrlField.setText(resolved.getAuthBaseUrl());
            serverIdField.setText(resolved.getServerId());
            launchTemplateArea.setText(resolved.getLaunchTemplate());
            launcherUpdatesEnabledCheck.setSelected(resolved.isLauncherUpdatesEnabled());
            updateFilesBeforeLaunchCheck.setSelected(resolved.isUpdateFilesBeforeLaunch());
            closeLauncherAfterGameStartCheck.setSelected(resolved.isCloseLauncherAfterGameStart());
        } finally {
            applyingConfigToFields = false;
        }
        setSettingsDirty(false);
    }

    private void configureMemoryPresets() {
        configureMemoryPresetButton(memory2Button, MEMORY_PRESET_2_GB_MB);
        configureMemoryPresetButton(memory4Button, MEMORY_PRESET_4_GB_MB);
        configureMemoryPresetButton(memory6Button, MEMORY_PRESET_6_GB_MB);
        configureMemoryPresetButton(memory8Button, MEMORY_PRESET_8_GB_MB);
        memoryManualButton.setToggleGroup(memoryToggleGroup);

        memoryToggleGroup.selectedToggleProperty().addListener((observable, oldToggle, selectedToggle) -> {
            if (selectedToggle == null && oldToggle != null) {
                memoryToggleGroup.selectToggle(oldToggle);
                return;
            }
            updateMemoryPresetState();
        });
        memoryMinField.textProperty().addListener((observable, oldValue, newValue) -> updateMemorySummary());
        memoryMaxField.textProperty().addListener((observable, oldValue, newValue) -> updateMemorySummary());
        memoryToggleGroup.selectToggle(memory4Button);
        updateMemoryPresetState();
    }

    private void configureDirtyTracking() {
        addDirtyListener(gameDirectoryField);
        addDirtyListener(javaCommandField);
        addDirtyListener(workingDirectoryField);
        addDirtyListener(memoryMinField);
        addDirtyListener(memoryMaxField);
        addDirtyListener(serverHostField);
        addDirtyListener(serverPortField);
        addDirtyListener(manifestUrlField);
        addDirtyListener(authBaseUrlField);
        addDirtyListener(serverIdField);
        addDirtyListener(launchTemplateArea);
        launcherUpdatesEnabledCheck.selectedProperty().addListener((observable, oldValue, newValue) -> markSettingsDirty());
        updateFilesBeforeLaunchCheck.selectedProperty().addListener((observable, oldValue, newValue) -> markSettingsDirty());
        closeLauncherAfterGameStartCheck.selectedProperty().addListener((observable, oldValue, newValue) -> markSettingsDirty());
        memoryToggleGroup.selectedToggleProperty().addListener((observable, oldToggle, selectedToggle) -> markSettingsDirty());
    }

    private void addDirtyListener(TextInputControl control) {
        control.textProperty().addListener((observable, oldValue, newValue) -> markSettingsDirty());
    }

    private void markSettingsDirty() {
        if (applyingConfigToFields) {
            return;
        }
        setSettingsDirty(true);
        setSettingsStatus("Есть несохранённые изменения", SETTINGS_STATUS_DIRTY);
    }

    private void setSettingsDirty(boolean dirty) {
        settingsDirty = dirty;
        if (saveButton != null) {
            saveButton.setDisable(!dirty);
        }
    }

    private void setSettingsStatus(String message, String statusStyleClass) {
        if (statusLabel == null) {
            return;
        }
        boolean stateChanged = !statusLabel.getStyleClass().contains(statusStyleClass);
        statusLabel.setText(message == null ? "" : message.trim());
        statusLabel.getStyleClass().removeAll(
            SETTINGS_STATUS_IDLE,
            SETTINGS_STATUS_DIRTY,
            SETTINGS_STATUS_SUCCESS,
            SETTINGS_STATUS_ERROR
        );
        statusLabel.getStyleClass().add(statusStyleClass);
        if (stateChanged) {
            MicroInteractions.playStatusSwap(statusLabel);
        }
    }

    private void configureMemoryPresetButton(ToggleButton button, int maxMemoryMb) {
        button.setToggleGroup(memoryToggleGroup);
        button.setUserData(maxMemoryMb);
    }

    private void selectMemoryPreset(int minMemoryMb, int maxMemoryMb) {
        ToggleButton presetButton = null;
        if (minMemoryMb == DEFAULT_MEMORY_MIN_MB) {
            presetButton = switch (maxMemoryMb) {
                case MEMORY_PRESET_2_GB_MB -> memory2Button;
                case MEMORY_PRESET_4_GB_MB -> memory4Button;
                case MEMORY_PRESET_6_GB_MB -> memory6Button;
                case MEMORY_PRESET_8_GB_MB -> memory8Button;
                default -> null;
            };
        }
        memoryToggleGroup.selectToggle(presetButton == null ? memoryManualButton : presetButton);
    }

    private void updateMemoryPresetState() {
        Integer presetMemoryMaxMb = selectedMemoryMaxMb();
        boolean manualMemory = presetMemoryMaxMb == null;
        manualMemoryBox.setManaged(manualMemory);
        manualMemoryBox.setVisible(manualMemory);
        if (!manualMemory) {
            memoryMinField.setText(Integer.toString(DEFAULT_MEMORY_MIN_MB));
            memoryMaxField.setText(Integer.toString(presetMemoryMaxMb));
        }
        updateMemorySummary();
    }

    private void updateMemorySummary() {
        Integer presetMemoryMaxMb = selectedMemoryMaxMb();
        if (presetMemoryMaxMb != null) {
            memorySummaryLabel.setText(formatMemory(presetMemoryMaxMb) + " RAM");
            return;
        }
        String minMemory = optionalText(memoryMinField.getText());
        String maxMemory = optionalText(memoryMaxField.getText());
        if (minMemory.isEmpty() || maxMemory.isEmpty()) {
            memorySummaryLabel.setText("Вручную");
            return;
        }
        memorySummaryLabel.setText(minMemory + "-" + maxMemory + " MB");
    }

    private Integer selectedMemoryMaxMb() {
        if (memoryToggleGroup.getSelectedToggle() == null || memoryToggleGroup.getSelectedToggle() == memoryManualButton) {
            return null;
        }
        Object userData = memoryToggleGroup.getSelectedToggle().getUserData();
        return userData instanceof Integer memoryMb ? memoryMb : null;
    }

    private void applyProfileState() {
        AuthAccount account = state().isAuthenticated() ? state().getSession().getAccount() : null;
        if (sidebarProfileAvatarView != null) {
            sidebarProfileAvatarView.setImage(AvatarImages.forAccount(account));
        }
        if (account == null) {
            setLabelText(sidebarProfileNameLabel, "Игрок");
            setLabelText(sidebarProfileStatusLabel, "Не в сети");
            setLabelText(sidebarProfileRoleLabel, "Player");
            refreshCleanGlassSidebar();
            return;
        }
        setLabelText(sidebarProfileNameLabel, account.getUsername());
        setLabelText(sidebarProfileStatusLabel, resolveAccountStatusLabel(account.getStatus()));
        setLabelText(sidebarProfileRoleLabel, resolveAccountRoleLabel(account.getRole()));
        refreshCleanGlassSidebar();
    }

    private void chooseDirectory(TextField field, String title) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title);
        Path initialDirectory = existingDirectory(field.getText());
        if (initialDirectory != null) {
            chooser.setInitialDirectory(initialDirectory.toFile());
        }
        File selected = chooser.showDialog(stage());
        if (selected != null) {
            field.setText(selected.toPath().toAbsolutePath().normalize().toString());
        }
    }

    private static int parsePort(String value) {
        String normalized = requireText(value, "Укажите порт Minecraft-сервера.");
        try {
            int port = Integer.parseInt(normalized);
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Порт должен быть от 1 до 65535.");
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Порт должен быть числом.", exception);
        }
    }

    private static int parseMemory(String value, String label) {
        String normalized = requireText(value, "Укажите значение: " + label + ".");
        try {
            int memory = Integer.parseInt(normalized);
            if (memory < MIN_MEMORY_MB || memory > MAX_MEMORY_MB) {
                throw new IllegalArgumentException(label + " должна быть от " + MIN_MEMORY_MB + " до " + MAX_MEMORY_MB + " MB.");
            }
            return memory;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " должна быть числом.", exception);
        }
    }

    private static String formatMemory(int memoryMb) {
        if (memoryMb % 1024 == 0) {
            return (memoryMb / 1024) + " GB";
        }
        return memoryMb + " MB";
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        return value == null ? "" : value.trim();
    }

    private static Path existingDirectory(String value) {
        if (value == null || value.trim().isEmpty()) {
            return userHome();
        }
        Path path = Paths.get(value.trim()).toAbsolutePath().normalize();
        if (Files.isDirectory(path)) {
            return path;
        }
        Path parent = path.getParent();
        return parent != null && Files.isDirectory(parent) ? parent : userHome();
    }

    private static Path existingParent(String value) {
        if (value == null || value.trim().isEmpty()) {
            return userHome();
        }
        try {
            Path path = Paths.get(value.trim()).toAbsolutePath().normalize();
            Path parent = Files.isDirectory(path) ? path : path.getParent();
            return parent != null && Files.isDirectory(parent) ? parent : userHome();
        } catch (RuntimeException exception) {
            return userHome();
        }
    }

    private static Path userHome() {
        return Paths.get(System.getProperty("user.home", ".")).toAbsolutePath().normalize();
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

    private static void setButtonGraphic(Button button, String iconName, double size, String color) {
        if (button != null) {
            button.setGraphic(LauncherIcons.icon(iconName, size, color));
            button.setGraphicTextGap(8.0d);
        }
    }

    private static void setButtonTooltip(Button button, String text) {
        if (button != null) {
            button.setTooltip(new Tooltip(text));
        }
    }

    private static void setLabelText(Label label, String value) {
        if (label != null) {
            label.setText(value);
        }
    }

    private static String resolveAccountStatusLabel(String status) {
        if (status == null || status.trim().isEmpty()) {
            return "в сети";
        }
        String normalized = status.trim().toLowerCase(java.util.Locale.ROOT);
        if ("active".equals(normalized)) {
            return "в сети";
        }
        if ("disabled".equals(normalized) || "blocked".equals(normalized) || "banned".equals(normalized)) {
            return "ограничен";
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
