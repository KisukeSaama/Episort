package com.episort.persistence;

import com.episort.filesystem.MediaFileFingerprint;
import com.episort.workflow.ExecutionReport;
import com.episort.workflow.FileExecutionResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Bounded, atomic store for reversible execution plans. */
public final class FileRollbackPlanStore {
    // Keep the historical filename so the next successful save replaces the
    // former single-slot format instead of leaving stale application data.
    private static final String FILE_NAME = "last-rollback-plan.txt";
    private static final String HEADER = "EPISORT_ROLLBACK_V2";
    private static final int DEFAULT_MAX_PLANS = 20;

    private final Path file;
    private final int maxPlans;

    public FileRollbackPlanStore(Path file) {
        this(file, DEFAULT_MAX_PLANS);
    }

    public FileRollbackPlanStore(Path file, int maxPlans) {
        if (maxPlans < 1) {
            throw new IllegalArgumentException("maxPlans must be positive");
        }
        this.file = file.toAbsolutePath().normalize();
        this.maxPlans = maxPlans;
    }

    public static FileRollbackPlanStore userProfileStore() {
        Path history = FileRunEventStore.userProfileStore().logFilePath();
        return new FileRollbackPlanStore(history.resolveSibling(FILE_NAME));
    }

    /** Records a reversible execution without discarding older independent plans. */
    public synchronized void save(ExecutionReport report) {
        if (report.succeeded().isEmpty() || !report.deleted().isEmpty()) {
            return;
        }
        List<RollbackMove> moves = new ArrayList<>();
        try {
            for (FileExecutionResult result : report.succeeded()) {
                if (result.destinationPath().isEmpty()) {
                    continue;
                }
                Path destination = result.destinationPath().orElseThrow();
                moves.add(new RollbackMove(
                        result.sourcePath(), destination, MediaFileFingerprint.capture(destination)));
            }
        } catch (IOException exception) {
            throw new RunEventStoreException("Unable to fingerprint the rollback plan", exception);
        }
        if (moves.isEmpty()) {
            return;
        }

        List<RollbackPlan> plans = new ArrayList<>(loadAll());
        plans.removeIf(plan -> plan.runId().equals(report.runId()));
        plans.add(new RollbackPlan(
                report.runId(), Instant.now(), report.workspaceRoot(), moves));
        plans.sort(Comparator.comparing(RollbackPlan::recordedAt));
        if (plans.size() > maxPlans) {
            plans = new ArrayList<>(plans.subList(plans.size() - maxPlans, plans.size()));
        }
        writeAll(plans);
    }

    /** Compatibility helper returning the newest retained plan. */
    public synchronized Optional<RollbackPlan> load() {
        return loadAll().stream().max(Comparator.comparing(RollbackPlan::recordedAt));
    }

    public synchronized Optional<RollbackPlan> load(UUID runId) {
        return loadAll().stream().filter(plan -> plan.runId().equals(runId)).findFirst();
    }

    public synchronized List<RollbackPlan> loadAll() {
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.isEmpty() || !HEADER.equals(lines.getFirst())) {
                return List.of();
            }
            List<RollbackPlan> plans = new ArrayList<>();
            int cursor = 1;
            while (cursor < lines.size()) {
                String[] planFields = lines.get(cursor++).split("\\t", -1);
                if (planFields.length != 4 || !"PLAN".equals(planFields[0])) {
                    return List.of();
                }
                UUID runId = UUID.fromString(planFields[1]);
                Instant recordedAt = Instant.parse(planFields[2]);
                Path workspace = decode(planFields[3]);
                List<RollbackMove> moves = new ArrayList<>();
                while (cursor < lines.size() && !"END".equals(lines.get(cursor))) {
                    String[] moveFields = lines.get(cursor++).split("\\t", -1);
                    if (moveFields.length != 6 || !"MOVE".equals(moveFields[0])) {
                        return List.of();
                    }
                    moves.add(new RollbackMove(
                            decode(moveFields[1]),
                            decode(moveFields[2]),
                            new MediaFileFingerprint(
                                    Long.parseLong(moveFields[3]),
                                    Long.parseLong(moveFields[4]),
                                    moveFields[5])));
                }
                if (cursor >= lines.size() || moves.isEmpty()) {
                    return List.of();
                }
                cursor++;
                plans.add(new RollbackPlan(runId, recordedAt, workspace, moves));
            }
            return List.copyOf(plans);
        } catch (IOException | RuntimeException exception) {
            return List.of();
        }
    }

    /** Consumes only the plan that was successfully restored. */
    public synchronized void remove(UUID runId) {
        List<RollbackPlan> retained = loadAll().stream()
                .filter(plan -> !plan.runId().equals(runId))
                .toList();
        if (retained.isEmpty()) {
            clear();
        } else {
            writeAll(retained);
        }
    }

    public synchronized void clear() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            throw new RunEventStoreException("Unable to clear the rollback plans", exception);
        }
    }

    private void writeAll(List<RollbackPlan> plans) {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        for (RollbackPlan plan : plans) {
            lines.add("PLAN\t" + plan.runId() + "\t" + plan.recordedAt() + "\t" + encode(plan.workspace()));
            for (RollbackMove move : plan.moves()) {
                MediaFileFingerprint fingerprint = move.fingerprint();
                lines.add("MOVE\t" + encode(move.originalPath())
                        + "\t" + encode(move.currentPath())
                        + "\t" + fingerprint.size()
                        + "\t" + fingerprint.lastModifiedMillis()
                        + "\t" + fingerprint.sampleSha256());
            }
            lines.add("END");
        }

        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.write(temporary, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, file,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new RunEventStoreException("Unable to save the rollback plans", exception);
        }
    }

    private static String encode(Path path) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(path.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static Path decode(String value) {
        return Path.of(new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8));
    }
}
