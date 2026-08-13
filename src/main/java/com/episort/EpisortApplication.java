package com.episort;

import com.episort.config.JanusConfigurationProvider;
import com.episort.config.FileSettingsStore;
import com.episort.config.JanusConfiguration;
import com.episort.config.WindowPlacement;
import com.episort.persistence.FileExecutionJournal;
import com.episort.persistence.FileRunEventStore;
import com.episort.persistence.RunEvent;
import com.episort.persistence.RunEventStatus;
import com.episort.persistence.RunEventStore;
import com.episort.persistence.RunEventType;
import com.episort.scanner.InventoryScanResult;
import com.episort.scanner.InventorySummary;
import com.episort.scanner.MediaInventoryScanner;
import com.episort.tmdb.HttpTmdbClient;
import com.episort.tmdb.HttpTmdbConnectionTester;
import com.episort.tmdb.TmdbClient;
import com.episort.tmdb.debug.TmdbRequestBus;
import com.episort.tmdb.debug.TmdbRequestTrace;
import com.episort.ui.AppLanguage;
import com.episort.ui.AppShell;
import com.episort.ui.AppShellViewModel;
import com.episort.ui.diagnostics.FrameRateProbe;
import com.episort.ui.FocusRelease;
import com.episort.ui.FxUpdateCoalescer;
import com.episort.ui.PopupMotion;
import com.episort.ui.UiText;
import com.episort.ui.WorkflowPhase;
import com.episort.ui.Theme;
import com.episort.ui.ThemePreference;
import com.episort.ui.ThemeStyles;
import com.episort.ui.platform.SystemTheme;
import com.episort.ui.platform.WindowManager;
import com.episort.ui.platform.WindowState;
import com.episort.ui.platform.WindowsTitleBar;
import com.episort.workflow.ApplicationError;
import com.episort.workflow.ErrorSeverity;
import com.episort.workflow.InventoryWorkflowService;
import com.episort.workflow.ScanCancellation;
import com.episort.workflow.ScanCancelledException;
import com.episort.workflow.StartupWorkflow;
import com.episort.workflow.TmdbBatchMatchResult;
import com.episort.workflow.TmdbBatchMatchService;
import com.episort.workflow.TmdbGatewayStatus;
import com.episort.workflow.TmdbGatewayService;
import com.episort.workflow.WorkspaceConfigurationService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;

public class EpisortApplication extends Application {
    /** The window the app opens into: both screens laid out in full (§3.1). */
    public static final double INITIAL_WIDTH = 1280;
    public static final double INITIAL_HEIGHT = 800;
    /**
     * The floor is what the compact layout can actually hold, not what the wide
     * one needs. It used to be 1180 × 760 against a single breakpoint at 1200,
     * so the narrow layout lived in a twenty-pixel band and the window could not
     * be tiled to half of a 1920 screen.
     */
    public static final double MIN_WIDTH = 820;
    public static final double MIN_HEIGHT = 600;

    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "episort-scan");
        thread.setDaemon(true);
        return thread;
    });

    private final FileSettingsStore settingsStoreEarly = FileSettingsStore.userProfileStore();

    private volatile AppShell appShellRef;
    private volatile ThemePreference themePreference = ThemePreference.SYSTEM;
    private final ScheduledExecutorService themeWatcher = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "episort-system-theme");
        thread.setDaemon(true);
        return thread;
    });
    private final FxUpdateCoalescer<TmdbProgress> tmdbProgressUpdates =
            new FxUpdateCoalescer<>(Platform::runLater, this::applyTmdbProgress);

    /**
     * Token of the analysis currently running. Cancelling it (Esc or the loader
     * button) makes the background pipeline unwind at its next checkpoint.
     */
    private final AtomicReference<ScanCancellation> activeScan =
            new AtomicReference<>();

    private final TmdbClient tmdbClient = new HttpTmdbClient();
    private final TmdbBatchMatchService tmdbBatchMatchService = new TmdbBatchMatchService(tmdbClient);
    private Supplier<Optional<JanusConfiguration>> tmdbCredentialsSupplier = Optional::empty;

    @Override
    public void start(Stage stage) {
        // Before anything can open a menu: the entrance is installed on the
        // window list, so a popup shown later is caught as it appears.
        PopupMotion.install();
        FileSettingsStore settingsStore = settingsStoreEarly;
        Optional<JanusConfiguration> janusConfiguration = JanusConfigurationProvider.load();
        tmdbCredentialsSupplier = () -> janusConfiguration;
        StartupWorkflow startupWorkflow = new StartupWorkflow(
                new WorkspaceConfigurationService(settingsStore),
                janusConfiguration
                        .map(configuration -> new TmdbGatewayService(configuration, new HttpTmdbConnectionTester()))
                        .orElse(null));
        TmdbGatewayStatus tmdbConfiguration = startupWorkflow.loadTmdbConfiguration();
        AppLanguage resolvedLanguage = settingsStore.loadLanguage()
                .map(EpisortApplication::parseLanguage)
                .orElseGet(AppLanguage::detectFromOs);
        // An absent preference means a first launch, which follows the desktop:
        // the choice is the user's to make, and until they make one the safest
        // guess is the one they already made for everything else. From the
        // first visit to the settings the stored preference wins instead.
        themePreference = settingsStore.loadThemePreference().orElse(ThemePreference.SYSTEM);
        Theme initialTheme = resolveTheme(themePreference);
        AppShellViewModel viewModel = AppShellViewModel.fromStartupPrerequisites(
                        startupWorkflow.loadWorkspaceConfiguration(),
                        tmdbConfiguration)
                .withLanguage(resolvedLanguage)
                .withTheme(initialTheme);
        RunEventStore runEventStore = FileRunEventStore.userProfileStore();
        AppShell appShell = new AppShell(
                viewModel,
                workspace -> AppShellViewModel.fromWorkspaceConfiguration(
                        startupWorkflow.configureWorkspace(workspace)),
                inputFolder -> scanInputFolder(startupWorkflow, inputFolder, runEventStore),
                tmdbConfiguration,
                () -> startupWorkflow.loadWorkspaceConfiguration().settings().workspaceDirectory(),
                () -> startupWorkflow.loadWorkspaceConfiguration().success(),
                () -> {},
                runEventStore,
                getHostServices()::showDocument,
                themePreference,
                preference -> applyThemePreference(preference, settingsStore, stage));
        this.appShellRef = appShell;
        appShell.setScanCancelHandler(this::cancelActiveScan);
        appShell.setInputSourcesLoader(paths -> scanInputSources(startupWorkflow, paths, runEventStore));
        appShell.setExecutionJournal(FileExecutionJournal.userProfileJournal());
        stage.setTitle("Episort");
        // No native chrome: the top bar draws the window buttons itself, which
        // is what keeps them from costing a second row of height.
        stage.initStyle(StageStyle.UNDECORATED);
        stage.getIcons().add(AppShell.logoImage());
        appShell.setLoading(true, UiText.loadingStartup(viewModel.language()));
        Scene scene = new Scene(appShell.root(), INITIAL_WIDTH, INITIAL_HEIGHT);
        ThemeStyles.registerScene(scene);
        // Without this the search field keeps the caret and the focus ring after
        // a click on empty space, and goes on capturing the keyboard.
        FocusRelease.install(scene);
        stage.setScene(scene);
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        WindowManager windowManager = appShell.installWindowDecorations(stage);
        settingsStore.loadWindowPlacement()
                .ifPresent(placement -> windowManager.restorePlacement(
                        placement.normalBounds(), placement.state()));
        // On hiding, not on close request: the title bar's own ✕ calls
        // Stage.close(), which hides the window without ever firing a close
        // request, so a placement saved there would only survive Alt+F4.
        stage.addEventHandler(WindowEvent.WINDOW_HIDING,
                event -> persistWindowPlacement(settingsStore, windowManager));
        stage.show();
        FrameRateProbe.startIfEnabled();
        Platform.runLater(() -> {
            appShell.setLoading(false, "");
            // Story 7.4: an execution that never closed means the app stopped mid-run.
            appShell.reportInterruptedExecution();
        });
        stage.toFront();
        stage.requestFocus();
        appShell.setLanguageChangeListener(language -> settingsStore.saveLanguage(language.name()));
        windowManager.addStateListener(state -> WindowsTitleBar.applyCornerPreference(
                stage, state == WindowState.NORMAL && !stage.isFullScreen()));
        stage.fullScreenProperty().addListener((observable, wasFullScreen, isFullScreen) ->
                WindowsTitleBar.applyCornerPreference(
                        stage, windowManager.state() == WindowState.NORMAL && !isFullScreen));
        WindowsTitleBar.applyTheme(stage, initialTheme);
        // And again once the window has its system frame back: adopting it is a
        // frame change, and DWM answers a frame change with its own defaults.
        // Without this the dark window wore the system's light border, which
        // Windows only paints on the active window — so it appeared on the way
        // back from another application rather than at startup.
        windowManager.whenFrameSettled(() -> applyWindowChrome(stage, windowManager));
        appShell.scanScreen().setTmdbLookup(tmdbClient, tmdbCredentialsSupplier);
        themeWatcher.scheduleWithFixedDelay(
                () -> refreshSystemTheme(stage), 1, 1, TimeUnit.SECONDS);
    }

    /** Everything Windows itself draws of this window: its border and its corners. */
    private void applyWindowChrome(Stage stage, WindowManager windowManager) {
        WindowsTitleBar.applyTheme(stage, resolveTheme(themePreference));
        WindowsTitleBar.applyCornerPreference(
                stage, windowManager.state() == WindowState.NORMAL && !stage.isFullScreen());
    }

    /** Best-effort: a settings write that fails must not hold up the exit. */
    private static void persistWindowPlacement(
            FileSettingsStore settingsStore, WindowManager windowManager) {
        try {
            settingsStore.saveWindowPlacement(
                    new WindowPlacement(windowManager.normalBounds(), windowManager.state()));
        } catch (RuntimeException ignored) {
            // ignore
        }
    }

    private static AppLanguage parseLanguage(String stored) {
        try {
            return AppLanguage.valueOf(stored);
        } catch (IllegalArgumentException ignored) {
            return AppLanguage.detectFromOs();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void stop() {
        scanExecutor.shutdownNow();
        themeWatcher.shutdownNow();
    }

    private void applyThemePreference(
            ThemePreference preference, FileSettingsStore settingsStore, Stage stage) {
        themePreference = preference;
        settingsStore.saveThemePreference(preference);
        applyResolvedTheme(resolveTheme(preference), stage);
    }

    private void refreshSystemTheme(Stage stage) {
        if (themePreference != ThemePreference.SYSTEM) return;
        Theme detected = SystemTheme.current();
        AppShell shell = appShellRef;
        if (shell != null && shell.currentViewModel().theme() != detected) {
            Platform.runLater(() -> applyResolvedTheme(detected, stage));
        }
    }

    private void applyResolvedTheme(Theme theme, Stage stage) {
        AppShell shell = appShellRef;
        if (shell != null) shell.setTheme(theme);
        WindowsTitleBar.applyTheme(stage, theme);
    }

    private static Theme resolveTheme(ThemePreference preference) {
        return switch (preference) {
            case SYSTEM -> SystemTheme.current();
            case DARK -> Theme.DARK;
            case LIGHT -> Theme.LIGHT;
        };
    }

    /**
     * Opens a fresh cancellation scope for an analysis, cancelling whatever run
     * was still in flight so a superseded pipeline stops burning TMDB calls.
     */
    private ScanCancellation beginScan() {
        ScanCancellation cancellation = ScanCancellation.none();
        ScanCancellation previous = activeScan.getAndSet(cancellation);
        if (previous != null) {
            previous.cancel();
        }
        return cancellation;
    }

    /** Invoked from the JavaFX thread when the user presses Esc or the cancel button. */
    private void cancelActiveScan() {
        ScanCancellation cancellation = activeScan.get();
        if (cancellation != null) {
            cancellation.cancel();
        }
    }

    private CompletableFuture<AppShellViewModel> scanInputFolder(
            StartupWorkflow startupWorkflow, Path inputFolder, RunEventStore runEventStore) {
        var selection = startupWorkflow.selectInputFolder(inputFolder);
        if (!selection.success()) {
            return CompletableFuture.completedFuture(AppShellViewModel.fromInputFolderSelection(selection));
        }
        var selectedFolder = selection.inputFolder().orElseThrow();
        Optional<Path> workspace = startupWorkflow.loadWorkspaceConfiguration().settings().workspaceDirectory();
        ScanCancellation cancellation = beginScan();
        InventoryWorkflowService service = new InventoryWorkflowService(new MediaInventoryScanner(), scanExecutor);
        return service.scan(selectedFolder, progress -> cancellation.throwIfCancelled())
                .thenApply(result -> {
                    InventoryScanResult scanResult = new InventoryScanResult(result.items(), result.groups(), result.summary());
                    cancellation.throwIfCancelled();
                    setScanWorkflowPhase(WorkflowPhase.ANALYSIS, true);
                    TmdbBatchMatchResult batchResult = runTmdbBatch(scanResult, cancellation);
                    cancellation.throwIfCancelled();
                    recordScanCompleted(runEventStore, workspace, selectedFolder, scanResult);
                    pushPostScanResultsAfterApply(batchResult);
                    return AppShellViewModel.fromInventoryScan(selectedFolder, scanResult);
                })
                .exceptionally(throwable -> {
                    if (ScanCancellation.isCancellation(throwable)) {
                        return cancelledViewModel();
                    }
                    recordScanFailed(runEventStore, workspace, selectedFolder, throwable);
                    return AppShellViewModel.fromError(ApplicationError.recoverable(
                            "INPUT_FOLDER_INVALID",
                            ErrorSeverity.BLOCKING,
                            "Inventory scan failed.",
                            ""));
                });
    }

    private CompletableFuture<AppShellViewModel> scanInputSources(
            StartupWorkflow startupWorkflow, List<Path> inputSources, RunEventStore runEventStore) {
        var selection = startupWorkflow.selectInputSources(inputSources);
        if (!selection.success()) {
            return CompletableFuture.completedFuture(AppShellViewModel.fromError(selection.error().orElseThrow()));
        }
        List<Path> selectedSources = selection.sources();
        Optional<Path> workspace = startupWorkflow.loadWorkspaceConfiguration().settings().workspaceDirectory();
        ScanCancellation cancellation = beginScan();
        InventoryWorkflowService service = new InventoryWorkflowService(new MediaInventoryScanner(), scanExecutor);
        return service.scanSources(selectedSources, progress -> cancellation.throwIfCancelled())
                .thenApply(result -> {
                    InventoryScanResult scanResult = new InventoryScanResult(result.items(), result.groups(), result.summary());
                    Path subject = selectedSources.size() == 1 ? selectedSources.getFirst()
                            : workspace.orElse(selectedSources.getFirst().getParent());
                    cancellation.throwIfCancelled();
                    setScanWorkflowPhase(WorkflowPhase.ANALYSIS, true);
                    TmdbBatchMatchResult batchResult = runTmdbBatch(scanResult, cancellation);
                    cancellation.throwIfCancelled();
                    recordScanCompleted(runEventStore, workspace, subject, scanResult);
                    pushPostScanResultsAfterApply(batchResult);
                    return AppShellViewModel.fromInventoryScan(subject, scanResult);
                })
                .exceptionally(throwable -> {
                    if (ScanCancellation.isCancellation(throwable)) {
                        return cancelledViewModel();
                    }
                    Path subject = selectedSources.isEmpty() ? Path.of("") : selectedSources.getFirst();
                    recordScanFailed(runEventStore, workspace, subject, throwable);
                    return AppShellViewModel.fromError(ApplicationError.recoverable(
                            "INPUT_SOURCE_INVALID",
                            ErrorSeverity.BLOCKING,
                            "Inventory scan failed.",
                            ""));
                });
    }

    /**
     * View model handed back for a cancelled run. The shell has already fenced
     * this run out, so it must carry no scan result that could overwrite what
     * the user sees now.
     */
    private AppShellViewModel cancelledViewModel() {
        AppShell shell = appShellRef;
        AppShellViewModel base = shell == null ? AppShellViewModel.initial() : shell.currentViewModel();
        return new AppShellViewModel(
                base.title(),
                UiText.loadingScanCancelled(base.language()),
                base.description(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                base.theme(),
                base.language());
    }

    private void pushPostScanResultsAfterApply(TmdbBatchMatchResult batchResult) {
        AppShell shell = appShellRef;
        if (shell == null) return;
        // Outer runLater ensures we are on the JFX thread; inner runLater
        // re-enqueues us so we run *after* the AppShell has applied the
        // viewmodel and rebuilt scan rows.
        Platform.runLater(
                () -> Platform.runLater(() -> {
                    if (batchResult != null) {
                        shell.scanScreen().applyTmdbBatchResult(batchResult);
                    }
                    shell.scanScreen().setWorkflowPhase(
                            WorkflowPhase.PLAN_REVIEW, false);
                }));
    }

    private TmdbBatchMatchResult runTmdbBatch(
            InventoryScanResult scanResult, ScanCancellation cancellation) {
        cancellation.throwIfCancelled();
        Optional<JanusConfiguration> credentials = tmdbCredentialsSupplier.get();
        if (credentials.isEmpty()) {
            publishBatchInfo("skipped: no TMDB credentials available");
            return TmdbBatchMatchResult.empty();
        }
        setScanWorkflowPhase(WorkflowPhase.TMDB_MATCHES, true);
        updateLoadingTextForTmdb();
        try {
            return tmdbBatchMatchService.run(scanResult, credentials.orElseThrow(),
                    (done, total) -> {
                        cancellation.throwIfCancelled();
                        updateLoadingForTmdbProgress(done, total);
                    });
        } catch (ScanCancelledException cancelled) {
            throw cancelled;
        } catch (RuntimeException ex) {
            publishBatchInfo("aborted: " + ex.getClass().getSimpleName()
                    + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
            return TmdbBatchMatchResult.empty();
        }
    }

    private static void publishBatchInfo(String message) {
        try {
            TmdbRequestBus.get().publish(new TmdbRequestTrace(
                    Instant.now(), "BATCH", "pre-run", 0, 0L, message, null, false));
        } catch (RuntimeException ignored) {
            // ignore
        }
    }

    private void setScanWorkflowPhase(WorkflowPhase phase, boolean inProgress) {
        AppShell shell = appShellRef;
        if (shell == null) return;
        Platform.runLater(() -> shell.scanScreen().setWorkflowPhase(phase, inProgress));
    }

    private void updateLoadingTextForTmdb() {
        AppShell shell = appShellRef;
        if (shell == null) return;
        AppLanguage language = shell.currentLanguage();
        Platform.runLater(() -> {
            shell.updateLoadingText(UiText.loadingScanTmdb(language));
            shell.setLoadingProgress(0.0);
        });
    }

    private void updateLoadingForTmdbProgress(int done, int total) {
        if (total > 0) {
            tmdbProgressUpdates.submit(new TmdbProgress(done, total));
        }
    }

    private void applyTmdbProgress(TmdbProgress update) {
        AppShell shell = appShellRef;
        if (shell == null) return;
        shell.updateLoadingText(UiText.loadingScanTmdb(shell.currentLanguage()));
        shell.setLoadingProgress((double) update.done() / update.total());
    }

    private record TmdbProgress(int done, int total) {}

    private static void recordScanCompleted(
            RunEventStore store,
            Optional<Path> workspace,
            Path subjectFolder,
            InventoryScanResult result) {
        InventorySummary summary = result.summary();
        Map<String, String> metrics = new LinkedHashMap<>();
        metrics.put("supported", String.valueOf(summary.supportedVideoCount()));
        metrics.put("sidecar", String.valueOf(summary.sidecarCount()));
        metrics.put("unsupported", String.valueOf(summary.unsupportedCount()));
        metrics.put("ignored", String.valueOf(summary.ignoredCount()));
        metrics.put("series", String.valueOf(summary.likelySeriesGroupCount()));
        metrics.put("movies", String.valueOf(summary.likelyMovieGroupCount()));
        metrics.put("unknown", String.valueOf(summary.unknownItemCount()));

        RunEventStatus status = summary.unknownItemCount() > 0
                ? RunEventStatus.WARNING
                : RunEventStatus.SUCCESS;
        String summaryText = summary.supportedVideoCount() + " video(s), "
                + summary.likelySeriesGroupCount() + " series, "
                + summary.likelyMovieGroupCount() + " movies, "
                + summary.unknownItemCount() + " unknown";

        try {
            store.append(RunEvent.of(
                    RunEventType.SCAN_COMPLETED,
                    status,
                    workspace,
                    Optional.of(subjectFolder),
                    summaryText,
                    metrics));
        } catch (RuntimeException ignored) {
            // Best-effort recording — do not fail the user-visible flow.
        }
    }

    private static void recordScanFailed(
            RunEventStore store,
            Optional<Path> workspace,
            Path subjectFolder,
            Throwable throwable) {
        Map<String, String> metrics = new LinkedHashMap<>();
        metrics.put("error", throwable.getClass().getSimpleName());
        try {
            store.append(RunEvent.of(
                    RunEventType.SCAN_FAILED,
                    RunEventStatus.FAILED,
                    workspace,
                    Optional.of(subjectFolder),
                    throwable.getMessage() == null ? "" : throwable.getMessage(),
                    metrics));
        } catch (RuntimeException ignored) {
            // Best-effort recording.
        }
    }

}
