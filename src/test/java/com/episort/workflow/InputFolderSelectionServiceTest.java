package com.episort.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.episort.config.AppSettings;
import java.io.IOException;
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
    void acceptsInputFolderReachedThroughRelativeSegments() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        Path real = Files.createDirectory(workspace.resolve("Season 02"));
        Path indirect = workspace.resolve("Season 02").resolve("..").resolve("Season 02");
        InputFolderSelectionService service = new InputFolderSelectionService();

        InputFolderSelectionResult result = service.selectInputFolder(new AppSettings(workspace), indirect);

        assertTrue(result.success());
        assertEquals(real.toRealPath(), result.inputFolder().orElseThrow());
    }

    @Test
    void rejectsInputFolderEscapingViaRelativeSegments() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        Files.createDirectory(tempDir.resolve("outside"));
        Path escape = workspace.resolve("..").resolve("outside");
        InputFolderSelectionService service = new InputFolderSelectionService();

        InputFolderSelectionResult result = service.selectInputFolder(new AppSettings(workspace), escape);

        assertFalse(result.success());
        assertEquals("INPUT_OUTSIDE_WORKSPACE", result.error().orElseThrow().code());
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
    void rejectsSymlinkEscapeAtServiceBoundary() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Path link = workspace.resolve("escape");
        boolean symlinkSupported;
        try {
            Files.createSymbolicLink(link, outside);
            symlinkSupported = true;
        } catch (UnsupportedOperationException | IOException exception) {
            symlinkSupported = false;
        }
        assumeTrue(symlinkSupported, "Symlink creation not supported on this platform/permission set");
        InputFolderSelectionService service = new InputFolderSelectionService();

        InputFolderSelectionResult result = service.selectInputFolder(new AppSettings(workspace), link);

        assertFalse(result.success());
        assertEquals("INPUT_OUTSIDE_WORKSPACE", result.error().orElseThrow().code());
    }

    @Test
    void reportsWorkspaceUnavailableWhenConfiguredPathDoesNotExist() throws Exception {
        Path workspace = tempDir.resolve("vanished");
        Path input = Files.createDirectory(tempDir.resolve("input"));
        InputFolderSelectionService service = new InputFolderSelectionService();

        InputFolderSelectionResult result = service.selectInputFolder(new AppSettings(workspace), input);

        assertFalse(result.success());
        assertEquals("WORKSPACE_UNAVAILABLE", result.error().orElseThrow().code());
    }

    @Test
    void reportsInputInvalidWhenInputPathDoesNotExist() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        Path missing = workspace.resolve("ghost");
        InputFolderSelectionService service = new InputFolderSelectionService();

        InputFolderSelectionResult result = service.selectInputFolder(new AppSettings(workspace), missing);

        assertFalse(result.success());
        assertEquals("INPUT_FOLDER_INVALID", result.error().orElseThrow().code());
    }

    @Test
    void rejectsBlankInputFolder() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        InputFolderSelectionService service = new InputFolderSelectionService();

        InputFolderSelectionResult result = service.selectInputFolder(new AppSettings(workspace), Path.of(""));

        assertFalse(result.success());
        assertEquals("INPUT_FOLDER_REQUIRED", result.error().orElseThrow().code());
    }

    @Test
    void rejectsSelectionWhenWorkspaceIsMissing() {
        InputFolderSelectionService service = new InputFolderSelectionService();

        InputFolderSelectionResult result = service.selectInputFolder(AppSettings.empty(), tempDir);

        assertFalse(result.success());
        assertEquals("WORKSPACE_REQUIRED", result.error().orElseThrow().code());
    }
}
