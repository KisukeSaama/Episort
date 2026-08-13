package com.episort.ui.diagnostics;

import java.time.Duration;
import java.util.Optional;

/**
 * Turns a stream of frame timestamps into periodic frame-rate samples.
 *
 * <p>Split from the JavaFX timer that feeds it so the arithmetic — which is the
 * part that can be wrong — is exercised by unit tests instead of by watching a
 * window. The meter reports the average rate over the window and the single
 * worst frame gap in it: an average alone hides the stutter that a user
 * actually notices.
 */
public final class FrameRateMeter {

    private final long windowNanos;

    private long windowStartNanos = Long.MIN_VALUE;
    private long previousFrameNanos = Long.MIN_VALUE;
    private int frames;
    private long worstIntervalNanos;

    public FrameRateMeter(Duration window) {
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
        this.windowNanos = window.toNanos();
    }

    /**
     * Records one frame, returning a sample when the window has elapsed.
     *
     * <p>The first frame only opens the window: no interval can be measured
     * from a single timestamp.
     */
    public Optional<FrameRateSample> record(long frameNanos) {
        if (windowStartNanos == Long.MIN_VALUE) {
            windowStartNanos = frameNanos;
            previousFrameNanos = frameNanos;
            return Optional.empty();
        }
        long interval = frameNanos - previousFrameNanos;
        previousFrameNanos = frameNanos;
        frames++;
        worstIntervalNanos = Math.max(worstIntervalNanos, interval);

        long elapsedNanos = frameNanos - windowStartNanos;
        if (elapsedNanos < windowNanos) {
            return Optional.empty();
        }
        FrameRateSample sample = new FrameRateSample(
                frames * 1_000_000_000.0 / elapsedNanos,
                worstIntervalNanos / 1_000_000.0,
                frames);
        windowStartNanos = frameNanos;
        frames = 0;
        worstIntervalNanos = 0;
        return Optional.of(sample);
    }
}
