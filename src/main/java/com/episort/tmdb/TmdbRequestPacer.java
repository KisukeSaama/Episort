package com.episort.tmdb;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * Keeps a whole library scan inside the quota Janus grants Episort.
 *
 * <p>Two knobs, both shared by every request the application sends: how many
 * may be in flight at once, and how far apart their departures are. The spacing
 * is not a fixed guess. Janus reports the quota left and when it resets on
 * every response, so the pacer spreads what remains over the time that remains.
 * A folder of twenty files never slows down; a full Plex library slows down
 * exactly as much as it has to, and only once the quota says so.
 *
 * <p>This is not a retry layer, which the gateway already owns for failing
 * upstream calls. The single status handled here is 429: that one is the caller
 * quota being enforced, and the gateway contract asks the caller to honour the
 * {@code Retry-After} it comes with rather than to keep pushing.
 */
public final class TmdbRequestPacer {

    /** Clock and sleep, injected so tests never wait for real time. */
    interface Timeline {
        long nanoTime();

        void sleepUntil(long deadlineNanos) throws InterruptedException;
    }

    private static final int MAX_IN_FLIGHT = 6;
    private static final Duration FLOOR_INTERVAL = Duration.ofMillis(20);
    private static final Duration CEILING_INTERVAL = Duration.ofSeconds(2);
    private static final Duration BLIND_COOLDOWN = Duration.ofSeconds(2);
    private static final int MAX_QUOTA_WAITS = 3;
    private static final long LONGEST_WAIT_SECONDS = 120;
    private static final int TOO_MANY_REQUESTS = 429;
    private static final String REMAINING_HEADER = "X-Janus-RateLimit-Remaining";
    private static final String RESET_HEADER = "X-Janus-RateLimit-Reset";
    private static final String RETRY_AFTER_HEADER = "Retry-After";

    private final Timeline timeline;
    private final Semaphore inFlight;
    private final long floorIntervalNanos;
    private final long ceilingIntervalNanos;
    private final int maxQuotaWaits;

    private final Object schedule = new Object();
    private long intervalNanos;
    private long lastDepartureNanos;
    private long nextDepartureNanos;

    public TmdbRequestPacer() {
        this(systemTimeline(), MAX_IN_FLIGHT, FLOOR_INTERVAL, CEILING_INTERVAL, MAX_QUOTA_WAITS);
    }

    TmdbRequestPacer(
            Timeline timeline,
            int maxInFlight,
            Duration floorInterval,
            Duration ceilingInterval,
            int maxQuotaWaits) {
        this.timeline = Objects.requireNonNull(timeline, "timeline");
        if (maxInFlight < 1) {
            throw new IllegalArgumentException("maxInFlight must be at least 1");
        }
        this.inFlight = new Semaphore(maxInFlight, true);
        this.floorIntervalNanos = Math.max(0L, floorInterval.toNanos());
        this.ceilingIntervalNanos = Math.max(this.floorIntervalNanos, ceilingInterval.toNanos());
        this.maxQuotaWaits = Math.max(0, maxQuotaWaits);
        this.intervalNanos = this.floorIntervalNanos;
        this.nextDepartureNanos = timeline.nanoTime();
    }

    /**
     * Runs one exchange at the pace the gateway currently allows. A 429 is
     * waited out and the exchange replayed, which is safe because every TMDB
     * call Episort makes is a GET.
     */
    public HttpResponse<String> send(Supplier<HttpResponse<String>> exchange) {
        Objects.requireNonNull(exchange, "exchange");
        for (int waits = 0; ; waits++) {
            HttpResponse<String> response = sendOnce(exchange);
            if (response == null || response.statusCode() != TOO_MANY_REQUESTS || waits >= maxQuotaWaits) {
                return response;
            }
            holdBack(retryAfterNanos(response));
        }
    }

    private HttpResponse<String> sendOnce(Supplier<HttpResponse<String>> exchange) {
        acquire();
        try {
            awaitDeparture();
            HttpResponse<String> response = exchange.get();
            observeQuota(response);
            return response;
        } finally {
            inFlight.release();
        }
    }

    private void acquire() {
        try {
            inFlight.acquire();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interruptedWhileWaiting();
        }
    }

    /**
     * Reserves the next departure slot and waits for it. The slot is taken
     * under the lock and slept off outside it, so threads queue instead of
     * piling onto the same instant.
     */
    private void awaitDeparture() {
        long departure;
        synchronized (schedule) {
            departure = Math.max(timeline.nanoTime(), nextDepartureNanos);
            lastDepartureNanos = departure;
            nextDepartureNanos = departure + intervalNanos;
        }
        sleepUntil(departure);
    }

    /** Pushes everyone's next departure back, which is what a 429 asks for. */
    private void holdBack(long nanos) {
        if (nanos <= 0) return;
        synchronized (schedule) {
            long resume = timeline.nanoTime() + nanos;
            if (resume > nextDepartureNanos) {
                nextDepartureNanos = resume;
            }
        }
    }

    /**
     * Turns the quota headers into a departure interval: whatever is left of
     * the quota, spread over whatever is left of the window. With a comfortable
     * quota the result lands under the floor and nothing slows down.
     */
    private void observeQuota(HttpResponse<String> response) {
        OptionalLong remaining = numericHeader(response, REMAINING_HEADER);
        OptionalLong window = resetNanos(response);
        if (remaining.isEmpty() || window.isEmpty()) return;
        long left = remaining.getAsLong();
        if (left <= 0) {
            setInterval(ceilingIntervalNanos);
            holdBack(window.getAsLong());
            return;
        }
        setInterval(window.getAsLong() / left);
    }

    private void setInterval(long candidate) {
        long clamped = Math.min(ceilingIntervalNanos, Math.max(floorIntervalNanos, candidate));
        synchronized (schedule) {
            intervalNanos = clamped;
            // A widened interval counts from the departure that just left, not
            // from the next one: a quota that tightens has to slow the run down
            // now rather than one request later.
            nextDepartureNanos = Math.max(nextDepartureNanos, lastDepartureNanos + clamped);
        }
    }

    private void sleepUntil(long deadlineNanos) {
        try {
            timeline.sleepUntil(deadlineNanos);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interruptedWhileWaiting();
        }
    }

    private static TmdbException interruptedWhileWaiting() {
        return TmdbException.recoverable(
                "TMDB_INTERRUPTED",
                "TMDB lookup was interrupted.",
                "Interrupted while waiting for a Janus request slot.");
    }

    private static long retryAfterNanos(HttpResponse<String> response) {
        OptionalLong seconds = numericHeader(response, RETRY_AFTER_HEADER);
        return seconds.isEmpty()
                ? BLIND_COOLDOWN.toNanos()
                : Duration.ofSeconds(clampSeconds(seconds.getAsLong())).toNanos();
    }

    private static OptionalLong resetNanos(HttpResponse<String> response) {
        OptionalLong raw = numericHeader(response, RESET_HEADER);
        if (raw.isEmpty()) return OptionalLong.empty();
        long value = raw.getAsLong();
        // The header carries either the seconds left in the window or the epoch
        // second the window ends on. Anything beyond a day can only be the latter.
        long seconds = value > 86_400L ? value - System.currentTimeMillis() / 1000L : value;
        return OptionalLong.of(Duration.ofSeconds(clampSeconds(seconds)).toNanos());
    }

    private static long clampSeconds(long seconds) {
        return Math.min(LONGEST_WAIT_SECONDS, Math.max(0L, seconds));
    }

    private static OptionalLong numericHeader(HttpResponse<String> response, String name) {
        if (response == null) return OptionalLong.empty();
        try {
            return response.headers()
                    .firstValue(name)
                    .map(String::trim)
                    .filter(value -> value.matches("\\d+"))
                    .map(Long::parseLong)
                    .map(OptionalLong::of)
                    .orElseGet(OptionalLong::empty);
        } catch (RuntimeException ignored) {
            // A malformed header must never cost a lookup: fall back to the floor.
            return OptionalLong.empty();
        }
    }

    private static Timeline systemTimeline() {
        return new Timeline() {
            @Override
            public long nanoTime() {
                return System.nanoTime();
            }

            @Override
            public void sleepUntil(long deadlineNanos) throws InterruptedException {
                long remaining = deadlineNanos - System.nanoTime();
                if (remaining > 0) {
                    Thread.sleep(Duration.ofNanos(remaining));
                }
            }
        };
    }
}
