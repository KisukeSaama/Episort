package com.episort.ui.platform;

import java.util.Optional;

/** Keeps the last normal placement intact while the window is maximized or snapped. */
final class WindowStateModel {
    private WindowState state = WindowState.NORMAL;
    private WindowBounds normalBounds;

    WindowState state() {
        return state;
    }

    Optional<WindowBounds> normalBounds() {
        return Optional.ofNullable(normalBounds);
    }

    void enter(WindowState nextState, WindowBounds currentBounds) {
        if (state == WindowState.NORMAL && nextState != WindowState.NORMAL) {
            normalBounds = currentBounds;
        }
        state = nextState;
    }

    void restore(WindowBounds currentBounds) {
        if (state == WindowState.NORMAL) {
            normalBounds = currentBounds;
        }
        state = WindowState.NORMAL;
    }
}
