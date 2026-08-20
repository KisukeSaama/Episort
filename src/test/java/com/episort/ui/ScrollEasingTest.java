package com.episort.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScrollEasingTest {

    @Test
    void movesTowardsTheTargetWithoutOvershooting() {
        double value = ScrollEasing.advance(0.0, 1.0, 0.016);

        assertTrue(value > 0.0, "did not move");
        assertTrue(value < 1.0, "overshot the target");
    }

    @Test
    void coversTheSameDistanceWhateverTheFrameRate() {
        // The same 20 ms of wall time, drawn once at 50 Hz and twice at 100 Hz.
        double slowFrame = ScrollEasing.advance(0.0, 1.0, 0.020);
        double fastFrames = ScrollEasing.advance(ScrollEasing.advance(0.0, 1.0, 0.010), 1.0, 0.010);

        assertEquals(slowFrame, fastFrames, 1e-12);
    }

    @Test
    void coversMostOfTheDistanceWithinOneTimeConstant() {
        double value = ScrollEasing.advance(0.0, 1.0, ScrollEasing.TIME_CONSTANT_SECONDS);

        assertEquals(0.632, value, 0.001);
    }

    @Test
    void convergesRatherThanCreeping() {
        double value = 0.0;
        for (int frame = 0; frame < 60; frame++) {
            value = ScrollEasing.advance(value, 1.0, 0.016);
        }

        assertTrue(ScrollEasing.settled(value, 1.0, 1.0), "still not settled after a second: " + value);
    }

    @Test
    void doesNotMoveWithoutElapsedTime() {
        assertEquals(0.4, ScrollEasing.advance(0.4, 1.0, 0.0));
        assertEquals(0.4, ScrollEasing.advance(0.4, 1.0, -0.016));
    }

    @Test
    void scalesTheSettledThresholdToTheRange() {
        assertTrue(ScrollEasing.settled(0.9999, 1.0, 1.0));
        assertFalse(ScrollEasing.settled(0.98, 1.0, 1.0));
        // A bar counted in pixels tolerates proportionally more.
        assertTrue(ScrollEasing.settled(6800.0, 6801.0, 6800.0));
    }
}
