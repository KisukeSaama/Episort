package com.episort.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Append-only execution journal stored in the per-user application directory —
 * deliberately outside the media workspace, so moving or losing the workspace
 * never destroys the record of what happened to it (Story 7.4).
 */
public final class FileExecutionJournal implements ExecutionJournal {
    private static final String FILE_NAME = "execution-journal.jsonl";

    private final Path journalFile;
    private final Clock clock;

    public FileExecutionJournal(Path journalFile) {
        this(journalFile, Clock.systemUTC());
    }

    public FileExecutionJournal(Path journalFile, Clock clock) {
        this.journalFile = Objects.requireNonNull(journalFile, "journalFile").toAbsolutePath().normalize();
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Same per-platform application directory as the run-history log. */
    public static FileExecutionJournal userProfileJournal() {
        Path runHistory = FileRunEventStore.userProfileStore().logFilePath();
        return new FileExecutionJournal(runHistory.resolveSibling(FILE_NAME));
    }

    @Override
    public Path location() {
        return journalFile;
    }

    @Override
    public void started(UUID runId, Path workspace, int totalOperations) {
        append(new ExecutionJournalEntry(
                runId, Instant.now(clock), workspace, totalOperations, 0, ExecutionRunState.RUNNING));
    }

    @Override
    public void progressed(UUID runId, Path workspace, int totalOperations, int completedOperations) {
        append(new ExecutionJournalEntry(
                runId, Instant.now(clock), workspace, totalOperations, completedOperations,
                ExecutionRunState.RUNNING));
    }

    @Override
    public void finished(
            UUID runId, Path workspace, int totalOperations, int completedOperations, ExecutionRunState state) {
        append(new ExecutionJournalEntry(
                runId, Instant.now(clock), workspace, totalOperations, completedOperations, state));
    }

    @Override
    public List<ExecutionJournalEntry> readAll() {
        if (!Files.exists(journalFile)) {
            return List.of();
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(journalFile, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new RunEventStoreException("Unable to read the execution journal", exception);
        }
        List<ExecutionJournalEntry> entries = new ArrayList<>(lines.size());
        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                entries.add(deserialize(trimmed));
            } catch (RuntimeException ignored) {
                // Tolerate malformed lines so one corrupt record cannot hide the rest.
            }
        }
        return List.copyOf(entries);
    }

    private void append(ExecutionJournalEntry entry) {
        try {
            Path parent = journalFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            OpenOption[] options = {
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.WRITE
            };
            Files.writeString(
                    journalFile, serialize(entry) + System.lineSeparator(), StandardCharsets.UTF_8, options);
        } catch (IOException exception) {
            throw new RunEventStoreException("Unable to append to the execution journal", exception);
        }
    }

    static String serialize(ExecutionJournalEntry entry) {
        return new JsonWriter()
                .put("runId", entry.runId().toString())
                .put("recordedAt", entry.recordedAt().toString())
                .put("workspace", entry.workspace().toString())
                .put("totalOperations", String.valueOf(entry.totalOperations()))
                .put("completedOperations", String.valueOf(entry.completedOperations()))
                .put("state", entry.state().name())
                .build();
    }

    static ExecutionJournalEntry deserialize(String json) {
        Map<String, Object> root = JsonReader.parseObject(json);
        return new ExecutionJournalEntry(
                UUID.fromString(stringField(root, "runId")),
                Instant.parse(stringField(root, "recordedAt")),
                Path.of(stringField(root, "workspace")),
                Integer.parseInt(stringField(root, "totalOperations")),
                Integer.parseInt(stringField(root, "completedOperations")),
                ExecutionRunState.valueOf(stringField(root, "state")));
    }

    private static String stringField(Map<String, Object> root, String key) {
        Object value = root.get(key);
        if (value instanceof String stringValue) {
            return stringValue;
        }
        throw new IllegalArgumentException("Field '" + key + "' is missing or not a string");
    }
}
