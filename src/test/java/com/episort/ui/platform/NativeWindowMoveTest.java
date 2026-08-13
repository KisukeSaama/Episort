package com.episort.ui.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.geometry.Point2D;
import org.junit.jupiter.api.Test;

class NativeWindowMoveTest {

    @Test
    void recognisesWindowsOnly() {
        assertTrue(NativeWindowMove.isWindows("Windows 11"));
        assertTrue(NativeWindowMove.isWindows("windows 10"));
        assertFalse(NativeWindowMove.isWindows("Linux"));
        assertFalse(NativeWindowMove.isWindows("Mac OS X"));
        assertFalse(NativeWindowMove.isWindows(null));
    }

    @Test
    void staysOnUnlessExplicitlyTurnedOff() {
        assertTrue(NativeWindowMove.isEnabled(null));
        assertTrue(NativeWindowMove.isEnabled("true"));
        assertFalse(NativeWindowMove.isEnabled("false"));
        assertFalse(NativeWindowMove.isEnabled("FALSE"));
    }

    @Test
    void readsThePointerInStageUnitsWhateverTheDisplayScale() {
        assertEquals(
                new Point2D(40, 16),
                NativeWindowMove.offsetInStage(40, 16, 1180, 760, 1180, 760).orElseThrow());
        // 150%: the window measures half again as many device pixels, and the
        // same grab lands on the same point of the title bar.
        assertEquals(
                new Point2D(40, 16),
                NativeWindowMove.offsetInStage(60, 24, 1770, 1140, 1180, 760).orElseThrow());
    }

    @Test
    void reportsNothingRatherThanDivideByAnEmptyWindow() {
        assertTrue(NativeWindowMove.offsetInStage(0, 0, 0, 760, 1180, 760).isEmpty());
        assertTrue(NativeWindowMove.offsetInStage(0, 0, 1180, 0, 1180, 760).isEmpty());
    }
}
