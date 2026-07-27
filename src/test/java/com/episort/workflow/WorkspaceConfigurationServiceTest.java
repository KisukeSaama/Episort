package com.episort.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.config.AppSettings;
import com.episort.config.FileSettingsStore;
import com.episort.config.InvalidSettingsException;
import com.episort.config.SettingsStore;
import com.episort.config.SettingsStoreException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceConfigurationServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void storesValidWorkspaceAndReloadsItForRestart() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("media"));
        FileSettingsStore store = new FileSettingsStore(tempDir.resolve("settings").resolve("episort.properties"));
        WorkspaceConfigurationService service = new WorkspaceConfigurationService(store);

        WorkspaceConfigurationResult result = service.configureWorkspace(workspace);
        WorkspaceConfigurationResult reloaded = new WorkspaceConfigurationService(store).loadConfiguredWorkspace();

        assertTrue(result.success());
        assertTrue(reloaded.success());
        assertEquals(workspace.toAbsolutePath().normalize(), reloaded.settings().workspaceDirectory().orElseThrow());
    }

    @Test
    void rejectsInvalidWorkspaceWithRecoverableBlockingError() {
        FileSettingsStore store = new FileSettingsStore(tempDir.resolve("settings").resolve("episort.properties"));
        WorkspaceConfigurationService service = new WorkspaceConfigurationService(store);

        WorkspaceConfigurationResult result = service.configureWorkspace(tempDir.resolve("missing"));

        assertFalse(result.success());
        assertEquals("WORKSPACE_INVALID", result.error().orElseThrow().code());
        assertEquals(ErrorSeverity.BLOCKING, result.error().orElseThrow().severity());
        assertTrue(result.error().orElseThrow().recoverable());
        assertFalse(result.error().orElseThrow().safeMessage().contains(tempDir.toString()));
        assertTrue(store.load().workspaceDirectory().isEmpty());
    }

    @Test
    void rejectsNullWorkspaceSelectionAsRecoverableBlockingError() {
        FileSettingsStore store = new FileSettingsStore(tempDir.resolve("settings").resolve("episort.properties"));
        WorkspaceConfigurationService service = new WorkspaceConfigurationService(store);

        WorkspaceConfigurationResult result = service.configureWorkspace(null);

        assertFalse(result.success());
        assertEquals("WORKSPACE_REQUIRED", result.error().orElseThrow().code());
    }

    @Test
    void rejectsWorkspaceThatContainsSettingsFile() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        FileSettingsStore store = new FileSettingsStore(workspace.resolve("Episort").resolve("settings.properties"));
        WorkspaceConfigurationService service = new WorkspaceConfigurationService(store);

        WorkspaceConfigurationResult result = service.configureWorkspace(workspace);

        assertFalse(result.success());
        assertEquals("WORKSPACE_CONTAINS_SETTINGS", result.error().orElseThrow().code());
        assertTrue(store.load().workspaceDirectory().isEmpty());
    }

    @Test
    void malformedPersistedWorkspaceReportsRecoverableBlockingError() {
        WorkspaceConfigurationService service = new WorkspaceConfigurationService(new ThrowingSettingsStore(
                new InvalidSettingsException("Invalid workspace path in settings.", null), null));

        WorkspaceConfigurationResult result = service.loadConfiguredWorkspace();

        assertFalse(result.success());
        assertEquals("WORKSPACE_INVALID", result.error().orElseThrow().code());
        assertFalse(result.error().orElseThrow().safeMessage().contains("InvalidPathException"));
    }

    @Test
    void settingsLoadFailureReportsUiSafeRecoverableError() {
        WorkspaceConfigurationService service = new WorkspaceConfigurationService(new ThrowingSettingsStore(
                new SettingsStoreException("raw failure", new RuntimeException("stack trace")), null));

        WorkspaceConfigurationResult result = service.loadConfiguredWorkspace();

        assertFalse(result.success());
        assertEquals("SETTINGS_UNAVAILABLE", result.error().orElseThrow().code());
        assertFalse(result.error().orElseThrow().safeMessage().contains("stack trace"));
    }

    @Test
    void settingsSaveFailureReportsUiSafeRecoverableError() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("media"));
        WorkspaceConfigurationService service = new WorkspaceConfigurationService(new ThrowingSettingsStore(
                null, new SettingsStoreException("raw failure", new RuntimeException("stack trace"))));

        WorkspaceConfigurationResult result = service.configureWorkspace(workspace);

        assertFalse(result.success());
        assertEquals("SETTINGS_UNAVAILABLE", result.error().orElseThrow().code());
    }

    @Test
    void emptyConfigurationReportsMissingWorkspace() {
        FileSettingsStore store = new FileSettingsStore(tempDir.resolve("settings").resolve("episort.properties"));
        WorkspaceConfigurationService service = new WorkspaceConfigurationService(store);

        WorkspaceConfigurationResult result = service.loadConfiguredWorkspace();

        assertFalse(result.success());
        assertEquals(AppSettings.empty(), result.settings());
        assertEquals("WORKSPACE_REQUIRED", result.error().orElseThrow().code());
    }

    @Test
    void successResultRejectsEmptySettings() {
        assertThrows(IllegalArgumentException.class, () -> WorkspaceConfigurationResult.success(AppSettings.empty()));
    }

    private record ThrowingSettingsStore(
            RuntimeException loadException,
            RuntimeException saveException) implements SettingsStore {
        @Override
        public AppSettings load() {
            if (loadException != null) {
                throw loadException;
            }
            return AppSettings.empty();
        }

        @Override
        public void save(AppSettings settings) {
            if (saveException != null) {
                throw saveException;
            }
        }

        @Override
        public Optional<Path> settingsFile() {
            return Optional.empty();
        }
    }
}
