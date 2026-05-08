package com.episort.config;

import java.nio.file.Path;
import java.util.Optional;

public interface SettingsStore {
    AppSettings load();

    void save(AppSettings settings);

    default Optional<Path> settingsFile() {
        return Optional.empty();
    }
}
