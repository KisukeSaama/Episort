package com.episort.ui.diagnostics;

import com.episort.ui.platform.DisplayRefreshRate;
import com.episort.ui.platform.RenderTuning;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Consumer;
import javafx.animation.AnimationTimer;
import javafx.stage.Window;

/**
 * Opt-in frame-rate readout, enabled with {@code -Depisort.debug.fps=true}.
 *
 * <p>Smoothness is the one quality claim that cannot be verified by reading the
 * code, and JavaFX caps its pulse at 60 Hz unless told otherwise. The probe
 * exists so the rate the application actually reaches on a given display is a
 * measurement rather than an assumption — before and after a change to
 * {@link com.episort.ui.platform.RenderTuning}.
 *
 * <p>It is off by default and costs nothing when off: the timer is never
 * started. When on, a running {@link AnimationTimer} also keeps the pulse alive
 * on an idle window, which is exactly what makes the ceiling measurable.
 */
public final class FrameRateProbe {

    public static final String ENABLED_PROPERTY = "episort.debug.fps";

    private FrameRateProbe() {
    }

    /** Starts the readout when the debug property is set, otherwise does nothing. */
    public static void startIfEnabled() {
        if (!Boolean.getBoolean(ENABLED_PROPERTY)) {
            return;
        }
        Consumer<String> sink = line -> System.out.println(line);
        sink.accept(String.format(
                Locale.ROOT,
                "[episort.fps] display %s Hz, %s=%s",
                DisplayRefreshRate.detect().stream().mapToObj(Integer::toString).findFirst().orElse("unknown"),
                RenderTuning.PULSE_PROPERTY,
                System.getProperty(RenderTuning.PULSE_PROPERTY, "default")));
        start(sink);
    }

    static void start(Consumer<String> sink) {
        FrameRateMeter meter = new FrameRateMeter(Duration.ofSeconds(1));
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                meter.record(now).ifPresent(sample -> sink.accept(format(sample)));
            }
        }.start();
    }

    static String format(FrameRateSample sample) {
        return String.format(
                Locale.ROOT,
                "[episort.fps] %.1f fps over %d frames, worst frame %.2f ms, focused=%s",
                sample.framesPerSecond(),
                sample.frames(),
                sample.worstFrameMillis(),
                anyWindowFocused());
    }

    /**
     * Reported alongside the rate because Windows presents a window that is not
     * in the foreground at the desktop's base rate, whatever the pulse asks for.
     * Without this, such a stretch reads as a regression in the application.
     */
    private static boolean anyWindowFocused() {
        return Window.getWindows().stream().anyMatch(Window::isFocused);
    }
}
