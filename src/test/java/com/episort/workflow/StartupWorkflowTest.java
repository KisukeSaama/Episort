package com.episort.workflow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.config.AppSettings;
import com.episort.config.JanusConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StartupWorkflowTest {
    @TempDir
    Path tempDir;

    @Test
    void organizationPrerequisitesRequireWorkspaceAndVerifiedTmdb() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("media"));
        WorkspaceConfigurationService workspaceService = new WorkspaceConfigurationService(new StubSettingsStore(
                new AppSettings(workspace)));
        TmdbGatewayService tmdbService = new TmdbGatewayService(
                new JanusConfiguration("key", Optional.empty()),
                configuration -> TmdbConnectionTestResult.passed());
        StartupWorkflow workflow = new StartupWorkflow(workspaceService, tmdbService);

        assertTrue(workflow.organizationPrerequisites().organizationAllowed());
    }

    @Test
    void organizationPrerequisitesBlockMissingTmdb() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("media"));
        WorkspaceConfigurationService workspaceService = new WorkspaceConfigurationService(new StubSettingsStore(
                new AppSettings(workspace)));
        StartupWorkflow workflow = new StartupWorkflow(workspaceService, null);

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
