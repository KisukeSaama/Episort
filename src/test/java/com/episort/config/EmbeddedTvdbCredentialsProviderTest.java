package com.episort.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmbeddedTvdbCredentialsProviderTest {
    @Test
    void distributedBuildProvidesProjectCredentialWithoutUserSetup() {
        TvdbCredentials credentials = EmbeddedTvdbCredentialsProvider.load().orElseThrow();

        assertEquals(36, credentials.apiKey().length());
        assertTrue(credentials.subscriberPin().isEmpty());
    }
}
