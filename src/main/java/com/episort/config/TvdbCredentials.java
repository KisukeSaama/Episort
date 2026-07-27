package com.episort.config;

import java.util.Objects;
import java.util.Optional;

public record TvdbCredentials(String apiKey, Optional<String> subscriberPin) {
    public TvdbCredentials {
        apiKey = Objects.requireNonNull(apiKey).trim();
        subscriberPin = Objects.requireNonNull(subscriberPin).map(String::trim).filter(value -> !value.isBlank());
        if (apiKey.isBlank()) {
            throw new IllegalArgumentException("TVDB API key is required.");
        }
    }

    @Override
    public String toString() {
        return "TvdbCredentials[apiKey=[REDACTED], subscriberPin="
                + (subscriberPin.isPresent() ? "Optional[[REDACTED]]" : "Optional.empty")
                + "]";
    }
}
