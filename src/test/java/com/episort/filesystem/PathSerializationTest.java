package com.episort.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PathSerializationTest {
    @TempDir
    Path tempDir;

    @Test
    void localPathsAreTheirOwnText() {
        Path path = tempDir.resolve("Show").resolve("ep.mkv");

        String text = PathSerialization.encode(path);

        assertEquals(path.toString(), text);
        assertEquals(path, PathSerialization.decode(text).orElseThrow());
    }

    @Test
    void blankOrMalformedTextDecodesToNothing() {
        assertTrue(PathSerialization.decode("").isEmpty());
        assertTrue(PathSerialization.decode(null).isEmpty());
        assertTrue(PathSerialization.decode("sftp://not a uri").isEmpty());
    }

    @Test
    void remotePathsComeBackOnlyWhileTheirServerIsConnected() throws IOException {
        Path serverRoot = Files.createDirectories(tempDir.resolve("server"));
        Files.createDirectories(serverRoot.resolve("Show"));
        try (EmbeddedSftpServer server = EmbeddedSftpServer.start(serverRoot);
                RemoteWorkspaceSessions sessions = new RemoteWorkspaceSessions(tempDir.resolve("known_hosts"))) {
            Path root = sessions.connect(server.workspace("/"));
            Path folder = root.resolve("Show");

            String text = PathSerialization.encode(folder);

            assertTrue(PathSerialization.isRemote(text));
            assertTrue(text.startsWith("sftp://" + EmbeddedSftpServer.USER + "@"));
            assertEquals(folder, PathSerialization.decode(text).orElseThrow());

            sessions.close();
            assertTrue(PathSerialization.decode(text).isEmpty(), "a disconnected server yields no path");
        }
    }
}
