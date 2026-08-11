package com.episort.ui.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WindowStateModelTest {
    @Test
    void normalBoundsSurviveMultipleNonNormalTransitions() {
        WindowBounds normal = new WindowBounds(120, 80, 1180, 760);
        WindowStateModel model = new WindowStateModel();

        model.enter(WindowState.MAXIMIZED, normal);
        model.enter(WindowState.SNAPPED_LEFT, new WindowBounds(0, 0, 960, 1040));

        assertEquals(WindowState.SNAPPED_LEFT, model.state());
        assertEquals(normal, model.normalBounds().orElseThrow());
    }

    @Test
    void normalPlacementUpdatesOnlyWhileTheWindowIsNormal() {
        WindowStateModel model = new WindowStateModel();
        WindowBounds first = new WindowBounds(120, 80, 1180, 760);
        WindowBounds second = new WindowBounds(200, 100, 1180, 760);

        model.restore(first);
        model.restore(second);
        model.enter(WindowState.MAXIMIZED, second);

        assertEquals(second, model.normalBounds().orElseThrow());
    }
}
