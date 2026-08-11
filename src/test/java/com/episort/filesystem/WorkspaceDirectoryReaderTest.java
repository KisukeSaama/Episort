package com.episort.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceDirectoryReaderTest {
    @TempDir
    Path tempDir;

    private final WorkspaceDirectoryReader reader = new WorkspaceDirectoryReader();

    @Test
    void listsDirectoriesFirstAndClassifiesSupportedMedia() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        Files.writeString(workspace.resolve("notes.txt"), "notes");
        Files.writeString(workspace.resolve("Episode.MKV"), "video");
        Files.createDirectory(workspace.resolve("Series"));

        List<WorkspaceDirectoryEntry> entries = reader.list(workspace, workspace);

        assertEquals(List.of("Series", "Episode.MKV", "notes.txt"),
                entries.stream().map(WorkspaceDirectoryEntry::name).toList());
        assertTrue(entries.get(0).directory());
        assertTrue(entries.get(1).supportedMedia());
        assertFalse(entries.get(2).supportedMedia());
    }

    @Test
    void refusesToListOutsideTheWorkspace() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        Path outside = Files.createDirectory(tempDir.resolve("outside"));

        assertThrows(IOException.class, () -> reader.list(workspace, outside));
    }

    @Test
    void symbolicLinksAreVisibleButNeverExpandable() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Path link = workspace.resolve("external-link");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | IOException exception) {
            return;
        }

        WorkspaceDirectoryEntry entry = reader.list(workspace, workspace).getFirst();

        assertTrue(entry.symbolicLink());
        assertFalse(entry.directory());
        assertFalse(entry.supportedMedia());
    }
}
