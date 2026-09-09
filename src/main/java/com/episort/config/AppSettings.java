package com.episort.config;

import com.episort.config.WorkspaceLocation.LocalWorkspace;
import com.episort.config.WorkspaceLocation.RemoteWorkspace;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Persisted preferences plus the live workspace they resolve to.
 *
 * @param workspace          what the settings file records
 * @param workspaceDirectory the directory the application may work in right
 *                           now: the local folder itself, or the mounted root of
 *                           a remote workspace once its session is open. Empty
 *                           while a remote workspace is not connected.
 */
public record AppSettings(Optional<WorkspaceLocation> workspace, Optional<Path> workspaceDirectory) {
    public AppSettings {
        workspace = Objects.requireNonNull(workspace);
        workspaceDirectory = Objects.requireNonNull(workspaceDirectory)
                .map(path -> path.toAbsolutePath().normalize());
    }

    public AppSettings(Path workspaceDirectory) {
        this(Optional.of(new LocalWorkspace(workspaceDirectory)), Optional.of(workspaceDirectory));
    }

    public static AppSettings empty() {
        return new AppSettings(Optional.empty(), Optional.empty());
    }

    public static AppSettings remote(RemoteWorkspace location) {
        return new AppSettings(Optional.of(location), Optional.empty());
    }

    public static AppSettings of(WorkspaceLocation location) {
        return switch (location) {
            case LocalWorkspace local -> new AppSettings(local.directory());
            case RemoteWorkspace remote -> remote(remote);
        };
    }

    public AppSettings withWorkspaceDirectory(Path mounted) {
        return new AppSettings(workspace, Optional.of(mounted));
    }

    public boolean isRemote() {
        return remoteWorkspace().isPresent();
    }

    public Optional<RemoteWorkspace> remoteWorkspace() {
        return workspace.filter(RemoteWorkspace.class::isInstance).map(RemoteWorkspace.class::cast);
    }
}
