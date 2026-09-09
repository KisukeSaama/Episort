package com.episort.filesystem;

import com.episort.config.SftpAuthentication;
import com.episort.config.SftpEndpoint;
import com.episort.config.WorkspaceLocation.RemoteWorkspace;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;

/**
 * A real SSH server on the loopback interface, serving one temporary directory
 * as its root. Tests exercise the remote workspace against it exactly as they
 * would against a Plex server, without any network beyond localhost.
 */
public final class EmbeddedSftpServer implements AutoCloseable {
    public static final String USER = "plex";
    public static final String PASSWORD = "correct horse battery staple";
    public static final String HOST = "127.0.0.1";

    private final SshServer server;
    private final Path root;

    private EmbeddedSftpServer(SshServer server, Path root) {
        this.server = server;
        this.root = root;
    }

    public static EmbeddedSftpServer start(Path root) throws IOException {
        SshServer server = SshServer.setUpDefaultServer();
        server.setHost(HOST);
        server.setPort(0);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        server.setPasswordAuthenticator(
                (username, password, session) -> USER.equals(username) && PASSWORD.equals(password));
        server.setSubsystemFactories(List.of(new SftpSubsystemFactory()));
        server.setFileSystemFactory(new VirtualFileSystemFactory(root.toAbsolutePath().normalize()));
        server.start();
        return new EmbeddedSftpServer(server, root);
    }

    public int port() {
        return server.getPort();
    }

    /** The directory on this machine the server exposes as {@code /}. */
    public Path root() {
        return root;
    }

    public SftpEndpoint endpoint() {
        return endpoint(SftpAuthentication.Password.of(PASSWORD));
    }

    public SftpEndpoint endpoint(SftpAuthentication authentication) {
        return new SftpEndpoint(HOST, port(), USER, authentication);
    }

    public RemoteWorkspace workspace(String remoteRoot) {
        return new RemoteWorkspace(endpoint(), remoteRoot);
    }

    @Override
    public void close() throws IOException {
        server.stop(true);
    }
}
