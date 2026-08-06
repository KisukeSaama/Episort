package com.episort.persistence;


public enum RunEventType {
    SCAN_COMPLETED,
    SCAN_FAILED,
    /** An approved plan finished running, with or without per-file failures. */
    EXECUTION_COMPLETED,
    /** An execution run could not be started or ended without processing anything. */
    EXECUTION_FAILED,
    /** The latest reversible execution was restored to its previous paths. */
    ROLLBACK_COMPLETED,
    /** A requested rollback was refused or failed validation. */
    ROLLBACK_FAILED
}
