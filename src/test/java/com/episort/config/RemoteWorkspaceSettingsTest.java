package com.episort.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.episort.config.SftpAuthentication.Password;
import com.episort.config.SftpAuthentication.PrivateKey;
import com.episort.config.WorkspaceLocation.RemoteWorkspace;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RemoteWorkspaceSettingsTest {
    @TempDir
    Path tempDir;

    @Test
    void remoteWorkspaceRoundTripsWithItsPasswordProtected() throws Exception {
        Path settingsFile = tempDir.resolve("settings").resolve("episort.properties");
        FileSettingsStore store = new FileSettingsStore(settingsFile, new UnprotectedCredentialProtector());
        RemoteWorkspace location = new RemoteWorkspace(
                new SftpEndpoint("nas.local", 2222, "plex", Password.of("s3cret")), "/mnt/plex/");

        store.save(AppSettings.remote(location));
        AppSettings loaded = store.load();

        assertTrue(loaded.isRemote());
        assertTrue(loaded.workspaceDirectory().isEmpty(), "a remote workspace has no path until it is mounted");
        assertEquals(location, loaded.remoteWorkspace().orElseThrow());
        assertEquals("/mnt/plex", loaded.remoteWorkspace().orElseThrow().rootPath());
        String text = Files.readString(settingsFile, StandardCharsets.UTF_8);
        assertFalse(text.contains("s3cret"), "the password is never written in clear");
        assertTrue(text.contains("workspace.sftp.secret.format=unprotected-v1"));
        assertEquals(store.knownHostsFile(), settingsFile.resolveSibling("known_hosts"));
    }

    @Test
    void privateKeyAuthenticationKeepsTheKeyFileAndProtectsThePassphrase() {
        FileSettingsStore store = new FileSettingsStore(
                tempDir.resolve("episort.properties"), new UnprotectedCredentialProtector());
        Path keyFile = tempDir.resolve("id_ed25519");
        RemoteWorkspace location = new RemoteWorkspace(
                new SftpEndpoint("nas.local", 22, "plex", PrivateKey.of(keyFile, "pass")), "/media");

        store.save(AppSettings.remote(location));

        assertEquals(location, store.load().remoteWorkspace().orElseThrow());
    }

    @Test
    void windowsProtectsTheSecretWithDpapi() {
        assumeTrue(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"));
        Path settingsFile = tempDir.resolve("episort.properties");
        FileSettingsStore store = new FileSettingsStore(settingsFile);
        RemoteWorkspace location = new RemoteWorkspace(
                new SftpEndpoint("nas.local", 22, "plex", Password.of("s3cret")), "/media");

        store.save(AppSettings.remote(location));

        assertEquals(Optional.of("s3cret"),
                store.load().remoteWorkspace().orElseThrow().endpoint().authentication().secret());
    }

    @Test
    void switchingBackToALocalWorkspaceDropsTheServerEntries() throws Exception {
        Path settingsFile = tempDir.resolve("episort.properties");
        FileSettingsStore store = new FileSettingsStore(settingsFile, new UnprotectedCredentialProtector());
        store.save(AppSettings.remote(new RemoteWorkspace(
                new SftpEndpoint("nas.local", 22, "plex", Password.of("s3cret")), "/media")));
        Path local = Files.createDirectories(tempDir.resolve("local"));

        store.save(new AppSettings(local));

        AppSettings loaded = store.load();
        assertFalse(loaded.isRemote());
        assertEquals(local, loaded.workspaceDirectory().orElseThrow());
        assertFalse(Files.readString(settingsFile).contains("workspace.sftp"));
    }

    @Test
    void unreadableSecretLeavesTheConnectionDetailsIntact() throws Exception {
        Path settingsFile = tempDir.resolve("episort.properties");
        Files.writeString(settingsFile, String.join("\n",
                "workspace.kind=sftp",
                "workspace.sftp.host=nas.local",
                "workspace.sftp.port=22",
                "workspace.sftp.username=plex",
                "workspace.sftp.root=/media",
                "workspace.sftp.auth=password",
                "workspace.sftp.secret=AAAA",
                "workspace.sftp.secret.format=some-other-machine-v9"));
        FileSettingsStore store = new FileSettingsStore(settingsFile, new UnprotectedCredentialProtector());

        RemoteWorkspace loaded = store.load().remoteWorkspace().orElseThrow();

        assertEquals("nas.local", loaded.endpoint().host());
        assertTrue(loaded.endpoint().authentication().secret().isEmpty());
    }

    @Test
    void malformedRemoteSettingsAreReportedAsInvalid() throws Exception {
        Path settingsFile = tempDir.resolve("episort.properties");
        Files.writeString(settingsFile, "workspace.kind=sftp\nworkspace.sftp.host=\nworkspace.sftp.port=99999\n");
        FileSettingsStore store = new FileSettingsStore(settingsFile, new UnprotectedCredentialProtector());

        assertThrows(InvalidSettingsException.class, store::load);
    }

    @Test
    void remoteRootsAreNormalizedPosixPaths() {
        SftpEndpoint endpoint = new SftpEndpoint("nas", 22, "plex", Password.of("x"));

        assertEquals("/mnt/plex", new RemoteWorkspace(endpoint, "/mnt//plex/./").rootPath());
        assertEquals("/", new RemoteWorkspace(endpoint, "/").rootPath());
        assertThrows(IllegalArgumentException.class, () -> new RemoteWorkspace(endpoint, "mnt/plex"));
        assertThrows(IllegalArgumentException.class, () -> new RemoteWorkspace(endpoint, "/mnt/../etc"));
        assertEquals("sftp://plex@nas/mnt/plex", new RemoteWorkspace(endpoint, "/mnt/plex").displayName());
        assertEquals("plex@nas:2222", new SftpEndpoint("nas", 2222, "plex", Password.of("x")).label());
    }

    @Test
    void secretsNeverAppearInToString() {
        assertFalse(Password.of("hunter2").toString().contains("hunter2"));
        assertFalse(PrivateKey.of(tempDir.resolve("k"), "hunter2").toString().contains("hunter2"));
    }
}
