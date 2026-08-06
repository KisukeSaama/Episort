package com.episort.tvdb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.episort.config.TvdbCredentials;
import com.episort.tvdb.cache.TvdbResponseCache;
import com.episort.tvdb.guard.TvdbRateLimitGuard;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CachedTvdbClientTest {
    @Test
    void cachesDifferentYearsSeparately(@TempDir Path directory) {
        CountingClient delegate = new CountingClient();
        CachedTvdbClient client = new CachedTvdbClient(
                delegate,
                new TvdbResponseCache(directory.resolve("cache.json")),
                new TvdbRateLimitGuard());
        TvdbCredentials credentials = new TvdbCredentials("key", Optional.empty());

        client.search(new TvdbSearchCriteria("Office", Optional.of(2001), Optional.empty()), credentials);
        client.search(new TvdbSearchCriteria("Office", Optional.of(2005), Optional.empty()), credentials);
        client.search(new TvdbSearchCriteria("Office", Optional.of(2005), Optional.empty()), credentials);

        assertEquals(2, delegate.searchCount);
    }

    @Test
    void coalescesConcurrentSearchesForTheSameKey(@TempDir Path directory) throws Exception {
        BlockingClient delegate = new BlockingClient();
        CachedTvdbClient client = cached(delegate, directory);
        TvdbCredentials credentials = new TvdbCredentials("key", Optional.empty());

        CompletableFuture<TvdbSearchResult> first = CompletableFuture.supplyAsync(
                () -> client.search("Office", credentials));
        delegate.started.await(2, TimeUnit.SECONDS);
        CompletableFuture<TvdbSearchResult> second = CompletableFuture.supplyAsync(
                () -> client.search("Office", credentials));
        delegate.release.countDown();
        CompletableFuture.allOf(first, second).get(2, TimeUnit.SECONDS);

        assertEquals(1, delegate.searchCount);
    }

    @Test
    void cachesEpisodeOrdersSeparatelyAndMergesAiredEpisodes(@TempDir Path directory) {
        CountingClient delegate = new CountingClient();
        CachedTvdbClient client = cached(delegate, directory);
        TvdbCredentials credentials = new TvdbCredentials("key", Optional.empty());
        TvdbIdentity identity = new TvdbIdentity("101", TvdbMediaType.SERIES, "Show");

        TvdbSeriesDetails dvd = client.seriesDetails(identity, TvdbEpisodeOrder.DVD, credentials);
        client.seriesDetails(identity, TvdbEpisodeOrder.DVD, credentials);

        assertEquals(1, delegate.airedCount);
        assertEquals(1, delegate.dvdCount);
        assertEquals(1, dvd.airedEpisodes().size());
        assertEquals(1, dvd.dvdEpisodes().size());
    }

    private static CachedTvdbClient cached(TvdbClient delegate, Path directory) {
        return new CachedTvdbClient(
                delegate,
                new TvdbResponseCache(directory.resolve("cache.json")),
                new TvdbRateLimitGuard());
    }

    private static class CountingClient implements TvdbClient {
        protected int searchCount;
        private int airedCount;
        private int dvdCount;

        @Override
        public TvdbSearchResult search(String query, TvdbCredentials credentials) {
            searchCount++;
            return new TvdbSearchResult(List.of(), List.of());
        }

        @Override
        public TvdbSearchResult search(TvdbSearchCriteria criteria, TvdbCredentials credentials) {
            return search(criteria.query(), credentials);
        }

        @Override
        public TvdbSeriesDetails seriesDetails(TvdbIdentity identity, TvdbCredentials credentials) {
            return seriesDetails(identity, TvdbEpisodeOrder.AIRED, credentials);
        }

        @Override
        public TvdbSeriesDetails seriesDetails(
                TvdbIdentity identity, TvdbEpisodeOrder order, TvdbCredentials credentials) {
            TvdbEpisode episode = new TvdbEpisode("e1", 1, 1, Optional.of(1), "Pilot", false);
            if (order == TvdbEpisodeOrder.DVD) {
                dvdCount++;
                return new TvdbSeriesDetails(identity, Set.of(TvdbEpisodeOrder.AIRED, TvdbEpisodeOrder.DVD),
                        List.of(), List.of(episode), List.of());
            }
            airedCount++;
            return new TvdbSeriesDetails(identity, Set.of(TvdbEpisodeOrder.AIRED, TvdbEpisodeOrder.DVD),
                    List.of(episode), List.of(), List.of());
        }

        @Override
        public TvdbMovieDetails movieDetails(TvdbIdentity identity, TvdbCredentials credentials) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class BlockingClient extends CountingClient {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public TvdbSearchResult search(String query, TvdbCredentials credentials) {
            searchCount++;
            started.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(exception);
            }
            return new TvdbSearchResult(List.of(), List.of());
        }
    }
}
