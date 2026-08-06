package com.episort.tvdb;

import com.episort.config.TvdbCredentials;
import com.episort.tvdb.cache.TvdbResponseCache;
import com.episort.tvdb.debug.TvdbRequestBus;
import com.episort.tvdb.debug.TvdbRequestTrace;
import com.episort.tvdb.guard.TvdbRateLimitGuard;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Decorator that serves TVDB responses from a local cache when possible and
 * routes uncached lookups through a {@link TvdbRateLimitGuard} before hitting
 * the network. TVDB metadata is global and stable for hours-to-days, so
 * caching is allowed under the service's terms — the goal here is to be a
 * good citizen and to keep API usage bounded.
 */
public final class CachedTvdbClient implements TvdbClient {
    private static final Duration DEFAULT_SEARCH_TTL = Duration.ofDays(7);
    private static final Duration DEFAULT_EMPTY_SEARCH_TTL = Duration.ofHours(6);
    private static final Duration DEFAULT_SERIES_TTL = Duration.ofDays(7);
    private static final Duration DEFAULT_MOVIE_TTL = Duration.ofDays(30);

    private final TvdbClient delegate;
    private final TvdbResponseCache cache;
    private final TvdbRateLimitGuard guard;
    private final Duration searchTtl;
    private final Duration seriesTtl;
    private final Duration movieTtl;
    private final ConcurrentHashMap<String, CompletableFuture<Object>> inFlight = new ConcurrentHashMap<>();

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
        return search(TvdbSearchCriteria.title(query), credentials);
    }

    @Override
    public TvdbSearchResult search(TvdbSearchCriteria criteria, TvdbCredentials credentials) {
        Objects.requireNonNull(criteria, "criteria");
        String normalized = normalizeQuery(criteria.query());
        String key = criteria.tvdbId()
                .map(id -> "search:id:" + id)
                .orElseGet(() -> "search:" + normalized + criteria.year().map(year -> ":year:" + year).orElse(""));
        Optional<TvdbSearchResult> hit = cache.get(key, TvdbSearchResult.class);
        if (hit.isPresent()) {
            TvdbRequestBus.get().publish(TvdbRequestTrace.cacheHit(Instant.now(), "(cache) " + key));
            return hit.get();
        }
        guard.allowOrThrow(key);
        TvdbSearchResult fresh = loadOnce(key, () -> delegate.search(criteria, credentials));
        Duration ttl = fresh.seriesCandidates().isEmpty() && fresh.movieCandidates().isEmpty()
                ? DEFAULT_EMPTY_SEARCH_TTL
                : searchTtl;
        cache.put(key, fresh, ttl);
        return fresh;
    }

    @Override
    public TvdbSeriesDetails seriesDetails(TvdbIdentity identity, TvdbCredentials credentials) {
        return seriesDetails(identity, TvdbEpisodeOrder.AIRED, credentials);
    }

    @Override
    public TvdbSeriesDetails seriesDetails(
            TvdbIdentity identity,
            TvdbEpisodeOrder order,
            TvdbCredentials credentials) {
        TvdbEpisodeOrder safeOrder = order == null ? TvdbEpisodeOrder.AIRED : order;
        String key = "series:" + identity.id() + ":order:" + safeOrder.name().toLowerCase(Locale.ROOT);
        Optional<TvdbSeriesDetails> hit = cache.get(key, TvdbSeriesDetails.class);
        if (hit.isPresent()) {
            TvdbRequestBus.get().publish(TvdbRequestTrace.cacheHit(Instant.now(), "(cache) " + key));
            return hit.get();
        }
        guard.allowOrThrow(key);
        TvdbSeriesDetails fresh = loadOnce(key, () -> delegate.seriesDetails(identity, safeOrder, credentials));
        if (safeOrder == TvdbEpisodeOrder.AIRED) {
            cache.put(key, fresh, seriesTtl);
            return fresh;
        }
        TvdbSeriesDetails aired = seriesDetails(identity, TvdbEpisodeOrder.AIRED, credentials);
        TvdbSeriesDetails merged = mergeOrders(aired, fresh, safeOrder);
        cache.put(key, merged, seriesTtl);
        return merged;
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
        TvdbMovieDetails fresh = loadOnce(key, () -> delegate.movieDetails(identity, credentials));
        cache.put(key, fresh, movieTtl);
        return fresh;
    }

    @SuppressWarnings("unchecked")
    private <T> T loadOnce(String key, Supplier<T> loader) {
        CompletableFuture<Object> mine = new CompletableFuture<>();
        CompletableFuture<Object> existing = inFlight.putIfAbsent(key, mine);
        if (existing != null) {
            try {
                return (T) existing.join();
            } catch (CompletionException exception) {
                if (exception.getCause() instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw exception;
            }
        }
        try {
            T value = loader.get();
            mine.complete(value);
            return value;
        } catch (RuntimeException exception) {
            mine.completeExceptionally(exception);
            throw exception;
        } finally {
            inFlight.remove(key, mine);
        }
    }

    private static TvdbSeriesDetails mergeOrders(
            TvdbSeriesDetails aired,
            TvdbSeriesDetails selected,
            TvdbEpisodeOrder selectedOrder) {
        java.util.EnumSet<TvdbEpisodeOrder> supported = java.util.EnumSet.noneOf(TvdbEpisodeOrder.class);
        supported.addAll(aired.supportedOrders());
        supported.addAll(selected.supportedOrders());
        List<TvdbEpisode> dvd = selectedOrder == TvdbEpisodeOrder.DVD
                ? selected.dvdEpisodes()
                : List.of();
        List<TvdbEpisode> absolute = selectedOrder == TvdbEpisodeOrder.ABSOLUTE
                ? selected.absoluteEpisodes()
                : List.of();
        if (selectedOrder == TvdbEpisodeOrder.ABSOLUTE && absolute.isEmpty()) {
            absolute = aired.airedEpisodes().stream()
                    .filter(episode -> episode.absoluteNumber().isPresent())
                    .toList();
        }
        return new TvdbSeriesDetails(
                selected.identity(), supported, aired.airedEpisodes(), dvd, absolute);
    }

    private static String normalizeQuery(String query) {
        if (query == null) return "";
        return query.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
