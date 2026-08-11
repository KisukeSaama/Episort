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
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

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
    void rejectsSymlinkEvenWhenItsTargetStaysInsideWorkspace() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        Path target = Files.createDirectory(workspace.resolve("target"));
        Path link = workspace.resolve("link");
        assumeSymbolicLink(link, target);

        WorkspaceBoundary boundary = new WorkspaceBoundary(workspace);

        assertFalse(boundary.contains(link));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void rejectsWindowsJunctionEvenWhenItsTargetStaysInsideWorkspace() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        Path target = Files.createDirectory(workspace.resolve("target"));
        Path junction = workspace.resolve("junction");
        Process creation = new ProcessBuilder(
                "cmd.exe", "/c", "mklink", "/J", junction.toString(), target.toString())
                .redirectErrorStream(true)
                .start();
        int exitCode = creation.waitFor();
        String output = new String(creation.getInputStream().readAllBytes());
        assumeTrue(exitCode == 0, "Junction creation unavailable: " + output);

        WorkspaceBoundary boundary = new WorkspaceBoundary(workspace);

        assertFalse(boundary.contains(junction));
        assertFalse(boundary.containsPlanned(junction.resolve("Season 01").resolve("Show - S01E01.mkv")));
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

    @Test
    void plannedDestinationsThatDoNotExistYetAreAcceptedInsideTheWorkspace() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        WorkspaceBoundary boundary = new WorkspaceBoundary(workspace);

        Path planned = workspace.resolve("Firefly").resolve("Season 01").resolve("Firefly - S01E01.mkv");

        assertTrue(boundary.containsPlanned(planned));
        assertEquals(workspace.toRealPath().resolve("Firefly").resolve("Season 01").resolve("Firefly - S01E01.mkv"),
                boundary.resolvePlannedInside(planned).orElseThrow());
    }

    @Test
    void plannedDestinationsOutsideTheWorkspaceAreRejected() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        WorkspaceBoundary boundary = new WorkspaceBoundary(workspace);

        Path escape = workspace.resolve("..").resolve("elsewhere").resolve("Show").resolve("Show - S01E01.mkv");

        assertFalse(boundary.containsPlanned(escape));
    }

    @Test
    void plannedDestinationsCannotEscapeThroughAnExistingSymlink() throws Exception {
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

        assertFalse(boundary.containsPlanned(link.resolve("Show").resolve("Show - S01E01.mkv")));
    }

    @Test
    void plannedDestinationsCannotTraverseAnInternalSymlink() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        Path target = Files.createDirectory(workspace.resolve("target"));
        Path link = workspace.resolve("link");
        assumeSymbolicLink(link, target);

        WorkspaceBoundary boundary = new WorkspaceBoundary(workspace);

        assertFalse(boundary.containsPlanned(link.resolve("Season 01").resolve("Show - S01E01.mkv")));
    }

    private static void assumeSymbolicLink(Path link, Path target) throws Exception {
        boolean symlinkSupported;
        try {
            Files.createSymbolicLink(link, target);
            symlinkSupported = true;
        } catch (UnsupportedOperationException | IOException exception) {
            symlinkSupported = false;
        }
        assumeTrue(symlinkSupported, "Symlink creation not supported on this platform/permission set");
    }
}
