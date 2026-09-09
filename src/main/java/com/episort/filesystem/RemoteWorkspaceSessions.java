package com.episort.filesystem;

import com.episort.config.SftpAuthentication;
import com.episort.config.SftpAuthentication.Password;
import com.episort.config.SftpAuthentication.PrivateKey;
import com.episort.config.SftpEndpoint;
import com.episort.config.WorkspaceLocation.RemoteWorkspace;
import com.episort.filesystem.RemoteWorkspaceException.Reason;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.keyverifier.KnownHostsServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.AttributeRepository.AttributeKey;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.session.SessionHeartbeatController.HeartbeatType;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.apache.sshd.sftp.client.fs.SftpFileSystem;

/**
 * Opens and keeps the SSH sessions behind remote workspaces.
 *
 * <p>A remote workspace becomes an ordinary {@link Path} on an SFTP-backed
 * {@link FileSystem}, which is what lets the scanner, the boundary checks and
 * the mover run unchanged against a server. Everything blocking lives in
 * {@link #connect}; {@link #mountedRoot} only looks a session up, so the
 * interface thread may call it freely.
 *
 * <p>Host keys are trusted on first use and recorded in a {@code known_hosts}
 * file; a key that later differs is refused, never silently accepted. The
 * secret used to authenticate stays in memory for the session only.
 *
 * <p>Mounts are also registered process-wide so that a path can be described
 * or decoded without a reference to the instance that opened it: the history
 * and rollback stores persist paths as text and need the live filesystem back.
 */
public final class RemoteWorkspaceSessions implements AutoCloseable {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(12);
    private static final Duration AUTHENTICATION_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration HEARTBEAT = Duration.ofSeconds(30);
    private static final String SCHEME = "sftp";
    private static final AttributeKey<Boolean> HOST_KEY_MISMATCH = new AttributeKey<>();

    /** Every open mount in the process, by {@link SftpEndpoint#connectionId()}. */
    private static final Map<String, Mount> OPEN_MOUNTS = new ConcurrentHashMap<>();

    private final Path knownHostsFile;
    private final Map<String, Mount> mounts = new ConcurrentHashMap<>();
    private SshClient client;

    public RemoteWorkspaceSessions(Path knownHostsFile) {
        this.knownHostsFile = Objects.requireNonNull(knownHostsFile, "knownHostsFile").toAbsolutePath().normalize();
    }

    /** The workspace root when its session is open, without touching the network. */
    public Optional<Path> mountedRoot(RemoteWorkspace location) {
        Objects.requireNonNull(location, "location");
        Mount mount = mounts.get(location.endpoint().connectionId());
        if (mount == null || !mount.isOpen()) {
            return Optional.empty();
        }
        return Optional.of(mount.path(location.rootPath()));
    }

    public boolean isConnected(RemoteWorkspace location) {
        return mountedRoot(location).isPresent();
    }

    /**
     * Opens the session, or reuses the open one for the same server and
     * account, then checks the root exists there.
     *
     * @return the live workspace root
     * @throws RemoteWorkspaceException naming what stood in the way
     */
    public synchronized Path connect(RemoteWorkspace location) throws RemoteWorkspaceException {
        Objects.requireNonNull(location, "location");
        SftpEndpoint endpoint = location.endpoint();
        Mount mount = mounts.get(endpoint.connectionId());
        if (mount == null || !mount.isOpen()) {
            if (mount != null) {
                forget(mount);
            }
            mount = open(endpoint);
            mounts.put(endpoint.connectionId(), mount);
            OPEN_MOUNTS.put(endpoint.connectionId(), mount);
        }
        Path root = mount.path(location.rootPath());
        if (!Files.isDirectory(root)) {
            throw new RemoteWorkspaceException(
                    Reason.ROOT_INVALID, "Remote root is not a directory: " + location.rootPath());
        }
        return root;
    }

    public synchronized void disconnect(RemoteWorkspace location) {
        Mount mount = mounts.get(location.endpoint().connectionId());
        if (mount != null) {
            forget(mount);
        }
    }

    @Override
    public synchronized void close() {
        for (Mount mount : mounts.values().toArray(Mount[]::new)) {
            forget(mount);
        }
        if (client != null) {
            client.stop();
            client = null;
        }
    }

    /** Resolves a previously encoded remote path against the open mount it belongs to. */
    public static Optional<Path> resolve(URI uri) {
        if (uri == null || !SCHEME.equals(uri.getScheme()) || uri.getUserInfo() == null || uri.getHost() == null) {
            return Optional.empty();
        }
        int port = uri.getPort() < 0 ? SftpEndpoint.DEFAULT_PORT : uri.getPort();
        String connectionId = uri.getUserInfo() + "@" + uri.getHost() + ":" + port;
        Mount mount = OPEN_MOUNTS.get(connectionId);
        if (mount == null || !mount.isOpen()) {
            return Optional.empty();
        }
        String path = uri.getPath() == null || uri.getPath().isEmpty() ? "/" : uri.getPath();
        return Optional.of(mount.path(path));
    }

    public static boolean isRemote(Path path) {
        return path != null && path.getFileSystem() != FileSystems.getDefault();
    }

    /** The endpoint behind a remote path, empty for a local path or a closed mount. */
    public static Optional<SftpEndpoint> endpointOf(Path path) {
        if (!isRemote(path)) {
            return Optional.empty();
        }
        return OPEN_MOUNTS.values().stream()
                .filter(mount -> mount.fileSystem() == path.getFileSystem())
                .map(Mount::endpoint)
                .findFirst();
    }

    /** {@code sftp://user@host:port/path}, the form paths are persisted in. */
    public static Optional<URI> remoteUri(Path path) {
        return endpointOf(path).map(endpoint -> {
            try {
                return new URI(SCHEME, endpoint.username(), endpoint.host(), endpoint.port(),
                        path.toAbsolutePath().normalize().toString(), null, null);
            } catch (URISyntaxException exception) {
                throw new IllegalArgumentException("Remote path cannot be expressed as a URI: " + path, exception);
            }
        });
    }

    /**
     * What the interface shows for a path: itself when local, its server-qualified
     * form when remote so the user always knows which machine a plan will touch.
     */
    public static String displayName(Path path) {
        if (!isRemote(path)) {
            return path.toString();
        }
        String remotePath = path.toAbsolutePath().normalize().toString();
        return endpointOf(path)
                .map(endpoint -> SCHEME + "://" + endpoint.label() + remotePath)
                .orElse(SCHEME + "://" + remotePath);
    }

    private Mount open(SftpEndpoint endpoint) throws RemoteWorkspaceException {
        SshClient sshClient = startedClient();
        ClientSession session;
        try {
            session = sshClient.connect(endpoint.username(), endpoint.host(), endpoint.port())
                    .verify(CONNECT_TIMEOUT)
                    .getSession();
        } catch (IOException | RuntimeException exception) {
            throw new RemoteWorkspaceException(
                    Reason.UNREACHABLE, "Cannot reach " + endpoint.label(), exception);
        }
        try {
            authenticate(session, endpoint.authentication());
            session.setSessionHeartbeat(HeartbeatType.IGNORE, HEARTBEAT);
            SftpFileSystem fileSystem = SftpClientFactory.instance().createSftpFileSystem(session);
            return new Mount(endpoint, session, fileSystem);
        } catch (RemoteWorkspaceException exception) {
            closeQuietly(session);
            throw exception;
        } catch (IOException | RuntimeException exception) {
            closeQuietly(session);
            throw new RemoteWorkspaceException(
                    Reason.UNREACHABLE, "SFTP is unavailable on " + endpoint.label(), exception);
        }
    }

    private static void authenticate(ClientSession session, SftpAuthentication authentication)
            throws RemoteWorkspaceException {
        switch (authentication) {
            case Password password -> session.addPasswordIdentity(password.password().orElse(""));
            case PrivateKey key -> {
                for (KeyPair keyPair : loadKeyPairs(session, key)) {
                    session.addPublicKeyIdentity(keyPair);
                }
            }
        }
        try {
            session.auth().verify(AUTHENTICATION_TIMEOUT);
        } catch (IOException exception) {
            if (Boolean.TRUE.equals(session.getAttribute(HOST_KEY_MISMATCH))) {
                throw new RemoteWorkspaceException(
                        Reason.HOST_KEY_CHANGED, "Server host key differs from the recorded one", exception);
            }
            if (isAuthenticationFailure(exception)) {
                throw new RemoteWorkspaceException(
                        Reason.AUTHENTICATION_FAILED, "Server rejected the credentials", exception);
            }
            throw new RemoteWorkspaceException(
                    Reason.UNREACHABLE, "Connection failed before authentication completed", exception);
        }
    }

    private static Collection<KeyPair> loadKeyPairs(ClientSession session, PrivateKey key)
            throws RemoteWorkspaceException {
        try {
            FilePasswordProvider passphrase = key.passphrase()
                    .map(FilePasswordProvider::of)
                    .orElse(FilePasswordProvider.EMPTY);
            Collection<KeyPair> pairs = SecurityUtils.getKeyPairResourceParser()
                    .loadKeyPairs(session, key.keyFile(), passphrase);
            if (pairs == null || pairs.isEmpty()) {
                throw new RemoteWorkspaceException(
                        Reason.KEY_UNREADABLE, "No usable key found in " + key.keyFile());
            }
            return pairs;
        } catch (IOException | GeneralSecurityException | RuntimeException exception) {
            throw new RemoteWorkspaceException(
                    Reason.KEY_UNREADABLE, "Private key cannot be read: " + key.keyFile(), exception);
        }
    }

    private static boolean isAuthenticationFailure(IOException exception) {
        if (exception instanceof SshException ssh
                && ssh.getDisconnectCode() == SshConstants.SSH2_DISCONNECT_NO_MORE_AUTH_METHODS_AVAILABLE) {
            return true;
        }
        String message = String.valueOf(exception.getMessage());
        return message.contains("authentication") || message.contains("No more authentication");
    }

    private SshClient startedClient() {
        if (client == null) {
            // The verifier appends to the file but never creates its folder;
            // on a first launch nothing else has created it yet either.
            try {
                Files.createDirectories(knownHostsFile.getParent());
            } catch (IOException | RuntimeException ignored) {
                // The host key then goes unrecorded and is asked about again
                // next time, which is safe, only less convenient.
            }
            SshClient created = SshClient.setUpDefaultClient();
            KnownHostsServerKeyVerifier verifier =
                    new KnownHostsServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE, knownHostsFile);
            verifier.setModifiedServerKeyAcceptor((session, remote, entry, expected, actual) -> {
                session.setAttribute(HOST_KEY_MISMATCH, Boolean.TRUE);
                return false;
            });
            created.setServerKeyVerifier(verifier);
            created.start();
            client = created;
        }
        return client;
    }

    private void forget(Mount mount) {
        mounts.remove(mount.endpoint().connectionId(), mount);
        OPEN_MOUNTS.remove(mount.endpoint().connectionId(), mount);
        try {
            mount.fileSystem().close();
        } catch (IOException | RuntimeException ignored) {
            // Closing is best-effort: the session goes next.
        }
        closeQuietly(mount.session());
    }

    private static void closeQuietly(ClientSession session) {
        try {
            session.close(true);
        } catch (RuntimeException ignored) {
            // ignore
        }
    }

    private record Mount(SftpEndpoint endpoint, ClientSession session, SftpFileSystem fileSystem) {
        boolean isOpen() {
            return fileSystem.isOpen() && session.isOpen();
        }

        Path path(String remotePath) {
            return fileSystem.getPath(remotePath).toAbsolutePath().normalize();
        }
    }
}
