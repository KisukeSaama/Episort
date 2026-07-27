package com.episort.ui.platform;

/** The usable portion of one display, excluding its taskbar or dock. */
public record WindowWorkArea(double x, double y, double width, double height) {
    public WindowWorkArea {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Work area bounds must be positive");
        }
    }

    public double right() {
        return x + width;
    }

    public double bottom() {
        return y + height;
    }

    public boolean contains(double screenX, double screenY) {
        return screenX >= x && screenX <= right() && screenY >= y && screenY <= bottom();
    }
}
