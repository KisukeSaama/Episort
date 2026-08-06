package com.episort.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaFileMoverTest {
    @TempDir
    Path tempDir;

    @Test
    void movesAFileIntoNewlyCreatedDestinationFolders() throws IOException {
        Path workspace = workspace();
        Path source = file(workspace, "show.s01e01.mkv");
        Path destination = workspace.resolve(Path.of("Show", "Season 01", "Show - S01E01.mkv"));

        mover(workspace).move(source, destination);

        assertTrue(Files.exists(destination));
        assertFalse(Files.exists(source));
        assertEquals("video-bytes", Files.readString(destination));
    }

    @Test
    void renamingInsideTheSameFolderIsSupported() throws IOException {
        Path workspace = workspace();
        Path source = file(workspace, "raw.mkv");
        Path destination = workspace.resolve("Clean Name.mkv");

        mover(workspace).move(source, destination);

        assertTrue(Files.exists(destination));
        assertFalse(Files.exists(source));
    }

    @Test
    void anOccupiedDestinationIsRefusedInsteadOfOverwritten() throws IOException {
        Path workspace = workspace();
        Path source = file(workspace, "new.mkv");
        Path destination = workspace.resolve("existing.mkv");
        Files.writeString(destination, "precious");

        MediaFileMover mover = mover(workspace);

        assertThrows(FileAlreadyExistsException.class, () -> mover.move(source, destination));
        assertEquals("precious", Files.readString(destination));
        assertTrue(Files.exists(source), "the source must stay untouched when the move is refused");
    }

    @Test
    void anOccupiedDestinationIsOverwrittenOnlyWhenReplacementIsRequested() throws IOException {
        Path workspace = workspace();
        Path source = file(workspace, "new.mkv");
        Path destination = workspace.resolve("existing.mkv");
        Files.writeString(destination, "outdated");

        mover(workspace).move(source, destination, true);

        assertEquals("video-bytes", Files.readString(destination));
        assertFalse(Files.exists(source));
    }

    @Test
    void aFolderStandingAtTheDestinationIsNeverReplaced() throws IOException {
        Path workspace = workspace();
        Path source = file(workspace, "new.mkv");
        Path destination = workspace.resolve("Show");
        Files.createDirectory(destination);
        Files.writeString(destination.resolve("kept.mkv"), "precious");

        MediaFileMover mover = mover(workspace);

        assertThrows(IOException.class, () -> mover.move(source, destination, true));
        assertEquals("precious", Files.readString(destination.resolve("kept.mkv")));
        assertTrue(Files.exists(source));
    }

    @Test
    void aMissingSourceFailsWithoutCreatingAnything() throws IOException {
        Path workspace = workspace();
        Path missing = workspace.resolve("ghost.mkv");
        Path destination = workspace.resolve(Path.of("Show", "ghost.mkv"));

        MediaFileMover mover = mover(workspace);

        assertThrows(NoSuchFileException.class, () -> mover.move(missing, destination));
        assertFalse(Files.exists(workspace.resolve("Show")));
    }

    @Test
    void destinationsOutsideTheWorkspaceAreRefused() throws IOException {
        Path workspace = workspace();
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Path source = file(workspace, "show.mkv");

        MediaFileMover mover = mover(workspace);

        IOException failure = assertThrows(IOException.class,
                () -> mover.move(source, outside.resolve("show.mkv")));
        assertTrue(failure.getMessage().contains("outside the configured workspace"));
        assertTrue(Files.exists(source));
    }

    @Test
    void sourcesOutsideTheWorkspaceAreRefused() throws IOException {
        Path workspace = workspace();
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Path source = outside.resolve("show.mkv");
        Files.writeString(source, "video-bytes");

        MediaFileMover mover = mover(workspace);

        assertThrows(IOException.class, () -> mover.move(source, workspace.resolve("show.mkv")));
        assertTrue(Files.exists(source));
    }

    @Test
    void existingFoldersAreReusedAndOnlyMissingOnesAreCreated() throws IOException {
        Path workspace = workspace();
        Files.createDirectories(workspace.resolve("Show"));

        List<Path> created = mover(workspace).createFolders(workspace.resolve(Path.of("Show", "Season 01")));

        assertEquals(List.of(workspace.toRealPath().resolve(Path.of("Show", "Season 01"))), created);
    }

    @Test
    void creatingFoldersTwiceIsANoOpTheSecondTime() throws IOException {
        Path workspace = workspace();
        MediaFileMover mover = mover(workspace);
        Path folder = workspace.resolve(Path.of("Show", "Season 01"));

        mover.createFolders(folder);

        assertTrue(mover.createFolders(folder).isEmpty());
    }

    @Test
    void deletingASourceFolderTakesEverythingLeftInsideIt() throws IOException {
        Path workspace = workspace();
        Path release = Files.createDirectories(workspace.resolve(Path.of("Show.S01", "Sample")));
        Files.writeString(release.resolve("sample.mkv"), "leftover");
        Files.writeString(workspace.resolve(Path.of("Show.S01", "show.nfo")), "metadata");

        assertTrue(mover(workspace).deleteFolderTree(workspace.resolve("Show.S01")));

        assertFalse(Files.exists(workspace.resolve("Show.S01")));
    }

    @Test
    void deletingAFolderThatIsAlreadyGoneIsANoOp() throws IOException {
        Path workspace = workspace();

        assertFalse(mover(workspace).deleteFolderTree(workspace.resolve("never-existed")));
    }

    @Test
    void theWorkspaceRootIsNeverDeleted() throws IOException {
        Path workspace = workspace();
        MediaFileMover mover = mover(workspace);

        IOException failure = assertThrows(IOException.class, () -> mover.deleteFolderTree(workspace));
        assertTrue(failure.getMessage().contains("workspace root"));
        assertTrue(Files.exists(workspace));
    }

    @Test
    void foldersOutsideTheWorkspaceAreNeverDeleted() throws IOException {
        Path workspace = workspace();
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Files.writeString(outside.resolve("precious.mkv"), "not ours");

        MediaFileMover mover = mover(workspace);

        assertThrows(IOException.class, () -> mover.deleteFolderTree(outside));
        assertTrue(Files.exists(outside.resolve("precious.mkv")));
    }

    @Test
    void anEmptyOnlyDeleteRefusesAFolderThatStillHoldsSomething() throws IOException {
        Path workspace = workspace();
        Path folder = Files.createDirectory(workspace.resolve("Show"));
        Files.writeString(folder.resolve("keep.mkv"), "video-bytes");

        assertFalse(mover(workspace).deleteFolderIfEmpty(folder));
        assertTrue(Files.exists(folder.resolve("keep.mkv")));
    }

    @Test
    void anEmptyOnlyDeleteRemovesAnEmptiedFolder() throws IOException {
        Path workspace = workspace();
        Path folder = Files.createDirectory(workspace.resolve("Show"));

        assertTrue(mover(workspace).deleteFolderIfEmpty(folder));
        assertFalse(Files.exists(folder));
    }

    /* ---- Deleting one approved file ------------------------------------ */

    @Test
    void anApprovedFileIsRemovedFromDisk() throws IOException {
        Path workspace = workspace();
        Path duplicate = file(workspace, "show.s01e01.720p.mkv");
        Path keeper = file(workspace, "show.s01e01.1080p.mkv");

        mover(workspace).deleteFile(duplicate);

        assertFalse(Files.exists(duplicate));
        assertTrue(Files.exists(keeper), "only the file that was asked for may go");
    }

    @Test
    void deletingRefusesAFileOutsideTheWorkspace() throws IOException {
        Path workspace = workspace();
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Path stranger = outside.resolve("precious.mkv");
        Files.writeString(stranger, "not yours");

        MediaFileMover mover = mover(workspace);

        assertThrows(IOException.class, () -> mover.deleteFile(stranger));
        assertTrue(Files.exists(stranger));
    }

    @Test
    void deletingRefusesAFolder() throws IOException {
        Path workspace = workspace();
        Path folder = Files.createDirectory(workspace.resolve("Show"));
        Path inside = file(folder, "show.s01e01.mkv");

        MediaFileMover mover = mover(workspace);

        assertThrows(IOException.class, () -> mover.deleteFile(folder));
        assertTrue(Files.exists(inside));
    }

    @Test
    void deletingRefusesTheWorkspaceRoot() throws IOException {
        Path workspace = workspace();
        MediaFileMover mover = mover(workspace);

        assertThrows(IOException.class, () -> mover.deleteFile(workspace));
        assertTrue(Files.isDirectory(workspace));
    }

    @Test
    void deletingAFileThatIsAlreadyGoneIsReportedAsSuch() throws IOException {
        Path workspace = workspace();
        MediaFileMover mover = mover(workspace);

        assertThrows(NoSuchFileException.class, () -> mover.deleteFile(workspace.resolve("ghost.mkv")));
    }

    @Test
    void renamesANonEmptyFolderInsideItsParent() throws IOException {
        Path workspace = workspace();
        Path source = Files.createDirectory(workspace.resolve("Show.S01"));
        Files.writeString(source.resolve("show.nfo"), "metadata");
        Path destination = workspace.resolve("[TRI]Show.S01");

        mover(workspace).renameFolder(source, destination);

        assertFalse(Files.exists(source));
        assertTrue(Files.exists(destination.resolve("show.nfo")));
    }

    @Test
    void renamingAFolderRefusesAnOccupiedDestination() throws IOException {
        Path workspace = workspace();
        Path source = Files.createDirectory(workspace.resolve("Show.S01"));
        Path destination = Files.createDirectory(workspace.resolve("[TRI]Show.S01"));

        assertThrows(FileAlreadyExistsException.class, () -> mover(workspace).renameFolder(source, destination));
        assertTrue(Files.exists(source));
    }

    private Path workspace() throws IOException {
        return Files.createDirectories(tempDir.resolve("workspace"));
    }

    private static MediaFileMover mover(Path workspace) throws IOException {
        return new MediaFileMover(new WorkspaceBoundary(workspace));
    }

    private static Path file(Path workspace, String name) throws IOException {
        Path path = workspace.resolve(name);
        Files.writeString(path, "video-bytes");
        return path;
    }
}
