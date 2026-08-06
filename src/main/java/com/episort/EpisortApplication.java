package com.episort;

import com.episort.config.EmbeddedTvdbCredentialsProvider;
import com.episort.config.FileSettingsStore;
import com.episort.config.FileTvdbCredentialStore;
import com.episort.config.SafeTvdbCredentialsProvider;
import com.episort.config.TvdbCredentials;
import com.episort.persistence.FileExecutionJournal;
import com.episort.persistence.FileRunEventStore;
import com.episort.persistence.RunEvent;
import com.episort.persistence.RunEventStatus;
import com.episort.persistence.RunEventStore;
import com.episort.persistence.RunEventType;
import com.episort.scanner.InventoryScanResult;
import com.episort.scanner.InventorySummary;
import com.episort.scanner.MediaInventoryScanner;
import com.episort.tvdb.CachedTvdbClient;
import com.episort.tvdb.HttpTvdbClient;
import com.episort.tvdb.HttpTvdbConnectionTester;
import com.episort.tvdb.TvdbClient;
import com.episort.tvdb.cache.TvdbResponseCache;
import com.episort.tvdb.debug.TvdbRequestBus;
import com.episort.tvdb.debug.TvdbRequestTrace;
import com.episort.tvdb.guard.TvdbRateLimitGuard;
import com.episort.tvdb.guard.TvdbRequestScheduler;
import com.episort.ui.AppLanguage;
import com.episort.ui.AppShell;
import com.episort.ui.AppShellViewModel;
import com.episort.ui.UiText;
import com.episort.ui.WorkflowPhase;
import com.episort.ui.platform.WindowManager;
import com.episort.ui.platform.WindowsTitleBar;
import com.episort.workflow.ApplicationError;
import com.episort.workflow.ErrorSeverity;
import com.episort.workflow.InventoryWorkflowService;
import com.episort.workflow.ScanCancellation;
import com.episort.workflow.ScanCancelledException;
import com.episort.workflow.StartupWorkflow;
import com.episort.workflow.TvdbBatchMatchResult;
import com.episort.workflow.TvdbBatchMatchService;
import com.episort.workflow.TvdbCredentialConfigurationResult;
import com.episort.workflow.TvdbCredentialConfigurationService;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.paint.Color;
import javafx.scene.Scene;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class EpisortApplication extends Application {
    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "episort-scan");
        thread.setDaemon(true);
        return thread;
    });

    private final FileSettingsStore settingsStoreEarly = FileSettingsStore.userProfileStore();

    private volatile AppShell appShellRef;

    /**
     * Token of the analysis currently running. Cancelling it (Esc or the loader
     * button) makes the background pipeline unwind at its next checkpoint.
     */
    private final AtomicReference<ScanCancellation> activeScan =
            new AtomicReference<>();

    private final TvdbResponseCache tvdbCache = TvdbResponseCache.userProfileCache();
    private final TvdbRateLimitGuard tvdbGuard = new TvdbRateLimitGuard();
    private final TvdbRequestScheduler tvdbRequestScheduler = new TvdbRequestScheduler();
    private final TvdbClient tvdbClient = new CachedTvdbClient(
            new HttpTvdbClient(tvdbRequestScheduler), tvdbCache, tvdbGuard);
    private final TvdbBatchMatchService tvdbBatchMatchService = new TvdbBatchMatchService(tvdbClient);
    private Supplier<Optional<TvdbCredentials>> tvdbCredentialsSupplier = Optional::empty;

    @Override
    public void start(Stage stage) {
        FileSettingsStore settingsStore = settingsStoreEarly;
        FileTvdbCredentialStore tvdbCredentialStore = FileTvdbCredentialStore.userProfileStore();
        SafeTvdbCredentialsProvider credentialsProvider = new SafeTvdbCredentialsProvider(
                EmbeddedTvdbCredentialsProvider::load, tvdbCredentialStore::load);
        tvdbCredentialsSupplier = credentialsProvider::load;
        StartupWorkflow startupWorkflow = new StartupWorkflow(
                new WorkspaceConfigurationService(settingsStore),
                new TvdbCredentialConfigurationService(
                        tvdbCredentialStore,
                        new HttpTvdbConnectionTester(tvdbRequestScheduler)));
        TvdbCredentialConfigurationResult tvdbConfiguration = EmbeddedTvdbCredentialsProvider.load()
                .map(startupWorkflow::configureAndTestTvdb)
                .orElseGet(startupWorkflow::loadTvdbConfiguration);
        AppLanguage resolvedLanguage = settingsStore.loadLanguage()
                .map(EpisortApplication::parseLanguage)
                .orElseGet(AppLanguage::detectFromOs);
        AppShellViewModel viewModel = AppShellViewModel.fromStartupPrerequisites(
                        startupWorkflow.loadWorkspaceConfiguration(),
                        tvdbConfiguration)
                .withLanguage(resolvedLanguage);
        RunEventStore runEventStore = FileRunEventStore.userProfileStore();
        AppShell appShell = new AppShell(
                viewModel,
                workspace -> AppShellViewModel.fromWorkspaceConfiguration(
                        startupWorkflow.configureWorkspace(workspace)),
                inputFolder -> scanInputFolder(startupWorkflow, inputFolder, runEventStore),
                tvdbConfiguration,
                () -> startupWorkflow.loadWorkspaceConfiguration().settings().workspaceDirectory(),
                () -> startupWorkflow.loadWorkspaceConfiguration().success(),
                () -> {},
                runEventStore,
                tvdbCache::clear,
                getHostServices()::showDocument);
        this.appShellRef = appShell;
        appShell.setScanCancelHandler(this::cancelActiveScan);
        appShell.setInputSourcesLoader(paths -> scanInputSources(startupWorkflow, paths, runEventStore));
        appShell.setExecutionJournal(FileExecutionJournal.userProfileJournal());
        stage.setTitle("Episort");
        // No native chrome: the top bar draws the window buttons itself, which
        // is what keeps them from costing a second row of height.
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.getIcons().add(AppShell.logoImage());
        appShell.setLoading(true, UiText.loadingStartup(viewModel.language()));
        Scene scene = new Scene(appShell.root(), 1180, 760, Color.TRANSPARENT);
        stage.setScene(scene);
        stage.setMinWidth(1180);
        stage.setMinHeight(760);
        stage.show();
        WindowManager windowManager = appShell.installWindowDecorations(stage);
        installWindowShape(stage, scene, appShell, windowManager);
        Platform.runLater(() -> {
            appShell.setLoading(false, "");
            // Story 7.4: an execution that never closed means the app stopped mid-run.
            appShell.reportInterruptedExecution();
        });
        stage.toFront();
        stage.requestFocus();
        appShell.setLanguageChangeListener(language -> settingsStore.saveLanguage(language.name()));
        WindowsTitleBar.applyDarkMode(stage);
        appShell.scanScreen().setTvdbLookup(tvdbClient, tvdbCredentialsSupplier);
    }

    private static void installWindowShape(
            Stage stage, Scene scene, AppShell appShell, WindowManager windowManager) {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(scene.widthProperty());
        clip.heightProperty().bind(scene.heightProperty());
        appShell.root().setClip(clip);

        Runnable refresh = () -> {
            boolean restored = windowManager.state() == com.episort.ui.platform.WindowState.NORMAL
                    && !stage.isFullScreen();
            double arc = restored ? 20 : 0;
            clip.setArcWidth(arc);
            clip.setArcHeight(arc);
            appShell.root().getStyleClass().remove("window-restored");
            if (restored) {
                appShell.root().getStyleClass().add("window-restored");
            }
        };
        windowManager.addStateListener(state -> refresh.run());
        stage.fullScreenProperty().addListener((observable, wasFullScreen, isFullScreen) -> refresh.run());
        refresh.run();
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
    }

    /**
     * Opens a fresh cancellation scope for an analysis, cancelling whatever run
     * was still in flight so a superseded pipeline stops burning TVDB calls.
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
                    TvdbBatchMatchResult batchResult = runTvdbBatch(scanResult, cancellation);
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
                    TvdbBatchMatchResult batchResult = runTvdbBatch(scanResult, cancellation);
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

    private void pushPostScanResultsAfterApply(TvdbBatchMatchResult batchResult) {
        AppShell shell = appShellRef;
        if (shell == null) return;
        // Outer runLater ensures we are on the JFX thread; inner runLater
        // re-enqueues us so we run *after* the AppShell has applied the
        // viewmodel and rebuilt scan rows.
        Platform.runLater(
                () -> Platform.runLater(() -> {
                    if (batchResult != null) {
                        shell.scanScreen().applyTvdbBatchResult(batchResult);
                    }
                    shell.scanScreen().setWorkflowPhase(
                            WorkflowPhase.PLAN_REVIEW, false);
                }));
    }

    private TvdbBatchMatchResult runTvdbBatch(
            InventoryScanResult scanResult, ScanCancellation cancellation) {
        cancellation.throwIfCancelled();
        Optional<TvdbCredentials> credentials = tvdbCredentialsSupplier.get();
        if (credentials.isEmpty()) {
            publishBatchInfo("skipped: no TVDB credentials available");
            return TvdbBatchMatchResult.empty();
        }
        setScanWorkflowPhase(WorkflowPhase.TVDB_MATCHES, true);
        updateLoadingTextForTvdb();
        try {
            return tvdbBatchMatchService.run(scanResult, credentials.orElseThrow(),
                    (done, total) -> {
                        cancellation.throwIfCancelled();
                        updateLoadingForTvdbProgress(done, total);
                    });
        } catch (ScanCancelledException cancelled) {
            throw cancelled;
        } catch (RuntimeException ex) {
            publishBatchInfo("aborted: " + ex.getClass().getSimpleName()
                    + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
            return TvdbBatchMatchResult.empty();
        }
    }

    private static void publishBatchInfo(String message) {
        try {
            TvdbRequestBus.get().publish(new TvdbRequestTrace(
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

    private void updateLoadingTextForTvdb() {
        AppShell shell = appShellRef;
        if (shell == null) return;
        AppLanguage language = shell.currentLanguage();
        Platform.runLater(() -> {
            shell.updateLoadingText(UiText.loadingScanTvdb(language));
            shell.setLoadingProgress(0.0);
        });
    }

    private void updateLoadingForTvdbProgress(int done, int total) {
        AppShell shell = appShellRef;
        if (shell == null || total <= 0) return;
        AppLanguage language = shell.currentLanguage();
        double progress = (double) done / (double) total;
        Platform.runLater(() -> {
            shell.updateLoadingText(UiText.loadingScanTvdb(language));
            shell.setLoadingProgress(progress);
        });
    }

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
