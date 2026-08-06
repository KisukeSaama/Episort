package com.episort.persistence;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Persisted inverse of one completed, reversible execution plan. */
public record RollbackPlan(UUID runId, Instant recordedAt, Path workspace, List<RollbackMove> moves) {
    public RollbackPlan {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(recordedAt, "recordedAt");
        Objects.requireNonNull(workspace, "workspace");
        moves = List.copyOf(moves);
    }
}
