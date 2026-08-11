package com.episort.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSettingsStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsWorkspaceOutsideWorkspaceDirectory() {
        Path workspace = tempDir.resolve("media");
        Path settingsFile = tempDir.resolve("settings").resolve("episort.properties");
        FileSettingsStore store = new FileSettingsStore(settingsFile);

        store.save(new AppSettings(workspace));

        assertTrue(store.load().workspaceDirectory().isPresent());
        assertEquals(workspace.toAbsolutePath().normalize(), store.load().workspaceDirectory().orElseThrow());
        assertFalse(settingsFile.normalize().startsWith(workspace.normalize()));
    }

    @Test
    void missingSettingsFileLoadsEmptySettings() {
        FileSettingsStore store = new FileSettingsStore(tempDir.resolve("missing").resolve("episort.properties"));

        assertTrue(store.load().workspaceDirectory().isEmpty());
    }

    @Test
    void malformedWorkspacePathThrowsInvalidSettingsException() throws Exception {
        Path settingsFile = tempDir.resolve("settings").resolve("episort.properties");
        Files.createDirectories(settingsFile.getParent());
        Files.writeString(settingsFile, "workspaceDirectory=\u0000bad");

        FileSettingsStore store = new FileSettingsStore(settingsFile);

        assertThrows(InvalidSettingsException.class, store::load);
    }

    @Test
    void userProfileStorePrefersLocalAppDataOnWindows() {
        FileSettingsStore store = FileSettingsStore.userProfileStore(
                "Windows 11",
                Map.of("LOCALAPPDATA", "C:\\Users\\Alex\\AppData\\Local"),
                Path.of("C:\\Users\\Alex"));

        assertEquals(
                Path.of("C:\\Users\\Alex\\AppData\\Local\\Episort\\settings.properties"),
                store.settingsFile().orElseThrow());
    }

    @Test
    void appSettingsRejectsNullOptional() {
        assertThrows(NullPointerException.class, () -> new AppSettings((Optional<Path>) null));
    }
}
