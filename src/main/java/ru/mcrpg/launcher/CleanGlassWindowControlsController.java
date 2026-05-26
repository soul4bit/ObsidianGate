package ru.mcrpg.launcher;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import ru.mcrpg.launcher.ui.LauncherIcons;

public final class CleanGlassWindowControlsController {

    @FXML
    private Button minimizeWindowButton;

    @FXML
    private Button closeWindowButton;

    private LauncherContext context;

    @FXML
    private void initialize() {
        configureButton(minimizeWindowButton, "window-minimize", "Свернуть");
        configureButton(closeWindowButton, "window-close", "Закрыть");
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
    private void closeWindow() {
        if (context != null) {
            context.getStage().close();
        }
    }

    private static void configureButton(Button button, String iconName, String tooltip) {
        if (button == null) {
            return;
        }
        button.setText("");
        button.setGraphic(LauncherIcons.icon(iconName, 16.0d, "#c9d1d9"));
        button.setTooltip(new Tooltip(tooltip));
    }
}
