package com.episort.ui;

/**
 * The curve a smoothed scroll follows between where it is and where the wheel
 * asked it to go.
 *
 * <p>Exponential approach rather than a fixed-length animation: a new wheel
 * notch only moves the target, and the motion continues from wherever it
 * currently is instead of restarting. That is what makes repeated notches feel
 * like one continuous movement.
 *
 * <p>Kept apart from the JavaFX plumbing because it is the part that can be
 * wrong in a way no one notices until the scroll feels different on another
 * display: the step must depend on elapsed time, not on how many frames the
 * machine happened to draw.
 */
final class ScrollEasing {

    /**
     * Time to cover roughly 63% of the remaining distance, which puts the
     * content essentially in place inside 100 ms.
     *
     * <p>This is the whole feel of scrolling in one number, and it is the one to
     * turn. Larger reads as floating — the content keeps travelling after the
     * wheel has stopped, which is a phone gesture, not a work tool. Smaller
     * tends back towards the frame-by-frame jump this replaced.
     */
    static final double TIME_CONSTANT_SECONDS = 0.035;

    /** Below this fraction of the scrollable range, the glide has arrived. */
    private static final double SETTLED_FRACTION = 0.0005;

    private ScrollEasing() {
    }

    /**
     * Advances {@code current} towards {@code target} for one frame.
     *
     * <p>Frame-rate independent: advancing once over 20 ms and twice over 10 ms
     * land on the same value, so the same gesture travels the same distance at
     * 60 Hz and at 500 Hz.
     */
    static double advance(double current, double target, double elapsedSeconds) {
        if (elapsedSeconds <= 0) {
            return current;
        }
        double progress = 1 - Math.exp(-elapsedSeconds / TIME_CONSTANT_SECONDS);
        return current + (target - current) * progress;
    }

    /** Whether the remaining distance is too small to be worth another frame. */
    static boolean settled(double current, double target, double range) {
        double tolerance = Math.max(range, 1) * SETTLED_FRACTION;
        return Math.abs(target - current) <= tolerance;
    }
}
