package com.episort.persistence;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record RunEvent(
        UUID id,
        Instant occurredAt,
        RunEventType type,
        RunEventStatus status,
        Optional<Path> workspace,
        Optional<Path> subjectPath,
        String summary,
        Map<String, String> metrics) {

    public RunEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(subjectPath, "subjectPath");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(metrics, "metrics");
        metrics = Map.copyOf(metrics);
    }

    public static RunEvent of(
            RunEventType type,
            RunEventStatus status,
            Optional<Path> workspace,
            Optional<Path> subjectPath,
            String summary,
            Map<String, String> metrics) {
        return new RunEvent(
                UUID.randomUUID(),
                Instant.now(),
                type,
                status,
                workspace,
                subjectPath,
                summary,
                new LinkedHashMap<>(metrics));
    }
}
