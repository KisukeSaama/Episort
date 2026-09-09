package com.episort.config;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Where the workspace lives: a folder this machine can see, or a folder on a
 * server reached over SFTP.
 *
 * <p>The location is what settings persist. The live {@link Path} the rest of
 * the application works with is derived from it: directly for a local folder,
 * through an open SSH session for a remote one.
 */
public sealed interface WorkspaceLocation {
    /** Short human-readable form, safe to display and to log. */
    String displayName();

    record LocalWorkspace(Path directory) implements WorkspaceLocation {
        public LocalWorkspace {
            directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        }

        @Override
        public String displayName() {
            return directory.toString();
        }
    }

    record RemoteWorkspace(SftpEndpoint endpoint, String rootPath) implements WorkspaceLocation {
        public RemoteWorkspace {
            Objects.requireNonNull(endpoint, "endpoint");
            rootPath = normalizeRoot(rootPath);
        }

        @Override
        public String displayName() {
            return "sftp://" + endpoint.label() + rootPath;
        }

        public RemoteWorkspace withRootPath(String replacement) {
            return new RemoteWorkspace(endpoint, replacement);
        }

        public RemoteWorkspace withEndpoint(SftpEndpoint replacement) {
            return new RemoteWorkspace(replacement, rootPath);
        }

        /**
         * Remote roots are POSIX absolute paths. Backslashes are not separators
         * there, and a relative root would depend on the login shell's home,
         * which a plan reviewed on screen must not depend on.
         */
        private static String normalizeRoot(String raw) {
            String trimmed = Objects.requireNonNull(raw, "rootPath").trim();
            if (!trimmed.startsWith("/")) {
                throw new IllegalArgumentException("rootPath must be an absolute POSIX path");
            }
            StringBuilder builder = new StringBuilder();
            for (String segment : trimmed.split("/")) {
                if (segment.isEmpty() || segment.equals(".")) {
                    continue;
                }
                if (segment.equals("..")) {
                    throw new IllegalArgumentException("rootPath must not contain '..'");
                }
                builder.append('/').append(segment);
            }
            return builder.isEmpty() ? "/" : builder.toString();
        }
    }
}
