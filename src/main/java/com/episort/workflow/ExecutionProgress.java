package com.episort.workflow;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Progress of a running execution (Story 7.4). Carries the file being worked on
 * so the UI can distinguish active work from a stalled workflow.
 */
public record ExecutionProgress(
        int completed,
        int total,
        Optional<Path> currentSource,
        Optional<Path> currentDestination) {

    public ExecutionProgress {
        Objects.requireNonNull(currentSource, "currentSource");
        Objects.requireNonNull(currentDestination, "currentDestination");
    }

    public double fraction() {
        return total <= 0 ? 1.0 : (double) completed / total;
    }
}
