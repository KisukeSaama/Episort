package com.episort.workflow;

import com.episort.config.AppSettings;
import com.episort.config.InvalidSettingsException;
import com.episort.config.SettingsStore;
import com.episort.config.SettingsStoreException;
import com.episort.config.WorkspaceLocation;
import com.episort.config.WorkspaceLocation.LocalWorkspace;
import com.episort.config.WorkspaceLocation.RemoteWorkspace;
import com.episort.filesystem.RemoteWorkspaceException;
import com.episort.filesystem.RemoteWorkspaceSessions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Owns the configured workspace: a local folder, or a folder on a server
 * reached over SFTP.
 *
 * <p>{@link #loadConfiguredWorkspace()} never touches the network. A remote
 * workspace whose session is not open is reported as disconnected, and the
 * caller decides when to pay for {@link #connectConfiguredWorkspace()}, which
 * blocks until the server answers.
 */
public final class WorkspaceConfigurationService {
    public static final String ERROR_REMOTE_DISCONNECTED = "WORKSPACE_REMOTE_DISCONNECTED";
    public static final String ERROR_REMOTE_UNREACHABLE = "WORKSPACE_REMOTE_UNREACHABLE";
    public static final String ERROR_REMOTE_AUTHENTICATION = "WORKSPACE_REMOTE_AUTH_FAILED";
    public static final String ERROR_REMOTE_HOST_KEY = "WORKSPACE_REMOTE_HOST_KEY_CHANGED";
    public static final String ERROR_REMOTE_KEY_UNREADABLE = "WORKSPACE_REMOTE_KEY_UNREADABLE";
    public static final String ERROR_REMOTE_ROOT = "WORKSPACE_REMOTE_ROOT_INVALID";

    private final SettingsStore settingsStore;
    private final RemoteWorkspaceSessions sessions;

    public WorkspaceConfigurationService(SettingsStore settingsStore) {
        this(settingsStore, new RemoteWorkspaceSessions(defaultKnownHosts(settingsStore)));
    }

    public WorkspaceConfigurationService(SettingsStore settingsStore, RemoteWorkspaceSessions sessions) {
        this.settingsStore = Objects.requireNonNull(settingsStore, "settingsStore");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    public RemoteWorkspaceSessions sessions() {
        return sessions;
    }

    public WorkspaceConfigurationResult configureWorkspace(Path workspaceDirectory) {
        if (workspaceDirectory == null) {
            return WorkspaceConfigurationResult.failure(AppSettings.empty(), missingWorkspaceError());
        }

        Path normalizedWorkspace = workspaceDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedWorkspace) || !Files.isReadable(normalizedWorkspace)) {
            return WorkspaceConfigurationResult.failure(AppSettings.empty(), invalidWorkspaceError(normalizedWorkspace));
        }

        if (workspaceContainsSettingsFile(normalizedWorkspace)) {
            return WorkspaceConfigurationResult.failure(AppSettings.empty(), workspaceContainsSettingsError());
        }

        AppSettings settings = new AppSettings(normalizedWorkspace);
        try {
            settingsStore.save(settings);
        } catch (SettingsStoreException exception) {
            return WorkspaceConfigurationResult.failure(AppSettings.empty(), settingsUnavailableError());
        }
        return WorkspaceConfigurationResult.success(settings);
    }

    /**
     * Connects to the server, checks the root exists there, and only then
     * records the location. Blocks for the round trips; call it off the
     * interface thread.
     */
    public WorkspaceConfigurationResult configureRemoteWorkspace(RemoteWorkspace location) {
        if (location == null) {
            return WorkspaceConfigurationResult.failure(AppSettings.empty(), missingWorkspaceError());
        }
        Path root;
        try {
            root = sessions.connect(location);
        } catch (RemoteWorkspaceException exception) {
            return WorkspaceConfigurationResult.failure(AppSettings.remote(location), remoteError(exception));
        }
        AppSettings settings = AppSettings.remote(location).withWorkspaceDirectory(root);
        try {
            settingsStore.save(settings);
        } catch (SettingsStoreException exception) {
            return WorkspaceConfigurationResult.failure(AppSettings.remote(location), settingsUnavailableError());
        }
        return WorkspaceConfigurationResult.success(settings);
    }

    public WorkspaceConfigurationResult loadConfiguredWorkspace() {
        AppSettings settings;
        try {
            settings = settingsStore.load();
        } catch (InvalidSettingsException exception) {
            return WorkspaceConfigurationResult.failure(AppSettings.empty(), invalidWorkspaceError(null));
        } catch (SettingsStoreException exception) {
            return WorkspaceConfigurationResult.failure(AppSettings.empty(), settingsUnavailableError());
        }

        return settings.workspace()
                .map(location -> switch (location) {
                    case LocalWorkspace local -> validateLoadedWorkspace(local.directory());
                    case RemoteWorkspace remote -> sessions.mountedRoot(remote)
                            .map(root -> WorkspaceConfigurationResult.success(settings.withWorkspaceDirectory(root)))
                            .orElseGet(() -> WorkspaceConfigurationResult.failure(settings, remoteDisconnectedError()));
                })
                .orElseGet(() -> WorkspaceConfigurationResult.failure(settings, missingWorkspaceError()));
    }

    /** The recorded location, whether or not it is reachable right now. */
    public Optional<WorkspaceLocation> configuredLocation() {
        try {
            return settingsStore.load().workspace();
        } catch (SettingsStoreException exception) {
            return Optional.empty();
        }
    }

    /**
     * Opens the session of a recorded remote workspace. A local workspace or an
     * already open session comes back as {@link #loadConfiguredWorkspace()} would.
     */
    public WorkspaceConfigurationResult connectConfiguredWorkspace() {
        WorkspaceConfigurationResult loaded = loadConfiguredWorkspace();
        Optional<RemoteWorkspace> remote = loaded.settings().remoteWorkspace();
        if (loaded.success() || remote.isEmpty()) {
            return loaded;
        }
        try {
            Path root = sessions.connect(remote.orElseThrow());
            return WorkspaceConfigurationResult.success(loaded.settings().withWorkspaceDirectory(root));
        } catch (RemoteWorkspaceException exception) {
            return WorkspaceConfigurationResult.failure(loaded.settings(), remoteError(exception));
        }
    }

    public void disconnectRemoteWorkspace() {
        configuredLocation()
                .filter(RemoteWorkspace.class::isInstance)
                .map(RemoteWorkspace.class::cast)
                .ifPresent(sessions::disconnect);
    }

    private WorkspaceConfigurationResult validateLoadedWorkspace(Path workspaceDirectory) {
        if (!Files.isDirectory(workspaceDirectory) || !Files.isReadable(workspaceDirectory)) {
            return WorkspaceConfigurationResult.failure(new AppSettings(workspaceDirectory), invalidWorkspaceError(workspaceDirectory));
        }

        return WorkspaceConfigurationResult.success(new AppSettings(workspaceDirectory));
    }

    private boolean workspaceContainsSettingsFile(Path workspaceDirectory) {
        return settingsStore.settingsFile()
                .map(settingsFile -> settingsFile.toAbsolutePath().normalize().startsWith(workspaceDirectory))
                .orElse(false);
    }

    private static Path defaultKnownHosts(SettingsStore settingsStore) {
        return settingsStore.settingsFile()
                .map(file -> file.resolveSibling("known_hosts"))
                .orElseGet(() -> Path.of(System.getProperty("java.io.tmpdir"), "episort-known_hosts"));
    }

    public static ApplicationError remoteError(RemoteWorkspaceException exception) {
        return switch (exception.reason()) {
            case UNREACHABLE -> ApplicationError.recoverable(
                    ERROR_REMOTE_UNREACHABLE,
                    ErrorSeverity.BLOCKING,
                    "The server cannot be reached. Check the host, the port, and that SSH is running.",
                    String.valueOf(exception.getMessage()));
            case AUTHENTICATION_FAILED -> ApplicationError.recoverable(
                    ERROR_REMOTE_AUTHENTICATION,
                    ErrorSeverity.BLOCKING,
                    "The server refused the credentials. Check the user name, the password, or the key.",
                    String.valueOf(exception.getMessage()));
            case HOST_KEY_CHANGED -> ApplicationError.recoverable(
                    ERROR_REMOTE_HOST_KEY,
                    ErrorSeverity.BLOCKING,
                    "The server's host key changed since it was first trusted. Remove it from known_hosts only if you expected this.",
                    String.valueOf(exception.getMessage()));
            case KEY_UNREADABLE -> ApplicationError.recoverable(
                    ERROR_REMOTE_KEY_UNREADABLE,
                    ErrorSeverity.BLOCKING,
                    "The private key file cannot be read or decrypted.",
                    String.valueOf(exception.getMessage()));
            case ROOT_INVALID -> ApplicationError.recoverable(
                    ERROR_REMOTE_ROOT,
                    ErrorSeverity.BLOCKING,
                    "The remote folder does not exist on the server.",
                    String.valueOf(exception.getMessage()));
        };
    }

    private ApplicationError remoteDisconnectedError() {
        return ApplicationError.recoverable(
                ERROR_REMOTE_DISCONNECTED,
                ErrorSeverity.BLOCKING,
                "Connect to your server before scanning media.",
                "Remote workspace session is not open.");
    }

    private ApplicationError missingWorkspaceError() {
        return ApplicationError.recoverable(
                "WORKSPACE_REQUIRED",
                ErrorSeverity.BLOCKING,
                "Choose a workspace directory before scanning media.",
                "Workspace path has not been configured.");
    }

    private ApplicationError invalidWorkspaceError(Path workspaceDirectory) {
        return ApplicationError.recoverable(
                "WORKSPACE_INVALID",
                ErrorSeverity.BLOCKING,
                "Choose an existing readable workspace directory.",
                "Workspace path is invalid or inaccessible: " + workspaceDirectory);
    }

    private ApplicationError workspaceContainsSettingsError() {
        return ApplicationError.recoverable(
                "WORKSPACE_CONTAINS_SETTINGS",
                ErrorSeverity.BLOCKING,
                "Choose a workspace that does not contain Episort settings.",
                "Workspace contains the settings file location.");
    }

    private ApplicationError settingsUnavailableError() {
        return ApplicationError.recoverable(
                "SETTINGS_UNAVAILABLE",
                ErrorSeverity.BLOCKING,
                "Episort settings are unavailable. Check your user profile permissions.",
                "Settings storage could not be read or written.");
    }
}
