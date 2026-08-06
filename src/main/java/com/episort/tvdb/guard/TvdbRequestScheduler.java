package com.episort.tvdb.guard;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Serializes physical TVDB requests and spaces them independently of the
 * higher-level operation that produced them. A series lookup can fan out into
 * multiple HTTP calls, so this scheduler deliberately lives next to the
 * transport instead of the response cache.
 */
public final class TvdbRequestScheduler {
    private static final Duration DEFAULT_MINIMUM_SPACING = Duration.ofMillis(500);
    private static final int DEFAULT_MAX_REQUESTS_PER_WINDOW = 30;
    private static final Duration DEFAULT_WINDOW = Duration.ofMinutes(1);

    private final Duration minimumSpacing;
    private final int maxRequestsPerWindow;
    private final Duration window;
    private final Clock clock;
    private final Sleeper sleeper;
    private final Deque<Instant> dispatches = new ArrayDeque<>();

    private Instant nextAllowedAt = Instant.MIN;

    public TvdbRequestScheduler() {
        this(DEFAULT_MINIMUM_SPACING, DEFAULT_MAX_REQUESTS_PER_WINDOW,
                DEFAULT_WINDOW, Clock.systemUTC(), Thread::sleep);
    }

    /** Scheduler without proactive spacing, useful for deterministic local transports and tests. */
    public static TvdbRequestScheduler unthrottled() {
        return new TvdbRequestScheduler(Duration.ZERO, Integer.MAX_VALUE,
                DEFAULT_WINDOW, Clock.systemUTC(), Thread::sleep);
    }

    TvdbRequestScheduler(
            Duration minimumSpacing,
            int maxRequestsPerWindow,
            Duration window,
            Clock clock,
            Sleeper sleeper) {
        this.minimumSpacing = requireNonNegative(minimumSpacing, "minimumSpacing");
        if (maxRequestsPerWindow <= 0) {
            throw new IllegalArgumentException("maxRequestsPerWindow > 0");
        }
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.window = Objects.requireNonNull(window, "window");
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window > 0");
        }
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    /** Executes one real network round-trip. The monitor also limits concurrency to one. */
    public synchronized <T> T execute(InterruptibleSupplier<T> request) throws Exception {
        Objects.requireNonNull(request, "request");
        awaitDispatchSlot();
        Instant dispatchedAt = clock.instant();
        dispatches.addLast(dispatchedAt);
        nextAllowedAt = dispatchedAt.plus(minimumSpacing);
        return request.get();
    }

    /** Prevents every caller from dispatching before a provider-directed cooldown expires. */
    public synchronized void deferFor(Duration delay) {
        Duration safeDelay = requireNonNegative(delay, "delay");
        Instant candidate = clock.instant().plus(safeDelay);
        if (candidate.isAfter(nextAllowedAt)) {
            nextAllowedAt = candidate;
        }
    }

    private void awaitDispatchSlot() throws InterruptedException {
        while (true) {
            Instant now = clock.instant();
            prune(now);
            Instant allowedAt = nextAllowedAt;
            if (dispatches.size() >= maxRequestsPerWindow) {
                Instant rateSlot = dispatches.peekFirst().plus(window);
                if (rateSlot.isAfter(allowedAt)) {
                    allowedAt = rateSlot;
                }
            }
            if (!allowedAt.isAfter(now)) {
                return;
            }
            sleeper.sleep(Duration.between(now, allowedAt).toMillis());
        }
    }

    private void prune(Instant now) {
        Instant cutoff = now.minus(window);
        while (!dispatches.isEmpty() && !dispatches.peekFirst().isAfter(cutoff)) {
            dispatches.removeFirst();
        }
    }

    private static Duration requireNonNegative(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    @FunctionalInterface
    public interface InterruptibleSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
