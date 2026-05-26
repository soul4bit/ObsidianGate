package ru.mcrpg.launcher;

import java.io.IOException;
import java.util.regex.Pattern;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import ru.mcrpg.launcher.ui.LauncherIcons;

public final class RegisterController extends AbstractScreenController {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    @FXML
    private Label versionLabel;

    @FXML
    private TextField usernameField;

    @FXML
    private Label usernameErrorLabel;

    @FXML
    private TextField emailField;

    @FXML
    private Label emailErrorLabel;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label passwordErrorLabel;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private Label confirmPasswordErrorLabel;

    @FXML
    private CheckBox rulesCheck;

    @FXML
    private Label rulesErrorLabel;

    @FXML
    private Button registerButton;

    @FXML
    private Hyperlink openLoginLink;

    @FXML
    private Label statusLabel;

    @FXML
    private void initialize() {
        configureChrome();
        configureEnterSubmit();
        clearFieldErrors();
        setStatus("", false);
    }

    private void configureChrome() {
        configureWindowButtons();
        versionLabel.setText("Лаунчер " + LauncherBrand.displayVersion());
        registerButton.setGraphic(LauncherIcons.icon("arrow-right", 16.0d, "#ffffff"));
        registerButton.setGraphicTextGap(8.0d);
        registerButton.setContentDisplay(ContentDisplay.RIGHT);
    }

    private void configureEnterSubmit() {
        usernameField.setOnAction(event -> emailField.requestFocus());
        emailField.setOnAction(event -> passwordField.requestFocus());
        passwordField.setOnAction(event -> {
            event.consume();
            if (confirmPasswordField.getText() == null || confirmPasswordField.getText().isBlank()) {
                confirmPasswordField.requestFocus();
                return;
            }
            onRegister();
        });
        confirmPasswordField.setOnAction(event -> {
            event.consume();
            onRegister();
        });
    }

    @FXML
    private void onRegister() {
        clearFieldErrors();
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();
        String confirmation = confirmPasswordField.getText() == null ? "" : confirmPasswordField.getText();

        boolean valid = true;
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            setFieldError(usernameErrorLabel, "Ник: A-Z, a-z, 0-9 или _, 3-16 символов.");
            valid = false;
        }
        if (email.isBlank() || !email.contains("@")) {
            setFieldError(emailErrorLabel, "Укажите корректный email.");
            valid = false;
        }
        if (password.length() < 8) {
            setFieldError(passwordErrorLabel, "Пароль должен быть не короче 8 символов.");
            valid = false;
        }
        if (!password.equals(confirmation)) {
            setFieldError(confirmPasswordErrorLabel, "Пароли не совпадают.");
            valid = false;
        }
        if (!rulesCheck.isSelected()) {
            setFieldError(rulesErrorLabel, "Нужно принять правила сервера.");
            valid = false;
        }
        if (!valid) {
            setStatus("Проверьте поля формы.", false);
            return;
        }

        setBusy(true);
        setStatus("Создаём аккаунт...", false);

        Task<AuthSession> task = new Task<AuthSession>() {
            @Override
            protected AuthSession call() throws Exception {
                return context().getAuthService().register(state().getConfig(), username, email, password, true);
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
                setStatus("Аккаунт создан.", true);
                router().open(ScreenRouter.Screen.HOME);
            } catch (IOException exception) {
                setStatus(exception.getMessage(), false);
            }
        });

        task.setOnFailed(event -> {
            setBusy(false);
            Throwable error = task.getException();
            setStatus(error == null ? "Не удалось создать аккаунт." : error.getMessage(), false);
        });

        Thread thread = new Thread(task, "auth-register");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void openLogin() {
        router().open(ScreenRouter.Screen.AUTH);
    }

    private void setBusy(boolean value) {
        usernameField.setDisable(value);
        emailField.setDisable(value);
        passwordField.setDisable(value);
        confirmPasswordField.setDisable(value);
        rulesCheck.setDisable(value);
        registerButton.setDisable(value);
        registerButton.setText(value ? "Создаём..." : "Зарегистрироваться");
        openLoginLink.setDisable(value);
    }

    private void setStatus(String message, boolean success) {
        statusLabel.setText(message == null ? "" : message.trim());
        statusLabel.getStyleClass().removeAll("status-error", "status-success");
        statusLabel.getStyleClass().add(success ? "status-success" : "status-error");
    }

    private void clearFieldErrors() {
        setFieldError(usernameErrorLabel, "");
        setFieldError(emailErrorLabel, "");
        setFieldError(passwordErrorLabel, "");
        setFieldError(confirmPasswordErrorLabel, "");
        setFieldError(rulesErrorLabel, "");
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
