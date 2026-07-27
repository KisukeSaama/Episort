package com.episort.config;

import java.util.Optional;

/** Provides the optional TVDB credential supplied by the build environment. */
public final class EmbeddedTvdbCredentialsProvider {
    private static final String API_KEY_ENVIRONMENT_VARIABLE = "TVDB_API_KEY";

    private EmbeddedTvdbCredentialsProvider() {
    }

    public static Optional<TvdbCredentials> load() {
        String apiKey = Optional.ofNullable(System.getenv(API_KEY_ENVIRONMENT_VARIABLE)).orElse("");
        return apiKey.isBlank()
                ? Optional.empty()
                : Optional.of(new TvdbCredentials(apiKey, Optional.empty()));
    }
}
