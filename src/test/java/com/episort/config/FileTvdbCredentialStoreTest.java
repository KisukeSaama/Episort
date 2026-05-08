package com.episort.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileTvdbCredentialStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void savesCredentialsOutsideWorkspaceAndOutsideSettings() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("media"));
        Path credentialFile = tempDir.resolve("profile").resolve("tvdb.properties");
        FileTvdbCredentialStore store = new FileTvdbCredentialStore(credentialFile);
        TvdbCredentials credentials = new TvdbCredentials("api-key", Optional.of("subscriber-pin"));

        store.save(credentials);

        assertEquals(credentials, store.load().orElseThrow());
        assertFalse(credentialFile.normalize().startsWith(workspace.normalize()));
    }

    @Test
    void missingCredentialFileLoadsEmpty() {
        FileTvdbCredentialStore store = new FileTvdbCredentialStore(tempDir.resolve("missing").resolve("tvdb.properties"));

        assertTrue(store.load().isEmpty());
    }
}
