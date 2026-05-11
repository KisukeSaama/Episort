package com.episort;

import com.episort.ui.debug.AiDebugWindow;
import com.episort.ai.AiChatService;
import com.episort.ai.AiPatternRefinementResult;
import com.episort.ai.AiPatternRefinementService;
import com.episort.ai.AiPrerequisiteService;
import com.episort.ai.BundledLocalAiPatternAssistant;
import com.episort.ai.BundledLocalAiRuntimeProbe;
import com.episort.ai.embedded.EmbeddedLlamaRuntime;
import com.episort.ai.embedded.LlamaServerClient;
import com.episort.ai.embedded.Qwen3ModelDownloader;
import com.episort.ui.AppLanguage;
import com.episort.ui.AppShell;
import com.episort.ui.AppShellViewModel;
import com.episort.ai.AiModelLibrary;
import com.episort.ui.settings.AiModelsSection;
import com.episort.workflow.AiWorkflowGate;
import com.episort.config.EmbeddedTvdbCredentialsProvider;
import com.episort.config.FileTvdbCredentialStore;
import com.episort.config.FileSettingsStore;
import com.episort.config.TvdbCredentials;
import com.episort.persistence.FileRunEventStore;
import com.episort.persistence.RunEvent;
import com.episort.persistence.RunEventStatus;
import com.episort.persistence.RunEventStore;
import com.episort.persistence.RunEventType;
import com.episort.scanner.InventoryScanResult;
import com.episort.scanner.InventorySummary;
import com.episort.scanner.MediaInventoryScanner;
import com.episort.workflow.StartupWorkflow;
import com.episort.workflow.InventoryWorkflowService;
import com.episort.workflow.TvdbCredentialConfigurationService;
import com.episort.workflow.WorkspaceConfigurationService;
import com.episort.tvdb.HttpTvdbConnectionTester;
import com.episort.workflow.ApplicationError;
import com.episort.workflow.ErrorSeverity;
import com.episort.ui.platform.WindowsTitleBar;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class EpisortApplication extends Application {
    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "episort-scan");
        thread.setDaemon(true);
        return thread;
    });

    private final java.util.concurrent.atomic.AtomicBoolean runtimeStartupSettled =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private final Qwen3ModelDownloader modelDownloader = new Qwen3ModelDownloader();
    private final FileSettingsStore settingsStoreEarly = FileSettingsStore.userProfileStore();
    private final AiModelLibrary aiModelLibrary = new AiModelLibrary(
            modelDownloader.modelPath().getParent(),
            settingsStoreEarly::loadSelectedAiModel,
            settingsStoreEarly::saveSelectedAiModel);
    private final EmbeddedLlamaRuntime embeddedRuntime = new EmbeddedLlamaRuntime(
            EmbeddedLlamaRuntime.defaultRuntimeZipDir(),
            EmbeddedLlamaRuntime.defaultExtractionDir(),
            (java.util.function.Supplier<java.nio.file.Path>) aiModelLibrary::activeModelPath);
    private final AiWorkflowGate aiWorkflowGate = new AiWorkflowGate(new AiPrerequisiteService(
            new BundledLocalAiRuntimeProbe(embeddedRuntime, modelDownloader)));
    private final BundledLocalAiPatternAssistant aiPatternAssistant = new BundledLocalAiPatternAssistant(
            () -> embeddedRuntime.baseUri().map(LlamaServerClient::new));
    private final AiPatternRefinementService aiPatternRefinementService = new AiPatternRefinementService(
            aiWorkflowGate, aiPatternAssistant);
    private final AiChatService aiChatService = new AiChatService(
            aiWorkflowGate,
            () -> embeddedRuntime.baseUri().map(LlamaServerClient::new));

    private volatile AppShell appShellRef;

    private boolean aiEnabledAtStartup = true;

    @Override
    public void start(Stage stage) {
        FileSettingsStore settingsStore = settingsStoreEarly;
        aiEnabledAtStartup = settingsStore.loadAiEnabled();
        StartupWorkflow startupWorkflow = new StartupWorkflow(
                new WorkspaceConfigurationService(settingsStore),
                new TvdbCredentialConfigurationService(
                        FileTvdbCredentialStore.userProfileStore(),
                        new HttpTvdbConnectionTester()));
        EmbeddedTvdbCredentialsProvider.load()
                .ifPresent(startupWorkflow::configureAndTestTvdb);
        AppLanguage resolvedLanguage = settingsStore.loadLanguage()
                .map(EpisortApplication::parseLanguage)
                .orElseGet(AppLanguage::detectFromOs);
        AppShellViewModel viewModel = AppShellViewModel.fromStartupPrerequisites(
                        startupWorkflow.loadWorkspaceConfiguration(),
                        startupWorkflow.loadTvdbConfiguration())
                .withLanguage(resolvedLanguage);
        RunEventStore runEventStore = FileRunEventStore.userProfileStore();
        AppShell appShell = new AppShell(
                viewModel,
                workspace -> AppShellViewModel.fromWorkspaceConfiguration(
                        startupWorkflow.configureWorkspace(workspace)),
                inputFolder -> scanInputFolder(startupWorkflow, inputFolder, runEventStore),
                (apiKey, subscriberPin) -> configureTvdb(startupWorkflow, apiKey, subscriberPin),
                () -> startupWorkflow.loadWorkspaceConfiguration().settings().workspaceDirectory(),
                () -> startupWorkflow.loadWorkspaceConfiguration().success(),
                () -> {},
                runEventStore);
        this.appShellRef = appShell;
        stage.setTitle("Episort");
        stage.getIcons().add(AppShell.logoImage());
        appShell.setLoading(true, com.episort.ui.UiText.loadingStartup(viewModel.language()));
        stage.setScene(new Scene(appShell.root(), 1180, 760));
        stage.setMinWidth(1180);
        stage.setMinHeight(760);
        stage.setMaximized(true);
        stage.show();
        javafx.application.Platform.runLater(() -> appShell.setLoading(false, ""));
        stage.toFront();
        stage.requestFocus();
        appShell.setLanguageChangeListener(language -> settingsStore.saveLanguage(language.name()));
        WindowsTitleBar.applyDarkMode(stage);
        installAiDebugShortcut(stage);
        if (Boolean.getBoolean("episort.aiDebug")) {
            AiDebugWindow.show();
        }
        appShell.setLocalAiReadiness(() -> {
            if (!aiEnabledAtStartup) return true;
            if (!embeddedRuntime.runtimeBinariesAvailable()) return true;
            if (aiModelLibrary.selectedId().isEmpty()) return false;
            // Optimistic: assume the runtime will come up. The async starter
            // flips this once it has settled (success or failure), and we
            // refresh the gate then.
            if (!runtimeStartupSettled.get()) return true;
            return embeddedRuntime.baseUri().isPresent();
        });
        attachAiModelsSection(appShell, viewModel.language(), settingsStore);
        appShell.scanScreen().setAiAssistanceEnabled(aiEnabledAtStartup);
        if (aiEnabledAtStartup) {
            appShell.scanScreen().setAiChatBackend(aiChatService);
            appShell.scanScreen().setAiPatternAssistant(aiPatternAssistant);
            if (embeddedRuntime.runtimeBinariesAvailable() && aiModelLibrary.selectedId().isPresent()) {
                startEmbeddedRuntimeAsync(appShell);
            } else {
                runtimeStartupSettled.set(true);
            }
        } else {
            runtimeStartupSettled.set(true);
        }
    }

    private void installAiDebugShortcut(Stage stage) {
        javafx.scene.input.KeyCombination combo = new javafx.scene.input.KeyCodeCombination(
                javafx.scene.input.KeyCode.D,
                javafx.scene.input.KeyCombination.CONTROL_DOWN,
                javafx.scene.input.KeyCombination.SHIFT_DOWN);
        stage.getScene().getAccelerators().put(combo, AiDebugWindow::toggle);
    }

    private AiModelsSection aiModelsSection;

    private void attachAiModelsSection(
            AppShell appShell,
            com.episort.ui.AppLanguage language,
            FileSettingsStore settingsStore) {
        if (appShell.settingsPane() == null) {
            return;
        }
        aiModelsSection = new AiModelsSection(
                language,
                aiModelLibrary,
                embeddedRuntime,
                () -> {
                    // Hot-swap: the active model selection just changed. Restart the
                    // embedded llama-server in the background so the next AI call
                    // hits the freshly-loaded model.
                    restartEmbeddedRuntimeAsync(appShell);
                },
                aiEnabledAtStartup,
                settingsStore::saveAiEnabled);
        appShell.settingsPane().attachExtraSection(
                aiModelsSection.root(), aiModelsSection::applyLanguage);
    }

    private void restartEmbeddedRuntimeAsync(AppShell appShell) {
        runtimeStartupSettled.set(false);
        javafx.application.Platform.runLater(() -> {
            appShell.refreshPrerequisitesGate();
            appShell.scanScreen().refreshAiChatAvailability();
            if (aiModelsSection != null) aiModelsSection.refresh();
        });
        Thread restarter = new Thread(() -> {
            try {
                if (embeddedRuntime.runtimeBinariesAvailable()
                        && aiModelLibrary.selectedId().isPresent()) {
                    embeddedRuntime.restart(java.time.Duration.ofMinutes(5));
                } else {
                    embeddedRuntime.stop();
                }
            } catch (Exception ignored) {
                // Probe will surface unavailable.
            } finally {
                runtimeStartupSettled.set(true);
                javafx.application.Platform.runLater(() -> {
                    appShell.refreshPrerequisitesGate();
                    appShell.scanScreen().refreshAiChatAvailability();
                    if (aiModelsSection != null) aiModelsSection.refresh();
                    appShell.reanalyzeLastFolder();
                });
            }
        }, "episort-local-ai-restart");
        restarter.setDaemon(true);
        restarter.start();
    }

    private void startEmbeddedRuntimeAsync(AppShell appShell) {
        Thread starter = new Thread(() -> {
            try {
                embeddedRuntime.startBlocking(java.time.Duration.ofMinutes(5));
            } catch (Exception ignored) {
                // Probe will surface unavailable; non-AI flows still work.
            } finally {
                runtimeStartupSettled.set(true);
                javafx.application.Platform.runLater(() -> {
                    appShell.refreshPrerequisitesGate();
                    appShell.scanScreen().refreshAiChatAvailability();
                    if (aiModelsSection != null) aiModelsSection.refresh();
                    // If the user already loaded a folder before the runtime
                    // came up, the original refinement was skipped. Re-run it
                    // now that the AI is available.
                    appShell.reanalyzeLastFolder();
                });
            }
        }, "episort-local-ai-start");
        starter.setDaemon(true);
        starter.start();
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
        embeddedRuntime.close();
    }

    private CompletableFuture<AppShellViewModel> scanInputFolder(
            StartupWorkflow startupWorkflow, Path inputFolder, RunEventStore runEventStore) {
        var selection = startupWorkflow.selectInputFolder(inputFolder);
        if (!selection.success()) {
            return CompletableFuture.completedFuture(AppShellViewModel.fromInputFolderSelection(selection));
        }
        var selectedFolder = selection.inputFolder().orElseThrow();
        Optional<Path> workspace = startupWorkflow.loadWorkspaceConfiguration().settings().workspaceDirectory();
        InventoryWorkflowService service = new InventoryWorkflowService(new MediaInventoryScanner(), scanExecutor);
        return service.scan(selectedFolder, progress -> {})
                .thenApply(result -> {
                    InventoryScanResult scanResult = new InventoryScanResult(result.items(), result.groups(), result.summary());
                    AiPatternRefinementResult refinement = aiPatternRefinementService.refine(
                            scanResult, Optional.of(selectedFolder),
                            (done, total) -> updateLoadingForAiProgress(done, total));
                    recordScanCompleted(runEventStore, workspace, selectedFolder, scanResult, refinement);
                    pushRefinementAfterApply(refinement);
                    return AppShellViewModel.fromInventoryScan(selectedFolder, scanResult);
                })
                .exceptionally(throwable -> {
                    recordScanFailed(runEventStore, workspace, selectedFolder, throwable);
                    return AppShellViewModel.fromError(ApplicationError.recoverable(
                            "INPUT_FOLDER_INVALID",
                            ErrorSeverity.BLOCKING,
                            "Inventory scan failed.",
                            ""));
                });
    }

    private void updateLoadingForAiProgress(int done, int total) {
        AppShell shell = appShellRef;
        if (shell == null || total <= 0) return;
        AppLanguage language = shell.currentLanguage();
        String text = com.episort.ui.UiText.loadingScanAi(language);
        double progress = (double) done / (double) total;
        javafx.application.Platform.runLater(() -> {
            shell.updateLoadingText(text);
            shell.setLoadingProgress(progress);
        });
    }

    private void pushRefinementAfterApply(AiPatternRefinementResult refinement) {
        AppShell shell = appShellRef;
        if (shell == null || refinement == null) return;
        // Outer runLater ensures we are on the JFX thread; inner runLater
        // re-enqueues us so we run *after* the AppShell has applied the
        // viewmodel and rebuilt scan rows.
        javafx.application.Platform.runLater(
                () -> javafx.application.Platform.runLater(
                        () -> shell.scanScreen().applyAiRefinement(refinement)));
    }

    private static void recordScanCompleted(
            RunEventStore store,
            Optional<Path> workspace,
            Path subjectFolder,
            InventoryScanResult result,
            AiPatternRefinementResult refinement) {
        InventorySummary summary = result.summary();
        Map<String, String> metrics = new LinkedHashMap<>();
        metrics.put("supported", String.valueOf(summary.supportedVideoCount()));
        metrics.put("sidecar", String.valueOf(summary.sidecarCount()));
        metrics.put("unsupported", String.valueOf(summary.unsupportedCount()));
        metrics.put("ignored", String.valueOf(summary.ignoredCount()));
        metrics.put("series", String.valueOf(summary.likelySeriesGroupCount()));
        metrics.put("movies", String.valueOf(summary.likelyMovieGroupCount()));
        metrics.put("unknown", String.valueOf(summary.unknownItemCount()));
        metrics.put("aiRefined", String.valueOf(refinement.refined()));
        metrics.put("aiSuggestions", String.valueOf(refinement.suggestions().size()));

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

    private AppShellViewModel configureTvdb(
            StartupWorkflow startupWorkflow,
            String apiKey,
            java.util.Optional<String> subscriberPin) {
        try {
            return AppShellViewModel.fromTvdbConfiguration(
                    startupWorkflow.configureAndTestTvdb(new TvdbCredentials(apiKey, subscriberPin)));
        } catch (IllegalArgumentException exception) {
            return AppShellViewModel.fromError(ApplicationError.recoverable(
                    "TVDB_CONFIGURATION_REQUIRED",
                    ErrorSeverity.BLOCKING,
                    "Enter and test TVDB access before metadata-backed organization.",
                    "TVDB API key was blank."));
        }
    }
}
