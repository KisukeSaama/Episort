package com.episort.config;

import java.util.Optional;

public final class EmbeddedTvdbCredentialsProvider {
    private EmbeddedTvdbCredentialsProvider() {
    }

    public static Optional<TvdbCredentials> load() {
        String apiKey = BuildTvdbCredentials.API_KEY.trim();
        if (apiKey.isBlank() || apiKey.equals("REPLACE_WITH_TVDB_API_KEY")) {
            return Optional.empty();
        }
        return Optional.of(new TvdbCredentials(apiKey, Optional.empty()));
    }
}
