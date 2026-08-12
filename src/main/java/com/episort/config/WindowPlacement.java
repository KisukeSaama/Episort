package com.episort.config;

import com.episort.ui.platform.WindowBounds;
import com.episort.ui.platform.WindowState;
import java.util.Objects;

/** Last user-selected placement of the main window. */
public record WindowPlacement(WindowBounds normalBounds, WindowState state) {
    public WindowPlacement {
        Objects.requireNonNull(normalBounds, "normalBounds");
        Objects.requireNonNull(state, "state");
    }
}
