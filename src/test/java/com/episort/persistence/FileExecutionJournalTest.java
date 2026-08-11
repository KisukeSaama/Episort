package com.episort.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileExecutionJournalTest {
    @TempDir
    Path tempDir;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-26T10:15:30Z"), ZoneOffset.UTC);

    @Test
    void entriesSurviveASerializeDeserializeRoundTrip() {
        Path file = tempDir.resolve("journal.jsonl");
        FileExecutionJournal journal = new FileExecutionJournal(file, clock);
        UUID runId = UUID.randomUUID();
        Path workspace = tempDir.resolve("workspace");

        journal.started(runId, workspace, 7);
        journal.finished(runId, workspace, 7, 7, ExecutionRunState.COMPLETED);

        List<ExecutionJournalEntry> entries = journal.readAll();
        assertEquals(2, entries.size());
        assertEquals(runId, entries.getFirst().runId());
        assertEquals(workspace, entries.getFirst().workspace());
        assertEquals(Instant.parse("2026-07-26T10:15:30Z"), entries.getFirst().recordedAt());
        assertEquals(7, entries.getLast().completedOperations());
        assertEquals(ExecutionRunState.COMPLETED, entries.getLast().state());
    }

    @Test
    void theJournalIsCreatedOnDemandAndReadsEmptyBeforeAnyRun() {
        FileExecutionJournal journal = new FileExecutionJournal(
                tempDir.resolve("nested").resolve("journal.jsonl"), clock);

        assertTrue(journal.readAll().isEmpty());
        assertTrue(journal.interruptedRun().isEmpty());

        journal.started(UUID.randomUUID(), tempDir, 1);

        assertTrue(Files.exists(journal.location()));
    }

    @Test
    void onlyAnUnfinishedLastRunCountsAsInterrupted() {
        FileExecutionJournal journal = new FileExecutionJournal(tempDir.resolve("journal.jsonl"), clock);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        journal.started(first, tempDir, 3);
        journal.finished(first, tempDir, 3, 3, ExecutionRunState.COMPLETED);
        assertTrue(journal.interruptedRun().isEmpty());

        journal.started(second, tempDir, 5);
        journal.progressed(second, tempDir, 5, 2);

        ExecutionJournalEntry interrupted = journal.interruptedRun().orElseThrow();
        assertEquals(second, interrupted.runId());
        assertEquals(2, interrupted.completedOperations());
    }

    @Test
    void aCorruptLineDoesNotHideTheOtherEntries() throws IOException {
        Path file = tempDir.resolve("journal.jsonl");
        FileExecutionJournal journal = new FileExecutionJournal(file, clock);
        journal.started(UUID.randomUUID(), tempDir, 1);
        Files.writeString(file, "{not json" + System.lineSeparator(),
                StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        journal.finished(UUID.randomUUID(), tempDir, 1, 1, ExecutionRunState.COMPLETED);

        assertEquals(2, journal.readAll().size());
    }

    @Test
    void theJournalLivesNextToTheRunHistoryOutsideAnyMediaWorkspace() {
        FileExecutionJournal journal = FileExecutionJournal.userProfileJournal();

        assertEquals("execution-journal.jsonl", journal.location().getFileName().toString());
        assertFalse(journal.location().toString().isBlank());
    }
}
