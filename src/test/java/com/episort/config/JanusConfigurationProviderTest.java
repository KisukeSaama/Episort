package com.episort.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JanusConfigurationProviderTest {
    @TempDir Path directory;

    private static final Map<String, String> DEFAULTS = Map.of(
            "JANUS_URL", "https://public.example",
            "JANUS_APPLICATION_ID", "public-app",
            "JANUS_API_KEY", "public-key");

    @Test
    void bundledPublicConfigurationWorksWithoutLocalEnvironment() {
        JanusConfiguration configuration = JanusConfigurationProvider.load(
                Map.of(), directory.resolve("missing.env"), DEFAULTS).orElseThrow();
        assertEquals("https://public.example/", configuration.baseUri().toString());
        assertEquals("public-app", configuration.applicationId());
        assertEquals("public-key", configuration.apiKey());
    }

    @Test
    void applicationBundleContainsCompleteJanusConfiguration() throws Exception {
        try (var input = JanusConfigurationProvider.class.getResourceAsStream("/janus-client.properties")) {
            var properties = new java.util.Properties();
            properties.load(java.util.Objects.requireNonNull(input));
            assertTrue(!properties.getProperty("JANUS_URL", "").isBlank());
            assertTrue(!properties.getProperty("JANUS_APPLICATION_ID", "").isBlank());
            assertTrue(!properties.getProperty("JANUS_API_KEY", "").isBlank());
        }
    }

    @Test
    void environmentOverridesDotenvAndBundledDefaults() throws Exception {
        Path dotenv = directory.resolve(".env");
        Files.writeString(dotenv, "JANUS_API_KEY=local-key\n");
        JanusConfiguration configuration = JanusConfigurationProvider.load(
                Map.of("JANUS_API_KEY", "ci-key"), dotenv, DEFAULTS).orElseThrow();
        assertEquals("ci-key", configuration.apiKey());
    }

    @Test
    void incompleteConfigurationIsRejected() {
        assertTrue(JanusConfigurationProvider.load(
                Map.of(), directory.resolve("missing.env"), Map.of()).isEmpty());
    }
}
