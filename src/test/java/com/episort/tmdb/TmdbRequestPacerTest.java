package com.episort.tmdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class TmdbRequestPacerTest {

    @Test
    void keepsDeparturesOneFloorIntervalApartWhenTheQuotaSaysNothing() {
        FakeTimeline timeline = new FakeTimeline();
        TmdbRequestPacer pacer = pacer(timeline, 4);

        pacer.send(() -> response(200, Map.of()));
        pacer.send(() -> response(200, Map.of()));
        pacer.send(() -> response(200, Map.of()));

        assertEquals(List.of(0L, 20L, 20L), timeline.waitsInMillis());
    }

    @Test
    void spreadsWhatIsLeftOfTheQuotaOverWhatIsLeftOfTheWindow() {
        FakeTimeline timeline = new FakeTimeline();
        TmdbRequestPacer pacer = pacer(timeline, 4);

        // Ten calls left and five seconds to go: one every 500 ms.
        pacer.send(() -> response(200, Map.of(
                "X-Janus-RateLimit-Remaining", "10", "X-Janus-RateLimit-Reset", "5")));
        pacer.send(() -> response(200, Map.of()));

        assertEquals(List.of(0L, 500L), timeline.waitsInMillis());
    }

    @Test
    void staysAtFullSpeedWhileTheQuotaIsComfortable() {
        FakeTimeline timeline = new FakeTimeline();
        TmdbRequestPacer pacer = pacer(timeline, 4);

        pacer.send(() -> response(200, Map.of(
                "X-Janus-RateLimit-Remaining", "4000", "X-Janus-RateLimit-Reset", "60")));
        pacer.send(() -> response(200, Map.of()));

        assertEquals(List.of(0L, 20L), timeline.waitsInMillis());
    }

    @Test
    void waitsOutRetryAfterThenReplaysTheRequest() {
        FakeTimeline timeline = new FakeTimeline();
        TmdbRequestPacer pacer = pacer(timeline, 4);
        AtomicInteger attempts = new AtomicInteger();

        HttpResponse<String> response = pacer.send(() -> attempts.incrementAndGet() == 1
                ? response(429, Map.of("Retry-After", "3"))
                : response(200, Map.of()));

        assertEquals(2, attempts.get());
        assertEquals(200, response.statusCode());
        assertTrue(timeline.waitsInMillis().get(1) >= 3_000L, "the replay must wait out Retry-After");
    }

    @Test
    void givesUpReplayingRatherThanLoopingOnAQuotaThatNeverOpens() {
        FakeTimeline timeline = new FakeTimeline();
        TmdbRequestPacer pacer = new TmdbRequestPacer(
                timeline, 4, Duration.ZERO, Duration.ofSeconds(2), 2);
        AtomicInteger attempts = new AtomicInteger();

        HttpResponse<String> response = pacer.send(() -> {
            attempts.incrementAndGet();
            return response(429, Map.of("Retry-After", "1"));
        });

        assertEquals(3, attempts.get());
        assertEquals(429, response.statusCode());
    }

    @Test
    void neverLetsMoreRequestsRunAtOnceThanTheGatewayIsGiven() throws Exception {
        TmdbRequestPacer pacer = new TmdbRequestPacer(
                systemTimeline(), 3, Duration.ZERO, Duration.ofSeconds(2), 0);
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(12);
        ExecutorService workers = Executors.newFixedThreadPool(12);

        try {
            for (int call = 0; call < 12; call++) {
                workers.submit(() -> {
                    pacer.send(() -> {
                        peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                        sleepQuietly();
                        inFlight.decrementAndGet();
                        return response(200, Map.of());
                    });
                    done.countDown();
                });
            }
            assertTrue(done.await(10, TimeUnit.SECONDS), "every paced request should complete");
        } finally {
            workers.shutdownNow();
        }

        assertTrue(peak.get() <= 3, "peak concurrency was " + peak.get());
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static TmdbRequestPacer pacer(TmdbRequestPacer.Timeline timeline, int maxInFlight) {
        return new TmdbRequestPacer(
                timeline, maxInFlight, Duration.ofMillis(20), Duration.ofSeconds(2), 3);
    }

    private static TmdbRequestPacer.Timeline systemTimeline() {
        return new TmdbRequestPacer.Timeline() {
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

    /** Virtual clock: time only moves when the pacer decides to wait. */
    private static final class FakeTimeline implements TmdbRequestPacer.Timeline {
        private long now;
        private final List<Long> waits = new ArrayList<>();

        @Override
        public long nanoTime() {
            return now;
        }

        @Override
        public void sleepUntil(long deadlineNanos) {
            long wait = Math.max(0L, deadlineNanos - now);
            waits.add(wait);
            now += wait;
        }

        List<Long> waitsInMillis() {
            return waits.stream().map(nanos -> nanos / 1_000_000L).toList();
        }
    }

    private static HttpResponse<String> response(int status, Map<String, String> headers) {
        Map<String, List<String>> raw = new java.util.HashMap<>();
        headers.forEach((name, value) -> raw.put(name, List.of(value)));
        return new StubResponse(status, HttpHeaders.of(raw, (name, value) -> true));
    }

    private record StubResponse(int statusCode, HttpHeaders headers) implements HttpResponse<String> {
        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public String body() {
            return "{}";
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return URI.create("https://example.invalid/");
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
