package com.episort.filesystem;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceBoundaryTest {
    @TempDir
    Path tempDir;

    @Test
    void acceptsFolderInsideWorkspaceAfterNormalization() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        Path input = Files.createDirectories(workspace.resolve("shows").resolve("..").resolve("shows"));

        WorkspaceBoundary boundary = new WorkspaceBoundary(workspace);

        assertTrue(boundary.contains(input));
    }

    @Test
    void rejectsFolderOutsideWorkspaceAfterNormalization() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        Path outside = Files.createDirectory(tempDir.resolve("outside"));

        WorkspaceBoundary boundary = new WorkspaceBoundary(workspace);

        assertFalse(boundary.contains(workspace.resolve("..").resolve(outside.getFileName())));
    }

    @Test
    void rejectsSymlinkEscapeWhenSupported() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Path link = workspace.resolve("link");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | java.io.IOException exception) {
            return;
        }

        WorkspaceBoundary boundary = new WorkspaceBoundary(workspace);

        assertFalse(boundary.contains(link));
    }
}
