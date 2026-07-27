package com.episort.config;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record AppSettings(Optional<Path> workspaceDirectory) {
    public AppSettings {
        workspaceDirectory = Objects.requireNonNull(workspaceDirectory);
    }

    public AppSettings(Path workspaceDirectory) {
        this(Optional.of(workspaceDirectory.toAbsolutePath().normalize()));
    }

    public static AppSettings empty() {
        return new AppSettings(Optional.empty());
    }
}
