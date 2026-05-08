package com.episort.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.config.AppSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InputFolderSelectionServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void acceptsInputFolderInsideConfiguredWorkspace() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        Path input = Files.createDirectory(workspace.resolve("Season 01"));
        InputFolderSelectionService service = new InputFolderSelectionService();

        InputFolderSelectionResult result = service.selectInputFolder(new AppSettings(workspace), input);

        assertTrue(result.success());
        assertEquals(input.toRealPath(), result.inputFolder().orElseThrow());
    }

    @Test
    void rejectsInputFolderOutsideConfiguredWorkspace() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        InputFolderSelectionService service = new InputFolderSelectionService();

        InputFolderSelectionResult result = service.selectInputFolder(new AppSettings(workspace), outside);

        assertFalse(result.success());
        assertEquals("INPUT_OUTSIDE_WORKSPACE", result.error().orElseThrow().code());
        assertFalse(result.error().orElseThrow().safeMessage().contains(tempDir.toString()));
    }

    @Test
    void rejectsSelectionWhenWorkspaceIsMissing() {
        InputFolderSelectionService service = new InputFolderSelectionService();

        InputFolderSelectionResult result = service.selectInputFolder(AppSettings.empty(), tempDir);

        assertFalse(result.success());
        assertEquals("WORKSPACE_REQUIRED", result.error().orElseThrow().code());
    }
}
