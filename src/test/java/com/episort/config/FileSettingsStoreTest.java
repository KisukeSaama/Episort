package com.episort.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import com.episort.ui.platform.WindowBounds;
import com.episort.ui.platform.WindowState;
import com.episort.ui.ThemePreference;
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

    @Test
    void persistsWindowPlacementWithoutOverwritingOtherSettings() {
        Path settingsFile = tempDir.resolve("settings").resolve("episort.properties");
        FileSettingsStore store = new FileSettingsStore(settingsFile);
        store.saveLanguage("FR");
        WindowPlacement placement = new WindowPlacement(
                new WindowBounds(120, 80, 1440, 900), WindowState.MAXIMIZED);

        store.saveWindowPlacement(placement);

        assertEquals(placement, store.loadWindowPlacement().orElseThrow());
        assertEquals("FR", store.loadLanguage().orElseThrow());
    }

    @Test
    void ignoresMalformedWindowPlacement() throws Exception {
        Path settingsFile = tempDir.resolve("settings").resolve("episort.properties");
        Files.createDirectories(settingsFile.getParent());
        Files.writeString(settingsFile, "window.x=0\nwindow.y=0\nwindow.width=nope\nwindow.height=900\n");

        assertTrue(new FileSettingsStore(settingsFile).loadWindowPlacement().isEmpty());
    }

    @Test
    void persistsThemePreferenceAndKeepsFirstLaunchUnset() {
        Path settingsFile = tempDir.resolve("settings").resolve("episort.properties");
        FileSettingsStore store = new FileSettingsStore(settingsFile);

        assertTrue(store.loadThemePreference().isEmpty());
        store.saveThemePreference(ThemePreference.SYSTEM);

        assertEquals(ThemePreference.SYSTEM, store.loadThemePreference().orElseThrow());
    }

    @Test
    void ignoresUnknownThemePreference() throws Exception {
        Path settingsFile = tempDir.resolve("settings").resolve("episort.properties");
        Files.createDirectories(settingsFile.getParent());
        Files.writeString(settingsFile, "theme.preference=NEON\n");

        assertTrue(new FileSettingsStore(settingsFile).loadThemePreference().isEmpty());
    }
}
