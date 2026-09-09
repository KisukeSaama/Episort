package com.episort.workflow;

import com.episort.config.AppSettings;
import com.episort.config.JanusConfiguration;
import com.episort.config.WorkspaceLocation;
import com.episort.config.WorkspaceLocation.RemoteWorkspace;
import com.episort.filesystem.RemoteWorkspaceSessions;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class StartupWorkflow {
    private final WorkspaceConfigurationService workspaceConfigurationService;
    private final TmdbGatewayService tmdbCredentialConfigurationService;
    private final InputFolderSelectionService inputFolderSelectionService;

    public StartupWorkflow(
            WorkspaceConfigurationService workspaceConfigurationService,
            TmdbGatewayService tmdbCredentialConfigurationService) {
        this.workspaceConfigurationService = workspaceConfigurationService;
        this.tmdbCredentialConfigurationService = tmdbCredentialConfigurationService;
        this.inputFolderSelectionService = new InputFolderSelectionService();
    }

    public WorkspaceConfigurationResult loadWorkspaceConfiguration() {
        if (workspaceConfigurationService == null) {
            return WorkspaceConfigurationResult.failure(AppSettings.empty(), missingWorkspace());
        }

        return workspaceConfigurationService.loadConfiguredWorkspace();
    }

    public WorkspaceConfigurationResult configureWorkspace(Path workspaceDirectory) {
        if (workspaceConfigurationService == null) {
            return WorkspaceConfigurationResult.failure(AppSettings.empty(), missingWorkspace());
        }

        return workspaceConfigurationService.configureWorkspace(workspaceDirectory);
    }

    /** Blocking: connects to the server before recording the location. */
    public WorkspaceConfigurationResult configureRemoteWorkspace(RemoteWorkspace location) {
        if (workspaceConfigurationService == null) {
            return WorkspaceConfigurationResult.failure(AppSettings.empty(), missingWorkspace());
        }

        return workspaceConfigurationService.configureRemoteWorkspace(location);
    }

    /** Blocking: opens the session of a recorded remote workspace. */
    public WorkspaceConfigurationResult connectConfiguredWorkspace() {
        if (workspaceConfigurationService == null) {
            return WorkspaceConfigurationResult.failure(AppSettings.empty(), missingWorkspace());
        }

        return workspaceConfigurationService.connectConfiguredWorkspace();
    }

    public Optional<WorkspaceLocation> configuredWorkspaceLocation() {
        if (workspaceConfigurationService == null) {
            return Optional.empty();
        }
        return workspaceConfigurationService.configuredLocation();
    }

    public void disconnectRemoteWorkspace() {
        if (workspaceConfigurationService != null) {
            workspaceConfigurationService.disconnectRemoteWorkspace();
        }
    }

    public Optional<RemoteWorkspaceSessions> remoteWorkspaceSessions() {
        return Optional.ofNullable(workspaceConfigurationService).map(WorkspaceConfigurationService::sessions);
    }

    public TmdbGatewayStatus loadTmdbConfiguration() {
        if (tmdbCredentialConfigurationService == null) {
            return TmdbGatewayStatus.failure(tmdbConfigurationRequired());
        }

        return tmdbCredentialConfigurationService.currentStatus();
    }

    public OrganizationPrerequisitesResult organizationPrerequisites() {
        WorkspaceConfigurationResult workspaceResult = loadWorkspaceConfiguration();
        if (!workspaceResult.success()) {
            return OrganizationPrerequisitesResult.blocked(workspaceResult.error().orElseThrow());
        }

        TmdbGatewayStatus tmdbResult = loadTmdbConfiguration();
        if (!tmdbResult.organizationAllowed()) {
            return OrganizationPrerequisitesResult.blocked(tmdbResult.error().orElseThrow());
        }

        return OrganizationPrerequisitesResult.allowed();
    }

    public InputFolderSelectionResult selectInputFolder(Path inputFolder) {
        if (workspaceConfigurationService == null || inputFolderSelectionService == null) {
            return InputFolderSelectionResult.failure(missingWorkspace());
        }

        return inputFolderSelectionService.selectInputFolder(
                workspaceConfigurationService.loadConfiguredWorkspace().settings(),
                inputFolder);
    }

    public InputSourceSelectionResult selectInputSources(List<Path> inputSources) {
        if (workspaceConfigurationService == null || inputFolderSelectionService == null) {
            return InputSourceSelectionResult.failure(missingWorkspace());
        }

        return inputFolderSelectionService.selectInputSources(
                workspaceConfigurationService.loadConfiguredWorkspace().settings(),
                inputSources);
    }

    public static ApplicationError missingWorkspace() {
        return ApplicationError.recoverable(
                "WORKSPACE_REQUIRED",
                ErrorSeverity.BLOCKING,
                "Choose a workspace directory before scanning media.",
                "Workspace path has not been configured.");
    }

    private ApplicationError tmdbConfigurationRequired() {
        return ApplicationError.recoverable(
                "TMDB_CONFIGURATION_REQUIRED",
                ErrorSeverity.BLOCKING,
                "TMDB is unavailable.",
                "No Janus TMDB gateway is configured.");
    }
}
