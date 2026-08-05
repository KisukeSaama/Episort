package com.episort.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SafeTvdbCredentialsProviderTest {
    @Test
    void embeddedCredentialsTakePriorityWithoutReadingPersistentStorage() {
        TvdbCredentials embedded = new TvdbCredentials("embedded-key", Optional.empty());
        SafeTvdbCredentialsProvider provider = new SafeTvdbCredentialsProvider(
                () -> Optional.of(embedded),
                () -> {
                    throw new AssertionError("persistent storage should not be read");
                });

        assertEquals(Optional.of(embedded), provider.load());
    }

    @Test
    void unavailableSecureStorageBehavesLikeMissingOptionalCredentials() {
        SafeTvdbCredentialsProvider provider = new SafeTvdbCredentialsProvider(
                Optional::empty,
                () -> {
                    throw new SettingsStoreException("secure storage unavailable", null);
                });

        assertTrue(provider.load().isEmpty());
    }

    @Test
    void returnsCredentialsFromPersistentStorageWhenAvailable() {
        TvdbCredentials stored = new TvdbCredentials("stored-key", Optional.of("pin"));
        SafeTvdbCredentialsProvider provider = new SafeTvdbCredentialsProvider(
                Optional::empty, () -> Optional.of(stored));

        assertEquals(Optional.of(stored), provider.load());
    }
}
