package ru.mcrpg.launcher;

import java.io.IOException;
import javafx.concurrent.Task;
import javafx.scene.control.ContentDisplay;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import ru.mcrpg.launcher.ui.LauncherIcons;

public final class AuthController extends AbstractScreenController {

    @FXML
    private Label versionLabel;

    @FXML
    private TextField loginField;

    @FXML
    private Label loginErrorLabel;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label passwordErrorLabel;

    @FXML
    private CheckBox rememberCheck;

    @FXML
    private Hyperlink openRegisterLink;

    @FXML
    private Button loginButton;

    @FXML
    private Label statusLabel;

    @FXML
    private void initialize() {
        configureChrome();
        configureEnterSubmit();
        rememberCheck.setSelected(true);
        clearFieldErrors();
        setStatus("", false);
    }

    private void configureChrome() {
        configureWindowButtons();
        versionLabel.setText("Лаунчер " + LauncherBrand.displayVersion());
        loginButton.setGraphic(LauncherIcons.icon("arrow-right", 20.0d, "#ffffff"));
        loginButton.setContentDisplay(ContentDisplay.RIGHT);
    }

    private void configureEnterSubmit() {
        loginField.setOnAction(event -> passwordField.requestFocus());
        passwordField.setOnAction(event -> {
            event.consume();
            onLogin();
        });
    }

    @Override
    protected void onContextBound(LauncherContext context) {
        String username = context.getState().getConfig().getUsername();
        if (username != null && !username.trim().isEmpty() && !"Player".equalsIgnoreCase(username.trim())) {
            loginField.setText(username.trim());
        }
        setStatus(resolveStatusNotice(), false);
    }

    @FXML
    private void onLogin() {
        clearFieldErrors();
        String login = loginField.getText() == null ? "" : loginField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        boolean valid = true;
        if (login.isBlank()) {
            setFieldError(loginErrorLabel, "Укажите email или логин.");
            valid = false;
        }
        if (password.isBlank()) {
            setFieldError(passwordErrorLabel, "Укажите пароль.");
            valid = false;
        }
        if (!valid) {
            setStatus("Проверьте поля формы.", false);
            return;
        }

        setBusy(true);
        setStatus("Выполняется вход...", false);

        Task<AuthSession> task = new Task<AuthSession>() {
            @Override
            protected AuthSession call() throws Exception {
                return context().getAuthService().login(state().getConfig(), login, password, rememberCheck.isSelected());
            }
        };

        task.setOnSucceeded(event -> {
            setBusy(false);
            AuthSession session = task.getValue();
            state().setSession(session);
            LauncherConfig config = state().getConfig().copy();
            config.setUsername(session.getAccount().getUsername());
            try {
                context().saveConfig(config);
                setStatus("Вход выполнен.", true);
                router().open(ScreenRouter.Screen.HOME);
            } catch (IOException exception) {
                setStatus(exception.getMessage(), false);
            }
        });

        task.setOnFailed(event -> {
            setBusy(false);
            Throwable error = task.getException();
            setStatus(error == null ? "Не удалось выполнить вход." : error.getMessage(), false);
        });

        Thread thread = new Thread(task, "auth-login");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void openRegister() {
        router().open(ScreenRouter.Screen.REGISTER);
    }

    private void setBusy(boolean value) {
        loginButton.setDisable(value);
        loginButton.setText(value ? "Входим..." : "Войти");
        loginField.setDisable(value);
        passwordField.setDisable(value);
        rememberCheck.setDisable(value);
        openRegisterLink.setDisable(value);
    }

    private void setStatus(String message, boolean success) {
        statusLabel.setText(message == null ? "" : message.trim());
        statusLabel.getStyleClass().removeAll("status-error", "status-success");
        statusLabel.getStyleClass().add(success ? "status-success" : "status-error");
    }

    private String resolveStatusNotice() {
        String notice = state().consumeAuthNotice();
        return notice == null ? "" : notice;
    }

    private void clearFieldErrors() {
        setFieldError(loginErrorLabel, "");
        setFieldError(passwordErrorLabel, "");
    }

    private static void setFieldError(Label label, String message) {
        if (label == null) {
            return;
        }
        String text = message == null ? "" : message.trim();
        label.setText(text);
        label.setManaged(!text.isEmpty());
        label.setVisible(!text.isEmpty());
    }

}
