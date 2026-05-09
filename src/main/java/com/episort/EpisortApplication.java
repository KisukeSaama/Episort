package com.episort;

import com.episort.ui.AppShell;
import com.episort.ui.AppShellViewModel;
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

    @Override
    public void start(Stage stage) {
        StartupWorkflow startupWorkflow = new StartupWorkflow(
                new WorkspaceConfigurationService(FileSettingsStore.userProfileStore()),
                new TvdbCredentialConfigurationService(
                        FileTvdbCredentialStore.userProfileStore(),
                        new HttpTvdbConnectionTester()));
        EmbeddedTvdbCredentialsProvider.load()
                .ifPresent(startupWorkflow::configureAndTestTvdb);
        AppShellViewModel viewModel = AppShellViewModel.fromStartupPrerequisites(
                startupWorkflow.loadWorkspaceConfiguration(),
                startupWorkflow.loadTvdbConfiguration());
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
        stage.setTitle("Episort");
        stage.getIcons().add(AppShell.logoImage());
        stage.setScene(new Scene(appShell.root(), 1180, 760));
        stage.setMinWidth(1180);
        stage.setMinHeight(760);
        stage.show();
        WindowsTitleBar.applyDarkMode(stage);
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void stop() {
        scanExecutor.shutdownNow();
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
                    recordScanCompleted(runEventStore, workspace, selectedFolder, scanResult);
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
