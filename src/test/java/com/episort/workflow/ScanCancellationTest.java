package com.episort.workflow;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class ScanCancellationTest {
    @Test
    void aFreshTokenLetsTheAnalysisRun() {
        ScanCancellation cancellation = ScanCancellation.none();

        assertFalse(cancellation.isCancelled());
        assertDoesNotThrow(cancellation::throwIfCancelled);
    }

    @Test
    void aCancelledTokenAbortsAtTheNextCheckpoint() {
        ScanCancellation cancellation = ScanCancellation.none();
        cancellation.cancel();

        assertTrue(cancellation.isCancelled());
        assertThrows(ScanCancelledException.class, cancellation::throwIfCancelled);
    }

    @Test
    void cancellingTwiceIsHarmless() {
        ScanCancellation cancellation = ScanCancellation.none();
        cancellation.cancel();
        cancellation.cancel();

        assertTrue(cancellation.isCancelled());
    }

    /** The UI only ever sees the wrapped form, never the raw exception. */
    @Test
    void aWrappedCancellationIsStillRecognized() {
        Throwable wrapped = new CompletionException(
                new InventoryWorkflowException("Inventory scan failed", new ScanCancelledException()));

        assertTrue(ScanCancellation.isCancellation(wrapped));
    }

    @Test
    void anOrdinaryFailureIsNotMistakenForACancellation() {
        Throwable failure = new CompletionException(new IllegalStateException("disk unplugged"));

        assertFalse(ScanCancellation.isCancellation(failure));
        assertFalse(ScanCancellation.isCancellation(null));
    }
}
