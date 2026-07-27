package com.episort.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class FileRunEventStore implements RunEventStore {
    private static final String FILE_NAME = "run-history.jsonl";

    private final Path logFile;

    public FileRunEventStore(Path logFile) {
        this.logFile = Objects.requireNonNull(logFile, "logFile").toAbsolutePath().normalize();
    }

    public static FileRunEventStore userProfileStore() {
        return userProfileStore(
                System.getProperty("os.name", ""),
                System.getenv(),
                Path.of(System.getProperty("user.home", ".")));
    }

    static FileRunEventStore userProfileStore(String osName, Map<String, String> environment, Path userHome) {
        String normalizedOsName = osName.toLowerCase();
        if (normalizedOsName.contains("win")) {
            String localAppData = environment.get("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                return new FileRunEventStore(Path.of(localAppData, "Episort", FILE_NAME));
            }
            return new FileRunEventStore(userHome.resolve(Path.of("AppData", "Local", "Episort", FILE_NAME)));
        }
        if (normalizedOsName.contains("mac")) {
            return new FileRunEventStore(userHome.resolve(Path.of("Library", "Application Support", "Episort", FILE_NAME)));
        }
        String xdgConfigHome = environment.get("XDG_CONFIG_HOME");
        if (xdgConfigHome != null && !xdgConfigHome.isBlank()) {
            return new FileRunEventStore(Path.of(xdgConfigHome, "episort", FILE_NAME));
        }
        return new FileRunEventStore(userHome.resolve(Path.of(".config", "episort", FILE_NAME)));
    }

    public Path logFilePath() {
        return logFile;
    }

    @Override
    public void append(RunEvent event) {
        Objects.requireNonNull(event, "event");
        try {
            Files.createDirectories(logFile.getParent());
            String line = serialize(event) + System.lineSeparator();
            OpenOption[] options = {
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.WRITE
            };
            Files.writeString(logFile, line, StandardCharsets.UTF_8, options);
        } catch (IOException exception) {
            throw new RunEventStoreException("Unable to append run event", exception);
        }
    }

    @Override
    public List<RunEvent> readAll() {
        if (!Files.exists(logFile)) {
            return List.of();
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new RunEventStoreException("Unable to read run history", exception);
        }
        List<RunEvent> events = new ArrayList<>(lines.size());
        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                events.add(deserialize(trimmed));
            } catch (RuntimeException ignored) {
                // Tolerate malformed lines so a single corrupt record can't blind the user.
            }
        }
        return events;
    }

    @Override
    public void clear() {
        try {
            Files.createDirectories(logFile.getParent());
            Files.writeString(
                    logFile,
                    "",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new RunEventStoreException("Unable to clear run history", exception);
        }
    }

    static String serialize(RunEvent event) {
        JsonWriter writer = new JsonWriter()
                .put("id", event.id().toString())
                .put("occurredAt", event.occurredAt().toString())
                .put("type", event.type().name())
                .put("status", event.status().name())
                .put("workspace", event.workspace().map(Path::toString).orElse(""))
                .put("subjectPath", event.subjectPath().map(Path::toString).orElse(""))
                .put("summary", event.summary())
                .putObject("metrics", event.metrics());
        return writer.build();
    }

    @SuppressWarnings("unchecked")
    static RunEvent deserialize(String json) {
        Map<String, Object> root = JsonReader.parseObject(json);

        UUID id = UUID.fromString(stringField(root, "id"));
        Instant occurredAt;
        try {
            occurredAt = Instant.parse(stringField(root, "occurredAt"));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Invalid occurredAt", exception);
        }
        RunEventType type = RunEventType.valueOf(stringField(root, "type"));
        RunEventStatus status = RunEventStatus.valueOf(stringField(root, "status"));

        Optional<Path> workspace = optionalPath(stringField(root, "workspace"));
        Optional<Path> subjectPath = optionalPath(stringField(root, "subjectPath"));
        String summary = stringField(root, "summary");

        Map<String, String> metrics = new LinkedHashMap<>();
        Object rawMetrics = root.get("metrics");
        if (rawMetrics instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                metrics.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }

        return new RunEvent(id, occurredAt, type, status, workspace, subjectPath, summary, metrics);
    }

    private static String stringField(Map<String, Object> root, String key) {
        Object value = root.get(key);
        if (value == null) {
            return "";
        }
        if (value instanceof String stringValue) {
            return stringValue;
        }
        throw new IllegalArgumentException("Field '" + key + "' is not a string");
    }

    private static Optional<Path> optionalPath(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Path.of(value));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}
