package ru.mcrpg.launcher;

import java.io.IOException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class ScreenRouter {

    private static final double DEFAULT_SCENE_WIDTH = 1500;
    private static final double DEFAULT_SCENE_HEIGHT = 900;

    public enum Screen {
        AUTH("/ru/mcrpg/launcher/AuthView.fxml", "/ru/mcrpg/launcher/account.css"),
        REGISTER("/ru/mcrpg/launcher/RegisterView.fxml", "/ru/mcrpg/launcher/account.css"),
        HOME("/ru/mcrpg/launcher/launcher-shell.fxml", "/ru/mcrpg/launcher/launcher-shell.css"),
        PROFILE("/ru/mcrpg/launcher/ProfileView.fxml", "/ru/mcrpg/launcher/account.css"),
        SETTINGS("/ru/mcrpg/launcher/SettingsView.fxml", "/ru/mcrpg/launcher/account.css");

        private final String fxmlPath;
        private final String stylesheetPath;

        Screen(String fxmlPath, String stylesheetPath) {
            this.fxmlPath = fxmlPath;
            this.stylesheetPath = stylesheetPath;
        }
    }

    private final Stage stage;
    private final LauncherContext context;

    public ScreenRouter(Stage stage, LauncherContext context) {
        this.stage = stage;
        this.context = context;
    }

    public void open(Screen screen) {
        try {
            FXMLLoader loader = new FXMLLoader(ScreenRouter.class.getResource(screen.fxmlPath));
            Parent root = loader.load();
            Object controller = loader.getController();
            if (controller instanceof LauncherContextAware aware) {
                aware.bindContext(context);
            }

            double sceneWidth = stage.getWidth() > 0 ? stage.getWidth() : DEFAULT_SCENE_WIDTH;
            double sceneHeight = stage.getHeight() > 0 ? stage.getHeight() : DEFAULT_SCENE_HEIGHT;
            Scene scene = new Scene(root, sceneWidth, sceneHeight);
            scene.getStylesheets().add(ScreenRouter.class.getResource(screen.stylesheetPath).toExternalForm());
            stage.setScene(scene);
            if (!stage.isShowing()) {
                stage.show();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось открыть экран " + screen.name() + ".", exception);
        }
    }
}
