package com.episort.config;

import java.util.Objects;

/**
 * One SSH server Episort can mount a workspace from.
 *
 * @param host           DNS name or IP address of the server
 * @param port           SSH port, 22 unless the operator moved it
 * @param username       account that owns, or may write to, the media library
 * @param authentication how that account is proven
 */
public record SftpEndpoint(String host, int port, String username, SftpAuthentication authentication) {
    public static final int DEFAULT_PORT = 22;

    public SftpEndpoint {
        host = requireText(host, "host");
        username = requireText(username, "username");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        Objects.requireNonNull(authentication, "authentication");
    }

    /** {@code user@host}, with the port only when it is not the default. */
    public String label() {
        return username + "@" + host + (port == DEFAULT_PORT ? "" : ":" + port);
    }

    /** Stable identity of the connection, independent of how it authenticates. */
    public String connectionId() {
        return username + "@" + host + ":" + port;
    }

    public SftpEndpoint withAuthentication(SftpAuthentication replacement) {
        return new SftpEndpoint(host, port, username, replacement);
    }

    private static String requireText(String value, String name) {
        String trimmed = Objects.requireNonNull(value, name).trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return trimmed;
    }
}
