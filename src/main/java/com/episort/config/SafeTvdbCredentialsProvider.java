package com.episort.config;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Resolves optional TVDB credentials without letting an unavailable vault break local scans. */
public final class SafeTvdbCredentialsProvider {
    private final Supplier<Optional<TvdbCredentials>> embeddedProvider;
    private final Supplier<Optional<TvdbCredentials>> persistentProvider;

    public SafeTvdbCredentialsProvider(
            Supplier<Optional<TvdbCredentials>> embeddedProvider,
            Supplier<Optional<TvdbCredentials>> persistentProvider) {
        this.embeddedProvider = Objects.requireNonNull(embeddedProvider, "embeddedProvider");
        this.persistentProvider = Objects.requireNonNull(persistentProvider, "persistentProvider");
    }

    public Optional<TvdbCredentials> load() {
        Optional<TvdbCredentials> embedded = embeddedProvider.get();
        if (embedded.isPresent()) {
            return embedded;
        }
        try {
            return persistentProvider.get();
        } catch (SettingsStoreException exception) {
            return Optional.empty();
        }
    }
}
