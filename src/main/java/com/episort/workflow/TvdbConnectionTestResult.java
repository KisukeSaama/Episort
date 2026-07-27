package com.episort.workflow;

import java.util.Optional;

public final class TvdbConnectionTestResult {
    private final Optional<ApplicationError> error;

    private TvdbConnectionTestResult(Optional<ApplicationError> error) {
        this.error = error;
    }

    public static TvdbConnectionTestResult passed() {
        return new TvdbConnectionTestResult(Optional.empty());
    }

    public static TvdbConnectionTestResult failure(ApplicationError error) {
        return new TvdbConnectionTestResult(Optional.of(error));
    }

    public Optional<ApplicationError> error() {
        return error;
    }

    public boolean success() {
        return error.isEmpty();
    }
}
