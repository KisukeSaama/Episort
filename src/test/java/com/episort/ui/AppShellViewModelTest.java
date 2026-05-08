package com.episort.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.config.AppSettings;
import com.episort.workflow.ApplicationError;
import com.episort.workflow.ErrorSeverity;
import com.episort.workflow.StartupWorkflow;
import com.episort.workflow.TvdbCredentialConfigurationResult;
import com.episort.workflow.WorkspaceConfigurationResult;
import com.episort.workflow.InputFolderSelectionResult;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AppShellViewModelTest {
    @Test
    void startsWithStableApplicationShellCopy() {
        AppShellViewModel viewModel = AppShellViewModel.initial();

        assertEquals("Episort", viewModel.title());
        assertEquals("Settings required", viewModel.primaryStatus());
        assertTrue(viewModel.description().contains("workspace"));
    }

    @Test
    void exposesRecoverableErrorsWithoutDetails() {
        AppShellViewModel viewModel = AppShellViewModel.fromError(ApplicationError.recoverable(
                "WORKSPACE_REQUIRED",
                ErrorSeverity.BLOCKING,
                "Choose a workspace directory before scanning media.",
                "C:\\Users\\Jonathan\\PrivateMedia"));

        assertEquals("WORKSPACE_REQUIRED", viewModel.errorCode().orElseThrow());
        assertEquals("Choose a workspace directory before scanning media.", viewModel.primaryStatus());
        assertTrue(viewModel.errorDetails().isEmpty());
    }

    @Test
    void canBeCreatedFromStartupWorkflowError() {
        AppShellViewModel viewModel = AppShellViewModel.fromError(new StartupWorkflow().missingWorkspace());

        assertEquals("WORKSPACE_REQUIRED", viewModel.errorCode().orElseThrow());
        assertEquals("Choose a workspace directory before scanning media.", viewModel.primaryStatus());
    }

    @Test
    void showsConfiguredWorkspaceAfterRestart() {
        Path workspace = Path.of("C:", "Media").toAbsolutePath().normalize();

        AppShellViewModel viewModel = AppShellViewModel.fromWorkspace(workspace);

        assertEquals("Workspace configured", viewModel.primaryStatus());
        assertTrue(viewModel.description().contains(workspace.toString()));
    }

    @Test
    void handlesInconsistentSuccessResultWithoutThrowing() {
        WorkspaceConfigurationResult result = new WorkspaceConfigurationResult(AppSettings.empty(), java.util.Optional.empty());

        assertDoesNotThrow(() -> AppShellViewModel.fromWorkspaceConfiguration(result));
    }

    @Test
    void showsTvdbConnectionTestSuccess() {
        AppShellViewModel viewModel = AppShellViewModel.fromTvdbConfiguration(TvdbCredentialConfigurationResult.passed());

        assertEquals("TVDB connection verified", viewModel.primaryStatus());
        assertTrue(viewModel.description().contains("metadata-backed organization"));
    }

    @Test
    void startupStateShowsTvdbBlockerWhenWorkspaceIsReadyButTvdbIsMissing() {
        Path workspace = Path.of("C:", "Media").toAbsolutePath().normalize();
        WorkspaceConfigurationResult workspaceResult = WorkspaceConfigurationResult.success(new AppSettings(workspace));
        TvdbCredentialConfigurationResult tvdbResult = TvdbCredentialConfigurationResult.failure(ApplicationError.recoverable(
                "TVDB_CONFIGURATION_REQUIRED",
                ErrorSeverity.BLOCKING,
                "Enter and test TVDB access before metadata-backed organization.",
                "No TVDB credentials are configured."));

        AppShellViewModel viewModel = AppShellViewModel.fromStartupPrerequisites(workspaceResult, tvdbResult);

        assertEquals("TVDB_CONFIGURATION_REQUIRED", viewModel.errorCode().orElseThrow());
        assertEquals("Enter and test TVDB access before metadata-backed organization.", viewModel.primaryStatus());
    }

    @Test
    void recoverableErrorIncludesRedactedCodeAndDetails() {
        AppShellViewModel viewModel = AppShellViewModel.fromError(ApplicationError.recoverable(
                "TVDB_CREDENTIALS_UNAVAILABLE",
                ErrorSeverity.BLOCKING,
                "TVDB credentials are unavailable.",
                "apiKey=secret-key could not be read."));

        assertTrue(viewModel.description().contains("TVDB_CREDENTIALS_UNAVAILABLE"));
        assertFalse(viewModel.description().contains("secret-key"));
    }

    @Test
    void showsAcceptedInputFolder() {
        Path input = Path.of("C:", "Media", "Shows").toAbsolutePath().normalize();

        AppShellViewModel viewModel = AppShellViewModel.fromInputFolderSelection(InputFolderSelectionResult.success(input));

        assertEquals("Input folder accepted", viewModel.primaryStatus());
        assertTrue(viewModel.description().contains(input.toString()));
    }
}
