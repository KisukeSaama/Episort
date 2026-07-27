package com.episort.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class EmbeddedTvdbCredentialsProviderTest {
    @Test
    void loadsEnvironmentConfiguredCredentialWhenAvailable() {
        assertNotNull(EmbeddedTvdbCredentialsProvider.load());
    }
}
