package com.episort.workflow;


/** What to do after one file failed (Story 7.3). */
public enum ExecutionFailureDecision {
    /** Try the same operation again — for locked or temporarily unavailable files. */
    RETRY,
    /** Leave this file alone and carry on with the rest of the plan. */
    CONTINUE,
    /** Stop the run; every remaining approved file is reported as skipped. */
    ABORT
}
