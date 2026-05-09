package com.episort;

import com.episort.ui.AppShell;
import com.episort.ui.AppShellViewModel;
import com.episort.config.EmbeddedTvdbCredentialsProvider;
import com.episort.config.FileTvdbCredentialStore;
import com.episort.config.FileSettingsStore;
import com.episort.config.TvdbCredentials;
import com.episort.scanner.MediaInventoryScanner;
import com.episort.workflow.StartupWorkflow;
import com.episort.workflow.InventoryWorkflowService;
import com.episort.workflow.TvdbCredentialConfigurationService;
import com.episort.workflow.WorkspaceConfigurationService;
import com.episort.tvdb.HttpTvdbConnectionTester;
import com.episort.workflow.ApplicationError;
import com.episort.workflow.ErrorSeverity;
import com.episort.ui.platform.WindowsTitleBar;
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
        AppShell appShell = new AppShell(
                viewModel,
                workspace -> AppShellViewModel.fromWorkspaceConfiguration(
                        startupWorkflow.configureWorkspace(workspace)),
                inputFolder -> scanInputFolder(startupWorkflow, inputFolder),
                (apiKey, subscriberPin) -> configureTvdb(startupWorkflow, apiKey, subscriberPin),
                () -> startupWorkflow.loadWorkspaceConfiguration().settings().workspaceDirectory(),
                () -> startupWorkflow.loadWorkspaceConfiguration().success(),
                () -> {},
                true);
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

    private CompletableFuture<AppShellViewModel> scanInputFolder(StartupWorkflow startupWorkflow, java.nio.file.Path inputFolder) {
        var selection = startupWorkflow.selectInputFolder(inputFolder);
        if (!selection.success()) {
            return CompletableFuture.completedFuture(AppShellViewModel.fromInputFolderSelection(selection));
        }
        var selectedFolder = selection.inputFolder().orElseThrow();
        InventoryWorkflowService service = new InventoryWorkflowService(new MediaInventoryScanner(), scanExecutor);
        return service.scan(selectedFolder, progress -> {})
                .thenApply(result -> AppShellViewModel.fromInventoryScan(
                        selectedFolder,
                        new com.episort.scanner.InventoryScanResult(result.items(), result.groups(), result.summary())));
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
