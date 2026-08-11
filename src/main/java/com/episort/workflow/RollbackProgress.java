package com.episort.workflow;

import java.nio.file.Path;
import java.util.Objects;

/** Progress of an inverse file plan, including the exact move currently shown. */
public record RollbackProgress(
        int completed,
        int total,
        Path currentPath,
        Path originalPath) {
    public RollbackProgress {
        if (completed < 0 || total < 0 || completed > total) {
            throw new IllegalArgumentException("Invalid rollback progress");
        }
        Objects.requireNonNull(currentPath, "currentPath");
        Objects.requireNonNull(originalPath, "originalPath");
    }

    public double fraction() {
        return total == 0 ? 1.0 : (double) completed / total;
    }
}
