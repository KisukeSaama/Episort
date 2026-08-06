package com.episort.workflow;

import com.episort.filesystem.MediaFileMover;
import com.episort.filesystem.WorkspaceBoundary;
import com.episort.persistence.FileRollbackPlanStore;
import com.episort.persistence.RollbackMove;
import com.episort.persistence.RollbackPlan;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Validates and reverses a retained successfully executed plan. */
public final class LastPlanRollbackService {
    private final FileRollbackPlanStore store;

    public LastPlanRollbackService(FileRollbackPlanStore store) {
        this.store = store;
    }

    public void record(ExecutionReport report) {
        store.save(report);
    }

    public Optional<RollbackPlan> availablePlan(UUID runId) {
        return store.load(runId);
    }

    public List<RollbackMove> validate(UUID runId) throws IOException {
        RollbackPlan plan = availablePlan(runId)
                .orElseThrow(() -> new IOException("The selected run is no longer reversible."));
        WorkspaceBoundary boundary = new WorkspaceBoundary(plan.workspace());
        for (RollbackMove move : plan.moves()) {
            Path original = boundary.resolvePlannedInside(move.originalPath())
                    .orElseThrow(() -> new IOException("Original path is outside the workspace: " + move.originalPath()));
            Path current = boundary.resolvePlannedInside(move.currentPath())
                    .orElseThrow(() -> new IOException("Current path is outside the workspace: " + move.currentPath()));
            if (Files.exists(original, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Original path is occupied: " + original);
            }
            if (!Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Moved file is missing or is no longer a regular file: " + current);
            }
            if (!move.fingerprint().matches(current)) {
                throw new IOException("Moved file has changed since the plan was recorded: " + current);
            }
        }
        return plan.moves();
    }

    public int rollback(UUID runId) throws IOException {
        return rollback(runId, progress -> { });
    }

    public int rollback(UUID runId, Consumer<RollbackProgress> progressListener) throws IOException {
        RollbackPlan plan = availablePlan(runId)
                .orElseThrow(() -> new IOException("The selected run is no longer reversible."));
        List<RollbackMove> moves = validate(runId);
        WorkspaceBoundary boundary = new WorkspaceBoundary(plan.workspace());
        MediaFileMover mover = new MediaFileMover(boundary);
        List<RollbackMove> reversed = new ArrayList<>(moves);
        Collections.reverse(reversed);
        int completed = 0;
        for (RollbackMove move : reversed) {
            progressListener.accept(new RollbackProgress(
                    completed, moves.size(), move.currentPath(), move.originalPath()));
            mover.move(move.currentPath(), move.originalPath());
            removeEmptyDestinationFolders(move.currentPath().getParent(), boundary);
            completed++;
            progressListener.accept(new RollbackProgress(
                    completed, moves.size(), move.currentPath(), move.originalPath()));
        }
        store.remove(runId);
        return moves.size();
    }

    private static void removeEmptyDestinationFolders(Path folder, WorkspaceBoundary boundary)
            throws IOException {
        Path current = folder;
        while (current != null && !current.equals(boundary.root())) {
            Path candidate = current;
            Path safe = boundary.resolvePlannedInside(candidate)
                    .orElseThrow(() -> new IOException("Folder is outside the workspace: " + candidate));
            try {
                if (!Files.deleteIfExists(safe)) {
                    return;
                }
            } catch (DirectoryNotEmptyException exception) {
                return;
            }
            current = current.getParent();
        }
    }
}
