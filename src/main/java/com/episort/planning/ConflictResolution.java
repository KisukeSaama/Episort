package com.episort.planning;


/** What the user decided to do with one conflicting operation. */
public enum ConflictResolution {
    /**
     * This file wins the destination: execution overwrites whatever occupies it.
     * Only offered when {@link PlanConflictType#resolvableByReplacement()}.
     */
    REPLACE,
    /**
     * This file loses: it stays exactly where it is and drops out of the plan.
     * Nothing is deleted, nothing is overwritten.
     */
    SKIP,
    /**
     * This file is the copy too many: execution removes it from disk, to the
     * recycle bin when the system offers one. The only decision that destroys
     * something, so it is offered exactly where a real duplicate is the problem
     * ({@link PlanConflictType#resolvableByReplacement()}) and never as a default.
     */
    DELETE_SOURCE
}
