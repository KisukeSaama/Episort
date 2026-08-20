package com.episort.ui.platform;

import java.util.OptionalInt;
import java.util.Properties;

/**
 * Matches the JavaFX pulse to the display instead of leaving it at 60 Hz.
 *
 * <p>JavaFX drives every layout, animation and scroll update from a fixed-rate
 * pulse whose default is 60 Hz regardless of the screen. On a 120, 240 or 480 Hz
 * display that ceiling is visible twice over: motion advances in 16 ms steps
 * while everything else on the desktop moves in 2 ms steps, and the pulse beats
 * against the display's own refresh, dropping a frame at regular intervals.
 * Both read as sluggishness, not as a frame-rate number.
 *
 * <p>Raising the pulse costs nothing at rest. JavaFX only pulses when something
 * in the scene is dirty, so an idle window still does no work per second — the
 * rate is a ceiling, not a treadmill.
 *
 * <p>Applied from {@code Launcher} before the toolkit starts, since the pulse
 * property is read once at toolkit initialisation. An explicit
 * {@code -Djavafx.animation.pulse} on the command line always wins: this is a
 * default, not a policy.
 */
public final class RenderTuning {

    public static final String PULSE_PROPERTY = "javafx.animation.pulse";

    /**
     * Below this, matching the display would mean slowing JavaFX down — a 50 Hz
     * screen still deserves the toolkit's own default.
     */
    static final int MINIMUM_PULSE_HZ = 60;

    /**
     * Ceiling on what we ask for. JavaFX turns the requested rate into a whole
     * number of milliseconds per pulse, so the effective rate is a ladder rather
     * than the number asked for — measured on a 480 Hz display: 120 gives 125 Hz
     * (8 ms), 240 gives 250 Hz (4 ms), and anything from 334 up gives 500 Hz
     * (2 ms). The cap therefore bounds the request, not the result; it exists so
     * an implausible display mode cannot ask for a 1 ms pulse.
     */
    static final int MAXIMUM_PULSE_HZ = 480;

    private RenderTuning() {
    }

    /** Detects the display rate and applies the matching pulse to system properties. */
    public static void applyDisplayPulse() {
        apply(System.getProperties(), DisplayRefreshRate.detect());
    }

    static void apply(Properties properties, OptionalInt refreshHz) {
        if (properties.getProperty(PULSE_PROPERTY) != null) {
            return;
        }
        if (refreshHz.isEmpty()) {
            return;
        }
        properties.setProperty(PULSE_PROPERTY, Integer.toString(pulseHz(refreshHz.getAsInt())));
    }

    static int pulseHz(int refreshHz) {
        return Math.clamp(refreshHz, MINIMUM_PULSE_HZ, MAXIMUM_PULSE_HZ);
    }
}
