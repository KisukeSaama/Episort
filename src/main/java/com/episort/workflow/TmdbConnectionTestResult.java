package com.episort.workflow;

import java.util.Optional;

public final class TmdbConnectionTestResult {
    private final Optional<ApplicationError> error;

    private TmdbConnectionTestResult(Optional<ApplicationError> error) {
        this.error = error;
    }

    public static TmdbConnectionTestResult passed() {
        return new TmdbConnectionTestResult(Optional.empty());
    }

    public static TmdbConnectionTestResult failure(ApplicationError error) {
        return new TmdbConnectionTestResult(Optional.of(error));
    }

    public Optional<ApplicationError> error() {
        return error;
    }

    public boolean success() {
        return error.isEmpty();
    }
}
