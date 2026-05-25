package ru.mcrpg.launcher;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import ru.mcrpg.launcher.ui.LauncherIcons;

public final class CleanGlassWindowControlsController {

    @FXML
    private Button minimizeWindowButton;

    @FXML
    private Button maximizeWindowButton;

    @FXML
    private Button closeWindowButton;

    private LauncherContext context;

    @FXML
    private void initialize() {
        configureButton(minimizeWindowButton, "window-minimize");
        configureButton(maximizeWindowButton, "window-maximize");
        configureButton(closeWindowButton, "window-close");
    }

    public void bindContext(LauncherContext context) {
        this.context = context;
    }

    @FXML
    private void minimizeWindow() {
        if (context != null) {
            context.getStage().setIconified(true);
        }
    }

    @FXML
    private void toggleMaximizeWindow() {
        if (context != null) {
            context.getStage().setMaximized(!context.getStage().isMaximized());
        }
    }

    @FXML
    private void closeWindow() {
        if (context != null) {
            context.getStage().close();
        }
    }

    private static void configureButton(Button button, String iconName) {
        if (button == null) {
            return;
        }
        button.setText("");
        button.setGraphic(LauncherIcons.icon(iconName, 16.0d, "#c9d1d9"));
    }
}
