package com.episort.workflow;

import com.episort.planning.PlannedOperation;

/**
 * Decides what to do when one file fails. The UI implements this to offer retry
 * or continue on locked files; headless callers can use {@link #alwaysContinue()}.
 */
@FunctionalInterface
public interface ExecutionFailureHandler {
    /**
     * @param attempt 1 for the first failure of this operation, incremented on
     *                every retry so a handler can stop looping
     */
    ExecutionFailureDecision onFailure(PlannedOperation operation, ApplicationError error, int attempt);

    /** Never retries, never aborts: one bad file does not stop the run. */
    static ExecutionFailureHandler alwaysContinue() {
        return (operation, error, attempt) -> ExecutionFailureDecision.CONTINUE;
    }

    /** Stops the run at the first failure, preserving completed operations. */
    static ExecutionFailureHandler alwaysAbort() {
        return (operation, error, attempt) -> ExecutionFailureDecision.ABORT;
    }
}
