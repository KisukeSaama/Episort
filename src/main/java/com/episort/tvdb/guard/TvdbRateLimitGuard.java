package com.episort.tvdb.guard;

import com.episort.tvdb.TvdbException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Local circuit-breaker that protects TVDB from accidental or malicious
 * traffic from a single Episort instance. Two checks run on every outbound
 * network call:
 *
 * <ol>
 *   <li><b>Sustained rate</b> — at most {@code maxCallsPerWindow} requests
 *       inside the sliding window {@code window}.</li>
 *   <li><b>Burst pattern</b> — more than {@code burstThreshold} unique queries
 *       inside {@code burstWindow} suggests a fuzzer or an automated probe and
 *       trips the breaker for {@code breakerHold}.</li>
 * </ol>
 *
 * Cached responses bypass the guard entirely — callers should only invoke
 * {@link #allowOrThrow(String)} just before a real network round-trip.
 */
public final class TvdbRateLimitGuard {
    private final int maxCallsPerWindow;
    private final Duration window;
    private final int burstThreshold;
    private final Duration burstWindow;
    private final Duration breakerHold;
    private final Clock clock;

    private final Deque<Instant> recentCalls = new ArrayDeque<>();
    private final Deque<QueryHit> recentQueries = new ArrayDeque<>();
    private Instant breakerOpenUntil = Instant.MIN;

    public TvdbRateLimitGuard() {
        this(60, Duration.ofMinutes(1), 30, Duration.ofSeconds(5), Duration.ofSeconds(60), Clock.systemUTC());
    }

    public TvdbRateLimitGuard(
            int maxCallsPerWindow,
            Duration window,
            int burstThreshold,
            Duration burstWindow,
            Duration breakerHold,
            Clock clock) {
        if (maxCallsPerWindow <= 0) throw new IllegalArgumentException("maxCallsPerWindow > 0");
        if (burstThreshold <= 0) throw new IllegalArgumentException("burstThreshold > 0");
        this.maxCallsPerWindow = maxCallsPerWindow;
        this.window = Objects.requireNonNull(window, "window");
        this.burstThreshold = burstThreshold;
        this.burstWindow = Objects.requireNonNull(burstWindow, "burstWindow");
        this.breakerHold = Objects.requireNonNull(breakerHold, "breakerHold");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized void allowOrThrow(String queryKey) {
        Instant now = Instant.now(clock);
        if (now.isBefore(breakerOpenUntil)) {
            throw TvdbException.recoverable(
                    "TVDB_RATE_LIMITED",
                    "TVDB requests are temporarily blocked.",
                    "Local circuit breaker is open until " + breakerOpenUntil + ".");
        }
        prune(now);
        if (recentCalls.size() >= maxCallsPerWindow) {
            tripBreaker(now);
            throw TvdbException.recoverable(
                    "TVDB_RATE_LIMITED",
                    "Too many TVDB requests, slow down.",
                    "Sustained rate exceeded: " + maxCallsPerWindow + " calls inside " + window + ".");
        }
        recentCalls.addLast(now);
        if (queryKey != null) {
            recentQueries.addLast(new QueryHit(queryKey, now));
            if (uniqueQueriesInBurstWindow(now) > burstThreshold) {
                tripBreaker(now);
                throw TvdbException.recoverable(
                        "TVDB_RATE_LIMITED",
                        "Suspicious TVDB usage detected, pausing.",
                        "Burst pattern: more than " + burstThreshold
                                + " distinct queries inside " + burstWindow + ".");
            }
        }
    }

    public synchronized boolean isBreakerOpen() {
        return Instant.now(clock).isBefore(breakerOpenUntil);
    }

    private void tripBreaker(Instant now) {
        breakerOpenUntil = now.plus(breakerHold);
    }

    private void prune(Instant now) {
        Instant rateCutoff = now.minus(window);
        while (!recentCalls.isEmpty() && recentCalls.peekFirst().isBefore(rateCutoff)) {
            recentCalls.pollFirst();
        }
        Instant burstCutoff = now.minus(burstWindow);
        while (!recentQueries.isEmpty() && recentQueries.peekFirst().at.isBefore(burstCutoff)) {
            recentQueries.pollFirst();
        }
    }

    private int uniqueQueriesInBurstWindow(Instant now) {
        Instant cutoff = now.minus(burstWindow);
        Set<String> distinct = new HashSet<>();
        for (QueryHit hit : recentQueries) {
            if (!hit.at.isBefore(cutoff)) {
                distinct.add(hit.query);
            }
        }
        return distinct.size();
    }

    private record QueryHit(String query, Instant at) {}
}
