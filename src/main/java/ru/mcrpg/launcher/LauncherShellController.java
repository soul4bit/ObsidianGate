package ru.mcrpg.launcher;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import ru.mcrpg.launcher.ui.AvatarImages;
import ru.mcrpg.launcher.ui.LauncherIcons;

public final class LauncherShellController extends AbstractScreenController {

    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter UPDATE_CHECK_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm");
    private static final int SERVER_STATUS_TIMEOUT_MS = 1500;
    private static final String DASHBOARD_UNKNOWN = "—";
    private static final int PREVIEW_ENTRY_LIMIT = 4;
    private static final int RECONNECT_TICKET_COUNT = 5;
    private static final String REQUIRED_LANGUAGE = "ru_ru";
    private static final String REQUIRED_RESOURCE_PACK = "ObsidianGate-Fixes-1.12.2";

    private final LaunchCommandBuilder commandBuilder = new LaunchCommandBuilder();
    private final MinecraftServerListWriter serverListWriter = new MinecraftServerListWriter();
    private final ModpackManifestClient manifestClient = new ModpackManifestClient();
    private final ModpackSyncService modpackSyncService = new ModpackSyncService(manifestClient);
    private final LauncherUpdateService launcherUpdateService = new LauncherUpdateService();
    private final AtomicLong endpointPreviewSequence = new AtomicLong();
    private final AtomicLong serverPresenceSequence = new AtomicLong();
    private final AtomicLong syncPreviewSequence = new AtomicLong();

    private LauncherConfig currentConfig = LauncherConfig.defaults();
    private LauncherUpdateCandidate availableLauncherUpdate;

    @FXML
    private Label brandLogoLabel;

    @FXML
    private Region serverPresenceIndicator;

    @FXML
    private Label serverPresenceLabel;

    @FXML
    private Label serverRouteValueLabel;

    @FXML
    private Label serverPingValueLabel;

    @FXML
    private Label serverVersionValueLabel;

    @FXML
    private StackPane serverLogoPane;

    @FXML
    private Label serverPlayersIconLabel;

    @FXML
    private Label serverPingIconLabel;

    @FXML
    private Label serverVersionIconLabel;

    @FXML
    private Label sidebarVersionLabel;

    @FXML
    private Label manifestUrlValueLabel;

    @FXML
    private Label downloadBaseValueLabel;

    @FXML
    private Label modpackSizeValueLabel;

    @FXML
    private Label onlinePlayersValueLabel;

    @FXML
    private Label minecraftVersionValueLabel;

    @FXML
    private Label manifestVersionValueLabel;

    @FXML
    private Label footerOnlinePlayersValueLabel;

    @FXML
    private Label footerMinecraftVersionValueLabel;

    @FXML
    private Label footerManifestVersionValueLabel;

    @FXML
    private Label configNameLabel;

    @FXML
    private Label homeWelcomeLabel;

    @FXML
    private Button homeNavButton;

    @FXML
    private Button settingsNavButton;

    @FXML
    private Button settingsButton;

    @FXML
    private Button openSiteButton;

    @FXML
    private Button profileManageButton;

    @FXML
    private Button syncButton;

    @FXML
    private Button previewButton;

    @FXML
    private Button launchButton;

    @FXML
    private Button launchArrowButton;

    @FXML
    private Button openLogButton;

    @FXML
    private Button clearLogButton;

    @FXML
    private Label syncFileLabel;

    @FXML
    private ProgressBar syncProgressBar;

    @FXML
    private Label syncPercentLabel;

    @FXML
    private Label syncStatusLabel;

    @FXML
    private Label syncBytesLabel;

    @FXML
    private Label previewSummaryLabel;

    @FXML
    private VBox previewChangesBox;

    @FXML
    private HBox launcherUpdateCard;

    @FXML
    private Label launcherUpdateTitleLabel;

    @FXML
    private Label launcherUpdateDescriptionLabel;

    @FXML
    private Button launcherUpdateButton;

    @FXML
    private Label launcherUpdateStatusIconLabel;

    @FXML
    private Label launcherUpdateStatusLabel;

    @FXML
    private Label launcherUpdateCheckedLabel;

    @FXML
    private Button launcherUpdateCheckButton;

    @FXML
    private TextArea logArea;

    @FXML
    private Label profileNameLabel;

    @FXML
    private ImageView profileAvatarView;

    @FXML
    private ImageView sidebarProfileAvatarView;

    @FXML
    private Label sidebarProfileNameLabel;

    @FXML
    private Label profileRankLabel;

    @FXML
    private Label sidebarProfileStatusLabel;

    @FXML
    private void initialize() {
        configureControls();
        updateProgressState(false, "Синхронизация модпака", "Готово", 0.0d);
        syncFileLabel.setText("Лаунчер готов к работе.");
        syncBytesLabel.setText("Файловая активность пока отсутствует.");
        resetPreviewState();
        updateOnlinePlayersValue(DASHBOARD_UNKNOWN);
        updateServerDetailValues(DASHBOARD_UNKNOWN, DASHBOARD_UNKNOWN);
        updateVersionValues(DASHBOARD_UNKNOWN, DASHBOARD_UNKNOWN);
        updateModpackSizeValue(DASHBOARD_UNKNOWN);
    }

    @Override
    protected void onContextBound(LauncherContext context) {
        if (!state().isAuthenticated()) {
            router().open(ScreenRouter.Screen.AUTH);
            return;
        }
        currentConfig = LauncherDefaults.applyMissingValues(state().getConfig().copy());
        applyConfigToView(currentConfig);
        applyProfileState();
        appendLog("Главный экран лаунчера загружен.");
    }

    @FXML
    private void openProfileScreen() {
        router().open(state().isAuthenticated() ? ScreenRouter.Screen.PROFILE : ScreenRouter.Screen.AUTH);
    }

    @FXML
    private void openSettings() {
        router().open(ScreenRouter.Screen.SETTINGS);
    }

    private void openConfigLocation() {
        try {
            Path configFile = context().getConfigStore().getConfigFile().toAbsolutePath().normalize();
            Path target = Files.exists(configFile) ? configFile : configFile.getParent();
            if (target == null) {
                throw new IOException("Путь к настройкам недоступен.");
            }
            openDesktopPath(target);
            appendLog("Открыта папка настроек лаунчера: " + target);
        } catch (Exception exception) {
            showLauncherError("Не удалось открыть настройки лаунчера: " + exception.getMessage());
        }
    }

    private void openDownloadBaseLocation() {
        try {
            String location = deriveManifestDirectory(buildCurrentConfig().getManifestUrl());
            openExternalLocation(location);
            appendLog("Открыт источник сборки: " + location);
        } catch (Exception exception) {
            showLauncherError("Не удалось открыть источник сборки: " + exception.getMessage());
        }
    }

    private void configureControls() {
        if (logArea != null) {
            logArea.setEditable(false);
            logArea.setWrapText(true);
        }

        configureWindowButtons();
        brandLogoLabel.setText(LauncherBrand.APP_TITLE);
        sidebarVersionLabel.setText("Лаунчер " + LauncherBrand.displayVersion());
        applyIcons();

        previewButton.setOnAction(event -> previewSyncChanges());
        launchButton.setOnAction(event -> launchClient());
        launchArrowButton.setOnAction(event -> launchClient());
        settingsButton.setOnAction(event -> router().open(ScreenRouter.Screen.SETTINGS));
        openSiteButton.setOnAction(event -> openDownloadBaseLocation());
        if (openLogButton != null) {
            openLogButton.setOnAction(event -> focusLaunchLog());
        }
        if (clearLogButton != null && logArea != null) {
            clearLogButton.setOnAction(event -> logArea.clear());
        }
        launcherUpdateButton.setOnAction(event -> updateLauncher());
        launcherUpdateCheckButton.setOnAction(event -> checkLauncherUpdates());
        hideLauncherUpdateCard();
    }

    private void applyIcons() {
        homeNavButton.setGraphic(LauncherIcons.icon("home", 18.0d, "#9b5cf6"));
        settingsNavButton.setGraphic(LauncherIcons.icon("settings", 18.0d, "#c9d1d9"));
        launchButton.setGraphic(LauncherIcons.icon("play", 28.0d, "#ffffff"));
        settingsButton.setGraphic(LauncherIcons.icon("settings", 22.0d, "#c9d1d9"));
        syncButton.setText("");
        syncButton.setGraphic(LauncherIcons.icon("check-circle", 36.0d, "#22c55e"));
        syncButton.setMouseTransparent(true);
        serverLogoPane.getChildren().setAll(LauncherIcons.logoCube(42.0d));
        serverPlayersIconLabel.setGraphic(LauncherIcons.icon("users", 22.0d, "#cfd3e6"));
        serverPingIconLabel.setGraphic(LauncherIcons.icon("signal", 22.0d, "#22c55e"));
        serverVersionIconLabel.setGraphic(LauncherIcons.icon("cube-small", 22.0d, "#cfd3e6"));
        previewButton.setGraphic(LauncherIcons.icon("refresh", 23.0d, "#c084fc"));
        launcherUpdateStatusIconLabel.setGraphic(LauncherIcons.icon("check-circle", 16.0d, "#22c55e"));
        launcherUpdateCheckButton.setGraphic(LauncherIcons.icon("refresh", 16.0d, "#aeb7c6"));
        openSiteButton.setGraphic(LauncherIcons.icon("external", 15.0d, "#f5f7fa"));
        profileManageButton.setGraphic(LauncherIcons.icon("profile", 21.0d, "#c084fc"));
        if (openLogButton != null) {
            openLogButton.setGraphic(LauncherIcons.icon("external", 15.0d, "#f5f7fa"));
        }
        if (clearLogButton != null) {
            clearLogButton.setGraphic(LauncherIcons.icon("trash", 15.0d, "#f5f7fa"));
        }
    }

    private void applyProfileState() {
        if (state().isAuthenticated()) {
            AuthAccount account = state().getSession().getAccount();
            profileNameLabel.setText(account.getUsername());
            sidebarProfileNameLabel.setText(account.getUsername());
            profileRankLabel.setText(resolveRoleLabel(account.getRole()));
            applyProfileAvatar(account);
            sidebarProfileStatusLabel.setText("в сети");
            if (homeWelcomeLabel != null) {
                homeWelcomeLabel.setText("Добро пожаловать, " + account.getUsername() + "!");
            }
            syncFileLabel.setText("Сессия активна. Перед запуском будет получен игровой ticket.");
            return;
        }

        profileNameLabel.setText("Не вошли");
        sidebarProfileNameLabel.setText("Не вошли");
        profileRankLabel.setText("Вход в аккаунт");
        applyProfileAvatar(null);
        sidebarProfileStatusLabel.setText("вход нужен");
        if (homeWelcomeLabel != null) {
            homeWelcomeLabel.setText("Войдите в аккаунт");
        }
        syncFileLabel.setText("Авторизуйтесь для запуска через серверный аккаунт.");
    }

    private void applyProfileAvatar(AuthAccount account) {
        if (profileAvatarView != null) {
            profileAvatarView.setImage(AvatarImages.forAccount(account));
        }
        if (sidebarProfileAvatarView != null) {
            sidebarProfileAvatarView.setImage(AvatarImages.forAccount(account));
        }
    }

    private void applyConfigToView(LauncherConfig config) {
        LauncherConfig resolvedConfig = LauncherDefaults.applyMissingValues(config.copy());
        String host = valueOrFallback(resolvedConfig.getServerHost(), LauncherConfig.DEFAULT_SERVER_HOST);
        String manifestUrl = valueOrFallback(
            resolvedConfig.getManifestUrl(),
            LauncherDefaults.defaultManifestUrl(host)
        );

        syncPreviewSequence.incrementAndGet();
        serverRouteValueLabel.setText(formatRoute(resolvedConfig));
        updateConfigurationLabel(DASHBOARD_UNKNOWN, DASHBOARD_UNKNOWN);
        manifestUrlValueLabel.setText(formatManifestLocation(manifestUrl));
        downloadBaseValueLabel.setText(deriveManifestDirectory(manifestUrl));
        updateModpackSizeValue(DASHBOARD_UNKNOWN);
        resetPreviewState();
        hideLauncherUpdateCard();
        applyDashboardLoadingState(hasText(manifestUrl));
        updateServerPresence("ПРОВЕРКА", "checking");
        applyLauncherUpdateFooterChecking();
        refreshEndpointPreviewAsync(resolvedConfig);
    }

    private LauncherConfig buildCurrentConfig() {
        LauncherConfig config = LauncherDefaults.applyMissingValues(currentConfig.copy());
        if (state().isAuthenticated()) {
            config.setUsername(state().getSession().getAccount().getUsername());
        }
        return config;
    }

    private void syncFiles() {
        LauncherConfig config;
        try {
            config = buildCurrentConfig();
            requireText(config.getManifestUrl(), "Укажи URL manifest.json в настройках лаунчера перед синхронизацией.");
            persistConfig(config, false);
        } catch (Exception exception) {
            showLauncherError(exception.getMessage());
            return;
        }

        appendLog("Запрошена синхронизация.");
        runTask(LauncherAction.SYNC_ONLY, config);
    }

    private void previewSyncChanges() {
        LauncherConfig config;
        try {
            config = buildCurrentConfig();
            requireText(config.getManifestUrl(), "Укажи URL manifest.json в настройках лаунчера перед предпросмотром.");
        } catch (Exception exception) {
            showLauncherError(exception.getMessage());
            return;
        }

        appendLog("Запрошен предпросмотр синхронизации.");
        long requestId = syncPreviewSequence.incrementAndGet();
        setBusy(true);
        applyPreviewLoadingState();
        updateProgressState(true, "Проверяем локальные файлы", "ПРОСМОТР", ProgressBar.INDETERMINATE_PROGRESS);

        Task<ModpackSyncPreviewResult> task = new Task<ModpackSyncPreviewResult>() {
            @Override
            protected ModpackSyncPreviewResult call() throws Exception {
                return modpackSyncService.preview(
                    config,
                    LauncherShellController.this::appendLogAsync,
                    LauncherShellController.this::applySyncProgressAsync
                );
            }
        };

        task.setOnSucceeded(event -> {
            if (requestId != syncPreviewSequence.get()) {
                return;
            }
            setBusy(false);
            applyPreviewResult(task.getValue());
        });

        task.setOnFailed(event -> {
            if (requestId != syncPreviewSequence.get()) {
                return;
            }
            setBusy(false);
            Throwable exception = task.getException();
            updateProgressState(false, "Предпросмотр завершился ошибкой", "ОШИБКА", 0.0d);
            syncBytesLabel.setText("Предпросмотр не построен.");
            setPreviewSummary("Не удалось сравнить локальные файлы с manifest.files[].");
            setPreviewChanges(
                createPreviewEmptyState(exception == null ? "Предпросмотр не удался." : exception.getMessage())
            );
            showLauncherError(exception == null ? "Предпросмотр не удался." : exception.getMessage());
        });

        Thread thread = new Thread(task, "launcher-shell-preview");
        thread.setDaemon(true);
        thread.start();
    }

    private void launchClient() {
        if (!state().isAuthenticated()) {
            state().setAuthNotice("Авторизуйтесь перед запуском клиента.");
            router().open(ScreenRouter.Screen.AUTH);
            return;
        }

        LauncherConfig config;
        try {
            config = buildCurrentConfig();
            persistConfig(config, false);
        } catch (Exception exception) {
            showLauncherError(exception.getMessage());
            return;
        }

        if (shouldSyncBeforeLaunch(config)) {
            appendLog("Автообновление включено: файлы будут синхронизированы перед запуском.");
        }

        runTask(LauncherAction.SYNC_AND_LAUNCH, config);
    }

    private void focusLaunchLog() {
        if (logArea == null) {
            return;
        }
        logArea.requestFocus();
        logArea.positionCaret(logArea.getLength());
    }

    private void checkLauncherUpdates() {
        LauncherConfig config;
        try {
            config = buildCurrentConfig();
            requireText(config.getManifestUrl(), "Укажи URL manifest.json в настройках лаунчера перед проверкой обновлений.");
        } catch (Exception exception) {
            showLauncherError(exception.getMessage());
            return;
        }

        appendLog("Запрошена проверка обновления лаунчера.");
        applyLauncherUpdateFooterChecking();
        refreshEndpointPreviewAsync(config);
    }

    private void updateLauncher() {
        LauncherUpdateCandidate update = availableLauncherUpdate;
        if (update == null) {
            showLauncherError("Обновление лаунчера недоступно.");
            return;
        }
        if (!update.isInstallSupported()) {
            showLauncherError("Автообновление работает только при запуске лаунчера из jar-файла.");
            return;
        }

        setBusy(true);
        launcherUpdateButton.setDisable(true);
        updateProgressState(true, "Скачиваем обновление лаунчера", "ОБНОВЛЕНИЕ", ProgressBar.INDETERMINATE_PROGRESS);
        syncFileLabel.setText("После установки лаунчер перезапустится автоматически.");
        appendLog("Запрошено обновление лаунчера: " + update.getVersion() + ".");

        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                launcherUpdateService.installAndRestart(update, LauncherShellController.this::appendLogAsync);
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            appendLog("Обновление лаунчера скачано. Перезапускаемся.");
            Platform.exit();
            System.exit(0);
        });

        task.setOnFailed(event -> {
            setBusy(false);
            Throwable exception = task.getException();
            updateProgressState(false, "Обновление лаунчера не удалось", "ОШИБКА", 0.0d);
            syncFileLabel.setText(resolveLauncherUpdateFailureSummary(exception));
            launcherUpdateButton.setDisable(false);
            showLauncherError(resolveLauncherUpdateFailureMessage(exception, update));
        });

        Thread thread = new Thread(task, "launcher-self-update");
        thread.setDaemon(true);
        thread.start();
    }

    private void runTask(LauncherAction action, LauncherConfig requestedConfig) {
        setBusy(true);
        updateProgressState(
            true,
            action == LauncherAction.SYNC_ONLY ? "Проверка манифеста и файлов" : "Подготовка клиента",
            "...",
            ProgressBar.INDETERMINATE_PROGRESS
        );

        Task<LauncherTaskResult> task = new Task<LauncherTaskResult>() {
            @Override
            protected LauncherTaskResult call() throws Exception {
                LauncherConfig effectiveConfig = requestedConfig.copy();
                ModpackSyncResult syncResult = null;
                AuthSession effectiveSession = state().getSession();

                if (action == LauncherAction.SYNC_ONLY || shouldSyncBeforeLaunch(effectiveConfig)) {
                    syncResult = modpackSyncService.sync(
                        effectiveConfig,
                        LauncherShellController.this::appendLogAsync,
                        LauncherShellController.this::applySyncProgressAsync
                    );
                    effectiveConfig = syncResult.getResolvedConfig();
                }

                Integer exitCode = null;
                if (action == LauncherAction.SYNC_AND_LAUNCH) {
                    List<String> command;
                    if (effectiveSession == null || effectiveSession.getAccount() == null) {
                        throw new AuthSessionExpiredException("Авторизуйтесь перед запуском клиента.", null);
                    }
                    effectiveSession = context().getAuthService().refreshIfNeeded(effectiveConfig, effectiveSession);
                    List<GameTicket> reconnectTickets = createReconnectTickets(effectiveConfig, effectiveSession);
                    GameTicket gameTicket = reconnectTickets.get(0);
                    Path sessionFile = context().getSessionFileWriter().write(effectiveConfig, reconnectTickets);
                    effectiveConfig.setUsername(effectiveSession.getAccount().getUsername());
                    appendLogAsync(
                        "Подготовлено билетов переподключения: " + reconnectTickets.size()
                            + " для " + gameTicket.getUsername() + "."
                    );
                    command = commandBuilder.build(
                        effectiveConfig,
                        LaunchIdentity.authenticated(
                            effectiveSession.getAccount().getUsername(),
                            gameTicket.getUuid(),
                            effectiveSession.getAccessToken(),
                            sessionFile
                        )
                    );

                    try {
                        serverListWriter.upsert(effectiveConfig);
                        appendLogAsync("Сохранена запись сервера Minecraft: " + effectiveConfig.getServerHost() + ":" + effectiveConfig.getServerPort());
                    } catch (IOException | IllegalArgumentException exception) {
                        appendLogAsync("Список серверов Minecraft не обновлен: " + exception.getMessage());
                    }

                    ensureRequiredResourcePack(effectiveConfig);
                    Path workingDirectory = resolveWorkingDirectory(effectiveConfig);
                    appendLogAsync("Команда запуска: " + commandBuilder.preview(command));
                    if (workingDirectory != null) {
                        appendLogAsync("Рабочая папка: " + workingDirectory.toAbsolutePath());
                    }

                    exitCode = Integer.valueOf(runProcess(command, workingDirectory));
                }

                return new LauncherTaskResult(effectiveConfig, syncResult, exitCode, effectiveSession);
            }
        };

        task.setOnSucceeded(event -> {
            setBusy(false);
            LauncherTaskResult result = task.getValue();
            try {
                if (result.session != null) {
                    state().setSession(result.session);
                    context().getAuthService().persist(result.session);
                }
                persistConfig(result.resolvedConfig, false);
                applyProfileState();

                if (result.exitCode != null) {
                    applyLastLaunchResult(result.exitCode.intValue());
                } else if (result.syncResult != null) {
                    applySyncResult(result.syncResult, result.resolvedConfig);
                } else {
                    updateProgressState(false, "Готово к запуску", "ГОТОВО", 0.0d);
                }

                if (result.exitCode != null) {
                    appendLog("Клиент завершился с кодом " + result.exitCode + ".");
                }
            } catch (IOException exception) {
                showLauncherError("Не удалось сохранить состояние лаунчера: " + exception.getMessage());
            }
        });

        task.setOnFailed(event -> {
            setBusy(false);
            Throwable exception = task.getException();
            if (handleExpiredSession(exception)) {
                return;
            }
            updateProgressState(false, "Операция завершилась ошибкой", "ОШИБКА", 0.0d);
            syncBytesLabel.setText("Подробности смотрите в журнале лаунчера.");
            showLauncherError(exception == null ? "Неизвестная ошибка лаунчера." : exception.getMessage());
        });

        task.setOnCancelled(event -> {
            setBusy(false);
            updateProgressState(false, "Операция отменена", "СТОП", 0.0d);
            syncBytesLabel.setText("Файлы не изменялись.");
        });

        Thread thread = new Thread(task, "launcher-shell-" + action.name().toLowerCase(Locale.ROOT));
        thread.setDaemon(true);
        thread.start();
    }

    private void applySyncResult(ModpackSyncResult syncResult, LauncherConfig resolvedConfig) {
        updateProgressState(false, "Синхронизация завершена", "100%", 1.0d);
        syncBytesLabel.setText(formatSyncSummary(syncResult));
        syncFileLabel.setText(
            "Загружено " + syncResult.getDownloadedFiles() + " файлов, переиспользовано "
                + syncResult.getReusedFiles() + "."
        );
        applyPreviewFromSyncResult(syncResult);
        manifestUrlValueLabel.setText(formatManifestLocation(resolvedConfig.getManifestUrl()));
        serverRouteValueLabel.setText(formatRoute(resolvedConfig));
        downloadBaseValueLabel.setText(resolveDisplayDownloadBase(resolvedConfig.getManifestUrl(), syncResult.getManifest()));
        updateModpackSizeValue(formatManifestSize(syncResult.getManifest()));
        updateVersionValues(
            resolveMinecraftVersion(syncResult.getManifest()),
            resolveManifestVersion(syncResult.getManifest())
        );
        refreshServerPresenceAsync(
            valueOrFallback(resolvedConfig.getServerHost(), LauncherConfig.DEFAULT_SERVER_HOST),
            resolvedConfig.getServerPort(),
            formatRoute(resolvedConfig)
        );
    }

    private void applyPreviewResult(ModpackSyncPreviewResult previewResult) {
        int downloadFiles = previewResult.getDownloadFiles();
        int reusedFiles = previewResult.getReusedFiles();
        int totalFiles = previewResult.getEntries().size();
        String manifestVersion = resolveManifestVersion(previewResult.getManifest());
        updateModpackSizeValue(formatManifestSize(previewResult.getManifest()));

        if (downloadFiles <= 0) {
            updateProgressState(false, "Локальные файлы актуальны", "ПРОСМОТР", 0.0d);
            syncFileLabel.setText("Все manifest.files[] уже совпадают с локальной сборкой.");
            syncBytesLabel.setText("К скачиванию 0 B");
            setPreviewSummary(
                "Manifest " + manifestVersion + ": все " + totalFiles
                    + " файлов актуальны. Предпросмотр сравнивает только manifest.files[]."
            );
            setPreviewChanges(
                createPreviewEmptyState("Изменений не найдено. Синхронизация скачает 0 файлов.")
            );
            return;
        }

        updateProgressState(false, "Нужна синхронизация", "ПРОСМОТР", 0.0d);
        syncFileLabel.setText("Найдено " + downloadFiles + " файлов для обновления до запуска.");
        syncBytesLabel.setText("К скачиванию " + formatBytes(previewResult.getDownloadBytes()));
        setPreviewSummary(
            "Manifest " + manifestVersion + ": " + downloadFiles + " из " + totalFiles
                + " файлов требуют синхронизации, актуальны " + reusedFiles + "."
        );
        renderPreviewChanges(previewResult.getEntries(), downloadFiles);
    }

    private void applyPreviewFromSyncResult(ModpackSyncResult syncResult) {
        setPreviewSummary(
            "Manifest " + resolveManifestVersion(syncResult.getManifest())
                + ": синхронизация завершена, локальная копия должна совпадать с manifest.files[]."
        );
        setPreviewChanges(
            createPreviewEmptyState("Последняя синхронизация завершена. Для перепроверки запустите предпросмотр снова.")
        );
    }

    private void applyPreviewLoadingState() {
        setPreviewSummary("Проверяем sha256 локальных файлов и сравниваем их с manifest.files[].");
        setPreviewChanges(
            createPreviewEmptyState("Сканируем папку игры и строим список изменений...")
        );
    }

    private void resetPreviewState() {
        setPreviewSummary("Предпросмотр ещё не запускался.");
        setPreviewChanges(
            createPreviewEmptyState("Нажмите «Предпросмотр», чтобы заранее увидеть изменения перед синхронизацией.")
        );
    }

    private void renderPreviewChanges(List<ModpackSyncPreviewEntry> entries, int totalChanged) {
        List<Node> nodes = new ArrayList<Node>();
        int shown = 0;
        for (ModpackSyncPreviewEntry entry : entries) {
            if (entry.getState() != ModpackSyncPreviewEntry.State.DOWNLOAD) {
                continue;
            }
            if (shown >= PREVIEW_ENTRY_LIMIT) {
                break;
            }
            nodes.add(createPreviewEntryCard(entry));
            shown++;
        }

        if (nodes.isEmpty()) {
            nodes.add(createPreviewEmptyState("Изменений не найдено."));
        } else if (totalChanged > shown) {
            Label moreLabel = new Label("Еще " + (totalChanged - shown) + " файлов ждут синхронизации.");
            moreLabel.getStyleClass().add("sync-preview-note");
            nodes.add(moreLabel);
        }

        setPreviewChanges(nodes);
    }

    private void setPreviewSummary(String message) {
        if (previewSummaryLabel != null) {
            previewSummaryLabel.setText(message);
        }
    }

    private void setPreviewChanges(Node... nodes) {
        if (previewChangesBox != null) {
            previewChangesBox.getChildren().setAll(nodes);
        }
    }

    private void setPreviewChanges(List<Node> nodes) {
        if (previewChangesBox != null) {
            previewChangesBox.getChildren().setAll(nodes);
        }
    }

    private Node createPreviewEntryCard(ModpackSyncPreviewEntry entry) {
        VBox card = new VBox(8.0);
        card.getStyleClass().add("sync-preview-card");
        card.setPadding(new Insets(12.0, 14.0, 12.0, 14.0));

        HBox titleRow = new HBox(10.0);
        Label pathLabel = new Label(normalizeText(entry.getPath()));
        pathLabel.getStyleClass().add("sync-preview-title");
        pathLabel.setWrapText(true);
        HBox.setHgrow(pathLabel, Priority.ALWAYS);
        titleRow.getChildren().addAll(
            pathLabel,
            createPreviewBadge(resolvePreviewCategory(entry.getPath())),
            createPreviewBadge(resolvePreviewReasonLabel(entry.getReason()))
        );

        Label reasonLabel = new Label(resolvePreviewReasonText(entry.getReason()));
        reasonLabel.getStyleClass().add("sync-preview-detail");
        reasonLabel.setWrapText(true);

        Label targetLabel = new Label("Куда: " + normalizeText(entry.getTargetPath()));
        targetLabel.getStyleClass().add("sync-preview-detail");
        targetLabel.setWrapText(true);

        HBox metaRow = new HBox(14.0);
        metaRow.getChildren().add(createPreviewMeta("РАЗМЕР", formatBytes(entry.getSize() == null ? 0L : entry.getSize().longValue())));
        metaRow.getChildren().add(createPreviewMeta("SHA", shortenHash(entry.getSha256())));

        card.getChildren().addAll(titleRow, reasonLabel, targetLabel, metaRow);
        return card;
    }

    private Node createPreviewMeta(String labelText, String valueText) {
        VBox meta = new VBox(2.0);
        Label label = new Label(labelText);
        label.getStyleClass().add("sync-preview-meta-label");
        Label value = new Label(valueText);
        value.getStyleClass().add("sync-preview-meta-value");
        meta.getChildren().addAll(label, value);
        return meta;
    }

    private Label createPreviewBadge(String text) {
        Label badge = new Label(text.toUpperCase(Locale.ROOT));
        badge.getStyleClass().add("sync-preview-badge");
        return badge;
    }

    private Node createPreviewEmptyState(String text) {
        VBox box = new VBox();
        box.getStyleClass().add("sync-preview-empty");
        box.setPadding(new Insets(12.0, 14.0, 12.0, 14.0));

        Label label = new Label(text);
        label.getStyleClass().add("sync-preview-detail");
        label.setWrapText(true);
        box.getChildren().add(label);
        return box;
    }

    private void refreshEndpointPreviewAsync(LauncherConfig config) {
        long requestId = endpointPreviewSequence.incrementAndGet();

        Thread thread = new Thread(() -> {
            LauncherConfig previewConfig = LauncherDefaults.applyMissingValues(config.copy());
            String manifestUrl = previewConfig.getManifestUrl();
            LauncherUpdateCandidate launcherUpdate = null;
            boolean launcherUpdateCheckSucceeded = true;
            String downloadBase = deriveManifestDirectory(manifestUrl);
            String manifestVersion = hasText(manifestUrl) ? "Нет данных" : "Не указан";
            String minecraftVersion = hasText(manifestUrl) ? DASHBOARD_UNKNOWN : "Не указан";
            String modpackSize = hasText(manifestUrl) ? DASHBOARD_UNKNOWN : "Не указан";

            try {
                LoadedManifest loadedManifest = manifestClient.load(manifestUrl);
                ModpackManifest manifest = loadedManifest.getManifest();
                applyManifestSettings(previewConfig, manifest);
                downloadBase = resolveDisplayDownloadBase(loadedManifest);
                manifestVersion = resolveManifestVersion(manifest);
                minecraftVersion = resolveMinecraftVersion(manifest);
                modpackSize = formatManifestSize(manifest);
                if (previewConfig.isLauncherUpdatesEnabled()) {
                    launcherUpdate = launcherUpdateService.findUpdate(loadedManifest, LauncherBrand.displayVersion());
                }
            } catch (Exception ignored) {
                launcherUpdateCheckSucceeded = false;
            }

            String resolvedRoute = formatRoute(previewConfig);
            String resolvedHost = valueOrFallback(previewConfig.getServerHost(), LauncherConfig.DEFAULT_SERVER_HOST);
            int resolvedPort = previewConfig.getServerPort();
            String resolvedDownloadBase = downloadBase;
            String resolvedManifestVersion = manifestVersion;
            String resolvedMinecraftVersion = minecraftVersion;
            String resolvedModpackSize = modpackSize;
            LauncherUpdateCandidate resolvedLauncherUpdate = launcherUpdate;
            boolean resolvedLauncherUpdateCheckSucceeded = launcherUpdateCheckSucceeded;

            Platform.runLater(() -> {
                if (requestId != endpointPreviewSequence.get()) {
                    return;
                }
                serverRouteValueLabel.setText(resolvedRoute);
                downloadBaseValueLabel.setText(resolvedDownloadBase);
                updateVersionValues(resolvedMinecraftVersion, resolvedManifestVersion);
                updateModpackSizeValue(resolvedModpackSize);
                applyLauncherUpdateState(resolvedLauncherUpdate, resolvedLauncherUpdateCheckSucceeded);
                refreshServerPresenceAsync(resolvedHost, resolvedPort, resolvedRoute);
            });
        }, "launcher-shell-endpoint-preview");

        thread.setDaemon(true);
        thread.start();
    }

    private void applyLauncherUpdateState(LauncherUpdateCandidate update, boolean checkSucceeded) {
        availableLauncherUpdate = update;
        if (!checkSucceeded) {
            hideLauncherUpdateCard();
            applyLauncherUpdateFooterFailed();
            return;
        }
        if (update == null) {
            hideLauncherUpdateCard();
            applyLauncherUpdateFooterUpToDate();
            return;
        }

        launcherUpdateCard.setManaged(true);
        launcherUpdateCard.setVisible(true);
        launcherUpdateTitleLabel.setText(
            update.isRequired()
                ? "Требуется обновление лаунчера"
                : "Доступно обновление лаунчера"
        );
        launcherUpdateDescriptionLabel.setText(
            "Текущая версия: " + valueOrFallback(update.getCurrentVersion(), "неизвестна")
                + ". Новая версия: " + update.getVersion() + "."
        );
        launcherUpdateButton.setDisable(!update.isInstallSupported());
        launcherUpdateButton.setText(update.isInstallSupported() ? "Обновить" : "Скачать вручную");
        applyLauncherUpdateFooterAvailable(update);
    }

    private void hideLauncherUpdateCard() {
        availableLauncherUpdate = null;
        if (launcherUpdateCard == null) {
            return;
        }
        launcherUpdateCard.setManaged(false);
        launcherUpdateCard.setVisible(false);
    }

    private void applyLauncherUpdateFooterChecking() {
        if (launcherUpdateStatusLabel == null) {
            return;
        }
        launcherUpdateStatusIconLabel.setGraphic(LauncherIcons.icon("refresh", 16.0d, "#aeb7c6"));
        launcherUpdateStatusLabel.setText("Проверяем обновления");
        if (launcherUpdateCheckButton != null) {
            launcherUpdateCheckButton.setDisable(true);
        }
    }

    private void applyLauncherUpdateFooterUpToDate() {
        applyLauncherUpdateFooter("Лаунчер актуален", "check-circle", "#22c55e");
    }

    private void applyLauncherUpdateFooterAvailable(LauncherUpdateCandidate update) {
        String version = update == null ? "" : update.getVersion();
        String text = hasText(version) ? "Доступно обновление " + version : "Доступно обновление";
        applyLauncherUpdateFooter(text, "download", "#c084fc");
    }

    private void applyLauncherUpdateFooterFailed() {
        applyLauncherUpdateFooter("Проверка не удалась", "refresh", "#ef4444");
        if (launcherUpdateCheckedLabel != null) {
            launcherUpdateCheckedLabel.setText("Обновлено: ошибка");
        }
    }

    private void applyLauncherUpdateFooter(String status, String icon, String color) {
        if (launcherUpdateStatusLabel == null) {
            return;
        }
        launcherUpdateStatusIconLabel.setGraphic(LauncherIcons.icon(icon, 16.0d, color));
        launcherUpdateStatusLabel.setText(status);
        if (launcherUpdateCheckedLabel != null) {
            launcherUpdateCheckedLabel.setText("Обновлено: " + LocalDateTime.now().format(UPDATE_CHECK_TIME_FORMAT));
        }
        if (launcherUpdateCheckButton != null) {
            launcherUpdateCheckButton.setDisable(false);
        }
    }

    private void applyManifestSettings(LauncherConfig config, ModpackManifest manifest) {
        if (config == null || manifest == null || manifest.getLauncher() == null) {
            return;
        }
        LauncherManifestSettings settings = manifest.getLauncher();
        if (hasText(settings.getServerHost())) {
            config.setServerHost(settings.getServerHost().trim());
        }
        if (settings.getServerPort() != null) {
            config.setServerPort(settings.getServerPort().intValue());
        }
        if (hasText(settings.getLaunchTemplate())) {
            config.setLaunchTemplate(settings.getLaunchTemplate().trim());
        }
        if (hasText(settings.getWorkingDirectory())) {
            config.setWorkingDirectory(settings.getWorkingDirectory().trim());
        }
        if (hasText(settings.getAuthBaseUrl())) {
            config.setAuthBaseUrl(settings.getAuthBaseUrl().trim());
        }
        if (hasText(settings.getServerId())) {
            config.setServerId(settings.getServerId().trim());
        }
    }

    private void refreshServerPresenceAsync(String host, int port, String route) {
        updateServerPresence("ПРОВЕРКА", "checking");
        updateOnlinePlayersValue(DASHBOARD_UNKNOWN);
        updateServerDetailValues("...", "...");
        long requestId = serverPresenceSequence.incrementAndGet();

        Thread thread = new Thread(() -> {
            MinecraftServerStatusProbe.ServerStatus status = null;
            try {
                status = MinecraftServerStatusProbe.probe(host, port, SERVER_STATUS_TIMEOUT_MS);
            } catch (IOException ignored) {
            }

            MinecraftServerStatusProbe.ServerStatus resolvedStatus = status;
            Platform.runLater(() -> {
                if (requestId != serverPresenceSequence.get()) {
                    return;
                }

                if (resolvedStatus != null) {
                    updateServerPresence("Онлайн", "online");
                    updateOnlinePlayersValue(formatPlayers(resolvedStatus));
                    updateServerDetailValues(resolvedStatus.getPingMs() + " мс", resolveServerVersion(resolvedStatus));
                    return;
                }

                updateServerPresence("Оффлайн", "offline");
                updateOnlinePlayersValue(DASHBOARD_UNKNOWN);
                updateServerDetailValues(DASHBOARD_UNKNOWN, DASHBOARD_UNKNOWN);
                appendLog("Нет ответа от " + route + ".");
            });
        }, "launcher-shell-presence");

        thread.setDaemon(true);
        thread.start();
    }

    private void applyDashboardLoadingState(boolean hasManifestUrl) {
        updateVersionValues(
            hasManifestUrl ? "проверка..." : "не указан",
            hasManifestUrl ? "проверка..." : "не указан"
        );
        updateOnlinePlayersValue(DASHBOARD_UNKNOWN);
        updateServerDetailValues(DASHBOARD_UNKNOWN, DASHBOARD_UNKNOWN);
        updateModpackSizeValue(hasManifestUrl ? DASHBOARD_UNKNOWN : "не указан");
    }

    private static String resolveManifestVersion(ModpackManifest manifest) {
        if (manifest == null || !hasText(manifest.getVersion())) {
            return "нет данных";
        }
        return manifest.getVersion().trim();
    }

    private static String resolveMinecraftVersion(ModpackManifest manifest) {
        if (manifest == null || manifest.getMinecraft() == null) {
            return "нет данных";
        }

        String minecraftVersion = manifest.getMinecraft().getVersion();
        String forgeVersion = manifest.getMinecraft().getForgeVersion();
        if (hasText(minecraftVersion) && hasText(forgeVersion)) {
            return minecraftVersion.trim() + " / " + forgeVersion.trim();
        }
        if (hasText(minecraftVersion)) {
            return minecraftVersion.trim();
        }
        if (hasText(forgeVersion)) {
            return "Forge " + forgeVersion.trim();
        }
        return "нет данных";
    }

    private static String formatPlayers(MinecraftServerStatusProbe.ServerStatus status) {
        if (status == null || status.getOnlinePlayers() < 0) {
            return DASHBOARD_UNKNOWN;
        }
        if (status.getMaxPlayers() < 0) {
            return Integer.toString(status.getOnlinePlayers());
        }
        return status.getOnlinePlayers() + " / " + status.getMaxPlayers();
    }

    private static String resolveServerVersion(MinecraftServerStatusProbe.ServerStatus status) {
        if (status == null || !hasText(status.getVersionName())) {
            return "неизвестно";
        }
        return status.getVersionName().trim();
    }

    private void updateServerPresence(String title, String tone) {
        serverPresenceLabel.setText(title);
        toggleStyleClass(serverPresenceIndicator, "presence-checking", "checking".equals(tone));
        toggleStyleClass(serverPresenceIndicator, "presence-online", "online".equals(tone));
        toggleStyleClass(serverPresenceIndicator, "presence-offline", "offline".equals(tone));
    }

    private int runProcess(List<String> command, Path workingDirectory) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (workingDirectory != null) {
            processBuilder.directory(workingDirectory.toFile());
        }
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
        )) {
            String line;
            while ((line = reader.readLine()) != null) {
                appendLogAsync(line);
            }
        }
        return process.waitFor();
    }

    private void ensureRequiredResourcePack(LauncherConfig config) {
        try {
            Path gameDirectory = resolveGameDirectory(config);
            if (MinecraftResourcePackOptions.ensureLanguage(gameDirectory, REQUIRED_LANGUAGE)) {
                appendLogAsync("Язык Minecraft установлен: " + REQUIRED_LANGUAGE);
            }
            if (MinecraftResourcePackOptions.ensureEnabled(gameDirectory, REQUIRED_RESOURCE_PACK)) {
                appendLogAsync("Обязательный ресурс-пак включен: " + REQUIRED_RESOURCE_PACK);
            }
        } catch (IOException | IllegalArgumentException exception) {
            appendLogAsync("Настройка обязательного ресурс-пака пропущена: " + exception.getMessage());
        }
    }

    private List<GameTicket> createReconnectTickets(LauncherConfig config, AuthSession session) throws IOException {
        List<GameTicket> tickets = new ArrayList<GameTicket>(RECONNECT_TICKET_COUNT);
        for (int index = 0; index < RECONNECT_TICKET_COUNT; index++) {
            tickets.add(context().getAuthService().createGameTicket(config, session));
        }
        return tickets;
    }

    private void openDesktopPath(Path target) throws IOException {
        if (!Desktop.isDesktopSupported()) {
            throw new IOException("Открытие через рабочий стол не поддерживается.");
        }
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.OPEN)) {
            throw new IOException("Открытие файлов не поддерживается.");
        }
        desktop.open(target.toFile());
    }

    private void openExternalLocation(String location) throws Exception {
        if (!hasText(location)) {
            throw new IOException("Адрес не настроен.");
        }
        if (!Desktop.isDesktopSupported()) {
            throw new IOException("Открытие через рабочий стол не поддерживается.");
        }
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.BROWSE)) {
            throw new IOException("Открытие ссылок не поддерживается.");
        }
        desktop.browse(new URI(location.trim()));
    }

    private void setBusy(boolean busy) {
        previewButton.setDisable(busy);
        launchButton.setDisable(busy);
        launchArrowButton.setDisable(busy);
        if (launcherUpdateButton != null) {
            launcherUpdateButton.setDisable(busy || availableLauncherUpdate == null || !availableLauncherUpdate.isInstallSupported());
        }
        if (launcherUpdateCheckButton != null) {
            launcherUpdateCheckButton.setDisable(busy);
        }
    }

    private void updateProgressState(boolean busy, String statusText, String percentText, double progress) {
        syncProgressBar.setProgress(progress);
        syncStatusLabel.setText(statusText);
        syncPercentLabel.setText(percentText);
        boolean showProgress = busy || progress > 0.0d || progress == ProgressBar.INDETERMINATE_PROGRESS;
        syncProgressBar.setManaged(showProgress);
        syncProgressBar.setVisible(showProgress);
        syncPercentLabel.setManaged(showProgress);
        syncPercentLabel.setVisible(showProgress);
        if (syncFileLabel != null) {
            syncFileLabel.setManaged(true);
            syncFileLabel.setVisible(true);
        }
        if (busy) {
            syncBytesLabel.setText("Лаунчер выполняет операцию...");
        }
    }

    private void applySyncProgressAsync(ModpackSyncProgress progress) {
        if (progress == null) {
            return;
        }
        Platform.runLater(() -> applySyncProgress(progress));
    }

    private void applySyncProgress(ModpackSyncProgress progress) {
        double progressValue = progress.getProgress() < 0.0d
            ? ProgressBar.INDETERMINATE_PROGRESS
            : progress.getProgress();
        updateProgressState(
            progress.getPhase() != ModpackSyncProgress.Phase.COMPLETE,
            progress.getMessage(),
            formatProgressPercent(progressValue),
            progressValue
        );
        syncBytesLabel.setText(formatProgressSummary(progress));
        syncFileLabel.setText(formatProgressDetail(progress));
    }

    private void applyLastLaunchResult(int exitCode) {
        String statusText = exitCode == 0
            ? "Последний запуск завершён успешно"
            : "Последний запуск завершён с ошибкой";
        updateProgressState(false, statusText, "ГОТОВО", 0.0d);
        syncBytesLabel.setText(LocalTime.now().format(LOG_TIME_FORMAT) + "  •  Код выхода: " + exitCode);
    }

    private void updateOnlinePlayersValue(String value) {
        onlinePlayersValueLabel.setText(value);
        if (footerOnlinePlayersValueLabel != null) {
            footerOnlinePlayersValueLabel.setText(value);
        }
    }

    private void updateServerDetailValues(String ping, String version) {
        serverPingValueLabel.setText(normalizeText(ping));
        serverVersionValueLabel.setText(normalizeText(version));
    }

    private void updateModpackSizeValue(String value) {
        modpackSizeValueLabel.setText(normalizeText(value));
    }

    private void updateVersionValues(String minecraftVersion, String manifestVersion) {
        minecraftVersionValueLabel.setText(minecraftVersion);
        manifestVersionValueLabel.setText(manifestVersion);
        updateConfigurationLabel(minecraftVersion, manifestVersion);
        if (footerMinecraftVersionValueLabel != null) {
            footerMinecraftVersionValueLabel.setText(minecraftVersion);
        }
        if (footerManifestVersionValueLabel != null) {
            footerManifestVersionValueLabel.setText(manifestVersion);
        }
    }

    private void updateConfigurationLabel(String minecraftVersion, String manifestVersion) {
        if (configNameLabel == null) {
            return;
        }
        String version = hasText(minecraftVersion) && !DASHBOARD_UNKNOWN.equals(minecraftVersion)
            ? minecraftVersion
            : manifestVersion;
        configNameLabel.setText("ObsidianGate " + formatBuildDisplayVersion(version));
    }

    private static String formatBuildDisplayVersion(String version) {
        String normalizedVersion = normalizeText(version);
        if (DASHBOARD_UNKNOWN.equals(normalizedVersion)) {
            return "1.12.2";
        }
        int forgeSeparatorIndex = normalizedVersion.indexOf('/');
        if (forgeSeparatorIndex >= 0) {
            normalizedVersion = normalizedVersion.substring(0, forgeSeparatorIndex).trim();
        }
        return hasText(normalizedVersion) ? normalizedVersion : "1.12.2";
    }

    private void persistConfig(LauncherConfig config, boolean logPath) throws IOException {
        currentConfig = LauncherDefaults.applyMissingValues(config.copy());
        context().saveConfig(currentConfig);
        applyConfigToView(currentConfig);
        if (logPath) {
            appendLog("Config saved: " + context().getConfigStore().getConfigFile());
        }
    }

    private void appendLog(String message) {
        if (logArea == null) {
            return;
        }
        String resolvedMessage = message == null ? "" : message;
        logArea.appendText("[" + LocalTime.now().format(LOG_TIME_FORMAT) + "] " + resolvedMessage + System.lineSeparator());
        logArea.positionCaret(logArea.getLength());
    }

    private void appendLogAsync(String message) {
        Platform.runLater(() -> appendLog(message));
    }

    private void showLauncherError(String message) {
        appendLog("Ошибка: " + message);
        showError(message);
    }

    private String resolveLauncherUpdateFailureSummary(Throwable exception) {
        String message = exception == null ? "" : valueOrFallback(exception.getMessage(), "");
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("http 404")) {
            return "Файл обновления не найден на сервере.";
        }
        if (normalized.contains("timed out")) {
            return "Сервер обновлений не ответил вовремя.";
        }
        if (normalized.contains("connection refused")) {
            return "Сервер обновлений отклонил соединение.";
        }
        return "Проверьте launcherUpdate в manifest и доступность файла лаунчера.";
    }

    private String resolveLauncherUpdateFailureMessage(Throwable exception, LauncherUpdateCandidate update) {
        if (exception == null) {
            return "Не удалось обновить лаунчер.";
        }

        String message = valueOrFallback(exception.getMessage(), "Неизвестная ошибка.");
        String normalized = message.toLowerCase(Locale.ROOT);
        String downloadUrl = update == null || update.getDownloadUrl() == null
            ? "не указан"
            : update.getDownloadUrl().toString();

        if (normalized.contains("http 404")) {
            return "Файл обновления не найден на сервере: " + downloadUrl
                + ". Опубликуйте jar в каталоге /launcher/ или проверьте launcherUpdate.url в manifest.";
        }
        if (normalized.contains("timed out")) {
            return "Сервер обновлений не ответил вовремя: " + downloadUrl + ". Проверьте доступность хоста и сети.";
        }
        if (normalized.contains("connection refused")) {
            return "Сервер обновлений отклонил соединение: " + downloadUrl + ". Проверьте, что веб-сервер запущен.";
        }

        return "Не удалось обновить лаунчер: " + message;
    }

    private boolean handleExpiredSession(Throwable exception) {
        if (!(exception instanceof AuthSessionExpiredException)) {
            return false;
        }

        String message = exception.getMessage();
        state().setSession(null);
        state().setAuthNotice(message);
        context().persistStateQuietly();
        applyProfileState();
        updateProgressState(false, "Требуется повторный вход", "СЕССИЯ", 0.0d);
        syncFileLabel.setText(message);
        syncBytesLabel.setText("Сохраненная сессия сброшена. Авторизуйтесь снова.");
        appendLog("Сохраненная сессия истекла. Открываем вход.");
        router().open(ScreenRouter.Screen.AUTH);
        return true;
    }

    private Path resolveWorkingDirectory(LauncherConfig config) {
        Path gameDirectory = resolveGameDirectory(config);

        if (!hasText(config.getWorkingDirectory())) {
            if (gameDirectory == null) {
                return null;
            }
            if (!Files.isDirectory(gameDirectory)) {
                throw new IllegalArgumentException("Папка игры не найдена: " + gameDirectory);
            }
            return gameDirectory;
        }

        Path path = Paths.get(config.getWorkingDirectory().trim());
        Path workingDirectory;
        if (path.isAbsolute() || gameDirectory == null) {
            workingDirectory = path.toAbsolutePath().normalize();
        } else {
            workingDirectory = gameDirectory.resolve(path).normalize();
        }

        if (!Files.isDirectory(workingDirectory)) {
            throw new IllegalArgumentException("Рабочая папка не найдена: " + workingDirectory);
        }
        return workingDirectory;
    }

    private static Path resolveGameDirectory(LauncherConfig config) {
        if (config == null || !hasText(config.getGameDirectory())) {
            return null;
        }
        return Paths.get(config.getGameDirectory().trim()).toAbsolutePath().normalize();
    }

    private static String formatRoute(LauncherConfig config) {
        return valueOrFallback(config.getServerHost(), LauncherConfig.DEFAULT_SERVER_HOST) + ":" + config.getServerPort();
    }

    private static String deriveManifestDirectory(String manifestUrl) {
        if (!hasText(manifestUrl)) {
            return "URL manifest не настроен.";
        }
        String resolved = manifestUrl.trim();
        int separator = resolved.lastIndexOf('/');
        if (separator < 0) {
            return resolved;
        }
        return resolved.substring(0, separator + 1);
    }

    private static String resolveDisplayDownloadBase(LoadedManifest loadedManifest) {
        ModpackManifest manifest = loadedManifest.getManifest();
        if (manifest != null && hasText(manifest.getBaseUrl())) {
            try {
                return new URL(loadedManifest.getSourceUrl(), manifest.getBaseUrl().trim()).toString();
            } catch (Exception ignored) {
            }
        }
        return deriveManifestDirectory(loadedManifest.getSourceUrl().toString());
    }

    private static String resolveDisplayDownloadBase(String manifestUrl, ModpackManifest manifest) {
        if (manifest != null && hasText(manifest.getBaseUrl()) && hasText(manifestUrl)) {
            try {
                return new URL(new URL(manifestUrl.trim()), manifest.getBaseUrl().trim()).toString();
            } catch (Exception ignored) {
            }
        }
        return deriveManifestDirectory(manifestUrl);
    }

    private static String formatProgressPercent(double progress) {
        if (progress == ProgressBar.INDETERMINATE_PROGRESS || progress < 0.0d) {
            return "...";
        }
        int percent = (int) Math.round(Math.max(0.0d, Math.min(1.0d, progress)) * 100.0d);
        return percent + "%";
    }

    private static String formatProgressSummary(ModpackSyncProgress progress) {
        if (progress.getPhase() == ModpackSyncProgress.Phase.CHECKING) {
            return "Проверено " + progress.getCheckedFiles() + "/" + progress.getTotalFiles()
                + " файлов • актуальны " + progress.getReusedFiles();
        }
        if (progress.getPhase() == ModpackSyncProgress.Phase.DOWNLOADING) {
            String bytes = progress.getTotalDownloadBytes() > 0L
                ? formatBytes(progress.getDownloadedBytes()) + " / " + formatBytes(progress.getTotalDownloadBytes())
                : formatBytes(progress.getDownloadedBytes());
            return "Скачано " + progress.getDownloadedFiles() + "/" + progress.getDownloadFiles()
                + " файлов • " + bytes;
        }
        if (progress.getPhase() == ModpackSyncProgress.Phase.COMPLETE) {
            return "Проверено " + progress.getCheckedFiles() + "/" + progress.getTotalFiles()
                + " файлов • скачано " + progress.getDownloadedFiles() + "/" + progress.getDownloadFiles();
        }
        return progress.getDownloadFiles() > 0
            ? "К скачиванию " + progress.getDownloadFiles() + " файлов • " + formatBytes(progress.getTotalDownloadBytes())
            : "Подготовка синхронизации...";
    }

    private static String formatProgressDetail(ModpackSyncProgress progress) {
        String currentFile = shortenPath(progress.getCurrentFile());
        if (progress.getPhase() == ModpackSyncProgress.Phase.CHECKING) {
            return hasText(currentFile) ? "Проверяется: " + currentFile : "Сканируем папку игры.";
        }
        if (progress.getPhase() == ModpackSyncProgress.Phase.DOWNLOADING) {
            StringBuilder builder = new StringBuilder();
            builder.append(hasText(currentFile) ? "Скачивается: " + currentFile : "Ожидаем загрузку файлов.");
            if (progress.getBytesPerSecond() > 0L) {
                builder.append(" • ").append(formatBytes(progress.getBytesPerSecond())).append("/s");
            }
            if (progress.getEstimatedRemainingMillis() >= 0L) {
                builder.append(" • осталось ").append(formatDuration(progress.getEstimatedRemainingMillis()));
            }
            return builder.toString();
        }
        if (progress.getPhase() == ModpackSyncProgress.Phase.RUNTIME) {
            return "Проверяем portable Java и при необходимости устанавливаем runtime.";
        }
        if (progress.getPhase() == ModpackSyncProgress.Phase.MINECRAFT) {
            return "Проверяем клиент Minecraft, Forge, библиотеки, assets и natives.";
        }
        if (progress.getPhase() == ModpackSyncProgress.Phase.CLEANUP) {
            return "Сверяем папку mods с manifest и переносим устаревшие файлы.";
        }
        if (progress.getPhase() == ModpackSyncProgress.Phase.COMPLETE) {
            return "Синхронизация завершена.";
        }
        return "Готовим manifest и локальный каталог.";
    }

    private static String formatSyncSummary(ModpackSyncResult syncResult) {
        String summary = syncResult.getDownloadedFiles()
            + " загружено / "
            + syncResult.getReusedFiles()
            + " повторно использовано / "
            + formatMegabytes(syncResult.getDownloadedBytes());
        if (syncResult.getRemovedFiles() > 0) {
            summary += " / устаревших убрано: " + syncResult.getRemovedFiles();
        }
        return summary;
    }

    private static String formatMegabytes(long bytes) {
        double megabytes = bytes / 1024.0d / 1024.0d;
        return String.format(Locale.US, "%.1f MB", Double.valueOf(megabytes));
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0L) {
            return "0 B";
        }
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double kilobytes = bytes / 1024.0d;
        if (kilobytes < 1024.0d) {
            return String.format(Locale.US, "%.1f KB", Double.valueOf(kilobytes));
        }
        double megabytes = kilobytes / 1024.0d;
        return String.format(Locale.US, "%.1f MB", Double.valueOf(megabytes));
    }

    private static String formatDuration(long millis) {
        long seconds = Math.max(0L, (millis + 999L) / 1000L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainingSeconds = seconds % 60L;
        if (hours > 0L) {
            return String.format(
                Locale.US,
                "%d:%02d:%02d",
                Long.valueOf(hours),
                Long.valueOf(minutes),
                Long.valueOf(remainingSeconds)
            );
        }
        return String.format(Locale.US, "%d:%02d", Long.valueOf(minutes), Long.valueOf(remainingSeconds));
    }

    private static String formatManifestSize(ModpackManifest manifest) {
        if (manifest == null || manifest.getFiles() == null || manifest.getFiles().isEmpty()) {
            return DASHBOARD_UNKNOWN;
        }
        long totalBytes = 0L;
        for (ModpackFile file : manifest.getFiles()) {
            if (file != null && file.getSize() != null && file.getSize().longValue() > 0L) {
                totalBytes += file.getSize().longValue();
            }
        }
        return formatBytes(totalBytes);
    }

    private static String formatManifestLocation(String manifestUrl) {
        if (!hasText(manifestUrl)) {
            return DASHBOARD_UNKNOWN;
        }
        String normalized = manifestUrl.trim();
        int separator = normalized.lastIndexOf('/');
        if (separator >= 0 && separator < normalized.length() - 1) {
            return normalized.substring(separator + 1);
        }
        return normalized;
    }

    private static String normalizeText(String value) {
        return hasText(value) ? value.trim() : DASHBOARD_UNKNOWN;
    }

    private static String shortenHash(String value) {
        if (!hasText(value)) {
            return DASHBOARD_UNKNOWN;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 16) {
            return trimmed;
        }
        return trimmed.substring(0, 12) + "..." + trimmed.substring(trimmed.length() - 8);
    }

    private static String shortenPath(String value) {
        if (!hasText(value)) {
            return "";
        }
        String normalized = value.trim().replace('\\', '/');
        if (normalized.length() <= 86) {
            return normalized;
        }
        int separator = normalized.lastIndexOf('/');
        if (separator > 0 && separator < normalized.length() - 1) {
            String fileName = normalized.substring(separator + 1);
            if (fileName.length() <= 72) {
                return "..." + normalized.substring(separator);
            }
        }
        return "..." + normalized.substring(normalized.length() - 83);
    }

    private static String resolvePreviewCategory(String path) {
        if (!hasText(path)) {
            return "файл";
        }
        String normalized = path.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalized.startsWith("mods/")) {
            return "мод";
        }
        if (normalized.startsWith("config/")) {
            return "конфиг";
        }
        if (normalized.startsWith("resourcepacks/")) {
            return "ресурсы";
        }
        return "файл";
    }

    private static String resolvePreviewReasonLabel(String reason) {
        if ("missing".equals(reason)) {
            return "нет";
        }
        if ("sha256-mismatch".equals(reason)) {
            return "замена";
        }
        if ("size-mismatch".equals(reason)) {
            return "замена";
        }
        return "готово";
    }

    private static String resolvePreviewReasonText(String reason) {
        if ("size-mismatch".equals(reason)) {
            return "Размер файла не совпадает с manifest, поэтому файл будет заменён.";
        }
        if ("missing".equals(reason)) {
            return "Файл отсутствует локально и будет скачан заново.";
        }
        if ("sha256-mismatch".equals(reason)) {
            return "SHA-256 не совпадает с manifest, поэтому файл будет заменен.";
        }
        return "Локальная копия уже совпадает с manifest.";
    }

    private static boolean shouldSyncBeforeLaunch(LauncherConfig config) {
        return hasText(config.getManifestUrl());
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String valueOrFallback(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private static String resolveRoleLabel(String role) {
        if (!hasText(role)) {
            return "Игрок";
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        if ("admin".equals(normalized)) {
            return "Администратор";
        }
        if ("moderator".equals(normalized)) {
            return "Модератор";
        }
        if ("vip".equals(normalized)) {
            return "VIP";
        }
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private static void toggleStyleClass(Node node, String styleClass, boolean enabled) {
        if (node == null) {
            return;
        }
        if (enabled) {
            if (!node.getStyleClass().contains(styleClass)) {
                node.getStyleClass().add(styleClass);
            }
        } else {
            node.getStyleClass().remove(styleClass);
        }
    }

    private enum LauncherAction {
        SYNC_ONLY,
        SYNC_AND_LAUNCH
    }

    private static final class LauncherTaskResult {
        private final LauncherConfig resolvedConfig;
        private final ModpackSyncResult syncResult;
        private final Integer exitCode;
        private final AuthSession session;

        private LauncherTaskResult(
            LauncherConfig resolvedConfig,
            ModpackSyncResult syncResult,
            Integer exitCode,
            AuthSession session
        ) {
            this.resolvedConfig = resolvedConfig;
            this.syncResult = syncResult;
            this.exitCode = exitCode;
            this.session = session;
        }
    }
}
