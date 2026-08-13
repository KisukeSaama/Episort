package com.episort.ui.platform;

import java.awt.DisplayMode;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.util.OptionalInt;

/**
 * Reads the refresh rate of the attached displays.
 *
 * <p>JavaFX exposes no such query, so this goes through AWT, which does. It is
 * read once during bootstrap, before the JavaFX toolkit starts, because the
 * pulse rate it feeds is only read when the toolkit initialises.
 *
 * <p>The highest rate across screens wins: the window can be dragged to any of
 * them, and pulsing faster than a screen refreshes costs nothing visible while
 * pulsing slower is exactly the stutter this avoids. An unknown rate returns
 * empty rather than a guess — the caller then leaves JavaFX on its own default.
 */
public final class DisplayRefreshRate {

    private DisplayRefreshRate() {
    }

    /** Highest refresh rate in hertz across all screens, empty when unknown. */
    public static OptionalInt detect() {
        if (GraphicsEnvironment.isHeadless()) {
            return OptionalInt.empty();
        }
        try {
            int highest = 0;
            for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
                DisplayMode mode = device.getDisplayMode();
                if (mode == null || mode.getRefreshRate() == DisplayMode.REFRESH_RATE_UNKNOWN) {
                    continue;
                }
                highest = Math.max(highest, mode.getRefreshRate());
            }
            return highest > 0 ? OptionalInt.of(highest) : OptionalInt.empty();
        } catch (RuntimeException | LinkageError ignored) {
            // A display query must never be the reason the application fails to
            // start; the default pulse is a working fallback.
            return OptionalInt.empty();
        }
    }
}
