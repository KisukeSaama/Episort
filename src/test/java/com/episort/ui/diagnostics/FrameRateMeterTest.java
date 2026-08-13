package com.episort.ui.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FrameRateMeterTest {

    private static final long MILLISECOND = 1_000_000L;

    @Test
    void reportsNothingBeforeTheWindowElapses() {
        FrameRateMeter meter = new FrameRateMeter(Duration.ofSeconds(1));

        assertTrue(meter.record(0).isEmpty());
        assertTrue(meter.record(500 * MILLISECOND).isEmpty());
        assertTrue(meter.record(999 * MILLISECOND).isEmpty());
    }

    @Test
    void measuresSixtyHertzAsSixtyFramesPerSecond() {
        List<FrameRateSample> samples = feed(1_000_000_000L / 60, Duration.ofSeconds(1), 90);

        assertEquals(1, samples.size());
        assertEquals(60.0, samples.getFirst().framesPerSecond(), 0.5);
    }

    @Test
    void measuresFourHundredEightyHertzAsFourHundredEightyFramesPerSecond() {
        List<FrameRateSample> samples = feed(1_000_000_000L / 480, Duration.ofSeconds(1), 600);

        assertEquals(1, samples.size());
        assertEquals(480.0, samples.getFirst().framesPerSecond(), 2.0);
    }

    @Test
    void reportsTheWorstFrameRatherThanHidingItInTheAverage() {
        FrameRateMeter meter = new FrameRateMeter(Duration.ofSeconds(1));
        long frameNanos = 1_000_000_000L / 240;
        long now = 0;
        meter.record(now);
        // 239 healthy frames plus one that took 40 ms: the average still looks
        // fine, and the stutter is the only thing the user would have seen.
        for (int frame = 0; frame < 239; frame++) {
            now += frameNanos;
            meter.record(now);
        }
        now += 40 * MILLISECOND;
        Optional<FrameRateSample> sample = meter.record(now);

        assertTrue(sample.isPresent());
        assertEquals(40.0, sample.orElseThrow().worstFrameMillis(), 0.01);
    }

    @Test
    void startsAFreshWindowAfterEachSample() {
        List<FrameRateSample> samples = feed(1_000_000_000L / 120, Duration.ofSeconds(1), 400);

        assertEquals(3, samples.size());
        for (FrameRateSample sample : samples) {
            assertEquals(120.0, sample.framesPerSecond(), 1.0);
            assertEquals(121, sample.frames(), 1);
        }
    }

    @Test
    void rejectsANonPositiveWindow() {
        assertThrows(IllegalArgumentException.class, () -> new FrameRateMeter(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new FrameRateMeter(Duration.ofSeconds(-1)));
    }

    private static List<FrameRateSample> feed(long frameNanos, Duration window, int frames) {
        FrameRateMeter meter = new FrameRateMeter(window);
        List<FrameRateSample> samples = new ArrayList<>();
        long now = 0;
        for (int frame = 0; frame <= frames; frame++) {
            meter.record(now).ifPresent(samples::add);
            now += frameNanos;
        }
        return samples;
    }
}
