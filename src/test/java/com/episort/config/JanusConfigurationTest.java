package com.episort.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import org.junit.jupiter.api.Test;

class JanusConfigurationTest {
    @Test
    void buildsTmdbRouteFromTheCurrentGatewayPrefix() {
        JanusConfiguration configuration = new JanusConfiguration(
                URI.create("https://janus.example"), "application-id", "caller-key");
        assertEquals("https://janus.example/gateway/tmdb-v3/", configuration.tmdbBaseUri().toString());
    }

    @Test
    void stringRepresentationRedactsSecrets() {
        JanusConfiguration credentials = new JanusConfiguration(
                URI.create("https://janus.example"), "application-id", "sensitive-api-key");

        String representation = credentials.toString();

        assertFalse(representation.contains("sensitive-api-key"));
        assertTrue(representation.contains("[REDACTED]"));
    }
}
