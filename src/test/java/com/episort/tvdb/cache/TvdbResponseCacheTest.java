package com.episort.tvdb.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.tvdb.TvdbCandidate;
import com.episort.tvdb.TvdbIdentity;
import com.episort.tvdb.TvdbMediaType;
import com.episort.tvdb.TvdbSearchResult;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TvdbResponseCacheTest {

    @Test
    void returnsCachedValueWithinTtl(@TempDir Path dir) {
        TvdbResponseCache cache = new TvdbResponseCache(dir.resolve("cache.json"));
        TvdbSearchResult value = result("Foo", "1");

        cache.put("search:foo", value, Duration.ofMinutes(5));

        Optional<TvdbSearchResult> hit = cache.get("search:foo", TvdbSearchResult.class);
        assertTrue(hit.isPresent());
        assertEquals("Foo", hit.orElseThrow().seriesCandidates().get(0).identity().displayName());
    }

    @Test
    void expiresEntriesAfterTtl(@TempDir Path dir) {
        AdjustableClock clock = new AdjustableClock(Instant.parse("2026-01-01T00:00:00Z"));
        TvdbResponseCache cache = new TvdbResponseCache(dir.resolve("cache.json"), clock);
        cache.put("search:foo", result("Foo", "1"), Duration.ofMinutes(5));

        clock.advance(Duration.ofMinutes(6));

        assertFalse(cache.get("search:foo", TvdbSearchResult.class).isPresent());
    }

    @Test
    void survivesReopen(@TempDir Path dir) {
        Path file = dir.resolve("cache.json");
        TvdbResponseCache first = new TvdbResponseCache(file);
        first.put("search:foo", result("Foo", "1"), Duration.ofHours(1));

        TvdbResponseCache second = new TvdbResponseCache(file);
        assertTrue(second.get("search:foo", TvdbSearchResult.class).isPresent());
    }

    private static TvdbSearchResult result(String name, String id) {
        return new TvdbSearchResult(
                List.of(new TvdbCandidate(
                        new TvdbIdentity(id, TvdbMediaType.SERIES, name),
                        Optional.empty(),
                        Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(),
                        com.episort.tvdb.OptionalDoubleScore.empty(),
                        0)),
                List.of());
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
