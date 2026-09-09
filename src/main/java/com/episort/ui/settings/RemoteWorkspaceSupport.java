package com.episort.ui.settings;

import com.episort.config.WorkspaceLocation;
import com.episort.config.WorkspaceLocation.RemoteWorkspace;
import com.episort.filesystem.RemoteWorkspaceSessions;
import com.episort.ui.AppShellViewModel;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * What the settings screen needs to offer a workspace on a server.
 *
 * @param configure       connects, records the location, and reports the
 *                        outcome; blocks, so the pane calls it off the
 *                        JavaFX thread
 * @param currentLocation the recorded workspace, local or remote
 * @param sessions        the open sessions, used to browse a server before
 *                        the location is recorded
 * @param disconnect      closes the session of the recorded remote workspace
 */
public record RemoteWorkspaceSupport(
        Function<RemoteWorkspace, AppShellViewModel> configure,
        Supplier<Optional<WorkspaceLocation>> currentLocation,
        RemoteWorkspaceSessions sessions,
        Runnable disconnect) {
    public RemoteWorkspaceSupport {
        Objects.requireNonNull(configure, "configure");
        Objects.requireNonNull(currentLocation, "currentLocation");
        Objects.requireNonNull(sessions, "sessions");
        Objects.requireNonNull(disconnect, "disconnect");
    }
}
