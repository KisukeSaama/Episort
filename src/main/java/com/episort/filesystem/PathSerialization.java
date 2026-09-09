package com.episort.filesystem;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Turns paths into text the stores can keep, and back.
 *
 * <p>A local path is its own text. A remote path is written as
 * {@code sftp://user@host:port/path} and only comes back while the session it
 * belongs to is open: a plan recorded against a server cannot be replayed
 * against this machine's disk by accident.
 */
public final class PathSerialization {
    private static final String REMOTE_PREFIX = "sftp://";

    private PathSerialization() {
    }

    public static String encode(Path path) {
        Objects.requireNonNull(path, "path");
        return RemoteWorkspaceSessions.remoteUri(path)
                .map(URI::toString)
                .orElseGet(path::toString);
    }

    public static boolean isRemote(String text) {
        return text != null && text.startsWith(REMOTE_PREFIX);
    }

    /**
     * @return the path, or empty when the text is malformed or names a server
     *         that is not connected right now
     */
    public static Optional<Path> decode(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        if (isRemote(text)) {
            try {
                return RemoteWorkspaceSessions.resolve(new URI(text));
            } catch (URISyntaxException exception) {
                return Optional.empty();
            }
        }
        try {
            return Optional.of(Path.of(text));
        } catch (InvalidPathException exception) {
            return Optional.empty();
        }
    }
}
