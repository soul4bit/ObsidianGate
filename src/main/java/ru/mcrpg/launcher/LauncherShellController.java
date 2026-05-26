package ru.mcrpg.launcher;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Arc;
import javafx.util.Duration;
import ru.mcrpg.launcher.ui.AvatarImages;
import ru.mcrpg.launcher.ui.LauncherIcons;

public final class LauncherShellController extends AbstractScreenController {

    private static final String MEETING_URL = "https://telemost.yandex.ru/";
    private static final int SERVER_STATUS_TIMEOUT_MS = 1500;
    private static final int SERVER_STATUS_REFRESH_SECONDS = 5;
    private static final int NEWS_REFRESH_SECONDS = 60;
    private static final int NEWS_ITEM_LIMIT = 3;
    private static final String FALLBACK_MODPACK_NAME = "Glass";
    private static final String UNKNOWN_VALUE = "-";
    private static final String STATUS_ONLINE = "server-state-online";
    private static final String STATUS_OFFLINE = "server-state-offline";
    private static final String STATUS_CHECKING = "server-state-checking";
    private static final String SYNC_STATUS_OK = "sync-state-ok";
    private static final String SYNC_STATUS_WORKING = "sync-state-working";
    private static final String SYNC_STATUS_ERROR = "sync-state-error";
    private static final String PLAY_BUTTON_UPDATE_STYLE = "launcher-update-button";
    private static final DateTimeFormatter UPDATED_AT_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm");

    private final ModpackManifestClient manifestClient = new ModpackManifestClient();
    private final ModpackSyncService modpackSyncService = new ModpackSyncService(manifestClient);
    private final LauncherUpdateService launcherUpdateService = new LauncherUpdateService();
    private final LaunchCommandBuilder launchCommandBuilder = new LaunchCommandBuilder();
    private final AtomicLong serverStatusRequestSequence = new AtomicLong();
    private final AtomicLong syncRequestSequence = new AtomicLong();
    private final AtomicLong newsRequestSequence = new AtomicLong();
    private volatile ScheduledExecutorService serverStatusExecutor;
    private volatile ScheduledExecutorService newsExecutor;
    private volatile LauncherUpdateCandidate availableLauncherUpdate;
    private volatile String serverHost = LauncherConfig.DEFAULT_SERVER_HOST;
    private volatile int serverPort = LauncherConfig.DEFAULT_SERVER_PORT;
    private volatile boolean syncInProgress;
    private volatile boolean launchInProgress;
    private volatile boolean launcherUpdateInProgress;
    private double lastSyncProgress;
    private FadeTransition syncPulseTransition;

    @FXML
    private Label brandLogoLabel;

    @FXML
    private Label sidebarVersionLabel;

    @FXML
    private Button homeNavButton;

    @FXML
    private Button settingsNavButton;

    @FXML
    private Button profileNavButton;

    @FXML
    private Button playButton;

    @FXML
    private Label heroTitleLabel;

    @FXML
    private Label buildVersionLabel;

    @FXML
    private Label forgeVersionBadgeLabel;

    @FXML
    private Label updatedAtLabel;

    @FXML
    private Button copyIpIconButton;

    @FXML
    private Button copyIpButton;

    @FXML
    private Button meetingButton;

    @FXML
    private Arc syncProgressArc;

    @FXML
    private Label syncProgressLabel;

    @FXML
    private Label syncStatusLabel;

    @FXML
    private Label syncDetailLabel;

    @FXML
    private Region serverStatusDot;

    @FXML
    private Label serverStatusLabel;

    @FXML
    private Label serverAddressLabel;

    @FXML
    private Label playersValueLabel;

    @FXML
    private Label pingValueLabel;

    @FXML
    private Label versionValueLabel;

    @FXML
    private VBox newsListBox;

    @FXML
    private ImageView sidebarProfileAvatarView;

    @FXML
    private Label sidebarProfileNameLabel;

    @FXML
    private Label sidebarProfileStatusLabel;

    @FXML
    private Label sidebarProfileRoleLabel;

    @FXML
    private Label playersIconLabel;

    @FXML
    private Label pingIconLabel;

    @FXML
    private Label versionIconLabel;

    @FXML
    private void initialize() {
        configureWindowButtons();
        configureCleanGlassSidebar(ScreenRouter.Screen.HOME);

        if (brandLogoLabel != null) {
            brandLogoLabel.setText("OBSIDIANGATE");
        }
        if (sidebarVersionLabel != null) {
            sidebarVersionLabel.setText("Лаунчер " + LauncherBrand.displayVersion());
        }
        applyFallbackAvatar();
        configureIcons();
        applyProfileState();
        applyManifestSummary(null);
        applyServerAddress();
        applyCheckingServerStatus();
        applySyncIdleState();
        applyNewsLoadingState();
    }

    @Override
    protected void onContextBound(LauncherContext context) {
        if (!Boolean.getBoolean(LauncherShellApplication.PREVIEW_HOME_PROPERTY) && !state().isAuthenticated()) {
            router().open(ScreenRouter.Screen.AUTH);
            return;
        }
        applyServerEndpointFromConfig(state().getConfig());
        configureCleanGlassSidebar(ScreenRouter.Screen.HOME);
        applyProfileState();
        Platform.runLater(this::startServerStatusPolling);
        Platform.runLater(this::startNewsPolling);
    }

    @FXML
    private void play() {
        if (hasLauncherUpdate()) {
            updateLauncher();
            return;
        }
        if (launchInProgress) {
            return;
        }
        if (syncInProgress) {
            showError("Дождитесь завершения синхронизации файлов.");
            return;
        }
        if (!state().isAuthenticated()) {
            state().setAuthNotice("Войдите в аккаунт, чтобы запустить игру.");
            router().open(ScreenRouter.Screen.AUTH);
            return;
        }

        AuthSession session = state().getSession();
        LauncherConfig launchConfig = LauncherDefaults.applyMissingValues(state().getConfig().copy());
        launchConfig.setUsername(session.getAccount().getUsername());

        long requestId = syncRequestSequence.incrementAndGet();
        launchConfig.setUpdateFilesBeforeLaunch(true);
        launchInProgress = true;
        syncInProgress = true;
        setPlayButtonBusy(true);
        applySyncPreparingState();

        Task<LaunchStartResult> task = new Task<LaunchStartResult>() {
            @Override
            protected LaunchStartResult call() throws Exception {
                return startGame(launchConfig, session, requestId);
            }
        };

        task.setOnSucceeded(event -> finishSuccessfulLaunch(task.getValue()));
        task.setOnFailed(event -> finishFailedLaunch(task.getException()));

        Thread thread = new Thread(task, "launcher-game-start");
        thread.setDaemon(true);
        thread.start();
    }

    private LaunchStartResult startGame(LauncherConfig baseConfig, AuthSession session, long syncRequestId) throws Exception {
        LauncherConfig launchConfig = baseConfig.copy();
        launchConfig.setUpdateFilesBeforeLaunch(true);
        ModpackSyncResult syncResult = modpackSyncService.sync(
            launchConfig,
            message -> {
            },
            progress -> Platform.runLater(() -> applySyncProgress(syncRequestId, progress))
        );
        if (syncResult != null && syncResult.getResolvedConfig() != null) {
            launchConfig = syncResult.getResolvedConfig().copy();
            launchConfig.setUsername(session.getAccount().getUsername());
            launchConfig.setUpdateFilesBeforeLaunch(true);
            context().saveConfig(launchConfig);
            LauncherConfig savedConfig = launchConfig.copy();
            ModpackManifest syncedManifest = syncResult.getManifest();
            Platform.runLater(() -> {
                applyServerEndpointFromConfig(savedConfig);
                applyManifestSummary(syncedManifest);
            });
        }

        AuthSession refreshedSession = context().getAuthService().refreshIfNeeded(launchConfig, session);
        if (refreshedSession == null || refreshedSession.getAccount() == null) {
            throw new AuthSessionExpiredException("Сессия истекла. Войдите в аккаунт снова.", null);
        }
        state().setSession(refreshedSession);

        GameTicket ticket = context().getAuthService().createGameTicket(launchConfig, refreshedSession);
        Path sessionFile = context().getSessionFileWriter().write(launchConfig, ticket);
        LaunchIdentity identity = LaunchIdentity.authenticated(
            ticket.getUsername(),
            ticket.getUuid(),
            refreshedSession.getAccessToken(),
            sessionFile
        );
        List<String> command = launchCommandBuilder.build(launchConfig, identity);
        Process process = startGameProcess(launchConfig, command);
        return new LaunchStartResult(process.pid(), gameLogFile(launchConfig), syncResult);
    }

    private void finishSuccessfulLaunch(LaunchStartResult result) {
        launchInProgress = false;
        syncInProgress = false;
        setPlayButtonBusy(false);
        lastSyncProgress = 1.0d;
        setSyncProgress(1.0d);
        setText(syncProgressLabel, "100%");
        setSyncStatus("Игра запущена", SYNC_STATUS_OK, "check-circle", "#86efac");
        setSyncDetail(describeSyncResult(result == null ? null : result.syncResult()));
    }

    private void finishFailedLaunch(Throwable exception) {
        launchInProgress = false;
        syncInProgress = false;
        setPlayButtonBusy(false);
        lastSyncProgress = 0.0d;
        setSyncProgress(0.0d);
        setText(syncProgressLabel, "!");
        setSyncStatus("Ошибка запуска", SYNC_STATUS_ERROR, "info", "#fda4af");
        setSyncDetail("Синхронизация не завершена");

        if (isSessionFailure(exception)) {
            state().setSession(null);
            state().setAuthNotice("Сессия истекла. Войдите в аккаунт снова.");
            router().open(ScreenRouter.Screen.AUTH);
            return;
        }
        showError("Не удалось запустить игру: " + errorMessage(exception));
    }

    private void setPlayButtonBusy(boolean busy) {
        if (playButton == null) {
            return;
        }
        playButton.setDisable(busy);
        if (busy) {
            playButton.getStyleClass().remove(PLAY_BUTTON_UPDATE_STYLE);
            setGraphic(playButton, "play", 16.0d, "#ffffff");
            playButton.setText("Запуск...");
            return;
        }
        applyPlayButtonState();
    }

    private void updateLauncher() {
        if (launcherUpdateInProgress) {
            return;
        }

        LauncherUpdateCandidate update = availableLauncherUpdate;
        if (update == null) {
            showError("Обновление лаунчера недоступно.");
            return;
        }
        if (!update.isInstallSupported()) {
            showError("Автообновление работает только при запуске лаунчера из jar-файла.");
            return;
        }

        launcherUpdateInProgress = true;
        applyPlayButtonState();
        setSyncStatus("Скачиваем лаунчер", SYNC_STATUS_WORKING, "download", "#fbbf24");

        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                launcherUpdateService.installAndRestart(update, message -> {
                });
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            setSyncStatus("Перезапуск лаунчера", SYNC_STATUS_OK, "check-circle", "#86efac");
            Platform.exit();
            System.exit(0);
        });
        task.setOnFailed(event -> {
            launcherUpdateInProgress = false;
            applyPlayButtonState();
            setSyncStatus("Ошибка обновления", SYNC_STATUS_ERROR, "info", "#fda4af");
            showError(resolveLauncherUpdateFailureMessage(task.getException(), update));
        });

        Thread thread = new Thread(task, "launcher-self-update");
        thread.setDaemon(true);
        thread.start();
    }

    private void applyPlayButtonState() {
        if (playButton == null || launchInProgress) {
            return;
        }

        playButton.getStyleClass().remove(PLAY_BUTTON_UPDATE_STYLE);
        if (launcherUpdateInProgress) {
            playButton.setDisable(true);
            setGraphic(playButton, "download", 16.0d, "#ffffff");
            playButton.setText("Обновление...");
            playButton.getStyleClass().add(PLAY_BUTTON_UPDATE_STYLE);
            return;
        }

        if (hasLauncherUpdate()) {
            playButton.setDisable(false);
            setGraphic(playButton, "download", 16.0d, "#ffffff");
            playButton.setText("Обновить лаунчер");
            playButton.getStyleClass().add(PLAY_BUTTON_UPDATE_STYLE);
            return;
        }

        playButton.setDisable(false);
        setGraphic(playButton, "play", 16.0d, "#ffffff");
        playButton.setText("Играть");
    }

    private boolean hasLauncherUpdate() {
        return availableLauncherUpdate != null;
    }

    private static Process startGameProcess(LauncherConfig config, List<String> command) throws IOException {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("Команда запуска пустая.");
        }

        Path workingDirectory = launchWorkingDirectory(config);
        Files.createDirectories(workingDirectory);
        Path logFile = gameLogFile(config);
        Files.createDirectories(logFile.getParent());

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDirectory.toFile());
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
        return processBuilder.start();
    }

    private static Path launchWorkingDirectory(LauncherConfig config) {
        String workingDirectory = hasText(config.getWorkingDirectory())
            ? config.getWorkingDirectory()
            : config.getGameDirectory();
        return Paths.get(requireText(workingDirectory, "Рабочая папка игры не настроена.")).toAbsolutePath().normalize();
    }

    private static Path gameLogFile(LauncherConfig config) {
        Path gameDirectory = Paths.get(requireText(config.getGameDirectory(), "Папка игры не настроена."))
            .toAbsolutePath()
            .normalize();
        return gameDirectory.resolve(".obsidiangate").resolve("game.log");
    }

    @FXML
    private void copyServerIp() {
        ClipboardContent content = new ClipboardContent();
        content.putString(serverAddress());
        Clipboard.getSystemClipboard().setContent(content);
    }

    @FXML
    private void openMeeting() {
        try {
            openExternalUri(URI.create(MEETING_URL));
        } catch (Exception exception) {
            showError("Не удалось открыть Телемост: " + exception.getMessage());
        }
    }

    @FXML
    private void openNews() {
        requestNewsRefresh();
    }

    @FXML
    private void openSettings() {
        router().open(ScreenRouter.Screen.SETTINGS);
    }

    @FXML
    private void openProfileScreen() {
        router().open(state().isAuthenticated() ? ScreenRouter.Screen.PROFILE : ScreenRouter.Screen.AUTH);
    }

    @FXML
    private void openProfileScreenFromKeyboard(KeyEvent event) {
        if (event == null || (event.getCode() != KeyCode.ENTER && event.getCode() != KeyCode.SPACE)) {
            return;
        }
        event.consume();
        openProfileScreen();
    }

    private void configureIcons() {
        setGraphic(homeNavButton, "home", 16.0d, "#ffffff");
        setGraphic(settingsNavButton, "settings", 16.0d, "#f8fafc");
        setGraphic(profileNavButton, "profile", 16.0d, "#f8fafc");
        setGraphic(playButton, "play", 16.0d, "#ffffff");
        setGraphic(copyIpIconButton, "copy", 16.0d, "#a8b3c3");
        setGraphic(copyIpButton, "copy", 16.0d, "#dbe4ef");
        setGraphic(meetingButton, "video", 16.0d, "#ffffff");
        setLabelGraphic(playersIconLabel, "players", 16.0d, "#b8c3d3");
        setLabelGraphic(pingIconLabel, "signal", 16.0d, "#b8c3d3");
        setLabelGraphic(versionIconLabel, "cube-small", 16.0d, "#b8c3d3");
        setTooltip(copyIpIconButton, "Скопировать IP");
    }

    private void applyProfileState() {
        AuthAccount account = context() != null && state().isAuthenticated()
            ? state().getSession().getAccount()
            : null;
        LauncherConfig config = context() == null ? LauncherConfig.defaults() : state().getConfig();
        String username = account == null
            ? firstText(config.getUsername(), LauncherDefaults.defaultUsername())
            : firstText(account.getUsername(), config.getUsername(), LauncherDefaults.defaultUsername());

        if (sidebarProfileAvatarView != null) {
            sidebarProfileAvatarView.setImage(AvatarImages.forAccount(account));
        }
        setText(heroTitleLabel, username + "!");
        setText(sidebarProfileNameLabel, username);
        setText(sidebarProfileStatusLabel, account == null ? "Не в сети" : "В сети");
        setText(sidebarProfileRoleLabel, account == null ? "Player" : roleLabel(account.getRole()));
        refreshCleanGlassSidebar();
    }

    private void applyFallbackAvatar() {
        if (sidebarProfileAvatarView != null) {
            sidebarProfileAvatarView.setImage(AvatarImages.fallback());
        }
    }

    private synchronized void startNewsPolling() {
        if (newsExecutor != null) {
            return;
        }
        if (!isNewsCardAttachedToCurrentScene()) {
            Platform.runLater(this::startNewsPolling);
            return;
        }
        newsExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "launcher-news-refresh");
            thread.setDaemon(true);
            return thread;
        });
        newsExecutor.scheduleWithFixedDelay(
            this::refreshNewsAsync,
            0L,
            NEWS_REFRESH_SECONDS,
            TimeUnit.SECONDS
        );
    }

    private synchronized void stopNewsPolling() {
        ScheduledExecutorService executor = newsExecutor;
        newsExecutor = null;
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void refreshNewsAsync() {
        long requestId = newsRequestSequence.incrementAndGet();
        String manifestUrl = manifestUrl();
        if (manifestUrl.isEmpty()) {
            Platform.runLater(() -> {
                applyLauncherUpdateState(null, true);
                applyNewsResult(requestId, Collections.emptyList(), "Не указан manifest.json");
            });
            return;
        }
        try {
            LoadedManifest loadedManifest = manifestClient.load(manifestUrl);
            ModpackManifest manifest = loadedManifest.getManifest();
            List<NewsItem> items = newsItems(manifest);
            LauncherUpdateCandidate launcherUpdate = null;
            boolean launcherUpdateCheckSucceeded = true;
            try {
                launcherUpdate = findLauncherUpdate(loadedManifest);
            } catch (Exception exception) {
                launcherUpdateCheckSucceeded = false;
            }
            LauncherUpdateCandidate resolvedLauncherUpdate = launcherUpdate;
            boolean resolvedLauncherUpdateCheckSucceeded = launcherUpdateCheckSucceeded;
            Platform.runLater(() -> {
                applyManifestSummary(manifest);
                applyLauncherUpdateState(resolvedLauncherUpdate, resolvedLauncherUpdateCheckSucceeded);
                applyNewsResult(requestId, items, null);
            });
        } catch (Exception exception) {
            Platform.runLater(() -> {
                applyLauncherUpdateState(null, false);
                applyNewsResult(requestId, Collections.emptyList(), errorMessage(exception));
            });
        }
    }

    private void requestNewsRefresh() {
        if (Platform.isFxApplicationThread()) {
            applyNewsLoadingState();
        } else {
            Platform.runLater(this::applyNewsLoadingState);
        }
        ScheduledExecutorService executor = newsExecutor;
        if (executor != null) {
            executor.execute(this::refreshNewsAsync);
            return;
        }
        Thread thread = new Thread(this::refreshNewsAsync, "launcher-news-refresh-once");
        thread.setDaemon(true);
        thread.start();
    }

    private String manifestUrl() {
        LauncherConfig config = context() == null ? null : state().getConfig();
        String value = config == null ? "" : config.getManifestUrl();
        return value == null ? "" : value.trim();
    }

    private LauncherUpdateCandidate findLauncherUpdate(LoadedManifest loadedManifest) throws IOException {
        if (loadedManifest == null || loadedManifest.getManifest() == null) {
            return null;
        }

        return launcherUpdateService.findUpdate(loadedManifest, LauncherBrand.displayVersion());
    }

    private void applyLauncherUpdateState(LauncherUpdateCandidate update, boolean checkSucceeded) {
        if (!checkSucceeded) {
            if (!hasLauncherUpdate()) {
                applyPlayButtonState();
            }
            return;
        }

        availableLauncherUpdate = update;
        applyPlayButtonState();
        if (!syncInProgress && !launchInProgress && !launcherUpdateInProgress && hasLauncherUpdate()) {
            setSyncStatus("Обновите лаунчер", SYNC_STATUS_WORKING, "download", "#fbbf24");
        }
    }

    private void applyManifestSummary(ModpackManifest manifest) {
        if (manifest == null) {
            setText(buildVersionLabel, "Загрузка...");
            setText(forgeVersionBadgeLabel, "Forge " + UNKNOWN_VALUE);
            setText(updatedAtLabel, "Обновлено: " + UNKNOWN_VALUE);
            return;
        }

        String minecraftVersion = minecraftVersion(manifest);
        String forgeVersion = forgeVersion(manifest);
        String packName = packDisplayName(manifest);

        setText(buildVersionLabel, hasText(minecraftVersion) ? packName + " " + minecraftVersion : packName);
        setText(forgeVersionBadgeLabel, hasText(forgeVersion) ? "Forge " + forgeVersion : "Forge " + UNKNOWN_VALUE);
        setText(updatedAtLabel, "Обновлено: " + UPDATED_AT_FORMAT.format(LocalDateTime.now()));
    }

    private void applyNewsLoadingState() {
        if (newsListBox == null) {
            return;
        }
        newsListBox.getChildren().setAll(newsState(
            "Загрузка новостей",
            "Проверяем manifest и историю обновлений.",
            "refresh",
            "#fbbf24",
            "empty-state-loading",
            false
        ));
    }

    private void applyNewsResult(long requestId, List<NewsItem> items, String error) {
        if (!isNewsCardAttachedToCurrentScene()) {
            stopNewsPolling();
            return;
        }
        if (requestId != newsRequestSequence.get() || newsListBox == null) {
            return;
        }
        if (error != null && !error.trim().isEmpty()) {
            newsListBox.getChildren().setAll(newsState(
                "Новости недоступны",
                shorten(error, 96),
                "info",
                "#fda4af",
                "empty-state-error",
                true
            ));
            return;
        }
        if (items.isEmpty()) {
            newsListBox.getChildren().setAll(newsState(
                "Новостей пока нет",
                "Manifest загружен, но блок новостей пуст.",
                "bell",
                "#c4b5fd",
                "empty-state-empty",
                true
            ));
            return;
        }

        List<Node> rows = new ArrayList<Node>();
        for (NewsItem item : items) {
            rows.add(newsRow(item));
        }
        newsListBox.getChildren().setAll(rows);
    }

    private boolean isNewsCardAttachedToCurrentScene() {
        return newsListBox != null
            && newsListBox.getScene() != null
            && context() != null
            && newsListBox.getScene() == stage().getScene();
    }

    private Node newsState(
        String title,
        String detail,
        String iconName,
        String iconColor,
        String stateStyleClass,
        boolean retryVisible
    ) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("empty-state-title");
        titleLabel.setWrapText(true);

        Label detailLabel = new Label(detail);
        detailLabel.getStyleClass().add("empty-state-detail");
        detailLabel.setWrapText(true);

        VBox copy = new VBox(2.0d, titleLabel, detailLabel);
        HBox.setHgrow(copy, Priority.ALWAYS);

        HBox row = new HBox(10.0d, LauncherIcons.icon(iconName, 16.0d, iconColor), copy);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("empty-state");
        row.getStyleClass().add(stateStyleClass);
        if (retryVisible) {
            Button retryButton = new Button("Повторить");
            retryButton.setMnemonicParsing(false);
            retryButton.setGraphic(LauncherIcons.icon("refresh", 16.0d, "#bbf7d0"));
            retryButton.setGraphicTextGap(8.0d);
            retryButton.getStyleClass().add("news-retry-button");
            retryButton.setOnAction(event -> requestNewsRefresh());
            row.getChildren().add(retryButton);
        }
        return row;
    }

    private static Node newsRow(NewsItem item) {
        Label dateLabel = new Label(item.date());
        dateLabel.getStyleClass().add("news-date");
        dateLabel.setMinWidth(62.0d);
        dateLabel.setPrefWidth(62.0d);

        Label titleLabel = new Label(item.title());
        titleLabel.getStyleClass().add("news-title");
        titleLabel.setWrapText(true);

        Label textLabel = new Label(item.text());
        textLabel.getStyleClass().add("news-text");
        textLabel.setWrapText(true);

        VBox body = new VBox(5.0d, titleLabel, textLabel);
        HBox.setHgrow(body, Priority.ALWAYS);

        HBox row = new HBox(12.0d, dateLabel, body);
        row.setAlignment(Pos.TOP_LEFT);
        return row;
    }

    private static String packDisplayName(ModpackManifest manifest) {
        if (manifest == null) {
            return FALLBACK_MODPACK_NAME;
        }
        return firstText(
            manifest.getDisplayName(),
            manifest.getName(),
            normalizeManifestId(manifest.getId()),
            FALLBACK_MODPACK_NAME
        );
    }

    private static String normalizeManifestId(String id) {
        if (!hasText(id)) {
            return "";
        }
        String normalized = id.trim();
        if ("mc-rpg".equalsIgnoreCase(normalized)) {
            return FALLBACK_MODPACK_NAME;
        }

        String[] parts = normalized.replace('_', '-').split("-");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!hasText(part)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private static String minecraftVersion(ModpackManifest manifest) {
        MinecraftBootstrapSettings minecraft = manifest == null ? null : manifest.getMinecraft();
        return minecraft == null ? "" : firstText(minecraft.getVersion());
    }

    private static String forgeVersion(ModpackManifest manifest) {
        MinecraftBootstrapSettings minecraft = manifest == null ? null : manifest.getMinecraft();
        return minecraft == null ? "" : firstText(minecraft.getForgeVersion());
    }

    private static String roleLabel(String role) {
        String normalized = firstText(role, "Player");
        String lower = normalized.toLowerCase(Locale.ROOT);
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

    private static List<NewsItem> newsItems(ModpackManifest manifest) {
        if (manifest == null) {
            return Collections.emptyList();
        }

        List<NewsItem> items = new ArrayList<NewsItem>();
        addNewsItem(items, manifest.getNews(), manifest.getVersion());
        addNewsItem(items, manifest.getChangelog(), manifest.getVersion());
        for (ModpackNews news : manifest.getHistory()) {
            addNewsItem(items, news, manifest.getVersion());
        }
        if (items.size() <= NEWS_ITEM_LIMIT) {
            return items;
        }
        return new ArrayList<NewsItem>(items.subList(0, NEWS_ITEM_LIMIT));
    }

    private static void addNewsItem(List<NewsItem> items, ModpackNews news, String manifestVersion) {
        if (news == null || !news.hasContent()) {
            return;
        }
        NewsItem item = toNewsItem(news, manifestVersion);
        for (NewsItem existing : items) {
            if (existing.equals(item)) {
                return;
            }
        }
        items.add(item);
    }

    private static NewsItem toNewsItem(ModpackNews news, String manifestVersion) {
        String date = firstText(news.getDate(), news.getVersion(), manifestVersion, "-");
        String title = firstText(news.getTitle(), news.getVersion(), "Обновление");
        String text = firstText(
            news.getBody(),
            firstListText(news.getImportant()),
            firstListText(news.getHighlights()),
            firstListText(news.getNewMods()),
            firstListText(news.getRemovedMods()),
            "Изменения сборки доступны для установки."
        );
        return new NewsItem(shorten(date, 18), shorten(title, 44), shorten(text, 72));
    }

    private static String firstListText(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.get(0);
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String shorten(String value, int maxLength) {
        String text = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 1)).trim() + "...";
    }

    private void applySyncIdleState() {
        lastSyncProgress = 0.0d;
        setSyncProgress(0.0d);
        setText(syncProgressLabel, "0%");
        setSyncStatus("Готово", SYNC_STATUS_OK, "check-circle", "#86efac");
        setSyncDetail("0 файлов · 0 Б");
    }

    private void applySyncPreparingState() {
        lastSyncProgress = 0.0d;
        setSyncProgress(0.0d);
        setText(syncProgressLabel, "0%");
        setSyncStatus("Подготовка", SYNC_STATUS_WORKING, "refresh", "#fbbf24");
        setSyncDetail("Проверка изменений");
    }

    private void applySyncProgress(long requestId, ModpackSyncProgress progress) {
        if (requestId != syncRequestSequence.get() || progress == null || !isSyncCardAttachedToCurrentScene()) {
            return;
        }

        double progressValue = progress.getProgress();
        if (progressValue >= 0.0d) {
            double clamped = clamp(progressValue);
            lastSyncProgress = clamped;
            setSyncProgress(clamped);
            setText(syncProgressLabel, Math.round(clamped * 100.0d) + "%");
        } else {
            double fallback = fallbackSyncProgress(progress.getPhase(), lastSyncProgress);
            lastSyncProgress = fallback;
            setSyncProgress(fallback);
            setText(syncProgressLabel, Math.round(fallback * 100.0d) + "%");
        }
        setSyncStatus(describeSyncProgress(progress), SYNC_STATUS_WORKING, "sync", "#fbbf24");
        setSyncDetail(describeSyncDetail(progress));
    }

    private void setSyncStatus(String text, String statusStyleClass, String iconName, String iconColor) {
        setText(syncStatusLabel, text);
        setLabelGraphic(syncStatusLabel, iconName, 16.0d, iconColor);
        setSyncStatusStyle(syncStatusLabel, statusStyleClass);
        applySyncVisualState(statusStyleClass);
    }

    private void setSyncDetail(String text) {
        setText(syncDetailLabel, text);
    }

    private boolean isSyncCardAttachedToCurrentScene() {
        return syncProgressLabel != null
            && syncProgressLabel.getScene() != null
            && context() != null
            && syncProgressLabel.getScene() == stage().getScene();
    }

    private void setSyncProgress(double progress) {
        if (syncProgressArc != null) {
            syncProgressArc.setLength(-360.0d * clamp(progress));
        }
    }

    private void applySyncVisualState(String statusStyleClass) {
        setSyncStatusStyle(syncProgressArc, statusStyleClass);
        setSyncStatusStyle(syncProgressLabel, statusStyleClass);
        if (SYNC_STATUS_WORKING.equals(statusStyleClass)) {
            startSyncPulse();
            return;
        }
        stopSyncPulse();
    }

    private void startSyncPulse() {
        if (syncProgressArc == null) {
            return;
        }
        if (syncPulseTransition == null) {
            syncPulseTransition = new FadeTransition(Duration.millis(900.0d), syncProgressArc);
            syncPulseTransition.setFromValue(0.62d);
            syncPulseTransition.setToValue(1.0d);
            syncPulseTransition.setAutoReverse(true);
            syncPulseTransition.setCycleCount(Animation.INDEFINITE);
        }
        if (syncPulseTransition.getStatus() != Animation.Status.RUNNING) {
            syncPulseTransition.play();
        }
    }

    private void stopSyncPulse() {
        if (syncPulseTransition != null) {
            syncPulseTransition.stop();
        }
        if (syncProgressArc != null) {
            syncProgressArc.setOpacity(1.0d);
        }
    }

    private static double fallbackSyncProgress(ModpackSyncProgress.Phase phase, double currentProgress) {
        if (phase == ModpackSyncProgress.Phase.PREPARING) {
            return 0.0d;
        }
        return Math.max(clamp(currentProgress), 0.9d);
    }

    private static String describeSyncProgress(ModpackSyncProgress progress) {
        if (progress.getPhase() == ModpackSyncProgress.Phase.DOWNLOADING) {
            if (progress.getDownloadFiles() > 0) {
                return "Скачивание " + progress.getDownloadedFiles() + " / " + progress.getDownloadFiles();
            }
            return "Файлы актуальны";
        }
        if (progress.getPhase() == ModpackSyncProgress.Phase.CHECKING && progress.getTotalFiles() > 0) {
            return "Проверка " + progress.getCheckedFiles() + " / " + progress.getTotalFiles();
        }
        if (progress.getMessage() != null && !progress.getMessage().trim().isEmpty()) {
            return progress.getMessage().trim();
        }
        return "Синхронизация";
    }

    private static String describeSyncDetail(ModpackSyncProgress progress) {
        if (progress.getPhase() == ModpackSyncProgress.Phase.DOWNLOADING) {
            int totalFiles = Math.max(0, progress.getDownloadFiles());
            if (totalFiles == 0) {
                return "Изменений нет · 0 Б";
            }
            int downloadedFiles = Math.min(Math.max(0, progress.getDownloadedFiles()), totalFiles);
            long totalBytes = Math.max(0L, progress.getTotalDownloadBytes());
            if (totalBytes > 0L) {
                return downloadedFiles + " / " + totalFiles + " файлов · "
                    + formatBytes(progress.getDownloadedBytes()) + " / " + formatBytes(totalBytes);
            }
            return downloadedFiles + " / " + totalFiles + " файлов";
        }
        if (progress.getPhase() == ModpackSyncProgress.Phase.CHECKING && progress.getTotalFiles() > 0) {
            return "Проверено: " + progress.getCheckedFiles() + " / " + progress.getTotalFiles() + " файлов";
        }
        if (progress.getPhase() == ModpackSyncProgress.Phase.COMPLETE) {
            int changedFiles = progress.getDownloadedFiles();
            if (changedFiles == 0) {
                return "Изменений нет · 0 Б";
            }
            return "Изменено: " + formatFileCount(changedFiles) + " · " + formatBytes(progress.getDownloadedBytes());
        }
        return "Изменений: " + progress.getDownloadFiles() + " файлов · " + formatBytes(progress.getTotalDownloadBytes());
    }

    private static String describeSyncResult(ModpackSyncResult result) {
        if (result == null) {
            return "Изменений: -";
        }
        int changedFiles = Math.max(0, result.getDownloadedFiles()) + Math.max(0, result.getRemovedFiles());
        if (changedFiles == 0) {
            return "Изменений нет · 0 Б";
        }
        return "Изменено: " + formatFileCount(changedFiles) + " · " + formatBytes(result.getDownloadedBytes());
    }

    private static String formatFileCount(int count) {
        return count + " " + fileWord(count);
    }

    private static String fileWord(int count) {
        int abs = Math.abs(count);
        int lastTwo = abs % 100;
        if (lastTwo >= 11 && lastTwo <= 14) {
            return "файлов";
        }
        return switch (abs % 10) {
            case 1 -> "файл";
            case 2, 3, 4 -> "файла";
            default -> "файлов";
        };
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " Б";
        }
        double value = bytes / 1024.0d;
        String unit = "КБ";
        if (value >= 1024.0d) {
            value /= 1024.0d;
            unit = "МБ";
        }
        if (value >= 1024.0d) {
            value /= 1024.0d;
            unit = "ГБ";
        }
        return String.format(Locale.US, "%.1f %s", value, unit);
    }

    private static double clamp(double value) {
        if (Double.isNaN(value) || value < 0.0d) {
            return 0.0d;
        }
        return Math.min(1.0d, value);
    }

    private static String resolveLauncherUpdateFailureMessage(Throwable exception, LauncherUpdateCandidate update) {
        String message = errorMessage(exception);
        String normalized = message.toLowerCase(Locale.ROOT);
        String downloadUrl = update == null || update.getDownloadUrl() == null
            ? "не указан"
            : update.getDownloadUrl().toString();

        if (normalized.contains("http 404")) {
            return "Файл обновления не найден: " + downloadUrl
                + ". Проверьте launcherUpdate.url в manifest.";
        }
        if (normalized.contains("timed out")) {
            return "Сервер обновлений не ответил вовремя: " + downloadUrl + ".";
        }
        if (normalized.contains("connection refused")) {
            return "Сервер обновлений отклонил соединение: " + downloadUrl + ".";
        }
        return "Не удалось обновить лаунчер: " + message;
    }

    private static String errorMessage(Throwable throwable) {
        if (throwable == null) {
            return "неизвестная ошибка.";
        }
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.trim().isEmpty()
            ? current.getClass().getSimpleName()
            : message.trim();
    }

    private static boolean isSessionFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof AuthSessionExpiredException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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

    private record LaunchStartResult(long pid, Path logFile, ModpackSyncResult syncResult) {
    }

    private record NewsItem(String date, String title, String text) {
    }

    private void applyServerAddress() {
        if (serverAddressLabel != null) {
            serverAddressLabel.setText(serverAddress());
        }
    }

    private void applyServerEndpointFromConfig(LauncherConfig config) {
        if (config == null) {
            serverHost = LauncherConfig.DEFAULT_SERVER_HOST;
            serverPort = LauncherConfig.DEFAULT_SERVER_PORT;
        } else {
            String configuredHost = config.getServerHost();
            serverHost = configuredHost == null || configuredHost.trim().isEmpty()
                ? LauncherConfig.DEFAULT_SERVER_HOST
                : configuredHost.trim();
            serverPort = config.getServerPort() > 0 ? config.getServerPort() : LauncherConfig.DEFAULT_SERVER_PORT;
        }
        applyServerAddress();
    }

    private String serverAddress() {
        return serverHost + ":" + serverPort;
    }

    private synchronized void startServerStatusPolling() {
        if (serverStatusExecutor != null) {
            return;
        }
        if (!isServerCardAttachedToCurrentScene()) {
            Platform.runLater(this::startServerStatusPolling);
            return;
        }
        serverStatusExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "launcher-server-status");
            thread.setDaemon(true);
            return thread;
        });
        serverStatusExecutor.scheduleWithFixedDelay(
            this::refreshServerStatus,
            0L,
            SERVER_STATUS_REFRESH_SECONDS,
            TimeUnit.SECONDS
        );
    }

    private synchronized void stopServerStatusPolling() {
        ScheduledExecutorService executor = serverStatusExecutor;
        serverStatusExecutor = null;
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void refreshServerStatus() {
        long requestId = serverStatusRequestSequence.incrementAndGet();
        String host = serverHost;
        int port = serverPort;
        try {
            MinecraftServerStatusProbe.ServerStatus status =
                MinecraftServerStatusProbe.probe(host, port, SERVER_STATUS_TIMEOUT_MS);
            Platform.runLater(() -> applyServerStatusResult(requestId, status, null));
        } catch (Exception exception) {
            Platform.runLater(() -> applyServerStatusResult(requestId, null, exception));
        }
    }

    private void applyServerStatusResult(
        long requestId,
        MinecraftServerStatusProbe.ServerStatus status,
        Exception exception
    ) {
        if (!isServerCardAttachedToCurrentScene()) {
            stopServerStatusPolling();
            return;
        }
        if (requestId != serverStatusRequestSequence.get()) {
            return;
        }
        if (status == null || exception != null) {
            applyOfflineServerStatus();
            return;
        }
        applyOnlineServerStatus(status);
    }

    private boolean isServerCardAttachedToCurrentScene() {
        return serverStatusLabel != null
            && serverStatusLabel.getScene() != null
            && context() != null
            && serverStatusLabel.getScene() == stage().getScene();
    }

    private void applyCheckingServerStatus() {
        setServerStatus("Проверяем", STATUS_CHECKING, "refresh", "#fbbf24");
        setText(playersValueLabel, UNKNOWN_VALUE);
        setText(pingValueLabel, UNKNOWN_VALUE);
        setText(versionValueLabel, UNKNOWN_VALUE);
    }

    private void applyOnlineServerStatus(MinecraftServerStatusProbe.ServerStatus status) {
        setServerStatus("Онлайн", STATUS_ONLINE, "check-circle", "#86efac");
        setText(playersValueLabel, formatPlayers(status.getOnlinePlayers(), status.getMaxPlayers()));
        setText(pingValueLabel, status.getPingMs() + " мс");
        setText(versionValueLabel, valueOrUnknown(status.getVersionName()));
    }

    private void applyOfflineServerStatus() {
        setServerStatus("Недоступен", STATUS_OFFLINE, "info", "#fda4af");
        setText(playersValueLabel, UNKNOWN_VALUE);
        setText(pingValueLabel, UNKNOWN_VALUE);
        setText(versionValueLabel, UNKNOWN_VALUE);
    }

    private void setServerStatus(String text, String statusStyleClass, String iconName, String iconColor) {
        setText(serverStatusLabel, text);
        setLabelGraphic(serverStatusLabel, iconName, 16.0d, iconColor);
        applyServerStatusStyle(statusStyleClass);
    }

    private void applyServerStatusStyle(String statusStyleClass) {
        setStatusStyle(serverStatusDot, statusStyleClass);
        setStatusStyle(serverStatusLabel, statusStyleClass);
    }

    private static void setStatusStyle(Node node, String statusStyleClass) {
        if (node == null) {
            return;
        }
        node.getStyleClass().removeAll(STATUS_ONLINE, STATUS_OFFLINE, STATUS_CHECKING);
        node.getStyleClass().add(statusStyleClass);
    }

    private static void setSyncStatusStyle(Node node, String statusStyleClass) {
        if (node == null) {
            return;
        }
        node.getStyleClass().removeAll(SYNC_STATUS_OK, SYNC_STATUS_WORKING, SYNC_STATUS_ERROR);
        node.getStyleClass().add(statusStyleClass);
    }

    private static void setText(Label label, String value) {
        if (label != null) {
            label.setText(value);
        }
    }

    private static String formatPlayers(int onlinePlayers, int maxPlayers) {
        if (onlinePlayers < 0 && maxPlayers < 0) {
            return UNKNOWN_VALUE;
        }
        if (maxPlayers < 0) {
            return onlinePlayers < 0 ? UNKNOWN_VALUE : Integer.toString(onlinePlayers);
        }
        String onlineValue = onlinePlayers < 0 ? UNKNOWN_VALUE : Integer.toString(onlinePlayers);
        return onlineValue + " / " + maxPlayers;
    }

    private static String valueOrUnknown(String value) {
        return value == null || value.trim().isEmpty() ? UNKNOWN_VALUE : value.trim();
    }

    private static void setGraphic(Button button, String iconName, double size, String color) {
        if (button != null) {
            button.setGraphic(LauncherIcons.icon(iconName, size, color));
            button.setGraphicTextGap(8.0d);
        }
    }

    private static void setLabelGraphic(Label label, String iconName, double size, String color) {
        if (label != null) {
            label.setGraphic(LauncherIcons.icon(iconName, size, color));
            label.setContentDisplay(ContentDisplay.LEFT);
            label.setGraphicTextGap(8.0d);
        }
    }

    private static void setTooltip(Button button, String text) {
        if (button != null) {
            button.setTooltip(new Tooltip(text));
        }
    }

    private static void openExternalUri(URI uri) throws IOException {
        if (!Desktop.isDesktopSupported()) {
            throw new IOException("Открытие ссылок через рабочий стол не поддерживается.");
        }
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.BROWSE)) {
            throw new IOException("Открытие ссылок не поддерживается.");
        }
        desktop.browse(uri);
    }

}
