package com.episort.ui.platform;

/** Screen-space bounds in JavaFX logical pixels. */
public record WindowBounds(double x, double y, double width, double height) {
    public WindowBounds {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Window bounds must be positive");
        }
    }

    public double right() {
        return x + width;
    }

    public double bottom() {
        return y + height;
    }
}
