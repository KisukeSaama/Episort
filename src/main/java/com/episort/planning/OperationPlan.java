package com.episort.planning;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The complete, immutable source-to-destination plan (Story 6.3).
 *
 * <p>Generating this object creates no folder and moves no file: it is proof of
 * what execution would do, produced before the user approves anything.
 */
public record OperationPlan(Path workspaceRoot, List<PlannedOperation> operations, List<Path> reusedFolders) {

    public OperationPlan {
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        operations = List.copyOf(operations);
        reusedFolders = List.copyOf(reusedFolders);
    }

    public static OperationPlan empty(Path workspaceRoot) {
        return new OperationPlan(workspaceRoot, List.of(), List.of());
    }

    public boolean isEmpty() {
        return operations.isEmpty();
    }

    /** Operations that will actually move or rename a file. */
    public List<PlannedOperation> executableOperations() {
        return operations.stream().filter(PlannedOperation::movesFile).toList();
    }

    /** Operations that will remove a file, on the user's explicit decision. */
    public List<PlannedOperation> deletions() {
        return operations.stream().filter(PlannedOperation::deletesFile).toList();
    }

    /**
     * Everything execution will touch — moves and deletions alike — in plan
     * order. This is what gets locked into an {@link ApprovedPlan}.
     */
    public List<PlannedOperation> mutatingOperations() {
        return operations.stream().filter(PlannedOperation::mutatesFile).toList();
    }

    public List<PlannedOperation> conflicts() {
        return operations.stream().filter(PlannedOperation::blocking).toList();
    }

    public List<PlannedOperation> excludedOperations() {
        return operations.stream()
                .filter(operation -> operation.status() == PlanOperationStatus.EXCLUDED)
                .toList();
    }

    public List<PlannedOperation> alreadyInPlaceOperations() {
        return operations.stream()
                .filter(operation -> operation.status() == PlanOperationStatus.ALREADY_IN_PLACE)
                .toList();
    }

    /** Exact-plan validation stays refused while this is true (Story 6.4). */
    public boolean hasBlockingConflicts() {
        return operations.stream().anyMatch(PlannedOperation::blocking);
    }

    /**
     * Destination folders that do not exist yet, ordered parents-first so
     * execution can create them in sequence.
     */
    public List<Path> foldersToCreate() {
        Set<Path> folders = new LinkedHashSet<>();
        for (PlannedOperation operation : executableOperations()) {
            Path parent = operation.destinationPath().orElseThrow().getParent();
            while (parent != null && parent.startsWith(workspaceRoot) && !parent.equals(workspaceRoot)) {
                folders.add(parent);
                parent = parent.getParent();
            }
        }
        List<Path> ordered = new ArrayList<>(folders);
        ordered.removeAll(reusedFolders);
        ordered.sort(Comparator.comparingInt(Path::getNameCount).thenComparing(Path::toString));
        return List.copyOf(ordered);
    }
}
