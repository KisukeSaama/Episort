package com.episort.persistence;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One recorded execution run. Deliberately holds counts and paths only — never
 * credentials, tokens, or TMDB metadata.
 */
public record ExecutionJournalEntry(
        UUID runId,
        Instant recordedAt,
        Path workspace,
        int totalOperations,
        int completedOperations,
        ExecutionRunState state) {

    public ExecutionJournalEntry {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(recordedAt, "recordedAt");
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(state, "state");
    }

    /** True when this run was never closed, i.e. the app stopped mid-execution. */
    public boolean interrupted() {
        return state == ExecutionRunState.RUNNING;
    }
}
