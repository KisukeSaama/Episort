package com.episort.persistence;

import com.episort.filesystem.MediaFileFingerprint;
import com.episort.filesystem.PathSerialization;
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

        StoredPlans stored = readFile();
        List<RollbackPlan> plans = new ArrayList<>(stored.plans());
        plans.removeIf(plan -> plan.runId().equals(report.runId()));
        plans.add(new RollbackPlan(
                report.runId(), Instant.now(), report.workspaceRoot(), moves));
        plans.sort(Comparator.comparing(RollbackPlan::recordedAt));
        if (plans.size() > maxPlans) {
            plans = new ArrayList<>(plans.subList(plans.size() - maxPlans, plans.size()));
        }
        writeAll(plans, stored.unresolved());
    }

    /** Compatibility helper returning the newest retained plan. */
    public synchronized Optional<RollbackPlan> load() {
        return loadAll().stream().max(Comparator.comparing(RollbackPlan::recordedAt));
    }

    public synchronized Optional<RollbackPlan> load(UUID runId) {
        return loadAll().stream().filter(plan -> plan.runId().equals(runId)).findFirst();
    }

    public synchronized List<RollbackPlan> loadAll() {
        return readFile().plans();
    }

    /**
     * Reads every retained plan. A plan recorded against a server that is not
     * connected right now cannot be turned into live paths, so its lines are
     * kept verbatim and written back untouched: being offline must not erase
     * the way back.
     */
    private StoredPlans readFile() {
        if (!Files.exists(file)) {
            return StoredPlans.empty();
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.isEmpty() || !HEADER.equals(lines.getFirst())) {
                return StoredPlans.empty();
            }
            List<RollbackPlan> plans = new ArrayList<>();
            List<List<String>> unresolved = new ArrayList<>();
            int cursor = 1;
            while (cursor < lines.size()) {
                int planStart = cursor;
                String[] planFields = lines.get(cursor++).split("\\t", -1);
                if (planFields.length != 4 || !"PLAN".equals(planFields[0])) {
                    return StoredPlans.empty();
                }
                UUID runId = UUID.fromString(planFields[1]);
                Instant recordedAt = Instant.parse(planFields[2]);
                Optional<Path> workspace = decode(planFields[3]);
                boolean resolvable = workspace.isPresent();
                List<RollbackMove> moves = new ArrayList<>();
                while (cursor < lines.size() && !"END".equals(lines.get(cursor))) {
                    String[] moveFields = lines.get(cursor++).split("\\t", -1);
                    if (moveFields.length != 6 || !"MOVE".equals(moveFields[0])) {
                        return StoredPlans.empty();
                    }
                    Optional<Path> original = decode(moveFields[1]);
                    Optional<Path> current = decode(moveFields[2]);
                    MediaFileFingerprint fingerprint = new MediaFileFingerprint(
                            Long.parseLong(moveFields[3]),
                            Long.parseLong(moveFields[4]),
                            moveFields[5]);
                    if (original.isEmpty() || current.isEmpty()) {
                        resolvable = false;
                    } else {
                        moves.add(new RollbackMove(original.orElseThrow(), current.orElseThrow(), fingerprint));
                    }
                }
                if (cursor >= lines.size() || (resolvable && moves.isEmpty())) {
                    return StoredPlans.empty();
                }
                cursor++;
                if (resolvable) {
                    plans.add(new RollbackPlan(runId, recordedAt, workspace.orElseThrow(), moves));
                } else {
                    unresolved.add(List.copyOf(lines.subList(planStart, cursor)));
                }
            }
            return new StoredPlans(List.copyOf(plans), List.copyOf(unresolved));
        } catch (IOException | RuntimeException exception) {
            return StoredPlans.empty();
        }
    }

    /** Consumes only the plan that was successfully restored. */
    public synchronized void remove(UUID runId) {
        StoredPlans stored = readFile();
        List<RollbackPlan> retained = stored.plans().stream()
                .filter(plan -> !plan.runId().equals(runId))
                .toList();
        if (retained.isEmpty() && stored.unresolved().isEmpty()) {
            clear();
        } else {
            writeAll(retained, stored.unresolved());
        }
    }

    public synchronized void clear() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            throw new RunEventStoreException("Unable to clear the rollback plans", exception);
        }
    }

    private void writeAll(List<RollbackPlan> plans, List<List<String>> unresolved) {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        for (List<String> block : unresolved) {
            lines.addAll(block);
        }
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
                .encodeToString(PathSerialization.encode(path).getBytes(StandardCharsets.UTF_8));
    }

    private static Optional<Path> decode(String value) {
        return PathSerialization.decode(new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8));
    }

    /** Plans usable now, plus the verbatim lines of those that are not. */
    private record StoredPlans(List<RollbackPlan> plans, List<List<String>> unresolved) {
        private static StoredPlans empty() {
            return new StoredPlans(List.of(), List.of());
        }
    }
}
