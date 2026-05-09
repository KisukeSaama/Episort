package com.episort.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileRunEventStoreTest {

    @Test
    void appendCreatesFileAndReadAllReturnsTheRecord(@TempDir Path tempDir) {
        Path logFile = tempDir.resolve("run-history.jsonl");
        FileRunEventStore store = new FileRunEventStore(logFile);

        Map<String, String> metrics = new LinkedHashMap<>();
        metrics.put("supported", "3");
        metrics.put("series", "1");

        RunEvent event = new RunEvent(
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                Instant.parse("2026-05-09T12:34:56Z"),
                RunEventType.SCAN_COMPLETED,
                RunEventStatus.SUCCESS,
                Optional.of(Path.of("C:/Media")),
                Optional.of(Path.of("C:/Media/Inbox")),
                "3 video(s), 1 series",
                metrics);

        store.append(event);

        List<RunEvent> events = store.readAll();
        assertEquals(1, events.size());
        RunEvent loaded = events.get(0);
        assertEquals(event.id(), loaded.id());
        assertEquals(event.occurredAt(), loaded.occurredAt());
        assertSame(event.type(), loaded.type());
        assertSame(event.status(), loaded.status());
        assertEquals(event.summary(), loaded.summary());
        assertEquals(event.metrics(), loaded.metrics());
    }

    @Test
    void appendsAreCumulativeAcrossInvocations(@TempDir Path tempDir) {
        Path logFile = tempDir.resolve("run-history.jsonl");
        FileRunEventStore store = new FileRunEventStore(logFile);

        store.append(scanCompletedEvent("first"));
        store.append(scanCompletedEvent("second"));

        List<RunEvent> events = store.readAll();
        assertEquals(2, events.size());
        assertEquals("first", events.get(0).summary());
        assertEquals("second", events.get(1).summary());
    }

    @Test
    void readAllOnMissingFileReturnsEmpty(@TempDir Path tempDir) {
        FileRunEventStore store = new FileRunEventStore(tempDir.resolve("missing.jsonl"));
        assertTrue(store.readAll().isEmpty());
    }

    @Test
    void malformedLineIsSkippedNotThrown(@TempDir Path tempDir) throws Exception {
        Path logFile = tempDir.resolve("run-history.jsonl");
        FileRunEventStore store = new FileRunEventStore(logFile);
        store.append(scanCompletedEvent("real"));

        // Append a corrupt line directly to the file.
        Files.writeString(logFile,
                "{this is not json}\n" + System.lineSeparator(),
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND);

        List<RunEvent> events = store.readAll();
        assertEquals(1, events.size());
        assertEquals("real", events.get(0).summary());
    }

    @Test
    void escapesQuotesAndNewlinesInSummary(@TempDir Path tempDir) {
        Path logFile = tempDir.resolve("run-history.jsonl");
        FileRunEventStore store = new FileRunEventStore(logFile);

        RunEvent event = scanCompletedEvent("line1\n\"with quotes\"\tand tab");
        store.append(event);

        List<RunEvent> events = store.readAll();
        assertEquals(1, events.size());
        assertEquals("line1\n\"with quotes\"\tand tab", events.get(0).summary());
    }

    private static RunEvent scanCompletedEvent(String summary) {
        return RunEvent.of(
                RunEventType.SCAN_COMPLETED,
                RunEventStatus.SUCCESS,
                Optional.empty(),
                Optional.empty(),
                summary,
                Map.of("k", "v"));
    }
}
