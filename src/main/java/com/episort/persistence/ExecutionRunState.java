package com.episort.persistence;


/** Lifecycle of a recorded execution run (Story 7.4). */
public enum ExecutionRunState {
    /** Started and not yet finished. Seeing this at launch means the previous run was interrupted. */
    RUNNING,
    /** Every approved file was moved or renamed. */
    COMPLETED,
    /** The run finished, but at least one file failed or was skipped. */
    COMPLETED_WITH_FAILURES,
    /** The user stopped the run before the end. */
    ABORTED
}
