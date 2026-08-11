package com.episort.ui;


/**
 * The step the user stands on, from loading a folder to applying the plan.
 *
 * <p>Shared by every screen that shows the workflow strip, so the scan review and
 * the exact-plan review advance the same trail instead of each telling its own
 * story about where the user is.
 */
public enum WorkflowPhase {
    CHOOSE_FOLDER,
    ANALYSIS,
    TMDB_MATCHES,
    PLAN_REVIEW,
    APPLY;

    /** Position on the strip, matching the order of {@code scan.workflow.steps}. */
    public int index() {
        return ordinal();
    }
}
