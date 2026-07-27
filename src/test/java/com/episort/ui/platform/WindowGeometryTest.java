package com.episort.ui.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WindowGeometryTest {
    private static final WindowWorkArea PRIMARY = new WindowWorkArea(0, 0, 1920, 1040);

    @Test
    void snapBoundsUseTheVisualWorkAreaInsteadOfThePhysicalDisplay() {
        assertEquals(new WindowBounds(0, 0, 960, 1040), WindowGeometry.snappedLeft(PRIMARY));
        assertEquals(new WindowBounds(960, 0, 960, 1040), WindowGeometry.snappedRight(PRIMARY));
        assertEquals(new WindowBounds(0, 0, 1920, 1040), WindowGeometry.maximized(PRIMARY));
    }

    @Test
    void negativeMonitorCoordinatesAreKeptForEdgeDetection() {
        WindowWorkArea leftMonitor = new WindowWorkArea(-1600, 40, 1600, 860);

        assertEquals(WindowSnapTarget.LEFT, WindowGeometry.snapTargetAt(-1590, 400, leftMonitor));
        assertEquals(WindowSnapTarget.RIGHT, WindowGeometry.snapTargetAt(-8, 400, leftMonitor));
        assertEquals(WindowSnapTarget.MAXIMIZE, WindowGeometry.snapTargetAt(-800, 42, leftMonitor));
    }

    @Test
    void keepingTheTitleBarVisibleDoesNotForceTheWholeWindowIntoTheDisplay() {
        WindowBounds offScreen = new WindowBounds(-1700, -400, 1180, 760);

        assertEquals(new WindowBounds(-1084, 0, 1180, 760),
                WindowGeometry.keepTitleBarVisible(offScreen, PRIMARY));
    }
}
