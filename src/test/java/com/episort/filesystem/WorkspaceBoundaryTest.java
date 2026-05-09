package com.episort.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
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
        boolean symlinkSupported;
        try {
            Files.createSymbolicLink(link, outside);
            symlinkSupported = true;
        } catch (UnsupportedOperationException | IOException exception) {
            symlinkSupported = false;
        }
        assumeTrue(symlinkSupported, "Symlink creation not supported on this platform/permission set");

        WorkspaceBoundary boundary = new WorkspaceBoundary(workspace);

        assertFalse(boundary.contains(link));
    }

    @Test
    void resolveInsideReturnsCanonicalPathForChildren() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        Path child = Files.createDirectory(workspace.resolve("shows"));

        WorkspaceBoundary boundary = new WorkspaceBoundary(workspace);

        assertEquals(child.toRealPath(), boundary.resolveInside(child).orElseThrow());
    }

    @Test
    void constructorPropagatesIOExceptionWhenWorkspaceIsUnreachable() {
        Path missing = tempDir.resolve("does-not-exist");

        assertThrows(IOException.class, () -> new WorkspaceBoundary(missing));
    }

    @Test
    void containsPropagatesIOExceptionWhenCandidateIsUnreachable() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        Path missing = workspace.resolve("ghost");
        WorkspaceBoundary boundary = new WorkspaceBoundary(workspace);

        assertThrows(IOException.class, () -> boundary.contains(missing));
    }
}
