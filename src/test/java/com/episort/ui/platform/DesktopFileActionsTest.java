package com.episort.ui.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

class DesktopFileActionsTest {

    @Test
    void windowsOpenParentTargetsTheContainingDirectory() {
        Path file = Path.of("library", "Show.S01E01.mkv").toAbsolutePath().normalize();

        assertEquals(
                List.of("explorer.exe", file.getParent().toString()),
                DesktopFileActions.openParentCommand(file, "Windows 11"));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void windowsOpenParentPreservesUncNetworkPaths() {
        Path file = Path.of("\\\\media-server\\series\\Show\\Show.S01E01.mkv");

        assertEquals(
                List.of("explorer.exe", "\\\\media-server\\series\\Show"),
                DesktopFileActions.openParentCommand(file, "Windows 11"));
    }

    @Test
    void nonWindowsOpenParentFallsBackToDesktopIntegration() {
        Path file = Path.of("library", "Show.S01E01.mkv").toAbsolutePath().normalize();

        assertTrue(DesktopFileActions.openParentCommand(file, "Linux").isEmpty());
    }
}
