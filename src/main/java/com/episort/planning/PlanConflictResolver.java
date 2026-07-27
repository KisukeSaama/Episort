package com.episort.planning;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Turns the conflicts of an operation plan into a plan that can run (Story 6.4).
 *
 * <p>Resolution is a pure replan: it reads modification times and probes the
 * destination, then rebuilds the plan. It creates, moves, and deletes nothing —
 * the only thing a resolution can change is what execution will later be allowed
 * to overwrite, and that always comes from an explicit user decision.
 *
 * <p>Three decisions exist. {@link ConflictResolution#REPLACE} makes the planned
 * file win its destination, {@link ConflictResolution#SKIP} leaves the file
 * untouched where it is, and {@link ConflictResolution#DELETE_SOURCE} plans the
 * removal of the extra copy. A skipped file is never deleted; it simply stops
 * being part of the plan. A deletion is only ever planned here — like every other
 * decision, it happens at execution, after the user has seen it in the plan.
 */
public final class PlanConflictResolver {

    /**
     * The automatic answer: for every conflict that is only about two files
     * fighting for one destination, the most recently modified file wins.
     *
     * <p>Conflicts that replacing cannot fix — outside the workspace, too long
     * for Windows, folder blocked by a file — are skipped, because the only other
     * option would be to leave the plan blocked.
     *
     * @return one decision per conflicting source path, in plan order
     */
    public Map<Path, ConflictResolution> mostRecentWins(OperationPlan plan) {
        return byAge(plan, true);
    }

    /**
     * The mirror answer: the oldest file wins. Same rules, opposite comparison —
     * for a duplicate it keeps the copy that has been on disk the longest, which
     * is the one a user reaches for when the fresher file is a re-download they
     * do not trust.
     *
     * @return one decision per conflicting source path, in plan order
     */
    public Map<Path, ConflictResolution> oldestWins(OperationPlan plan) {
        return byAge(plan, false);
    }

    private Map<Path, ConflictResolution> byAge(OperationPlan plan, boolean keepNewest) {
        Objects.requireNonNull(plan, "plan");
        Map<Path, ConflictResolution> decisions = new LinkedHashMap<>();

        // A duplicate group is decided as a group: exactly one file can win, and
        // deciding each row on its own would let every one of them claim the spot.
        Map<String, List<PlannedOperation>> duplicateGroups = new LinkedHashMap<>();
        for (PlannedOperation operation : plan.conflicts()) {
            PlanConflict conflict = operation.conflict().orElseThrow();
            if (operation.destinationPath().isEmpty()) {
                continue;
            }
            if (conflict.type() == PlanConflictType.DUPLICATE_DESTINATION) {
                duplicateGroups
                        .computeIfAbsent(key(operation.destinationPath().orElseThrow()), ignored -> new ArrayList<>())
                        .add(operation);
            }
        }
        Map<Path, ConflictResolution> duplicateDecisions = new LinkedHashMap<>();
        for (List<PlannedOperation> group : duplicateGroups.values()) {
            PlannedOperation winner = group.getFirst();
            for (PlannedOperation candidate : group) {
                if (wins(candidate.sourcePath(), winner.sourcePath(), keepNewest)) {
                    winner = candidate;
                }
            }
            Path destination = winner.destinationPath().orElseThrow();
            // The winner still has to beat whatever already sits at the destination.
            boolean winnerTakesIt = !Files.exists(destination)
                    || wins(winner.sourcePath(), destination, keepNewest);
            for (PlannedOperation candidate : group) {
                boolean wins = candidate.sourcePath().equals(winner.sourcePath()) && winnerTakesIt;
                duplicateDecisions.put(
                        candidate.sourcePath(), wins ? ConflictResolution.REPLACE : ConflictResolution.SKIP);
            }
        }

        for (PlannedOperation operation : plan.conflicts()) {
            PlanConflict conflict = operation.conflict().orElseThrow();
            Path source = operation.sourcePath();
            if (conflict.type() == PlanConflictType.DUPLICATE_DESTINATION) {
                decisions.put(source, duplicateDecisions.getOrDefault(source, ConflictResolution.SKIP));
                continue;
            }
            if (conflict.type() == PlanConflictType.DUPLICATE_MEDIA) {
                // Only the extra copy is a row here; the copy the plan keeps is
                // named by the conflict. Either this one wins — and the other is
                // retired — or it is the one to let go. A duplicate always ends
                // with a single file, which is the whole point of the answer.
                decisions.put(source, conflict.duplicateOf()
                        .map(kept -> wins(source, kept, keepNewest)
                                ? ConflictResolution.REPLACE
                                : ConflictResolution.DELETE_SOURCE)
                        .orElse(ConflictResolution.SKIP));
                continue;
            }
            if (conflict.type() == PlanConflictType.MEDIA_ALREADY_IN_LIBRARY) {
                // The copy already in the library only loses to a file that wins on
                // age; otherwise the incoming file stays where it is and nothing is
                // deleted on a guess.
                decisions.put(source, conflict.duplicateOf()
                        .filter(existing -> wins(source, existing, keepNewest))
                        .map(existing -> ConflictResolution.REPLACE)
                        .orElse(ConflictResolution.SKIP));
                continue;
            }
            if (conflict.type() == PlanConflictType.DESTINATION_FILE_EXISTS
                    && operation.destinationPath().isPresent()) {
                decisions.put(source, wins(source, operation.destinationPath().orElseThrow(), keepNewest)
                        ? ConflictResolution.REPLACE
                        : ConflictResolution.SKIP);
                continue;
            }
            decisions.put(source, ConflictResolution.SKIP);
        }
        return Map.copyOf(decisions);
    }

    /**
     * Applies the decisions and returns the resulting plan.
     *
     * <p>Every operation keeps its position, so a caller holding the plan by index
     * stays aligned. Conflicts without a decision are left blocking: a plan is
     * never silently unblocked.
     *
     * @throws IllegalArgumentException when a decision asks to replace something
     *         that replacing cannot fix, or a file with no destination at all
     */
    public OperationPlan resolve(OperationPlan plan, Map<Path, ConflictResolution> decisions) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(decisions, "decisions");

        List<PlannedOperation> resolved = new ArrayList<>(plan.operations().size());
        Set<String> retired = new LinkedHashSet<>();
        for (PlannedOperation operation : plan.operations()) {
            ConflictResolution decision = operation.blocking() ? decisions.get(operation.sourcePath()) : null;
            if (decision == null) {
                resolved.add(operation);
                continue;
            }
            if (decision == ConflictResolution.REPLACE) {
                retiredByWinningDuplicate(operation).ifPresent(loser -> retired.add(key(loser)));
            }
            resolved.add(switch (decision) {
                case SKIP -> PlannedOperation.excluded(
                        operation.sourcePath(), operation.kind(), PlanExclusionReason.CONFLICT_SKIPPED);
                case REPLACE -> replace(operation);
                case DELETE_SOURCE -> delete(plan, operation);
            });
        }
        resolved = retire(plan, resolved, retired);

        // Two rows that both won the same destination would destroy one of the
        // user's files: re-run the duplicate check so that comes back as a conflict.
        List<PlannedOperation> checked = OperationPlanner.markDuplicateDestinations(resolved);
        return new OperationPlan(plan.workspaceRoot(), checked, stillReusedFolders(plan, checked));
    }

    /** Last modification time of a file, empty when it is gone or unreadable. */
    public static Optional<Instant> lastModified(Path path) {
        if (path == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.getLastModifiedTime(path).toInstant());
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    /**
     * The copy a winning duplicate row pushes out. Letting the extra copy win only
     * means something if the copy the plan was keeping actually goes: otherwise
     * both files stay and the answer was a no-op.
     */
    private static Optional<Path> retiredByWinningDuplicate(PlannedOperation operation) {
        PlanConflict conflict = operation.conflict().orElseThrow();
        return conflict.type() == PlanConflictType.DUPLICATE_MEDIA
                ? conflict.duplicateOf()
                : Optional.empty();
    }

    /**
     * Turns the rows of the retired copies into deletions, in place.
     *
     * <p>The retired file has a row of its own — it was the copy the plan meant to
     * keep — so the removal has to happen there, not as a side effect hidden in the
     * winner's row. That keeps one file to one row: the user sees the deletion
     * listed and the run never moves a file it is about to delete.
     */
    private static List<PlannedOperation> retire(
            OperationPlan plan, List<PlannedOperation> operations, Set<String> retired) {
        if (retired.isEmpty()) {
            return operations;
        }
        List<PlannedOperation> result = new ArrayList<>(operations);
        for (int index = 0; index < result.size(); index++) {
            PlannedOperation operation = result.get(index);
            if (!retired.contains(key(operation.sourcePath()))
                    || operation.status() == PlanOperationStatus.DELETE) {
                continue;
            }
            Path source = operation.sourcePath().toAbsolutePath().normalize();
            if (!source.startsWith(plan.workspaceRoot().toAbsolutePath().normalize())) {
                throw new IllegalArgumentException("Refusing to delete a file outside the workspace: " + source);
            }
            result.set(index, PlannedOperation.deleting(operation.sourcePath(), operation.kind()));
        }
        return result;
    }

    private static PlannedOperation replace(PlannedOperation operation) {
        PlanConflict conflict = operation.conflict().orElseThrow();
        PlanConflictType type = conflict.type();
        if (!type.resolvableByReplacement()) {
            throw new IllegalArgumentException("Replacing cannot resolve a " + type + " conflict");
        }
        Path destination = operation.destinationPath()
                .orElseThrow(() -> new IllegalArgumentException("Cannot replace without a destination"));
        // A winning copy that already sits at its own name has nothing to move.
        // Planning the move anyway would ask the mover to move a file onto itself,
        // which is at best a no-op and at worst a file destroyed to make room for
        // itself. What settles its conflict is the other copy going, not this one
        // travelling.
        if (key(operation.sourcePath()).equals(key(destination))) {
            return PlannedOperation.alreadyInPlace(operation.sourcePath(), destination, operation.kind());
        }
        // Overwrite permission is only granted where something is actually in the
        // way; an empty destination keeps the ordinary never-overwrite move.
        boolean occupied = Files.exists(destination);
        // A duplicate sitting under another name is not in the way of the move, so
        // overwriting cannot retire it: the winning file has to carry it explicitly,
        // or the library would keep both copies. Only the copy already in the
        // library is retired this way — when the duplicate is another row of the
        // plan, it has its own decision to make and is never removed by this one.
        Optional<Path> duplicate = type == PlanConflictType.MEDIA_ALREADY_IN_LIBRARY
                ? conflict.duplicateOf().filter(path ->
                        !path.toAbsolutePath().normalize().equals(destination.toAbsolutePath().normalize()))
                : Optional.empty();
        if (duplicate.isPresent()) {
            return PlannedOperation.superseding(
                    operation.sourcePath(), destination, operation.kind(), duplicate.orElseThrow(), occupied);
        }
        return occupied
                ? PlannedOperation.replacing(operation.sourcePath(), destination, operation.kind())
                : PlannedOperation.executable(operation.sourcePath(), destination, operation.kind());
    }

    /**
     * Turns a conflicting row into a deletion. Refused where the conflict is not
     * about two real files fighting over one destination, and refused outside the
     * workspace: a decision taken in the review must never be able to reach a file
     * the run had no business touching.
     */
    private static PlannedOperation delete(OperationPlan plan, PlannedOperation operation) {
        PlanConflictType type = operation.conflict().orElseThrow().type();
        if (!type.deletableSource()) {
            throw new IllegalArgumentException("Deleting the source cannot resolve a " + type + " conflict");
        }
        Path source = operation.sourcePath().toAbsolutePath().normalize();
        if (!source.startsWith(plan.workspaceRoot().toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Refusing to delete a file outside the workspace: " + source);
        }
        return PlannedOperation.deleting(operation.sourcePath(), operation.kind());
    }

    /** Keeps only the reused folders that some remaining destination still needs. */
    private static List<Path> stillReusedFolders(OperationPlan plan, List<PlannedOperation> operations) {
        List<Path> destinations = operations.stream()
                .map(PlannedOperation::destinationPath)
                .flatMap(Optional::stream)
                .toList();
        return plan.reusedFolders().stream()
                .filter(folder -> destinations.stream().anyMatch(destination -> destination.startsWith(folder)))
                .toList();
    }

    /**
     * True when {@code candidate} beats {@code reference} on age, in the direction
     * the caller asked for. Equal dates never win: a tie is not a reason to move
     * or remove anything.
     */
    private static boolean wins(Path candidate, Path reference, boolean keepNewest) {
        Optional<Instant> left = lastModified(candidate);
        Optional<Instant> right = lastModified(reference);
        if (left.isEmpty()) {
            return false;
        }
        // Nothing readable on the other side cannot outrank a file we can date.
        if (right.isEmpty()) {
            return true;
        }
        return keepNewest
                ? left.orElseThrow().isAfter(right.orElseThrow())
                : left.orElseThrow().isBefore(right.orElseThrow());
    }


    private static String key(Path path) {
        return path.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
    }
}
