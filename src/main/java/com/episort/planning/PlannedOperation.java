package com.episort.planning;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * One line of the operation plan: where a file is, where it would go, and
 * whether it will actually move.
 *
 * @param destinationPath empty for excluded items and for items whose
 *                        destination could not be generated
 * @param replaceExisting true only when the user explicitly resolved a
 *                        destination conflict in favour of this file; execution
 *                        will then overwrite the file already sitting there.
 *                        Never set by the planner on its own.
 * @param supersedes      the duplicate copy this move replaces, sitting under a
 *                        different name than the destination. Execution removes
 *                        it once the move has landed, which is the only way the
 *                        library does not keep both. Never set by the planner on
 *                        its own: it always carries a user decision.
 */
public record PlannedOperation(
        Path sourcePath,
        Optional<Path> destinationPath,
        PlanMediaKind kind,
        PlanOperationStatus status,
        PlanExclusionReason exclusionReason,
        Optional<PlanConflict> conflict,
        boolean replaceExisting,
        Optional<Path> supersedes) {

    public PlannedOperation {
        Objects.requireNonNull(sourcePath, "sourcePath");
        Objects.requireNonNull(destinationPath, "destinationPath");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(exclusionReason, "exclusionReason");
        Objects.requireNonNull(conflict, "conflict");
        Objects.requireNonNull(supersedes, "supersedes");
        if (replaceExisting && status != PlanOperationStatus.EXECUTABLE) {
            throw new IllegalArgumentException("Only an executable operation can replace an existing file");
        }
        if (supersedes.isPresent() && status != PlanOperationStatus.EXECUTABLE) {
            throw new IllegalArgumentException("Only an executable operation can supersede a duplicate");
        }
        // A deletion has no destination by construction: the file goes away, it
        // does not land anywhere. A path here would read as a move on screen.
        if (status == PlanOperationStatus.DELETE && destinationPath.isPresent()) {
            throw new IllegalArgumentException("A deletion cannot carry a destination");
        }
    }

    static PlannedOperation executable(Path source, Path destination, PlanMediaKind kind) {
        return new PlannedOperation(
                source,
                Optional.of(destination),
                kind,
                PlanOperationStatus.EXECUTABLE,
                PlanExclusionReason.NONE,
                Optional.empty(),
                false,
                Optional.empty());
    }

    /**
     * An executable operation that the user allowed to overwrite whatever
     * occupies its destination. Only {@link PlanConflictResolver} produces these.
     */
    static PlannedOperation replacing(Path source, Path destination, PlanMediaKind kind) {
        return new PlannedOperation(
                source,
                Optional.of(destination),
                kind,
                PlanOperationStatus.EXECUTABLE,
                PlanExclusionReason.NONE,
                Optional.empty(),
                true,
                Optional.empty());
    }

    /**
     * An executable operation that also retires the duplicate copy the library
     * already holds under another name. Only {@link PlanConflictResolver}
     * produces these, and only from an explicit user decision.
     */
    static PlannedOperation superseding(
            Path source, Path destination, PlanMediaKind kind, Path duplicate, boolean replaceExisting) {
        return new PlannedOperation(
                source,
                Optional.of(destination),
                kind,
                PlanOperationStatus.EXECUTABLE,
                PlanExclusionReason.NONE,
                Optional.empty(),
                replaceExisting,
                Optional.of(duplicate));
    }

    /**
     * A file the user asked to remove while resolving a conflict. Only {@link
     * PlanConflictResolver} produces these, and only from an explicit decision.
     */
    static PlannedOperation deleting(Path source, PlanMediaKind kind) {
        return new PlannedOperation(
                source,
                Optional.empty(),
                kind,
                PlanOperationStatus.DELETE,
                PlanExclusionReason.NONE,
                Optional.empty(),
                false,
                Optional.empty());
    }

    static PlannedOperation alreadyInPlace(Path source, Path destination, PlanMediaKind kind) {
        return new PlannedOperation(
                source,
                Optional.of(destination),
                kind,
                PlanOperationStatus.ALREADY_IN_PLACE,
                PlanExclusionReason.NONE,
                Optional.empty(),
                false,
                Optional.empty());
    }

    static PlannedOperation excluded(Path source, PlanMediaKind kind, PlanExclusionReason reason) {
        return new PlannedOperation(
                source,
                Optional.empty(),
                kind,
                PlanOperationStatus.EXCLUDED,
                reason,
                Optional.empty(),
                false,
                Optional.empty());
    }

    static PlannedOperation conflicting(
            Path source, Optional<Path> destination, PlanMediaKind kind, PlanConflict conflict) {
        return new PlannedOperation(
                source,
                destination,
                kind,
                PlanOperationStatus.CONFLICT,
                PlanExclusionReason.NONE,
                Optional.of(conflict),
                false,
                Optional.empty());
    }

    PlannedOperation withConflict(PlanConflict newConflict) {
        return new PlannedOperation(
                sourcePath,
                destinationPath,
                kind,
                PlanOperationStatus.CONFLICT,
                exclusionReason,
                Optional.of(newConflict),
                false,
                Optional.empty());
    }

    /** True when execution will actually move or rename this file. */
    public boolean movesFile() {
        return status == PlanOperationStatus.EXECUTABLE;
    }

    /** True when execution will remove this file from disk. */
    public boolean deletesFile() {
        return status == PlanOperationStatus.DELETE;
    }

    /** True when execution touches this file at all, whichever way. */
    public boolean mutatesFile() {
        return movesFile() || deletesFile();
    }

    public boolean blocking() {
        return status == PlanOperationStatus.CONFLICT;
    }
}
