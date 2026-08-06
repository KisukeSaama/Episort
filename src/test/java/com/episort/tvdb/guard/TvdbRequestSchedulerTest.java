package com.episort.tvdb.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TvdbRequestSchedulerTest {
    @Test
    void spacesPhysicalRequestsAndHonoursGlobalDeferral() throws Exception {
        AdjustableClock clock = new AdjustableClock(Instant.parse("2026-01-01T00:00:00Z"));
        List<Long> sleeps = new ArrayList<>();
        TvdbRequestScheduler scheduler = new TvdbRequestScheduler(
                Duration.ofMillis(500), 30, Duration.ofMinutes(1), clock,
                millis -> {
                    sleeps.add(millis);
                    clock.advance(Duration.ofMillis(millis));
                });

        scheduler.execute(() -> "first");
        scheduler.execute(() -> "second");
        scheduler.deferFor(Duration.ofSeconds(3));
        scheduler.execute(() -> "third");

        assertEquals(List.of(500L, 3000L), sleeps);
    }

    @Test
    void waitsForRollingWindowCapacity() throws Exception {
        AdjustableClock clock = new AdjustableClock(Instant.parse("2026-01-01T00:00:00Z"));
        List<Long> sleeps = new ArrayList<>();
        TvdbRequestScheduler scheduler = new TvdbRequestScheduler(
                Duration.ZERO, 2, Duration.ofMinutes(1), clock,
                millis -> {
                    sleeps.add(millis);
                    clock.advance(Duration.ofMillis(millis));
                });

        scheduler.execute(() -> 1);
        scheduler.execute(() -> 2);
        scheduler.execute(() -> 3);

        assertEquals(List.of(60_000L), sleeps);
    }

    private static final class AdjustableClock extends Clock {
        private Instant instant;

        AdjustableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
