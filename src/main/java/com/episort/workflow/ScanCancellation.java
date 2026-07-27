package com.episort.workflow;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cooperative cancellation token covering one analysis run (inventory scan →
 * AI refinement → TVDB batch matching).
 *
 * <p>The UI thread flips the token via {@link #cancel()}; the background
 * pipeline observes it at its progress checkpoints and unwinds by throwing
 * {@link ScanCancelledException}. Nothing here interrupts threads: the long
 * running steps (llama-server prompts, TVDB requests) run to the end of the
 * current unit of work, then the run is abandoned and its results discarded.
 */
public final class ScanCancellation {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /** A token that can never be cancelled — for callers with no UI to cancel from. */
    public static ScanCancellation none() {
        return new ScanCancellation();
    }

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    /** Aborts the current run when the user asked for it; no-op otherwise. */
    public void throwIfCancelled() {
        if (cancelled.get()) {
            throw new ScanCancelledException();
        }
    }

    /**
     * Unwraps the causal chain of {@code throwable} looking for a cancellation.
     * Needed because the workflow layers wrap failures ({@code CompletionException},
     * {@link InventoryWorkflowException}) before they reach the UI.
     */
    public static boolean isCancellation(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof ScanCancelledException) {
                return true;
            }
            if (current.getCause() == current) {
                return false;
            }
        }
        return false;
    }
}
