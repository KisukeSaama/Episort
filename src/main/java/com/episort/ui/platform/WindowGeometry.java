package com.episort.ui.platform;

/** Pure placement calculations used by the JavaFX window controller. */
final class WindowGeometry {
    static final double SNAP_DISTANCE = 18;
    static final double MINIMUM_VISIBLE_TITLE_BAR = 96;

    private WindowGeometry() {
    }

    static WindowBounds maximized(WindowWorkArea area) {
        return new WindowBounds(area.x(), area.y(), area.width(), area.height());
    }

    static WindowBounds snappedLeft(WindowWorkArea area) {
        double leftWidth = Math.floor(area.width() / 2);
        return new WindowBounds(area.x(), area.y(), leftWidth, area.height());
    }

    static WindowBounds snappedRight(WindowWorkArea area) {
        double leftWidth = Math.floor(area.width() / 2);
        return new WindowBounds(area.x() + leftWidth, area.y(), area.width() - leftWidth, area.height());
    }

    static WindowSnapTarget snapTargetAt(double screenX, double screenY, WindowWorkArea area) {
        if (screenY <= area.y() + SNAP_DISTANCE) {
            return WindowSnapTarget.MAXIMIZE;
        }
        if (screenX <= area.x() + SNAP_DISTANCE) {
            return WindowSnapTarget.LEFT;
        }
        if (screenX >= area.right() - SNAP_DISTANCE) {
            return WindowSnapTarget.RIGHT;
        }
        return WindowSnapTarget.NONE;
    }

    static WindowBounds boundsFor(WindowSnapTarget target, WindowWorkArea area) {
        return switch (target) {
            case LEFT -> snappedLeft(area);
            case RIGHT -> snappedRight(area);
            case MAXIMIZE -> maximized(area);
            case NONE -> throw new IllegalArgumentException("No bounds for a non-snap target");
        };
    }

    static WindowBounds keepTitleBarVisible(WindowBounds bounds, WindowWorkArea area) {
        double x = Math.max(area.x() - bounds.width() + MINIMUM_VISIBLE_TITLE_BAR,
                Math.min(bounds.x(), area.right() - MINIMUM_VISIBLE_TITLE_BAR));
        double y = Math.max(area.y(), Math.min(bounds.y(), area.bottom() - MINIMUM_VISIBLE_TITLE_BAR));
        return new WindowBounds(x, y, bounds.width(), bounds.height());
    }
}
