package com.episort.workflow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.config.AppSettings;
import com.episort.config.InMemoryTvdbCredentialStore;
import com.episort.config.TvdbCredentials;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StartupWorkflowTest {
    @TempDir
    Path tempDir;

    @Test
    void organizationPrerequisitesRequireWorkspaceAndVerifiedTvdb() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("media"));
        WorkspaceConfigurationService workspaceService = new WorkspaceConfigurationService(new StubSettingsStore(
                new AppSettings(workspace)));
        InMemoryTvdbCredentialStore credentialStore = new InMemoryTvdbCredentialStore();
        credentialStore.save(new TvdbCredentials("key", Optional.empty()));
        TvdbCredentialConfigurationService tvdbService = new TvdbCredentialConfigurationService(
                credentialStore, credentials -> TvdbConnectionTestResult.passed());
        StartupWorkflow workflow = new StartupWorkflow(workspaceService, tvdbService);

        assertTrue(workflow.organizationPrerequisites().organizationAllowed());
    }

    @Test
    void organizationPrerequisitesBlockMissingTvdb() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("media"));
        WorkspaceConfigurationService workspaceService = new WorkspaceConfigurationService(new StubSettingsStore(
                new AppSettings(workspace)));
        TvdbCredentialConfigurationService tvdbService = new TvdbCredentialConfigurationService(
                new InMemoryTvdbCredentialStore(), credentials -> TvdbConnectionTestResult.passed());
        StartupWorkflow workflow = new StartupWorkflow(workspaceService, tvdbService);

        assertFalse(workflow.organizationPrerequisites().organizationAllowed());
    }

    private record StubSettingsStore(AppSettings settings) implements com.episort.config.SettingsStore {
        @Override
        public AppSettings load() {
            return settings;
        }

        @Override
        public void save(AppSettings settings) {
        }
    }
}
