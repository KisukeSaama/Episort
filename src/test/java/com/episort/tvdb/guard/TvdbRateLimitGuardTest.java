package com.episort.tvdb.guard;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.tvdb.TvdbException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TvdbRateLimitGuardTest {

    @Test
    void blocksWhenSustainedRateExceeded() {
        AdjustableClock clock = new AdjustableClock(Instant.parse("2026-01-01T00:00:00Z"));
        TvdbRateLimitGuard guard = new TvdbRateLimitGuard(
                3, Duration.ofMinutes(1), 100, Duration.ofSeconds(5), Duration.ofSeconds(30), clock);

        guard.allowOrThrow("q1");
        guard.allowOrThrow("q2");
        guard.allowOrThrow("q3");

        TvdbException ex = assertThrows(TvdbException.class, () -> guard.allowOrThrow("q4"));
        assertEquals("TVDB_RATE_LIMITED", ex.error().code());
        assertTrue(guard.isBreakerOpen());
    }

    @Test
    void blocksOnBurstOfDistinctQueries() {
        AdjustableClock clock = new AdjustableClock(Instant.parse("2026-01-01T00:00:00Z"));
        TvdbRateLimitGuard guard = new TvdbRateLimitGuard(
                1000, Duration.ofMinutes(1), 3, Duration.ofSeconds(5), Duration.ofSeconds(30), clock);

        guard.allowOrThrow("a");
        guard.allowOrThrow("b");
        guard.allowOrThrow("c");

        assertThrows(TvdbException.class, () -> guard.allowOrThrow("d"));
    }

    @Test
    void breakerHealsAfterHoldElapses() {
        AdjustableClock clock = new AdjustableClock(Instant.parse("2026-01-01T00:00:00Z"));
        TvdbRateLimitGuard guard = new TvdbRateLimitGuard(
                1, Duration.ofMinutes(1), 100, Duration.ofSeconds(5), Duration.ofSeconds(10), clock);
        guard.allowOrThrow("q1");
        assertThrows(TvdbException.class, () -> guard.allowOrThrow("q2"));

        clock.advance(Duration.ofMinutes(2));

        assertDoesNotThrow(() -> guard.allowOrThrow("q3"));
    }

    private static final class AdjustableClock extends Clock {
        private Instant instant;

        AdjustableClock(Instant start) { this.instant = start; }

        void advance(Duration duration) { instant = instant.plus(duration); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
