package ru.mcrpg.launcher;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import ru.mcrpg.launcher.ui.LauncherIcons;

public abstract class AbstractScreenController implements LauncherContextAware {

    private LauncherContext context;
    private double dragOffsetX;
    private double dragOffsetY;

    @FXML
    private Button minimizeWindowButton;

    @FXML
    private Button maximizeWindowButton;

    @FXML
    private Button closeWindowButton;

    @FXML
    private StackPane brandLogoPane;

    @FXML
    private CleanGlassSidebarController sidebarController;

    @FXML
    private CleanGlassWindowControlsController windowControlsController;

    @Override
    public final void bindContext(LauncherContext context) {
        this.context = context;
        bindCleanGlassControllers();
        onContextBound(context);
    }

    protected void onContextBound(LauncherContext context) {
    }

    protected final LauncherContext context() {
        return context;
    }

    protected final LauncherState state() {
        return context.getState();
    }

    protected final ScreenRouter router() {
        return context.getScreenRouter();
    }

    protected final Stage stage() {
        return context.getStage();
    }

    protected final void configureWindowButtons() {
        configureWindowButton(minimizeWindowButton, "window-minimize");
        configureWindowButton(maximizeWindowButton, "window-maximize");
        configureWindowButton(closeWindowButton, "window-close");
        configureBrandLogo(brandLogoPane);
    }

    protected final void configureCleanGlassSidebar(ScreenRouter.Screen activeScreen) {
        if (sidebarController == null) {
            return;
        }
        sidebarController.setActiveScreen(activeScreen);
        if (context != null) {
            sidebarController.bindContext(context);
        }
    }

    protected final void refreshCleanGlassSidebar() {
        if (sidebarController != null) {
            sidebarController.refreshProfile();
        }
    }

    private void bindCleanGlassControllers() {
        if (windowControlsController != null) {
            windowControlsController.bindContext(context);
        }
        if (sidebarController != null) {
            sidebarController.bindContext(context);
        }
    }

    protected final void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, resolveMessage(message), ButtonType.OK);
        alert.initOwner(stage());
        alert.setTitle(LauncherBrand.APP_NAME);
        alert.setHeaderText("Ошибка");
        alert.showAndWait();
    }

    protected final void openLauncherConfigLocation() {
        try {
            Path configFile = context().getConfigStore().getConfigFile().toAbsolutePath().normalize();
            Path target = Files.exists(configFile) ? configFile : configFile.getParent();
            if (target == null) {
                throw new IOException("Путь к настройкам недоступен.");
            }
            openDesktopPath(target);
        } catch (Exception exception) {
            showError("Не удалось открыть настройки лаунчера: " + exception.getMessage());
        }
    }

    @FXML
    protected final void captureWindowDrag(MouseEvent event) {
        dragOffsetX = event.getScreenX() - stage().getX();
        dragOffsetY = event.getScreenY() - stage().getY();
    }

    @FXML
    protected final void dragWindow(MouseEvent event) {
        stage().setX(event.getScreenX() - dragOffsetX);
        stage().setY(event.getScreenY() - dragOffsetY);
    }

    @FXML
    protected final void minimizeWindow() {
        stage().setIconified(true);
    }

    @FXML
    protected final void toggleMaximizeWindow() {
        stage().setMaximized(!stage().isMaximized());
    }

    @FXML
    protected final void closeWindow() {
        stage().close();
    }

    private static String resolveMessage(String message) {
        return message == null || message.trim().isEmpty() ? "Неизвестное состояние лаунчера." : message.trim();
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

    private static void configureWindowButton(Button button, String iconName) {
        if (button == null) {
            return;
        }
        button.setText("");
        button.setGraphic(LauncherIcons.icon(iconName, 16.0d, "#c9d1d9"));
    }

    private static void configureBrandLogo(StackPane pane) {
        if (pane == null) {
            return;
        }
        pane.getChildren().setAll(LauncherIcons.logoCube(44.0d));
    }
}
