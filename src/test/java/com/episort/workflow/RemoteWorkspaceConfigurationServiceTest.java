package com.episort.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.config.AppSettings;
import com.episort.config.FileSettingsStore;
import com.episort.config.SftpAuthentication;
import com.episort.config.WorkspaceLocation.RemoteWorkspace;
import com.episort.filesystem.EmbeddedSftpServer;
import com.episort.filesystem.RemoteWorkspaceSessions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RemoteWorkspaceConfigurationServiceTest {
    @TempDir
    Path tempDir;

    private EmbeddedSftpServer server;
    private RemoteWorkspaceSessions sessions;
    private FileSettingsStore store;
    private WorkspaceConfigurationService service;

    @BeforeEach
    void setUp() throws IOException {
        Path serverRoot = Files.createDirectories(tempDir.resolve("server"));
        Files.createDirectories(serverRoot.resolve("media"));
        server = EmbeddedSftpServer.start(serverRoot);
        store = new FileSettingsStore(tempDir.resolve("settings").resolve("episort.properties"));
        sessions = new RemoteWorkspaceSessions(store.knownHostsFile());
        service = new WorkspaceConfigurationService(store, sessions);
    }

    @AfterEach
    void tearDown() throws IOException {
        sessions.close();
        server.close();
    }

    @Test
    void configuringARemoteWorkspaceConnectsThenRecordsIt() {
        WorkspaceConfigurationResult result = service.configureRemoteWorkspace(server.workspace("/media"));

        assertTrue(result.success(), () -> String.valueOf(result.error()));
        assertTrue(RemoteWorkspaceSessions.isRemote(result.settings().workspaceDirectory().orElseThrow()));
        assertEquals(server.workspace("/media"), store.load().remoteWorkspace().orElseThrow());

        WorkspaceConfigurationResult reloaded = service.loadConfiguredWorkspace();
        assertTrue(reloaded.success());
        assertEquals(result.settings().workspaceDirectory(), reloaded.settings().workspaceDirectory());
        assertEquals(server.workspace("/media"), service.configuredLocation().orElseThrow());
    }

    @Test
    void aFailedConnectionRecordsNothing() {
        RemoteWorkspace wrongPassword = new RemoteWorkspace(
                server.endpoint(SftpAuthentication.Password.of("wrong")), "/media");

        WorkspaceConfigurationResult result = service.configureRemoteWorkspace(wrongPassword);

        assertFalse(result.success());
        assertEquals(WorkspaceConfigurationService.ERROR_REMOTE_AUTHENTICATION, result.error().orElseThrow().code());
        assertTrue(store.load().workspace().isEmpty());
    }

    @Test
    void missingRemoteRootIsReportedAsSuch() {
        WorkspaceConfigurationResult result = service.configureRemoteWorkspace(server.workspace("/nope"));

        assertFalse(result.success());
        assertEquals(WorkspaceConfigurationService.ERROR_REMOTE_ROOT, result.error().orElseThrow().code());
    }

    @Test
    void aRecordedRemoteWorkspaceIsDisconnectedUntilConnected() {
        store.save(AppSettings.remote(server.workspace("/media")));

        WorkspaceConfigurationResult beforeConnect = service.loadConfiguredWorkspace();
        assertFalse(beforeConnect.success());
        assertEquals(WorkspaceConfigurationService.ERROR_REMOTE_DISCONNECTED, beforeConnect.error().orElseThrow().code());
        assertTrue(beforeConnect.settings().isRemote());

        WorkspaceConfigurationResult connected = service.connectConfiguredWorkspace();
        assertTrue(connected.success(), () -> String.valueOf(connected.error()));
        assertTrue(service.loadConfiguredWorkspace().success());

        service.disconnectRemoteWorkspace();
        assertFalse(service.loadConfiguredWorkspace().success());
    }

    @Test
    void connectingALocalWorkspaceIsANoOp() throws IOException {
        Path local = Files.createDirectories(tempDir.resolve("local"));
        service.configureWorkspace(local);

        WorkspaceConfigurationResult result = service.connectConfiguredWorkspace();

        assertTrue(result.success());
        assertEquals(local, result.settings().workspaceDirectory().orElseThrow());
    }

    @Test
    void startupWorkflowExposesTheRemoteOperations() {
        StartupWorkflow workflow = new StartupWorkflow(service, null);

        assertTrue(workflow.configureRemoteWorkspace(server.workspace("/media")).success());
        assertEquals(server.workspace("/media"), workflow.configuredWorkspaceLocation().orElseThrow());
        assertTrue(workflow.connectConfiguredWorkspace().success());
        workflow.disconnectRemoteWorkspace();
        assertEquals(WorkspaceConfigurationService.ERROR_REMOTE_DISCONNECTED,
                workflow.loadWorkspaceConfiguration().error().orElseThrow().code());
    }
}
