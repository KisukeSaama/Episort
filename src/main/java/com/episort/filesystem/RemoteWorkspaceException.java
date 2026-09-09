package com.episort.filesystem;

import java.io.IOException;
import java.util.Objects;

/** Why a remote workspace could not be mounted, in terms the interface can name. */
public final class RemoteWorkspaceException extends IOException {
    public enum Reason {
        /** The host did not answer, or refused the connection. */
        UNREACHABLE,
        /** The server answered but rejected the password or the key. */
        AUTHENTICATION_FAILED,
        /** The server presented a host key different from the one recorded. */
        HOST_KEY_CHANGED,
        /** The private key file could not be read or decrypted. */
        KEY_UNREADABLE,
        /** The session opened but the configured root is not a directory there. */
        ROOT_INVALID
    }

    private final Reason reason;

    public RemoteWorkspaceException(Reason reason, String message) {
        this(reason, message, null);
    }

    public RemoteWorkspaceException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }
}
