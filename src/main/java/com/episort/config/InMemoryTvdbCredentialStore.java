package com.episort.config;

import java.util.Optional;

public final class InMemoryTvdbCredentialStore implements TvdbCredentialStore {
    private TvdbCredentials credentials;

    @Override
    public Optional<TvdbCredentials> load() {
        return Optional.ofNullable(credentials);
    }

    @Override
    public void save(TvdbCredentials credentials) {
        this.credentials = credentials;
    }
}
