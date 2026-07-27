package com.episort.workflow;


/**
 * Thrown by {@link ScanCancellation#throwIfCancelled()} to unwind an analysis
 * run the user cancelled. It is a control-flow signal, not a failure: callers
 * must not report it as an error to the user.
 */
public final class ScanCancelledException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ScanCancelledException() {
        super("Analysis cancelled by the user.");
    }
}
