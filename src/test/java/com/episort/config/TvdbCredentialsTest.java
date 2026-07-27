package com.episort.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class TvdbCredentialsTest {
    @Test
    void stringRepresentationRedactsSecrets() {
        TvdbCredentials credentials =
                new TvdbCredentials("sensitive-api-key", Optional.of("sensitive-pin"));

        String representation = credentials.toString();

        assertFalse(representation.contains("sensitive-api-key"));
        assertFalse(representation.contains("sensitive-pin"));
        assertTrue(representation.contains("[REDACTED]"));
    }
}
