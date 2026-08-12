package com.episort.config;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/** Public caller configuration used to reach TMDB through the Janus gateway. */
public record JanusConfiguration(URI baseUri, String applicationId, String apiKey) {
    private static final URI DEFAULT_URL = URI.create("https://janus.kisukesaama.com");
    private static final String DEFAULT_APPLICATION_ID = "be061c51-1947-4ec5-9ac7-86e917168e41";
    public JanusConfiguration {
        baseUri = normalizeBaseUri(Objects.requireNonNull(baseUri, "baseUri"));
        applicationId = requireValue(applicationId, "applicationId");
        apiKey = requireValue(apiKey, "apiKey");
        boolean loopbackTestEndpoint = "http".equalsIgnoreCase(baseUri.getScheme())
                && ("localhost".equalsIgnoreCase(baseUri.getHost()) || "127.0.0.1".equals(baseUri.getHost()));
        if (!"https".equalsIgnoreCase(baseUri.getScheme()) && !loopbackTestEndpoint) {
            throw new IllegalArgumentException("Janus URL must use HTTPS.");
        }
    }

    /** Convenience constructor used by isolated domain tests. */
    public JanusConfiguration(String apiKey, Optional<String> ignoredLegacyToken) {
        this(DEFAULT_URL, DEFAULT_APPLICATION_ID, apiKey);
    }

    public static JanusConfiguration callerKey(String callerKey) {
        return new JanusConfiguration(DEFAULT_URL, DEFAULT_APPLICATION_ID, callerKey);
    }

    public URI tmdbBaseUri() {
        return baseUri.resolve("gateway/tmdb-v3/");
    }

    private static URI normalizeBaseUri(URI value) {
        String text = value.toString();
        return URI.create(text.endsWith("/") ? text : text + "/");
    }

    private static String requireValue(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return normalized;
    }

    @Override
    public String toString() {
        return "JanusConfiguration[baseUri=" + baseUri
                + ", applicationId=" + applicationId + ", apiKey=[REDACTED]]";
    }
}
