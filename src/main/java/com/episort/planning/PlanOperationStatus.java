package com.episort.planning;


/** Execution outlook of a planned item, decided before anything touches disk. */
public enum PlanOperationStatus {
    /** Will be moved or renamed when the plan is executed. */
    EXECUTABLE,
    /** Already sits at its destination: nothing to do, nothing to touch. */
    ALREADY_IN_PLACE,
    /** Deliberately left out — ignored, unsupported, duplicate, or unassigned. */
    EXCLUDED,
    /** Blocked by a conflict that must be resolved or excluded first. */
    CONFLICT,
    /**
     * Will be removed from disk when the plan is executed, because the user
     * explicitly asked for it in front of the exact plan. Never produced by the
     * planner on its own.
     */
    DELETE
}
