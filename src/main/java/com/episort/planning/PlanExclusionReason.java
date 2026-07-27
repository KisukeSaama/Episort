package com.episort.planning;


/**
 * Why an inventoried file produces no executable operation. Excluded files stay
 * visible in the plan so the user can see they will remain untouched.
 */
public enum PlanExclusionReason {
    /** The item is executable. */
    NONE,
    /** The user marked the file as ignored. */
    IGNORED,
    /** The extension is not a supported media container. */
    UNSUPPORTED,
    /** Another file already claims the same target episode or movie. */
    DUPLICATE,
    /** No series, movie, season, or episode identity was assigned. */
    UNASSIGNED,
    /** The match is still ambiguous or in error and was never resolved. */
    AMBIGUOUS,
    /**
     * The file was in conflict and the user resolved that conflict by leaving it
     * where it is (Story 6.4 resolution). The file is untouched, not lost.
     */
    CONFLICT_SKIPPED
}
