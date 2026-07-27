package com.episort.planning;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The immutable, approved snapshot of an operation plan (Story 7.1).
 *
 * <p>Locking happens once, before the first mutation. Nothing observed later —
 * a new scan, a corrected row, a regenerated plan — can change what this object
 * says will happen, so execution cannot drift from what the user approved.
 */
public record ApprovedPlan(
        UUID id,
        Path workspaceRoot,
        List<PlannedOperation> operations,
        List<Path> foldersToCreate) {

    public ApprovedPlan {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        operations = List.copyOf(operations);
        foldersToCreate = List.copyOf(foldersToCreate);
    }

    /**
     * Locks the executable part of a plan.
     *
     * <p>Excluded and already-in-place items are dropped here: they carry no
     * mutation, and keeping them out guarantees execution can never touch them.
     * What is kept is everything that mutates — the approved moves and the
     * deletions the user asked for while resolving conflicts.
     *
     * @throws IllegalStateException when blocking conflicts remain — the exact
     *         plan gate must refuse validation before this is ever reached
     */
    public static ApprovedPlan lock(OperationPlan plan) {
        Objects.requireNonNull(plan, "plan");
        if (plan.hasBlockingConflicts()) {
            throw new IllegalStateException("Cannot approve a plan with unresolved blocking conflicts");
        }
        return new ApprovedPlan(
                UUID.randomUUID(),
                plan.workspaceRoot(),
                plan.mutatingOperations(),
                plan.foldersToCreate());
    }

    public int size() {
        return operations.size();
    }

    public boolean isEmpty() {
        return operations.isEmpty();
    }
}
