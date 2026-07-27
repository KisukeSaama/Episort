package com.episort.workflow;

import com.episort.config.AppSettings;
import com.episort.config.InvalidSettingsException;
import com.episort.config.SettingsStore;
import com.episort.config.SettingsStoreException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WorkspaceConfigurationService {
    private final SettingsStore settingsStore;

    public WorkspaceConfigurationService(SettingsStore settingsStore) {
        this.settingsStore = settingsStore;
    }

    public WorkspaceConfigurationResult configureWorkspace(Path workspaceDirectory) {
        if (workspaceDirectory == null) {
            return WorkspaceConfigurationResult.failure(AppSettings.empty(), missingWorkspaceError());
        }

        Path normalizedWorkspace = workspaceDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedWorkspace) || !Files.isReadable(normalizedWorkspace)) {
            return WorkspaceConfigurationResult.failure(AppSettings.empty(), invalidWorkspaceError(normalizedWorkspace));
        }

        if (workspaceContainsSettingsFile(normalizedWorkspace)) {
            return WorkspaceConfigurationResult.failure(AppSettings.empty(), workspaceContainsSettingsError());
        }

        AppSettings settings = new AppSettings(normalizedWorkspace);
        try {
            settingsStore.save(settings);
        } catch (SettingsStoreException exception) {
            return WorkspaceConfigurationResult.failure(AppSettings.empty(), settingsUnavailableError());
        }
        return WorkspaceConfigurationResult.success(settings);
    }

    public WorkspaceConfigurationResult loadConfiguredWorkspace() {
        AppSettings settings;
        try {
            settings = settingsStore.load();
        } catch (InvalidSettingsException exception) {
            return WorkspaceConfigurationResult.failure(AppSettings.empty(), invalidWorkspaceError(null));
        } catch (SettingsStoreException exception) {
            return WorkspaceConfigurationResult.failure(AppSettings.empty(), settingsUnavailableError());
        }

        return settings.workspaceDirectory()
                .map(this::validateLoadedWorkspace)
                .orElseGet(() -> WorkspaceConfigurationResult.failure(settings, missingWorkspaceError()));
    }

    private WorkspaceConfigurationResult validateLoadedWorkspace(Path workspaceDirectory) {
        if (!Files.isDirectory(workspaceDirectory) || !Files.isReadable(workspaceDirectory)) {
            return WorkspaceConfigurationResult.failure(new AppSettings(workspaceDirectory), invalidWorkspaceError(workspaceDirectory));
        }

        return WorkspaceConfigurationResult.success(new AppSettings(workspaceDirectory));
    }

    private boolean workspaceContainsSettingsFile(Path workspaceDirectory) {
        return settingsStore.settingsFile()
                .map(settingsFile -> settingsFile.toAbsolutePath().normalize().startsWith(workspaceDirectory))
                .orElse(false);
    }

    private ApplicationError missingWorkspaceError() {
        return ApplicationError.recoverable(
                "WORKSPACE_REQUIRED",
                ErrorSeverity.BLOCKING,
                "Choose a workspace directory before scanning media.",
                "Workspace path has not been configured.");
    }

    private ApplicationError invalidWorkspaceError(Path workspaceDirectory) {
        return ApplicationError.recoverable(
                "WORKSPACE_INVALID",
                ErrorSeverity.BLOCKING,
                "Choose an existing readable workspace directory.",
                "Workspace path is invalid or inaccessible: " + workspaceDirectory);
    }

    private ApplicationError workspaceContainsSettingsError() {
        return ApplicationError.recoverable(
                "WORKSPACE_CONTAINS_SETTINGS",
                ErrorSeverity.BLOCKING,
                "Choose a workspace that does not contain Episort settings.",
                "Workspace contains the settings file location.");
    }

    private ApplicationError settingsUnavailableError() {
        return ApplicationError.recoverable(
                "SETTINGS_UNAVAILABLE",
                ErrorSeverity.BLOCKING,
                "Episort settings are unavailable. Check your user profile permissions.",
                "Settings storage could not be read or written.");
    }
}
