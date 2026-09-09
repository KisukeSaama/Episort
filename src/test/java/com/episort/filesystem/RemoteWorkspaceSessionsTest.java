package com.episort.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.config.SftpAuthentication;
import com.episort.config.SftpEndpoint;
import com.episort.config.WorkspaceLocation.RemoteWorkspace;
import com.episort.filesystem.RemoteWorkspaceException.Reason;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.List;
import java.util.stream.Stream;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RemoteWorkspaceSessionsTest {
    @TempDir
    Path tempDir;

    private Path serverRoot;
    private Path knownHosts;
    private EmbeddedSftpServer server;
    private RemoteWorkspaceSessions sessions;

    @BeforeEach
    void startServer() throws IOException {
        serverRoot = Files.createDirectories(tempDir.resolve("server"));
        knownHosts = tempDir.resolve("config").resolve("known_hosts");
        server = EmbeddedSftpServer.start(serverRoot);
        sessions = new RemoteWorkspaceSessions(knownHosts);
    }

    @AfterEach
    void stopServer() throws IOException {
        sessions.close();
        server.close();
    }

    @Test
    void connectMountsTheRemoteRootAsALiveDirectory() throws IOException {
        Files.createDirectories(serverRoot.resolve("media").resolve("Show"));
        Files.writeString(serverRoot.resolve("media").resolve("Show").resolve("ep.mkv"), "bytes");
        RemoteWorkspace workspace = server.workspace("/media");

        Path root = sessions.connect(workspace);

        assertTrue(Files.isDirectory(root));
        assertTrue(RemoteWorkspaceSessions.isRemote(root));
        try (Stream<Path> children = Files.list(root.resolve("Show"))) {
            assertEquals(List.of("ep.mkv"), children.map(path -> path.getFileName().toString()).toList());
        }
        assertEquals(root, sessions.mountedRoot(workspace).orElseThrow());
        assertTrue(sessions.isConnected(workspace));
    }

    @Test
    void mountedRootIsEmptyUntilConnected() {
        assertTrue(sessions.mountedRoot(server.workspace("/")).isEmpty());
        assertFalse(sessions.isConnected(server.workspace("/")));
    }

    @Test
    void firstConnectionRecordsTheHostKeyAndReusesTheSession() throws IOException {
        RemoteWorkspace workspace = server.workspace("/");

        Path first = sessions.connect(workspace);
        Path second = sessions.connect(workspace.withRootPath("/"));

        assertTrue(Files.exists(knownHosts), "known_hosts must be written on first use");
        assertTrue(Files.readString(knownHosts).contains(String.valueOf(server.port())));
        assertEquals(first.getFileSystem(), second.getFileSystem(), "one session per server and account");
    }

    @Test
    void wrongPasswordIsReportedAsAuthenticationFailure() {
        SftpEndpoint endpoint = server.endpoint(SftpAuthentication.Password.of("nope"));

        RemoteWorkspaceException failure = assertThrows(RemoteWorkspaceException.class,
                () -> sessions.connect(new RemoteWorkspace(endpoint, "/")));

        assertEquals(Reason.AUTHENTICATION_FAILED, failure.reason());
        assertTrue(sessions.mountedRoot(new RemoteWorkspace(endpoint, "/")).isEmpty());
    }

    @Test
    void closedPortIsReportedAsUnreachable() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        SftpEndpoint endpoint = new SftpEndpoint(
                EmbeddedSftpServer.HOST, closedPort, EmbeddedSftpServer.USER,
                SftpAuthentication.Password.of(EmbeddedSftpServer.PASSWORD));

        RemoteWorkspaceException failure = assertThrows(RemoteWorkspaceException.class,
                () -> sessions.connect(new RemoteWorkspace(endpoint, "/")));

        assertEquals(Reason.UNREACHABLE, failure.reason());
    }

    @Test
    void missingRemoteRootIsReportedWithoutDroppingTheSession() throws IOException {
        RemoteWorkspaceException failure = assertThrows(RemoteWorkspaceException.class,
                () -> sessions.connect(server.workspace("/does-not-exist")));

        assertEquals(Reason.ROOT_INVALID, failure.reason());
        assertTrue(Files.isDirectory(sessions.connect(server.workspace("/"))));
    }

    @Test
    void unreadableKeyFileIsReportedAsSuch() throws IOException {
        Path bogusKey = Files.writeString(tempDir.resolve("id_bogus"), "not a key", StandardCharsets.UTF_8);
        SftpEndpoint endpoint = server.endpoint(SftpAuthentication.PrivateKey.of(bogusKey, null));

        RemoteWorkspaceException failure = assertThrows(RemoteWorkspaceException.class,
                () -> sessions.connect(new RemoteWorkspace(endpoint, "/")));

        assertEquals(Reason.KEY_UNREADABLE, failure.reason());
    }

    @Test
    void changedHostKeyIsRefused() throws Exception {
        KeyPair impostor = KeyUtils.generateKeyPair("ssh-rsa", 2048);
        String entry = PublicKeyEntry.toString(impostor.getPublic());
        Files.createDirectories(knownHosts.getParent());
        Files.writeString(knownHosts,
                "[" + EmbeddedSftpServer.HOST + "]:" + server.port() + " " + entry + "\n",
                StandardCharsets.UTF_8);

        RemoteWorkspaceException failure = assertThrows(RemoteWorkspaceException.class,
                () -> sessions.connect(server.workspace("/")));

        assertEquals(Reason.HOST_KEY_CHANGED, failure.reason());
    }

    @Test
    void remotePathsRoundTripThroughTheirUri() throws IOException {
        Files.createDirectories(serverRoot.resolve("media").resolve("A Show"));
        Path root = sessions.connect(server.workspace("/media"));
        Path folder = root.resolve("A Show");

        URI uri = RemoteWorkspaceSessions.remoteUri(folder).orElseThrow();

        assertEquals("sftp", uri.getScheme());
        assertEquals(EmbeddedSftpServer.USER, uri.getUserInfo());
        assertEquals(server.port(), uri.getPort());
        assertEquals("/media/A Show", uri.getPath());
        assertEquals(folder, RemoteWorkspaceSessions.resolve(uri).orElseThrow());
        assertEquals("sftp://" + server.endpoint().label() + "/media/A Show",
                RemoteWorkspaceSessions.displayName(folder));
    }

    @Test
    void disconnectingForgetsTheMountAndItsUris() throws IOException {
        RemoteWorkspace workspace = server.workspace("/");
        Path root = sessions.connect(workspace);
        URI uri = RemoteWorkspaceSessions.remoteUri(root).orElseThrow();

        sessions.disconnect(workspace);

        assertTrue(sessions.mountedRoot(workspace).isEmpty());
        assertTrue(RemoteWorkspaceSessions.resolve(uri).isEmpty());
    }

    @Test
    void localPathsAreNeverRemote() {
        assertFalse(RemoteWorkspaceSessions.isRemote(tempDir));
        assertEquals(tempDir.toString(), RemoteWorkspaceSessions.displayName(tempDir));
        assertTrue(RemoteWorkspaceSessions.remoteUri(tempDir).isEmpty());
    }
}
