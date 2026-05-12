package com.episort.workflow;

public final class StartupWorkflow {
    private final WorkspaceConfigurationService workspaceConfigurationService;
    private final TvdbCredentialConfigurationService tvdbCredentialConfigurationService;
    private final InputFolderSelectionService inputFolderSelectionService;

    public StartupWorkflow(
            WorkspaceConfigurationService workspaceConfigurationService,
            TvdbCredentialConfigurationService tvdbCredentialConfigurationService) {
        this.workspaceConfigurationService = workspaceConfigurationService;
        this.tvdbCredentialConfigurationService = tvdbCredentialConfigurationService;
        this.inputFolderSelectionService = new InputFolderSelectionService();
    }

    public WorkspaceConfigurationResult loadWorkspaceConfiguration() {
        if (workspaceConfigurationService == null) {
            return WorkspaceConfigurationResult.failure(com.episort.config.AppSettings.empty(), missingWorkspace());
        }

        return workspaceConfigurationService.loadConfiguredWorkspace();
    }

    public WorkspaceConfigurationResult configureWorkspace(java.nio.file.Path workspaceDirectory) {
        if (workspaceConfigurationService == null) {
            return WorkspaceConfigurationResult.failure(com.episort.config.AppSettings.empty(), missingWorkspace());
        }

        return workspaceConfigurationService.configureWorkspace(workspaceDirectory);
    }

    public TvdbCredentialConfigurationResult loadTvdbConfiguration() {
        if (tvdbCredentialConfigurationService == null) {
            return TvdbCredentialConfigurationResult.failure(tvdbConfigurationRequired());
        }

        return tvdbCredentialConfigurationService.currentStatus();
    }

    public TvdbCredentialConfigurationResult configureAndTestTvdb(com.episort.config.TvdbCredentials credentials) {
        if (tvdbCredentialConfigurationService == null) {
            return TvdbCredentialConfigurationResult.failure(tvdbConfigurationRequired());
        }

        return tvdbCredentialConfigurationService.configureAndTest(credentials);
    }

    public OrganizationPrerequisitesResult organizationPrerequisites() {
        WorkspaceConfigurationResult workspaceResult = loadWorkspaceConfiguration();
        if (!workspaceResult.success()) {
            return OrganizationPrerequisitesResult.blocked(workspaceResult.error().orElseThrow());
        }

        TvdbCredentialConfigurationResult tvdbResult = loadTvdbConfiguration();
        if (!tvdbResult.organizationAllowed()) {
            return OrganizationPrerequisitesResult.blocked(tvdbResult.error().orElseThrow());
        }

        return OrganizationPrerequisitesResult.allowed();
    }

    public InputFolderSelectionResult selectInputFolder(java.nio.file.Path inputFolder) {
        if (workspaceConfigurationService == null || inputFolderSelectionService == null) {
            return InputFolderSelectionResult.failure(missingWorkspace());
        }

        return inputFolderSelectionService.selectInputFolder(
                workspaceConfigurationService.loadConfiguredWorkspace().settings(),
                inputFolder);
    }

    public InputSourceSelectionResult selectInputSources(java.util.List<java.nio.file.Path> inputSources) {
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

    private ApplicationError tvdbConfigurationRequired() {
        return ApplicationError.recoverable(
                "TVDB_CONFIGURATION_REQUIRED",
                ErrorSeverity.BLOCKING,
                "Enter and test TVDB access before metadata-backed organization.",
                "No TVDB credential service is configured.");
    }
}
