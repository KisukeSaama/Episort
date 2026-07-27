package com.episort.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

class FileTvdbCredentialStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void savesCredentialsOutsideWorkspaceAndOutsideSettings() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("media"));
        Path credentialFile = tempDir.resolve("profile").resolve("tvdb.properties");
        FileTvdbCredentialStore store = new FileTvdbCredentialStore(credentialFile, new TestCredentialProtector());
        TvdbCredentials credentials = new TvdbCredentials("api-key", Optional.of("subscriber-pin"));

        store.save(credentials);

        assertEquals(credentials, store.load().orElseThrow());
        assertFalse(credentialFile.normalize().startsWith(workspace.normalize()));
        String storedContent = Files.readString(credentialFile);
        assertFalse(storedContent.contains("api-key"));
        assertFalse(storedContent.contains("subscriber-pin"));
        assertTrue(storedContent.contains("format=test-v1"));
    }

    @Test
    void missingCredentialFileLoadsEmpty() {
        FileTvdbCredentialStore store = new FileTvdbCredentialStore(
                tempDir.resolve("missing").resolve("tvdb.properties"),
                new TestCredentialProtector());

        assertTrue(store.load().isEmpty());
    }

    @Test
    void migratesLegacyPlaintextCredentialsOnLoad() throws Exception {
        Path credentialFile = tempDir.resolve("profile").resolve("tvdb.properties");
        Files.createDirectories(credentialFile.getParent());
        Files.writeString(credentialFile, "apiKey=legacy-key\nsubscriberPin=legacy-pin\n");
        FileTvdbCredentialStore store = new FileTvdbCredentialStore(credentialFile, new TestCredentialProtector());

        assertEquals(
                new TvdbCredentials("legacy-key", Optional.of("legacy-pin")),
                store.load().orElseThrow());

        String migratedContent = Files.readString(credentialFile);
        assertFalse(migratedContent.contains("legacy-key"));
        assertFalse(migratedContent.contains("legacy-pin"));
        assertTrue(migratedContent.contains("format=test-v1"));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void windowsStoreUsesCurrentUserDpapi() throws Exception {
        Path credentialFile = tempDir.resolve("profile").resolve("tvdb.properties");
        FileTvdbCredentialStore store = new FileTvdbCredentialStore(credentialFile);
        TvdbCredentials credentials = new TvdbCredentials("windows-api-key", Optional.of("windows-pin"));

        store.save(credentials);

        assertEquals(credentials, store.load().orElseThrow());
        String storedContent = Files.readString(credentialFile);
        assertFalse(storedContent.contains("windows-api-key"));
        assertFalse(storedContent.contains("windows-pin"));
        assertTrue(storedContent.contains("format=windows-dpapi-v1"));
    }

    private static final class TestCredentialProtector implements CredentialProtector {
        private static final byte MASK = 0x5A;

        @Override
        public String format() {
            return "test-v1";
        }

        @Override
        public byte[] protect(byte[] plaintext) {
            return transform(plaintext);
        }

        @Override
        public byte[] unprotect(byte[] protectedData) {
            return transform(protectedData);
        }

        private byte[] transform(byte[] input) {
            byte[] output = Arrays.copyOf(input, input.length);
            for (int index = 0; index < output.length; index++) {
                output[index] ^= MASK;
            }
            return output;
        }
    }
}
