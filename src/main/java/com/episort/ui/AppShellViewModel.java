package com.episort.ui;

import com.episort.workflow.ApplicationError;
import com.episort.workflow.TvdbCredentialConfigurationResult;
import com.episort.workflow.WorkspaceConfigurationResult;
import com.episort.workflow.InputFolderSelectionResult;
import java.nio.file.Path;
import java.util.Optional;

public record AppShellViewModel(
        String title,
        String primaryStatus,
        String description,
        Optional<String> errorCode,
        Optional<String> errorDetails) {
    public static AppShellViewModel initial() {
        return new AppShellViewModel(
                "Episort",
                "Settings required",
                "Choose a trusted workspace before scanning media. TVDB setup is required before organization workflows.",
                Optional.empty(),
                Optional.empty());
    }

    public static AppShellViewModel fromError(ApplicationError error) {
        return new AppShellViewModel(
                "Episort",
                error.safeMessage(),
                "Error " + error.code() + ": " + new com.episort.logging.SecretRedactor().redact(error.details()),
                Optional.of(error.code()),
                Optional.empty());
    }

    public static AppShellViewModel fromWorkspace(Path workspaceDirectory) {
        return new AppShellViewModel(
                "Episort",
                "Workspace configured",
                "Current workspace: " + workspaceDirectory.toAbsolutePath().normalize(),
                Optional.empty(),
                Optional.empty());
    }

    public static AppShellViewModel fromWorkspaceConfiguration(WorkspaceConfigurationResult result) {
        if (result.success() && result.settings().workspaceDirectory().isEmpty()) {
            return AppShellViewModel.fromError(ApplicationError.recoverable(
                    "WORKSPACE_REQUIRED",
                    com.episort.workflow.ErrorSeverity.BLOCKING,
                    "Choose a workspace directory before scanning media.",
                    "Workspace configuration result was successful without a workspace directory."));
        }

        return result.settings()
                .workspaceDirectory()
                .filter(ignored -> result.success())
                .map(AppShellViewModel::fromWorkspace)
                .orElseGet(() -> AppShellViewModel.fromError(result.error().orElseThrow()));
    }

    public static AppShellViewModel fromTvdbConfiguration(TvdbCredentialConfigurationResult result) {
        if (result.success()) {
            return new AppShellViewModel(
                    "Episort",
                    "TVDB connection verified",
                    "TVDB access is ready for metadata-backed organization.",
                    Optional.empty(),
                    Optional.empty());
        }

        return AppShellViewModel.fromError(result.error().orElseThrow());
    }

    public static AppShellViewModel fromStartupPrerequisites(
            WorkspaceConfigurationResult workspaceResult,
            TvdbCredentialConfigurationResult tvdbResult) {
        if (!workspaceResult.success()) {
            return fromWorkspaceConfiguration(workspaceResult);
        }
        if (!tvdbResult.organizationAllowed()) {
            return fromTvdbConfiguration(tvdbResult);
        }
        return fromWorkspace(workspaceResult.settings().workspaceDirectory().orElseThrow());
    }

    public static AppShellViewModel fromInputFolderSelection(InputFolderSelectionResult result) {
        if (result.success()) {
            return new AppShellViewModel(
                    "Episort",
                    "Input folder accepted",
                    "Input folder: " + result.inputFolder().orElseThrow(),
                    Optional.empty(),
                    Optional.empty());
        }

        return fromError(result.error().orElseThrow());
    }
}
