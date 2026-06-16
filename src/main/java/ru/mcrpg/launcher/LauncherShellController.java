package ru.mcrpg.launcher;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Arc;
import javafx.util.Duration;
import ru.mcrpg.launcher.ui.AvatarImages;
import ru.mcrpg.launcher.ui.LauncherIcons;
import ru.mcrpg.launcher.ui.MicroInteractions;

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
    private static final String PLAY_BUTTON_READY_STYLE = "play-ready";
    private static final String PLAY_BUTTON_BUSY_STYLE = "play-busy";
    private static final String PLAY_BUTTON_RIPPLE_STYLE = "play-ripple";
    private static final DateTimeFormatter UPDATED_AT_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm");

    private final ModpackManifestClient manifestClient = new ModpackManifestClient();
    private final ModpackSyncService modpackSyncService = new ModpackSyncService(manifestClient);
    private final LauncherUpdateService launcherUpdateService = new LauncherUpdateService();
    private final LaunchCommandBuilder launchCommandBuilder = new LaunchCommandBuilder();
    private final MinecraftServerListWriter serverListWriter = new MinecraftServerListWriter();
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
    private Timeline syncRingRotation;
    private Timeline syncProgressTimeline;
    private Timeline heroBreathingTimeline;
    private Timeline heroSweepTimeline;
    private Timeline heroPortalEnergyTimeline;
    private Timeline playButtonFeedbackTimeline;
    private Timeline playButtonPulseTimeline;
    private List<NewsItem> currentNewsItems = Collections.emptyList();
    private StackPane newsOverlay;
    private StackPane launcherUpdateOverlay;
    private Label launcherUpdateTitleLabel;
    private Label launcherUpdateDetailLabel;
    private Label launcherUpdatePercentLabel;
    private ProgressBar launcherUpdateProgressBar;

    @FXML
    private Label brandLogoLabel;

    @FXML
    private Label sidebarVersionLabel;

    @FXML
    private StackPane launcherShellRoot;

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
    private Arc syncActivityArc;

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
    private Node heroCard;

    @FXML
    private Node serverCard;

    @FXML
    private Node syncCard;

    @FXML
    private Node newsCard;

    @FXML
    private Node syncProgressWrap;

    @FXML
    private Node heroPortalGlow;

    @FXML
    private Node heroPortalCore;

    @FXML
    private Node heroPortalSweep;

    @FXML
    private Node heroParallaxLayer;

    @FXML
    private Node heroPortalShardOne;

    @FXML
    private Node heroPortalShardTwo;

    @FXML
    private Node heroPortalShardThree;

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
        configureMicroInteractions();
        configureHeroPortalMotion();
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
        if (Boolean.getBoolean(LauncherShellApplication.UPDATE_COMPLETE_PROPERTY)) {
            Platform.runLater(this::showCompletedLauncherUpdate);
        }
        Platform.runLater(this::startServerStatusPolling);
        Platform.runLater(this::startNewsPolling);
    }

    private void configureMicroInteractions() {
        MicroInteractions.installHoverLift(serverCard);
        MicroInteractions.installHoverLift(syncCard);
        MicroInteractions.installHoverLift(newsCard);
        MicroInteractions.installHoverLift(playButton, -1.0d, 1.01d);
        MicroInteractions.installHoverLift(copyIpButton, -1.0d, 1.005d);
        MicroInteractions.installHoverLift(meetingButton, -1.0d, 1.005d);
        MicroInteractions.playEntrance(heroCard, serverCard, syncCard, newsCard, updatedAtLabel);
    }

    private void configureHeroPortalMotion() {
        if (heroPortalGlow != null) {
            heroBreathingTimeline = new Timeline(
                new KeyFrame(
                    Duration.ZERO,
                    new KeyValue(heroPortalGlow.opacityProperty(), 0.72d, Interpolator.EASE_BOTH),
                    new KeyValue(heroPortalGlow.scaleXProperty(), 0.96d, Interpolator.EASE_BOTH),
                    new KeyValue(heroPortalGlow.scaleYProperty(), 0.96d, Interpolator.EASE_BOTH)
                ),
                new KeyFrame(
                    Duration.millis(2600.0d),
                    new KeyValue(heroPortalGlow.opacityProperty(), 1.0d, Interpolator.EASE_BOTH),
                    new KeyValue(heroPortalGlow.scaleXProperty(), 1.08d, Interpolator.EASE_BOTH),
                    new KeyValue(heroPortalGlow.scaleYProperty(), 1.08d, Interpolator.EASE_BOTH)
                ),
                new KeyFrame(
                    Duration.millis(5200.0d),
                    new KeyValue(heroPortalGlow.opacityProperty(), 0.72d, Interpolator.EASE_BOTH),
                    new KeyValue(heroPortalGlow.scaleXProperty(), 0.96d, Interpolator.EASE_BOTH),
                    new KeyValue(heroPortalGlow.scaleYProperty(), 0.96d, Interpolator.EASE_BOTH)
                )
            );
            heroBreathingTimeline.setCycleCount(Animation.INDEFINITE);
            heroBreathingTimeline.play();
        }

        if (heroPortalCore != null && heroPortalShardOne != null
            && heroPortalShardTwo != null && heroPortalShardThree != null) {
            heroPortalEnergyTimeline = new Timeline(
                new KeyFrame(
                    Duration.ZERO,
                    new KeyValue(heroPortalCore.scaleXProperty(), 0.98d, Interpolator.EASE_BOTH),
                    new KeyValue(heroPortalCore.scaleYProperty(), 0.98d, Interpolator.EASE_BOTH),
                    new KeyValue(heroPortalCore.opacityProperty(), 0.78d, Interpolator.EASE_BOTH),
                    new KeyValue(heroPortalShardOne.translateYProperty(), 0.0d, Interpolator.EASE_BOTH),
                    new KeyValue(heroPortalShardTwo.translateYProperty(), 0.0d, Interpolator.EASE_BOTH),
                    new KeyValue(heroPortalShardThree.translateYProperty(), 0.0d, Interpolator.EASE_BOTH)
                ),
                new KeyFrame(
                    Duration.millis(1800.0d),
                    new KeyValue(heroPortalCore.scaleXProperty(), 1.04d, Interpolator.EASE_BOTH),
                    new KeyValue(heroPortalCore.scaleYProperty(), 1.04d, Interpolator.EASE_BOTH),
                    new KeyValue(heroPortalCore.opacityProperty(), 1.0d, Interpolator.EASE_BOTH),
                    new KeyValue(heroPortalShardOne.translateYProperty(), -8.0d, Interpolator.EASE_BOTH),
                    new KeyValue(heroPortalShardTwo.translateYProperty(), 7.0d, Interpolator.EASE_BOTH),
                    new KeyValue(heroPortalShardThree.translateYProperty(), -5.0d, Interpolator.EASE_BOTH)
                ),
                new KeyFrame(
                    Duration.millis(3600.0d),
                    new KeyValue(heroPortalCore.scaleXProperty(), 0.98d, Interpolator.EASE_BOTH),
                    new KeyValue(heroPortalCore.scaleYProperty(), 0.98d, Interpolator.EASE_BOTH),
                    new KeyValue(heroPortalCore.opacityProperty(), 0.78d, Interpolator.EASE_BOTH),
                    new KeyValue(heroPortalShardOne.translateYProperty(), 0.0d, Interpolator.EASE_BOTH),
                    new KeyValue(heroPortalShardTwo.translateYProperty(), 0.0d, Interpolator.EASE_BOTH),
                    new KeyValue(heroPortalShardThree.translateYProperty(), 0.0d, Interpolator.EASE_BOTH)
                )
            );
            heroPortalEnergyTimeline.setCycleCount(Animation.INDEFINITE);
            heroPortalEnergyTimeline.play();
        }

        if (heroPortalSweep != null) {
            heroPortalSweep.setTranslateX(-64.0d);
            heroSweepTimeline = new Timeline(
                new KeyFrame(
                    Duration.ZERO,
                    new KeyValue(heroPortalSweep.translateXProperty(), -64.0d, Interpolator.EASE_BOTH),
                    new KeyValue(heroPortalSweep.opacityProperty(), 0.0d, Interpolator.EASE_BOTH)
                ),
                new KeyFrame(
                    Duration.millis(1300.0d),
                    new KeyValue(heroPortalSweep.opacityProperty(), 0.58d, Interpolator.EASE_BOTH)
                ),
                new KeyFrame(
                    Duration.millis(3400.0d),
                    new KeyValue(heroPortalSweep.translateXProperty(), 64.0d, Interpolator.EASE_BOTH),
                    new KeyValue(heroPortalSweep.opacityProperty(), 0.0d, Interpolator.EASE_BOTH)
                ),
                new KeyFrame(Duration.millis(6400.0d))
            );
            heroSweepTimeline.setCycleCount(Animation.INDEFINITE);
            heroSweepTimeline.play();
        }

        if (heroCard != null) {
            heroCard.setOnMouseMoved(event -> applyHeroParallax(event.getX(), event.getY()));
            heroCard.setOnMouseExited(event -> resetHeroParallax());
        }
    }

    private void applyHeroParallax(double mouseX, double mouseY) {
        if (heroCard == null) {
            return;
        }
        double width = Math.max(1.0d, heroCard.getBoundsInLocal().getWidth());
        double height = Math.max(1.0d, heroCard.getBoundsInLocal().getHeight());
        double offsetX = ((mouseX / width) - 0.5d) * 12.0d;
        double offsetY = ((mouseY / height) - 0.5d) * 8.0d;
        if (heroPortalGlow != null) {
            heroPortalGlow.setTranslateX(offsetX);
            heroPortalGlow.setTranslateY(offsetY * 0.7d);
        }
        if (heroPortalCore != null) {
            heroPortalCore.setTranslateX(offsetX * 0.40d);
            heroPortalCore.setTranslateY(offsetY * 0.34d);
        }
        if (heroPortalSweep != null) {
            heroPortalSweep.setTranslateY(offsetY * 0.34d);
        }
        if (heroParallaxLayer != null) {
            heroParallaxLayer.setTranslateX(offsetX * 0.35d);
            heroParallaxLayer.setTranslateY(offsetY * 0.35d);
        }
        applyShardParallax(heroPortalShardOne, offsetX * 0.25d);
        applyShardParallax(heroPortalShardTwo, offsetX * 0.42d);
        applyShardParallax(heroPortalShardThree, offsetX * 0.32d);
    }

    private void resetHeroParallax() {
        if (heroPortalGlow != null) {
            heroPortalGlow.setTranslateX(0.0d);
            heroPortalGlow.setTranslateY(0.0d);
        }
        if (heroParallaxLayer != null) {
            heroParallaxLayer.setTranslateX(0.0d);
            heroParallaxLayer.setTranslateY(0.0d);
        }
        if (heroPortalCore != null) {
            heroPortalCore.setTranslateX(0.0d);
            heroPortalCore.setTranslateY(0.0d);
        }
        if (heroPortalSweep != null) {
            heroPortalSweep.setTranslateY(0.0d);
        }
        applyShardParallax(heroPortalShardOne, 0.0d);
        applyShardParallax(heroPortalShardTwo, 0.0d);
        applyShardParallax(heroPortalShardThree, 0.0d);
    }

    private static void applyShardParallax(Node shard, double offsetX) {
        if (shard == null) {
            return;
        }
        shard.setTranslateX(offsetX);
    }

    @FXML
    private void play() {
        playButtonClickFeedback();
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
        launchInProgress = true;
        syncInProgress = launchConfig.isUpdateFilesBeforeLaunch();
        setPlayButtonBusy(true);
        if (syncInProgress) {
            applySyncPreparingState();
        } else {
            applyLaunchStep("Запускаем Minecraft", "Проверка файлов модпака отключена в настройках", "play", "#fbbf24");
        }

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
        ModpackSyncResult syncResult = null;
        if (launchConfig.isUpdateFilesBeforeLaunch()) {
            ModpackSyncService.LaunchModpackSyncPreview launchPreview = previewModpackSync(launchConfig, syncRequestId);
            ModpackSyncPreviewResult previewResult = launchPreview.getPreviewResult();
            if (!confirmModpackSync(previewResult)) {
                throw new LaunchCancelledException();
            }
            if (previewResult != null && previewResult.getResolvedConfig() != null) {
                launchConfig = previewResult.getResolvedConfig().copy();
                launchConfig.setUsername(session.getAccount().getUsername());
                launchConfig.setUpdateFilesBeforeLaunch(true);
            }

            syncResult = modpackSyncService.sync(
                launchPreview,
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
        }

        Platform.runLater(() -> applyLaunchStep("Настраиваем сервер", "Добавляем ObsidianGate в список серверов Minecraft", "server", "#fbbf24"));
        disableXaeroUpdateNotifications(launchConfig);
        serverListWriter.upsert(launchConfig);

        Platform.runLater(() -> applyLaunchStep("Проверяем сессию", "Обновляем вход в аккаунт", "profile", "#fbbf24"));
        AuthSession refreshedSession = context().getAuthService().refreshIfNeeded(launchConfig, session);
        if (refreshedSession == null || refreshedSession.getAccount() == null) {
            throw new AuthSessionExpiredException("Сессия истекла. Войдите в аккаунт снова.", null);
        }
        state().setSession(refreshedSession);
        launchConfig.setUsername(refreshedSession.getAccount().getUsername());

        Platform.runLater(() -> applyLaunchStep("Получаем билет входа", "Готовим одноразовый ticket для сервера", "shield", "#fbbf24"));
        GameTicket ticket = context().getAuthService().createGameTicket(launchConfig, refreshedSession);
        Platform.runLater(() -> applyLaunchStep("Запускаем Minecraft", "Передаем профиль и session.json клиенту", "play", "#fbbf24"));
        Path sessionFile = context().getSessionFileWriter().write(launchConfig, ticket);
        LaunchIdentity identity = LaunchIdentity.authenticated(
            ticket.getUsername(),
            ticket.getUuid(),
            refreshedSession.getAccessToken(),
            sessionFile
        );
        List<String> command = launchCommandBuilder.build(launchConfig, identity);
        Path logFile = gameLogFile(launchConfig);
        Process process;
        try {
            process = startGameProcess(launchConfig, command);
        } catch (IOException exception) {
            throw new LaunchFailureException(exception.getMessage(), logFile, exception);
        }
        if (process.waitFor(3L, TimeUnit.SECONDS)) {
            throw new LaunchFailureException("Minecraft завершился сразу с кодом " + process.exitValue() + ".", logFile, null);
        }
        return new LaunchStartResult(process.pid(), logFile, syncResult);
    }

    private static void disableXaeroUpdateNotifications(LauncherConfig launchConfig) throws IOException {
        if (launchConfig == null || !hasText(launchConfig.getGameDirectory())) {
            return;
        }
        XaeroUpdateNotificationOptions.disable(Paths.get(launchConfig.getGameDirectory()));
    }

    private ModpackSyncService.LaunchModpackSyncPreview previewModpackSync(
        LauncherConfig launchConfig,
        long syncRequestId
    ) throws IOException {
        Platform.runLater(() -> applyLaunchStep("Проверяем файлы", "Смотрим, что изменилось в модпаке", "sync", "#fbbf24"));
        return modpackSyncService.previewForLaunch(
            launchConfig,
            message -> {
            },
            progress -> Platform.runLater(() -> applySyncProgress(syncRequestId, progress))
        );
    }

    private boolean confirmModpackSync(ModpackSyncPreviewResult previewResult) throws Exception {
        if (previewResult == null || previewResult.getDownloadFiles() <= 0) {
            return true;
        }

        CompletableFuture<Boolean> answer = new CompletableFuture<Boolean>();
        Platform.runLater(() -> {
            try {
                showModpackSyncPreviewOverlay(previewResult, answer);
            } catch (RuntimeException exception) {
                answer.completeExceptionally(exception);
            }
        });
        return answer.get().booleanValue();
    }

    private void showModpackSyncPreviewOverlay(
        ModpackSyncPreviewResult previewResult,
        CompletableFuture<Boolean> answer
    ) {
        if (launcherShellRoot == null) {
            answer.complete(true);
            return;
        }

        Label titleLabel = new Label("Нужно обновить файлы модпака");
        titleLabel.getStyleClass().add("modpack-preview-title");

        Label summaryLabel = new Label(
            "Будет скачано: " + formatFileCount(previewResult.getDownloadFiles())
                + " · " + formatBytes(previewResult.getDownloadBytes())
                + "\nУже актуально: " + formatFileCount(previewResult.getReusedFiles())
        );
        summaryLabel.setWrapText(true);
        summaryLabel.getStyleClass().add("modpack-preview-summary");

        ScrollPane detailsArea = createModpackPreviewDetails(previewResult);

        Button detailsButton = new Button("Свернуть детали");
        detailsButton.setMnemonicParsing(false);
        detailsButton.getStyleClass().add("modpack-preview-secondary-button");
        detailsButton.setGraphic(LauncherIcons.icon("chevron-up", 15.0d, "#d8b4fe"));
        detailsButton.setOnAction(event -> {
            boolean visible = detailsArea.isVisible();
            detailsArea.setVisible(!visible);
            detailsArea.setManaged(!visible);
            detailsButton.setText(visible ? "Развернуть детали" : "Свернуть детали");
            detailsButton.setGraphic(LauncherIcons.icon(visible ? "chevron-down" : "chevron-up", 15.0d, "#d8b4fe"));
        });

        Button continueButton = new Button("Продолжить");
        continueButton.setMnemonicParsing(false);
        continueButton.getStyleClass().add("modpack-preview-primary-button");
        continueButton.setGraphic(LauncherIcons.icon("download", 15.0d, "#ffffff"));

        Button cancelButton = new Button("Отмена");
        cancelButton.setMnemonicParsing(false);
        cancelButton.getStyleClass().add("modpack-preview-secondary-button");

        HBox actions = new HBox(10.0d, detailsButton, new Region(), cancelButton, continueButton);
        HBox.setHgrow(actions.getChildren().get(1), Priority.ALWAYS);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.getStyleClass().add("modpack-preview-actions");

        VBox panel = new VBox(12.0d, titleLabel, summaryLabel, detailsArea, actions);
        panel.getStyleClass().add("modpack-preview-panel");
        panel.setMaxWidth(460.0d);
        panel.setMaxHeight(Region.USE_PREF_SIZE);

        StackPane overlay = new StackPane(panel);
        overlay.getStyleClass().add("modpack-preview-overlay");
        overlay.setFocusTraversable(true);
        overlay.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                event.consume();
                completeModpackPreview(answer, overlay, false);
            }
        });

        cancelButton.setOnAction(event -> completeModpackPreview(answer, overlay, false));
        continueButton.setOnAction(event -> completeModpackPreview(answer, overlay, true));

        launcherShellRoot.getChildren().add(overlay);
        Platform.runLater(overlay::requestFocus);
    }

    private void completeModpackPreview(CompletableFuture<Boolean> answer, StackPane overlay, boolean confirmed) {
        if (overlay != null && launcherShellRoot != null) {
            launcherShellRoot.getChildren().remove(overlay);
        }
        answer.complete(Boolean.valueOf(confirmed));
    }

    private static ScrollPane createModpackPreviewDetails(ModpackSyncPreviewResult previewResult) {
        VBox list = new VBox(8.0d);
        list.getStyleClass().add("modpack-preview-list");

        for (ModpackSyncPreviewEntry entry : previewResult.getEntries()) {
            if (entry.getState() != ModpackSyncPreviewEntry.State.DOWNLOAD) {
                continue;
            }
            list.getChildren().add(createModpackPreviewFileRow(entry));
        }

        if (list.getChildren().isEmpty()) {
            Label emptyLabel = new Label("Список файлов пуст.");
            emptyLabel.getStyleClass().add("modpack-preview-more");
            list.getChildren().add(emptyLabel);
        }

        ScrollPane scrollPane = new ScrollPane(list);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("modpack-preview-details");
        return scrollPane;
    }

    private static HBox createModpackPreviewFileRow(ModpackSyncPreviewEntry entry) {
        Label typeLabel = new Label(fileTypeLabel(entry.getPath()));
        typeLabel.getStyleClass().add("modpack-preview-file-type");

        Label nameLabel = new Label(fileDisplayName(entry.getPath()));
        nameLabel.getStyleClass().add("modpack-preview-file-name");
        nameLabel.setMaxWidth(Double.MAX_VALUE);

        Label reasonLabel = new Label(reasonLabel(entry.getReason()));
        reasonLabel.getStyleClass().add("modpack-preview-file-reason");

        Label sizeLabel = new Label(entry.getSize() == null || entry.getSize().longValue() <= 0L
            ? "размер неизвестен"
            : formatBytes(entry.getSize().longValue()));
        sizeLabel.getStyleClass().add("modpack-preview-file-size");

        VBox textBox = new VBox(2.0d, nameLabel, reasonLabel);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        HBox row = new HBox(10.0d, typeLabel, textBox, sizeLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("modpack-preview-file-row");
        return row;
    }

    private static String fileTypeLabel(String path) {
        String normalized = path == null ? "" : path.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalized.startsWith("mods/") || normalized.endsWith(".jar")) {
            return "Мод";
        }
        if (normalized.startsWith("config/") || normalized.endsWith(".cfg") || normalized.endsWith(".properties")) {
            return "Конфиг";
        }
        if (normalized.startsWith("resourcepacks/")) {
            return "Ресурсы";
        }
        if (normalized.startsWith("scripts/")) {
            return "Скрипт";
        }
        return "Файл";
    }

    private static String fileDisplayName(String path) {
        if (!hasText(path)) {
            return "Без имени";
        }
        String normalized = path.replace('\\', '/');
        int slashIndex = normalized.lastIndexOf('/');
        return slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
    }

    private static String reasonLabel(String reason) {
        if (!hasText(reason)) {
            return "требуется обновление";
        }
        String normalized = reason.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "missing" -> "файла нет локально";
            case "size-mismatch" -> "не совпал размер";
            case "sha256-mismatch" -> "изменилась контрольная сумма";
            case "changed-during-check" -> "файл изменился во время проверки";
            default -> "требуется обновление";
        };
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
        context().persistStateQuietly();
        if (state().getConfig().isCloseLauncherAfterGameStart()) {
            Platform.exit();
            System.exit(0);
        }
    }

    private void finishFailedLaunch(Throwable exception) {
        launchInProgress = false;
        syncInProgress = false;
        setPlayButtonBusy(false);
        if (isLaunchCancelled(exception)) {
            applySyncIdleState();
            return;
        }
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
        showError(launchFailureMessage(exception));
    }

    private void setPlayButtonBusy(boolean busy) {
        if (playButton == null) {
            return;
        }
        playButton.setDisable(busy);
        if (busy) {
            setPlayButtonVisualState(PLAY_BUTTON_BUSY_STYLE);
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
        showLauncherUpdateOverlay(update);

        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                launcherUpdateService.installAndRestart(
                    update,
                    message -> {
                    },
                    (downloadedBytes, totalBytes, elapsedMillis) -> Platform.runLater(
                        () -> applyLauncherUpdateProgress(downloadedBytes, totalBytes, elapsedMillis)
                    )
                );
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            setSyncStatus("Перезапуск лаунчера", SYNC_STATUS_OK, "check-circle", "#86efac");
            updateLauncherOverlay(1.0d, "Устанавливаем обновление", "Новая версия уже загружена. Перезапускаем лаунчер...", "100%");
            context().persistStateQuietly();
            Platform.exit();
            System.exit(0);
        });
        task.setOnFailed(event -> {
            launcherUpdateInProgress = false;
            hideLauncherUpdateOverlay();
            applyPlayButtonState();
            setSyncStatus("Ошибка обновления", SYNC_STATUS_ERROR, "info", "#fda4af");
            showError(resolveLauncherUpdateFailureMessage(task.getException(), update));
        });

        Thread thread = new Thread(task, "launcher-self-update");
        thread.setDaemon(true);
        thread.start();
    }

    private void applyLauncherUpdateProgress(long downloadedBytes, long totalBytes, long elapsedMillis) {
        long safeDownloaded = Math.max(0L, downloadedBytes);
        long safeTotal = Math.max(0L, totalBytes);
        if (safeTotal > 0L) {
            double progress = clamp(safeDownloaded / (double) safeTotal);
            setSyncProgress(progress);
            setText(syncProgressLabel, Math.round(progress * 100.0d) + "%");
            setSyncStatus("Скачиваем лаунчер", SYNC_STATUS_WORKING, "download", "#fbbf24");
            setSyncDetail(formatBytes(safeDownloaded) + " / " + formatBytes(safeTotal) + " · " + formatSpeed(safeDownloaded, elapsedMillis));
            updateLauncherOverlay(
                progress,
                "Обновляем ObsidianGate",
                formatBytes(safeDownloaded) + " из " + formatBytes(safeTotal) + " · " + formatSpeed(safeDownloaded, elapsedMillis),
                Math.round(progress * 100.0d) + "%"
            );
            return;
        }

        setSyncStatus("Скачиваем лаунчер", SYNC_STATUS_WORKING, "download", "#fbbf24");
        setSyncDetail(formatBytes(safeDownloaded) + " · " + formatSpeed(safeDownloaded, elapsedMillis));
        updateLauncherOverlay(
            ProgressBar.INDETERMINATE_PROGRESS,
            "Обновляем ObsidianGate",
            formatBytes(safeDownloaded) + " · " + formatSpeed(safeDownloaded, elapsedMillis),
            ""
        );
    }

    private void showLauncherUpdateOverlay(LauncherUpdateCandidate update) {
        if (launcherShellRoot == null) {
            return;
        }
        hideLauncherUpdateOverlay();

        Label eyebrow = new Label("ОБНОВЛЕНИЕ ЛАУНЧЕРА");
        eyebrow.getStyleClass().add("launcher-update-eyebrow");

        launcherUpdateTitleLabel = new Label("Готовим новую версию");
        launcherUpdateTitleLabel.getStyleClass().add("launcher-update-title");

        launcherUpdateDetailLabel = new Label(
            update == null ? "Подключаемся к серверу обновлений" : "Версия " + update.getVersion()
        );
        launcherUpdateDetailLabel.getStyleClass().add("launcher-update-detail");

        launcherUpdateProgressBar = new ProgressBar(0.0d);
        launcherUpdateProgressBar.setMaxWidth(Double.MAX_VALUE);
        launcherUpdateProgressBar.getStyleClass().add("launcher-update-progress");

        launcherUpdatePercentLabel = new Label("0%");
        launcherUpdatePercentLabel.getStyleClass().add("launcher-update-percent");

        HBox progressRow = new HBox(14.0d, launcherUpdateProgressBar, launcherUpdatePercentLabel);
        progressRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(launcherUpdateProgressBar, Priority.ALWAYS);
        progressRow.setMaxWidth(560.0d);

        Label note = new Label("Не закрывайте лаунчер. После установки интерфейс обновится автоматически.");
        note.setWrapText(true);
        note.getStyleClass().add("launcher-update-note");

        VBox content = new VBox(
            16.0d,
            LauncherIcons.logoCube(78.0d),
            eyebrow,
            launcherUpdateTitleLabel,
            launcherUpdateDetailLabel,
            progressRow,
            note
        );
        content.setAlignment(Pos.CENTER);
        content.setMaxWidth(620.0d);

        launcherUpdateOverlay = new StackPane(content);
        launcherUpdateOverlay.getStyleClass().add("launcher-update-overlay");
        launcherUpdateOverlay.setFocusTraversable(true);
        launcherShellRoot.getChildren().add(launcherUpdateOverlay);
        Platform.runLater(launcherUpdateOverlay::requestFocus);
    }

    private void showCompletedLauncherUpdate() {
        System.clearProperty(LauncherShellApplication.UPDATE_COMPLETE_PROPERTY);
        showLauncherUpdateOverlay(null);
        updateLauncherOverlay(1.0d, "Обновление завершено", "Новая версия лаунчера готова", "100%");

        FadeTransition reveal = new FadeTransition(Duration.millis(650.0d), launcherUpdateOverlay);
        reveal.setDelay(Duration.millis(700.0d));
        reveal.setFromValue(1.0d);
        reveal.setToValue(0.0d);
        reveal.setOnFinished(event -> hideLauncherUpdateOverlay());
        reveal.play();
    }

    private void updateLauncherOverlay(double progress, String title, String detail, String percent) {
        if (launcherUpdateOverlay == null) {
            return;
        }
        if (launcherUpdateProgressBar != null) {
            launcherUpdateProgressBar.setProgress(progress);
        }
        setText(launcherUpdateTitleLabel, title);
        setText(launcherUpdateDetailLabel, detail);
        setText(launcherUpdatePercentLabel, percent);
    }

    private void hideLauncherUpdateOverlay() {
        if (launcherUpdateOverlay != null && launcherShellRoot != null) {
            launcherShellRoot.getChildren().remove(launcherUpdateOverlay);
        }
        launcherUpdateOverlay = null;
        launcherUpdateTitleLabel = null;
        launcherUpdateDetailLabel = null;
        launcherUpdatePercentLabel = null;
        launcherUpdateProgressBar = null;
    }

    private void applyPlayButtonState() {
        if (playButton == null || launchInProgress) {
            return;
        }

        setPlayButtonVisualState(PLAY_BUTTON_READY_STYLE);
        if (launcherUpdateInProgress) {
            playButton.setDisable(true);
            setGraphic(playButton, "download", 16.0d, "#ffffff");
            playButton.setText("Обновление...");
            setPlayButtonVisualState(PLAY_BUTTON_BUSY_STYLE);
            return;
        }

        if (hasLauncherUpdate()) {
            playButton.setDisable(false);
            setGraphic(playButton, "download", 16.0d, "#ffffff");
            playButton.setText("Обновить лаунчер");
            setPlayButtonVisualState(PLAY_BUTTON_UPDATE_STYLE);
            return;
        }

        playButton.setDisable(false);
        setGraphic(playButton, "play", 16.0d, "#ffffff");
        playButton.setText("Играть");
    }

    private void setPlayButtonVisualState(String styleClass) {
        if (playButton == null) {
            return;
        }
        playButton.getStyleClass().removeAll(
            PLAY_BUTTON_UPDATE_STYLE,
            PLAY_BUTTON_READY_STYLE,
            PLAY_BUTTON_BUSY_STYLE
        );
        playButton.getStyleClass().add(styleClass);
        configurePlayButtonPulse(styleClass);
    }

    private void configurePlayButtonPulse(String styleClass) {
        if (playButtonPulseTimeline != null) {
            playButtonPulseTimeline.stop();
            playButtonPulseTimeline = null;
        }
        playButton.setOpacity(1.0d);

        if (PLAY_BUTTON_READY_STYLE.equals(styleClass)) {
            return;
        }

        double lowOpacity = PLAY_BUTTON_UPDATE_STYLE.equals(styleClass) ? 0.86d : 0.78d;
        double pulseDuration = PLAY_BUTTON_UPDATE_STYLE.equals(styleClass) ? 1500.0d : 920.0d;
        playButtonPulseTimeline = new Timeline(
            new KeyFrame(
                Duration.ZERO,
                new KeyValue(playButton.opacityProperty(), lowOpacity, Interpolator.EASE_BOTH)
            ),
            new KeyFrame(
                Duration.millis(pulseDuration / 2.0d),
                new KeyValue(playButton.opacityProperty(), 1.0d, Interpolator.EASE_BOTH)
            ),
            new KeyFrame(
                Duration.millis(pulseDuration),
                new KeyValue(playButton.opacityProperty(), lowOpacity, Interpolator.EASE_BOTH)
            )
        );
        playButtonPulseTimeline.setCycleCount(Animation.INDEFINITE);
        playButtonPulseTimeline.play();
    }

    private void playButtonClickFeedback() {
        if (playButton == null || playButton.isDisabled()) {
            return;
        }
        if (playButtonFeedbackTimeline != null) {
            playButtonFeedbackTimeline.stop();
        }
        playButton.getStyleClass().remove(PLAY_BUTTON_RIPPLE_STYLE);
        playButton.getStyleClass().add(PLAY_BUTTON_RIPPLE_STYLE);
        playButtonFeedbackTimeline = new Timeline(
            new KeyFrame(
                Duration.ZERO,
                new KeyValue(playButton.scaleXProperty(), 1.0d, Interpolator.EASE_BOTH),
                new KeyValue(playButton.scaleYProperty(), 1.0d, Interpolator.EASE_BOTH)
            ),
            new KeyFrame(
                Duration.millis(80.0d),
                new KeyValue(playButton.scaleXProperty(), 0.97d, Interpolator.EASE_BOTH),
                new KeyValue(playButton.scaleYProperty(), 0.97d, Interpolator.EASE_BOTH)
            ),
            new KeyFrame(
                Duration.millis(220.0d),
                new KeyValue(playButton.scaleXProperty(), 1.015d, Interpolator.EASE_BOTH),
                new KeyValue(playButton.scaleYProperty(), 1.015d, Interpolator.EASE_BOTH)
            ),
            new KeyFrame(
                Duration.millis(360.0d),
                new KeyValue(playButton.scaleXProperty(), 1.0d, Interpolator.EASE_BOTH),
                new KeyValue(playButton.scaleYProperty(), 1.0d, Interpolator.EASE_BOTH)
            )
        );
        playButtonFeedbackTimeline.setOnFinished(event -> playButton.getStyleClass().remove(PLAY_BUTTON_RIPPLE_STYLE));
        playButtonFeedbackTimeline.play();
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
        if (currentNewsItems.isEmpty()) {
            requestNewsRefresh();
            return;
        }
        showNewsOverlay(0);
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
        if (!launcherUpdatesEnabled()) {
            return null;
        }
        if (loadedManifest == null || loadedManifest.getManifest() == null) {
            return null;
        }

        return launcherUpdateService.findUpdate(loadedManifest, LauncherBrand.displayVersion());
    }

    private void applyLauncherUpdateState(LauncherUpdateCandidate update, boolean checkSucceeded) {
        if (!launcherUpdatesEnabled()) {
            availableLauncherUpdate = null;
            applyPlayButtonState();
            return;
        }
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

    private boolean launcherUpdatesEnabled() {
        LauncherConfig config = context() == null ? null : state().getConfig();
        return config == null || config.isLauncherUpdatesEnabled();
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
            currentNewsItems = Collections.emptyList();
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
            currentNewsItems = Collections.emptyList();
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

        currentNewsItems = new ArrayList<NewsItem>(items);
        List<Node> rows = new ArrayList<Node>();
        int visibleItems = Math.min(items.size(), NEWS_ITEM_LIMIT);
        for (int index = 0; index < visibleItems; index++) {
            rows.add(newsRow(items.get(index), index));
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

    private Node newsRow(NewsItem item, int index) {
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
        row.getStyleClass().add("news-row");
        row.setOnMouseClicked(event -> showNewsOverlay(index));
        return row;
    }

    private void showNewsOverlay(int requestedIndex) {
        if (launcherShellRoot == null || currentNewsItems.isEmpty()) {
            return;
        }

        int index = Math.max(0, Math.min(requestedIndex, currentNewsItems.size() - 1));
        NewsItem item = currentNewsItems.get(index);
        closeNewsOverlay();

        Label dateLabel = new Label(item.date());
        dateLabel.getStyleClass().add("news-overlay-date");

        Label counterLabel = new Label((index + 1) + " / " + currentNewsItems.size());
        counterLabel.getStyleClass().add("news-overlay-counter");

        Label titleLabel = new Label(item.fullTitle());
        titleLabel.getStyleClass().add("news-overlay-title");
        titleLabel.setWrapText(true);

        Label bodyLabel = new Label(item.fullText());
        bodyLabel.getStyleClass().add("news-overlay-body");
        bodyLabel.setWrapText(true);

        VBox content = new VBox(12.0d, titleLabel, bodyLabel);
        addNewsSection(content, "Важное", item.important());
        addNewsSection(content, "Главное", item.highlights());
        addNewsSection(content, "Новые моды", item.newMods());
        addNewsSection(content, "Удалено", item.removedMods());

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.getStyleClass().add("news-overlay-scroll");
        scrollPane.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        Button previousButton = newsOverlayButton("chevron-left", "Назад");
        previousButton.getStyleClass().add("news-overlay-bottom-button");
        previousButton.setDisable(index == 0);
        previousButton.setOnAction(event -> showNewsOverlay(index - 1));

        Button nextButton = newsOverlayButton("chevron-right", "Дальше");
        nextButton.getStyleClass().add("news-overlay-bottom-button");
        nextButton.setDisable(index >= currentNewsItems.size() - 1);
        nextButton.setOnAction(event -> showNewsOverlay(index + 1));

        Button closeButton = newsOverlayButton("x", "Закрыть");
        closeButton.setOnAction(event -> closeNewsOverlay());

        HBox topBar = new HBox(10.0d, dateLabel, counterLabel, spacer(), closeButton);
        topBar.setAlignment(Pos.CENTER_LEFT);

        HBox navigation = new HBox(10.0d, previousButton, spacer(), nextButton);
        navigation.setAlignment(Pos.CENTER_LEFT);
        navigation.getStyleClass().add("news-overlay-navigation");

        VBox panel = new VBox(14.0d, topBar, scrollPane, navigation);
        panel.getStyleClass().add("news-overlay-panel");
        panel.setMaxWidth(860.0d);
        panel.setMaxHeight(Double.MAX_VALUE);
        StackPane.setAlignment(panel, Pos.CENTER);
        StackPane.setMargin(panel, new Insets(16.0d, 22.0d, 16.0d, 22.0d));

        StackPane overlay = new StackPane(panel);
        overlay.getStyleClass().add("news-overlay");
        overlay.setFocusTraversable(true);
        overlay.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                event.consume();
                closeNewsOverlay();
            } else if (event.getCode() == KeyCode.LEFT && index > 0) {
                event.consume();
                showNewsOverlay(index - 1);
            } else if (event.getCode() == KeyCode.RIGHT && index < currentNewsItems.size() - 1) {
                event.consume();
                showNewsOverlay(index + 1);
            }
        });
        overlay.setOnMouseClicked(event -> {
            if (event.getTarget() == overlay) {
                closeNewsOverlay();
            }
        });

        newsOverlay = overlay;
        launcherShellRoot.getChildren().add(overlay);
        Platform.runLater(overlay::requestFocus);
    }

    private void closeNewsOverlay() {
        if (newsOverlay == null || launcherShellRoot == null) {
            newsOverlay = null;
            return;
        }
        launcherShellRoot.getChildren().remove(newsOverlay);
        newsOverlay = null;
    }

    private static Button newsOverlayButton(String iconName, String tooltipText) {
        Button button = new Button();
        button.setMnemonicParsing(false);
        button.setGraphic(LauncherIcons.icon(iconName, 16.0d, "#f8fafc"));
        button.getStyleClass().add("news-overlay-button");
        button.setTooltip(new Tooltip(tooltipText));
        return button;
    }

    private static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private static void addNewsSection(VBox content, String title, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("news-overlay-section-title");

        VBox list = new VBox(6.0d);
        for (String value : values) {
            if (!hasText(value)) {
                continue;
            }
            Label itemLabel = new Label("• " + value.trim());
            itemLabel.getStyleClass().add("news-overlay-list-item");
            itemLabel.setWrapText(true);
            list.getChildren().add(itemLabel);
        }
        if (!list.getChildren().isEmpty()) {
            content.getChildren().add(new VBox(7.0d, titleLabel, list));
        }
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
        return items;
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
        return new NewsItem(
            shorten(date, 18),
            shorten(title, 44),
            title,
            shorten(text, 72),
            firstText(news.getBody(), "Изменения сборки доступны для установки."),
            safeList(news.getHighlights()),
            safeList(news.getNewMods()),
            safeList(news.getRemovedMods()),
            safeList(news.getImportant())
        );
    }

    private static List<String> safeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<String>(values);
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
        setPlayButtonLaunchProgress(progress);
        setSyncStatus(describeSyncProgress(progress), SYNC_STATUS_WORKING, iconForSyncPhase(progress.getPhase()), "#fbbf24");
        setSyncDetail(describeSyncDetail(progress));
    }

    private void applyLaunchStep(String buttonText, String detailText, String iconName, String iconColor) {
        if (!launchInProgress || !isSyncCardAttachedToCurrentScene()) {
            return;
        }
        setPlayButtonLaunchText(buttonText);
        setSyncStatus(buttonText, SYNC_STATUS_WORKING, iconName, iconColor);
        setSyncDetail(detailText);
    }

    private void setSyncStatus(String text, String statusStyleClass, String iconName, String iconColor) {
        boolean stateChanged = !hasStyle(syncStatusLabel, statusStyleClass);
        setText(syncStatusLabel, text);
        setLabelGraphic(syncStatusLabel, iconName, 16.0d, iconColor);
        setSyncStatusStyle(syncStatusLabel, statusStyleClass);
        applySyncVisualState(statusStyleClass);
        if (stateChanged) {
            MicroInteractions.playStatusSwap(syncStatusLabel, syncProgressLabel, syncProgressWrap);
        }
    }

    private void setSyncDetail(String text) {
        setText(syncDetailLabel, text);
    }

    private void setPlayButtonLaunchProgress(ModpackSyncProgress progress) {
        if (progress == null || !launchInProgress) {
            return;
        }
        setPlayButtonLaunchText(describePlayButtonProgress(progress));
    }

    private void setPlayButtonLaunchText(String text) {
        if (playButton == null || text == null || text.trim().isEmpty()) {
            return;
        }
        playButton.setText(text.trim());
    }

    private boolean isSyncCardAttachedToCurrentScene() {
        return syncProgressLabel != null
            && syncProgressLabel.getScene() != null
            && context() != null
            && syncProgressLabel.getScene() == stage().getScene();
    }

    private void setSyncProgress(double progress) {
        if (syncProgressArc != null) {
            double targetLength = -360.0d * clamp(progress);
            if (syncProgressTimeline != null) {
                syncProgressTimeline.stop();
            }
            syncProgressTimeline = new Timeline(new KeyFrame(
                Duration.millis(260.0d),
                new KeyValue(syncProgressArc.lengthProperty(), targetLength, Interpolator.EASE_BOTH)
            ));
            syncProgressTimeline.play();
        }
    }

    private void applySyncVisualState(String statusStyleClass) {
        setSyncStatusStyle(syncProgressWrap, statusStyleClass);
        setSyncStatusStyle(syncProgressArc, statusStyleClass);
        setSyncStatusStyle(syncActivityArc, statusStyleClass);
        setSyncStatusStyle(syncProgressLabel, statusStyleClass);
        if (SYNC_STATUS_WORKING.equals(statusStyleClass)) {
            startSyncPulse();
            return;
        }
        stopSyncPulse();
    }

    private void startSyncPulse() {
        if (syncActivityArc == null) {
            return;
        }
        if (syncPulseTransition == null) {
            syncPulseTransition = new FadeTransition(Duration.millis(720.0d), syncActivityArc);
            syncPulseTransition.setFromValue(0.48d);
            syncPulseTransition.setToValue(1.0d);
            syncPulseTransition.setAutoReverse(true);
            syncPulseTransition.setCycleCount(Animation.INDEFINITE);
        }
        syncActivityArc.setOpacity(1.0d);
        if (syncPulseTransition.getStatus() != Animation.Status.RUNNING) {
            syncPulseTransition.play();
        }
        if (syncRingRotation == null) {
            syncRingRotation = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(syncActivityArc.startAngleProperty(), 90.0d)),
                new KeyFrame(
                    Duration.millis(1250.0d),
                    new KeyValue(syncActivityArc.startAngleProperty(), 450.0d, Interpolator.LINEAR)
                )
            );
            syncRingRotation.setCycleCount(Animation.INDEFINITE);
        }
        if (syncRingRotation.getStatus() != Animation.Status.RUNNING) {
            syncRingRotation.play();
        }
    }

    private void stopSyncPulse() {
        if (syncPulseTransition != null) {
            syncPulseTransition.stop();
        }
        if (syncRingRotation != null) {
            syncRingRotation.stop();
        }
        if (syncActivityArc != null) {
            syncActivityArc.setOpacity(0.0d);
            syncActivityArc.setStartAngle(90.0d);
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

    private static String describePlayButtonProgress(ModpackSyncProgress progress) {
        if (progress.getPhase() == ModpackSyncProgress.Phase.CHECKING) {
            return "Проверяем файлы";
        }
        if (progress.getPhase() == ModpackSyncProgress.Phase.DOWNLOADING) {
            long totalBytes = Math.max(0L, progress.getTotalDownloadBytes());
            if (totalBytes > 0L) {
                return "Скачиваем " + formatBytes(totalBytes);
            }
            return "Скачиваем файлы";
        }
        if (progress.getPhase() == ModpackSyncProgress.Phase.RUNTIME) {
            return "Проверяем Java";
        }
        if (progress.getPhase() == ModpackSyncProgress.Phase.MINECRAFT) {
            return "Готовим Minecraft";
        }
        if (progress.getPhase() == ModpackSyncProgress.Phase.CLEANUP) {
            return "Наводим порядок";
        }
        if (progress.getPhase() == ModpackSyncProgress.Phase.COMPLETE) {
            return "Получаем билет входа";
        }
        return "Подготовка...";
    }

    private static String iconForSyncPhase(ModpackSyncProgress.Phase phase) {
        if (phase == ModpackSyncProgress.Phase.DOWNLOADING) {
            return "download";
        }
        if (phase == ModpackSyncProgress.Phase.RUNTIME || phase == ModpackSyncProgress.Phase.MINECRAFT) {
            return "play";
        }
        if (phase == ModpackSyncProgress.Phase.COMPLETE) {
            return "shield";
        }
        return "sync";
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
            return "Проверка файлов отключена";
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

    private static String formatSpeed(long bytes, long elapsedMillis) {
        long safeElapsed = Math.max(1L, elapsedMillis);
        long bytesPerSecond = Math.round(bytes * 1000.0d / safeElapsed);
        return formatBytes(bytesPerSecond) + "/с";
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

    private static String launchFailureMessage(Throwable exception) {
        StringBuilder message = new StringBuilder("Не удалось запустить игру: ").append(errorMessage(exception));
        Path logFile = gameLogFileFromFailure(exception);
        String logTail = tailLog(logFile, 40);
        if (hasText(logTail)) {
            message.append("\n\nПоследние строки game.log:\n").append(logTail);
        }
        return message.toString();
    }

    private static Path gameLogFileFromFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof LaunchFailureException launchFailureException) {
                return launchFailureException.getLogFile();
            }
            current = current.getCause();
        }
        return null;
    }

    private static String tailLog(Path logFile, int maxLines) {
        if (logFile == null || !Files.isRegularFile(logFile)) {
            return "";
        }
        ArrayDeque<String> lines = new ArrayDeque<String>(Math.max(1, maxLines));
        try (java.util.stream.Stream<String> stream = Files.lines(logFile)) {
            stream.forEach(line -> {
                if (lines.size() >= maxLines) {
                    lines.removeFirst();
                }
                lines.addLast(line);
            });
        } catch (IOException exception) {
            return "";
        }
        return String.join("\n", lines);
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

    private static boolean isLaunchCancelled(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof LaunchCancelledException) {
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

    private static final class LaunchCancelledException extends Exception {
    }

    private static final class LaunchFailureException extends Exception {
        private final Path logFile;

        private LaunchFailureException(String message, Path logFile, Throwable cause) {
            super(message, cause);
            this.logFile = logFile;
        }

        private Path getLogFile() {
            return logFile;
        }
    }

    private record NewsItem(
        String date,
        String title,
        String fullTitle,
        String text,
        String fullText,
        List<String> highlights,
        List<String> newMods,
        List<String> removedMods,
        List<String> important
    ) {
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
        boolean stateChanged = !hasStyle(serverStatusLabel, statusStyleClass);
        setText(serverStatusLabel, text);
        setLabelGraphic(serverStatusLabel, iconName, 16.0d, iconColor);
        applyServerStatusStyle(statusStyleClass);
        if (stateChanged) {
            MicroInteractions.playStatusSwap(serverStatusLabel, serverStatusDot);
        }
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

    private static boolean hasStyle(Node node, String styleClass) {
        return node != null && node.getStyleClass().contains(styleClass);
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
