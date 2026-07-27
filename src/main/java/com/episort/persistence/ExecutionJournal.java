package com.episort.persistence;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Diagnostic record of execution runs, written outside the media workspace
 * (Story 7.4) so a run can be reconstructed even if the workspace is gone.
 */
public interface ExecutionJournal {
    void started(UUID runId, Path workspace, int totalOperations);

    void progressed(UUID runId, Path workspace, int totalOperations, int completedOperations);

    void finished(UUID runId, Path workspace, int totalOperations, int completedOperations, ExecutionRunState state);

    List<ExecutionJournalEntry> readAll();

    /** Where the journal lives, so the UI can point the user at it. */
    Path location();

    /**
     * The last run if it never reached a terminal state — meaning the app closed
     * or stopped mid-execution.
     */
    default Optional<ExecutionJournalEntry> interruptedRun() {
        List<ExecutionJournalEntry> entries = readAll();
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        ExecutionJournalEntry last = entries.getLast();
        return last.interrupted() ? Optional.of(last) : Optional.empty();
    }
}
