package com.episort.tvdb;

import com.episort.config.TvdbCredentials;
import com.episort.tvdb.cache.TvdbResponseCache;
import com.episort.tvdb.debug.TvdbRequestBus;
import com.episort.tvdb.debug.TvdbRequestTrace;
import com.episort.tvdb.guard.TvdbRateLimitGuard;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Decorator that serves TVDB responses from a local cache when possible and
 * routes uncached lookups through a {@link TvdbRateLimitGuard} before hitting
 * the network. TVDB metadata is global and stable for hours-to-days, so
 * caching is allowed under the service's terms — the goal here is to be a
 * good citizen and to keep API usage bounded.
 */
public final class CachedTvdbClient implements TvdbClient {
    private static final Duration DEFAULT_SEARCH_TTL = Duration.ofDays(7);
    private static final Duration DEFAULT_SERIES_TTL = Duration.ofDays(7);
    private static final Duration DEFAULT_MOVIE_TTL = Duration.ofDays(30);

    private final TvdbClient delegate;
    private final TvdbResponseCache cache;
    private final TvdbRateLimitGuard guard;
    private final Duration searchTtl;
    private final Duration seriesTtl;
    private final Duration movieTtl;

    public CachedTvdbClient(TvdbClient delegate, TvdbResponseCache cache, TvdbRateLimitGuard guard) {
        this(delegate, cache, guard, DEFAULT_SEARCH_TTL, DEFAULT_SERIES_TTL, DEFAULT_MOVIE_TTL);
    }

    public CachedTvdbClient(
            TvdbClient delegate,
            TvdbResponseCache cache,
            TvdbRateLimitGuard guard,
            Duration searchTtl,
            Duration seriesTtl,
            Duration movieTtl) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.searchTtl = Objects.requireNonNull(searchTtl, "searchTtl");
        this.seriesTtl = Objects.requireNonNull(seriesTtl, "seriesTtl");
        this.movieTtl = Objects.requireNonNull(movieTtl, "movieTtl");
    }

    @Override
    public TvdbSearchResult search(String query, TvdbCredentials credentials) {
        String normalized = normalizeQuery(query);
        String key = "search:" + normalized;
        Optional<TvdbSearchResult> hit = cache.get(key, TvdbSearchResult.class);
        if (hit.isPresent()) {
            TvdbRequestBus.get().publish(TvdbRequestTrace.cacheHit(Instant.now(), "(cache) " + key));
            return hit.get();
        }
        guard.allowOrThrow("search:" + normalized);
        TvdbSearchResult fresh = delegate.search(query, credentials);
        cache.put(key, fresh, searchTtl);
        return fresh;
    }

    @Override
    public TvdbSeriesDetails seriesDetails(TvdbIdentity identity, TvdbCredentials credentials) {
        String key = "series:" + identity.id();
        Optional<TvdbSeriesDetails> hit = cache.get(key, TvdbSeriesDetails.class);
        if (hit.isPresent()) {
            TvdbRequestBus.get().publish(TvdbRequestTrace.cacheHit(Instant.now(), "(cache) " + key));
            return hit.get();
        }
        guard.allowOrThrow(key);
        TvdbSeriesDetails fresh = delegate.seriesDetails(identity, credentials);
        cache.put(key, fresh, seriesTtl);
        return fresh;
    }

    @Override
    public TvdbMovieDetails movieDetails(TvdbIdentity identity, TvdbCredentials credentials) {
        String key = "movie:" + identity.id();
        Optional<TvdbMovieDetails> hit = cache.get(key, TvdbMovieDetails.class);
        if (hit.isPresent()) {
            TvdbRequestBus.get().publish(TvdbRequestTrace.cacheHit(Instant.now(), "(cache) " + key));
            return hit.get();
        }
        guard.allowOrThrow(key);
        TvdbMovieDetails fresh = delegate.movieDetails(identity, credentials);
        cache.put(key, fresh, movieTtl);
        return fresh;
    }

    private static String normalizeQuery(String query) {
        if (query == null) return "";
        return query.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
