package com.episort.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.scanner.InventoryScanResult;
import com.episort.scanner.MediaInventoryScanner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The scanner, the boundary and the mover against a real SFTP session: what
 * the application does to a Plex server, exercised end to end on localhost.
 */
class RemoteMediaFileMoverTest {
    @TempDir
    Path tempDir;

    private Path serverRoot;
    private EmbeddedSftpServer server;
    private RemoteWorkspaceSessions sessions;
    private Path workspace;

    @BeforeEach
    void mountWorkspace() throws IOException {
        serverRoot = Files.createDirectories(tempDir.resolve("server"));
        Files.createDirectories(serverRoot.resolve("media"));
        Files.createDirectories(serverRoot.resolve("elsewhere"));
        server = EmbeddedSftpServer.start(serverRoot);
        sessions = new RemoteWorkspaceSessions(tempDir.resolve("known_hosts"));
        workspace = sessions.connect(server.workspace("/media"));
    }

    @AfterEach
    void unmount() throws IOException {
        sessions.close();
        server.close();
    }

    @Test
    void scansTheRemoteTreeAndMovesIntoNewFolders() throws IOException {
        Path local = serverRoot.resolve("media").resolve("Some.Show.S01E01.mkv");
        Files.writeString(local, "video-bytes");

        InventoryScanResult scan = new MediaInventoryScanner().scan(workspace, progress -> { });
        assertEquals(1, scan.items().size());
        assertTrue(RemoteWorkspaceSessions.isRemote(scan.items().getFirst().sourcePath()));

        Path source = workspace.resolve("Some.Show.S01E01.mkv");
        Path destination = workspace.resolve("Some Show").resolve("Season 01").resolve("Some Show - S01E01.mkv");
        MediaFileMover mover = new MediaFileMover(new WorkspaceBoundary(workspace));
        mover.move(source, destination);

        assertTrue(Files.exists(destination));
        assertFalse(Files.exists(source));
        assertEquals("video-bytes", Files.readString(
                serverRoot.resolve("media").resolve("Some Show").resolve("Season 01").resolve("Some Show - S01E01.mkv")));
    }

    @Test
    void refusesToLeaveTheRemoteWorkspace() throws IOException {
        Files.writeString(serverRoot.resolve("media").resolve("clip.mkv"), "bytes");
        Path source = workspace.resolve("clip.mkv");
        Path outside = workspace.getFileSystem().getPath("/elsewhere/clip.mkv");
        MediaFileMover mover = new MediaFileMover(new WorkspaceBoundary(workspace));

        IOException failure = assertThrows(IOException.class, () -> mover.move(source, outside));

        assertTrue(failure.getMessage().contains("outside the configured workspace"));
        assertTrue(Files.exists(serverRoot.resolve("media").resolve("clip.mkv")));
        assertFalse(Files.exists(serverRoot.resolve("elsewhere").resolve("clip.mkv")));
    }

    @Test
    void deletesARemoteFileOutrightAndFingerprintsRemoteFiles() throws IOException {
        Files.writeString(serverRoot.resolve("media").resolve("dupe.mkv"), "same-bytes");
        Path remote = workspace.resolve("dupe.mkv");
        MediaFileFingerprint fingerprint = MediaFileFingerprint.capture(remote);
        assertTrue(fingerprint.matches(remote));

        new MediaFileMover(new WorkspaceBoundary(workspace)).deleteFile(remote);

        assertFalse(Files.exists(serverRoot.resolve("media").resolve("dupe.mkv")));
    }

    @Test
    void listsARemoteDirectoryForTheExplorer() throws IOException {
        Files.createDirectories(serverRoot.resolve("media").resolve("Folder"));
        Files.writeString(serverRoot.resolve("media").resolve("movie.mp4"), "bytes");
        Files.writeString(serverRoot.resolve("media").resolve("notes.txt"), "bytes");

        List<WorkspaceDirectoryEntry> entries = new WorkspaceDirectoryReader().list(workspace, workspace);

        assertEquals(List.of("Folder", "movie.mp4", "notes.txt"),
                entries.stream().map(WorkspaceDirectoryEntry::name).toList());
        assertTrue(entries.get(0).directory());
        assertTrue(entries.get(1).supportedMedia());
        assertFalse(entries.get(2).supportedMedia());
    }
}
