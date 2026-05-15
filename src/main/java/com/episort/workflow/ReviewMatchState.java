package com.episort.workflow;

public enum ReviewMatchState {
    READY,
    NEEDS_REVIEW,
    AMBIGUOUS,
    CONFLICT,
    DUPLICATE,
    IGNORED,
    UNSUPPORTED,
    UNKNOWN
}
