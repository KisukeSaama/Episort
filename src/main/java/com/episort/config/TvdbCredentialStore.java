package com.episort.config;

import java.nio.file.Path;
import java.util.Optional;

public interface TvdbCredentialStore {
    Optional<TvdbCredentials> load();

    void save(TvdbCredentials credentials);

    default Optional<Path> credentialFile() {
        return Optional.empty();
    }
}
