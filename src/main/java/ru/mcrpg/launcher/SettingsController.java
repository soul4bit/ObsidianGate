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
import javafx.scene.image.ImageView;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import ru.mcrpg.launcher.ui.AvatarImages;
import ru.mcrpg.launcher.ui.LauncherIcons;

public final class SettingsController extends AbstractScreenController {

    private static final int MIN_MEMORY_MB = 512;
    private static final int MAX_MEMORY_MB = 65536;

    @FXML
    private Label versionLabel;

    @FXML
    private Button homeNavButton;

    @FXML
    private Button settingsNavButton;

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
    private CheckBox updateFilesBeforeLaunchCheck;

    @FXML
    private CheckBox launcherUpdatesEnabledCheck;

    @FXML
    private Label statusLabel;

    @FXML
    private Label sidebarProfileNameLabel;

    @FXML
    private ImageView sidebarProfileAvatarView;

    @FXML
    private Label sidebarProfileStatusLabel;

    @FXML
    private Label clientIconLabel;

    @FXML
    private Label serverIconLabel;

    @FXML
    private Label launchIconLabel;

    @FXML
    private void initialize() {
        configureChrome();
    }

    @Override
    protected void onContextBound(LauncherContext context) {
        applyProfileState();
        applyConfigToFields(LauncherDefaults.applyMissingValues(state().getConfig().copy()));
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
    private void onSaveSettings() {
        try {
            LauncherConfig config = readConfigFromFields();
            context().saveConfig(config);
            applyConfigToFields(state().getConfig());
            statusLabel.setText("Настройки сохранены.");
        } catch (Exception exception) {
            statusLabel.setText(exception.getMessage());
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
        statusLabel.setText("Загружены значения по умолчанию. Нажмите «Сохранить», чтобы применить их.");
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
            statusLabel.setText("Открыта папка игры: " + gameDirectory);
        } catch (Exception exception) {
            statusLabel.setText(exception.getMessage());
            showError("Не удалось открыть папку игры: " + exception.getMessage());
        }
    }

    private void configureChrome() {
        configureWindowButtons();
        versionLabel.setText("Лаунчер " + LauncherBrand.displayVersion());
        homeNavButton.setGraphic(LauncherIcons.icon("home", 18.0d, "#c9d1d9"));
        settingsNavButton.setGraphic(LauncherIcons.icon("settings", 18.0d, "#c9d1d9"));
        saveButton.setGraphic(LauncherIcons.icon("check-circle", 17.0d, "#ffffff"));
        resetButton.setGraphic(LauncherIcons.icon("refresh", 16.0d, "#d8b4fe"));
        openConfigButton.setGraphic(LauncherIcons.icon("external", 16.0d, "#d8b4fe"));
        browseGameDirectoryButton.setGraphic(LauncherIcons.icon("folder", 16.0d, "#f5f7fa"));
        browseJavaButton.setGraphic(LauncherIcons.icon("folder", 16.0d, "#f5f7fa"));
        browseWorkingDirectoryButton.setGraphic(LauncherIcons.icon("folder", 16.0d, "#f5f7fa"));
        openGameDirectoryButton.setGraphic(LauncherIcons.icon("external", 16.0d, "#f5f7fa"));
        clientIconLabel.setGraphic(LauncherIcons.icon("download", 18.0d, "#c084fc"));
        serverIconLabel.setGraphic(LauncherIcons.icon("server", 18.0d, "#c084fc"));
        launchIconLabel.setGraphic(LauncherIcons.icon("play", 18.0d, "#c084fc"));
    }

    private LauncherConfig readConfigFromFields() {
        LauncherConfig config = state().getConfig().copy();
        config.setGameDirectory(requireText(gameDirectoryField.getText(), "Укажите папку игры."));
        config.setJavaCommand(requireText(javaCommandField.getText(), "Укажите команду Java."));
        config.setWorkingDirectory(optionalText(workingDirectoryField.getText()));
        config.setMemoryMinMb(parseMemory(memoryMinField.getText(), "Минимальная RAM"));
        config.setMemoryMaxMb(parseMemory(memoryMaxField.getText(), "Максимальная RAM"));
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

        if (state().isAuthenticated() && state().getSession().getAccount() != null) {
            config.setUsername(state().getSession().getAccount().getUsername());
        }
        return LauncherDefaults.applyMissingValues(config);
    }

    private void applyConfigToFields(LauncherConfig config) {
        LauncherConfig resolved = LauncherDefaults.applyMissingValues(config.copy());
        gameDirectoryField.setText(resolved.getGameDirectory());
        javaCommandField.setText(resolved.getJavaCommand());
        workingDirectoryField.setText(resolved.getWorkingDirectory());
        memoryMinField.setText(Integer.toString(resolved.getMemoryMinMb()));
        memoryMaxField.setText(Integer.toString(resolved.getMemoryMaxMb()));
        serverHostField.setText(resolved.getServerHost());
        serverPortField.setText(Integer.toString(resolved.getServerPort()));
        manifestUrlField.setText(resolved.getManifestUrl());
        authBaseUrlField.setText(resolved.getAuthBaseUrl());
        serverIdField.setText(resolved.getServerId());
        launchTemplateArea.setText(resolved.getLaunchTemplate());
        updateFilesBeforeLaunchCheck.setSelected(resolved.isUpdateFilesBeforeLaunch());
        launcherUpdatesEnabledCheck.setSelected(resolved.isLauncherUpdatesEnabled());
    }

    private void applyProfileState() {
        AuthAccount account = state().isAuthenticated() ? state().getSession().getAccount() : null;
        sidebarProfileAvatarView.setImage(AvatarImages.forAccount(account));
        if (account == null) {
            sidebarProfileNameLabel.setText("Не вошли");
            sidebarProfileStatusLabel.setText("вход нужен");
            return;
        }
        sidebarProfileNameLabel.setText(account.getUsername());
        sidebarProfileStatusLabel.setText(resolveAccountStatusLabel(account.getStatus()));
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
}
