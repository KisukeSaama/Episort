package com.episort.workflow;

import com.episort.config.JanusConfiguration;
import com.episort.matching.EpisodeMovieMatchService;
import com.episort.matching.MediaMatchProposal;
import com.episort.matching.TmdbEpisodeMetadata;
import com.episort.matching.TmdbMovieMetadata;
import com.episort.matching.TmdbSeriesMetadata;
import com.episort.scanner.InventoryGroup;
import com.episort.scanner.InventoryGroupType;
import com.episort.scanner.InventoryItem;
import com.episort.scanner.InventoryScanResult;
import com.episort.tmdb.TmdbCandidate;
import com.episort.tmdb.TmdbCandidateScorer;
import com.episort.tmdb.TmdbClient;
import com.episort.tmdb.TmdbEpisode;
import com.episort.tmdb.TmdbException;
import com.episort.tmdb.TmdbMediaType;
import com.episort.tmdb.TmdbMovieDetails;
import com.episort.tmdb.TmdbQueryCleaner;
import com.episort.tmdb.TmdbSearchCriteria;
import com.episort.tmdb.TmdbSearchResult;
import com.episort.tmdb.TmdbSeriesDetails;
import com.episort.tmdb.debug.TmdbRequestBus;
import com.episort.tmdb.debug.TmdbRequestTrace;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Resolves every supported inventory group to a TMDB identity right after the
 * scan completes. Output is purely advisory: nothing is renamed here, the UI
 * applies it to rows and the user can override at the next step.
 *
 * <p>A whole Plex library lands here at once, so the run is built around
 * spending as few requests as possible and sending the ones that remain
 * several at a time:
 *
 * <ul>
 *   <li>groups resolve on a small worker pool instead of one after another;
 *   <li>groups whose titles normalize the same share one search, even when
 *       they start at the same instant on different threads;
 *   <li>groups that land on the same show share one episode load;
 *   <li>a group already read as a series never queries the movie index, and
 *       the other way around;
 *   <li>a film needs no detail request at all: the search answer already
 *       carries its title and release year.
 * </ul>
 *
 * <p>How fast those requests actually leave is not decided here. The client
 * holds one application-wide pacer that spaces departures according to the
 * quota Janus reports, so adding workers here never turns into pressure on the
 * gateway.
 */
public final class TmdbBatchMatchService {
    private static final Set<InventoryGroupType> CANDIDATE_TYPES = EnumSet.of(
            InventoryGroupType.LIKELY_SERIES,
            InventoryGroupType.LIKELY_MOVIE,
            InventoryGroupType.UNKNOWN);

    /**
     * Enough groups in the air to keep the gateway busy through the latency of
     * each request, few enough that a cancelled scan stops within moments and
     * that the pacer, not this pool, stays the thing deciding the rate.
     */
    private static final int MAX_WORKERS = 8;
    private static final AtomicInteger WORKER_NUMBERS = new AtomicInteger();

    private final TmdbClient tmdbClient;
    private final EpisodeMovieMatchService matchService;
    private final TmdbCandidateScorer scorer = new TmdbCandidateScorer();

    public TmdbBatchMatchService(TmdbClient tmdbClient) {
        this(tmdbClient, new EpisodeMovieMatchService());
    }

    public TmdbBatchMatchService(TmdbClient tmdbClient, EpisodeMovieMatchService matchService) {
        this.tmdbClient = Objects.requireNonNull(tmdbClient, "tmdbClient");
        this.matchService = Objects.requireNonNull(matchService, "matchService");
    }

    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(int done, int total);
    }

    public TmdbBatchMatchResult run(
            InventoryScanResult inventory,
            JanusConfiguration credentials,
            ProgressListener progress) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(credentials, "credentials");
        ProgressListener safeProgress = progress == null ? (a, b) -> {} : progress;

        List<InventoryGroup> candidates = new ArrayList<>();
        for (InventoryGroup group : inventory.groups()) {
            if (CANDIDATE_TYPES.contains(group.type()) && !group.items().isEmpty()) {
                candidates.add(group);
            }
        }
        int total = candidates.size();
        safeProgress.onProgress(0, total);

        Map<String, TmdbBatchMatchResult.GroupMatch> matches = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        if (total > 0) {
            collect(resolveAll(candidates, credentials, safeProgress), matches, errors);
        }
        publishBatchSummary(total, matches, errors);
        return new TmdbBatchMatchResult(matches, errors);
    }

    /**
     * Runs the groups on the pool and hands back their outcomes in scan order.
     * Progress is reported from the calling thread as each group lands, which
     * is also where cancellation is observed: unwinding here shuts the pool
     * down, so a superseded scan stops spending quota instead of running to
     * the end.
     */
    private List<GroupOutcome> resolveAll(
            List<InventoryGroup> candidates, JanusConfiguration credentials, ProgressListener progress) {
        Resolution resolution = new Resolution(credentials);
        int workerCount = Math.min(MAX_WORKERS, candidates.size());
        ExecutorService workers = Executors.newFixedThreadPool(workerCount, TmdbBatchMatchService::worker);
        List<GroupOutcome> outcomes = new ArrayList<>(candidates.size());
        try {
            CompletionService<GroupOutcome> completion = new ExecutorCompletionService<>(workers);
            for (int index = 0; index < candidates.size(); index++) {
                InventoryGroup group = candidates.get(index);
                int position = index;
                completion.submit(() -> resolution.resolve(position, group));
            }
            for (int done = 1; done <= candidates.size(); done++) {
                outcomes.add(await(completion));
                progress.onProgress(done, candidates.size());
            }
        } finally {
            workers.shutdownNow();
        }
        outcomes.sort(Comparator.comparingInt(GroupOutcome::index));
        return outcomes;
    }

    private static void collect(
            List<GroupOutcome> outcomes,
            Map<String, TmdbBatchMatchResult.GroupMatch> matches,
            List<String> errors) {
        for (GroupOutcome outcome : outcomes) {
            outcome.match().ifPresent(match -> matches.put(outcome.seed(), match));
            outcome.error().ifPresent(errors::add);
        }
    }

    private static GroupOutcome await(CompletionService<GroupOutcome> completion) {
        try {
            return completion.take().get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw TmdbException.recoverable(
                    "TMDB_INTERRUPTED",
                    "TMDB lookup was interrupted.",
                    "Interrupted while collecting batch results.");
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("TMDB batch resolution failed", cause);
        }
    }

    private static Thread worker(Runnable runnable) {
        Thread thread = new Thread(runnable, "episort-tmdb-" + WORKER_NUMBERS.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    }

    /** One group's outcome, tagged with its rank in the scan so order survives the pool. */
    private record GroupOutcome(
            int index, String seed, Optional<TmdbBatchMatchResult.GroupMatch> match, Optional<String> error) {
    }

    /**
     * State of a single run. Every worker reads through it, so identical work
     * is done once no matter how many groups ask for it or when they ask.
     */
    private final class Resolution {
        private final JanusConfiguration credentials;
        private final ConcurrentMap<String, CompletableFuture<List<TmdbCandidate>>> searches =
                new ConcurrentHashMap<>();
        private final ConcurrentMap<String, CompletableFuture<TmdbSeriesDetails>> seriesDetails =
                new ConcurrentHashMap<>();

        private Resolution(JanusConfiguration credentials) {
            this.credentials = credentials;
        }

        private GroupOutcome resolve(int index, InventoryGroup group) {
            try {
                return new GroupOutcome(index, group.seedName(), resolveGroup(group), Optional.empty());
            } catch (TmdbException ex) {
                return failure(index, group, "[" + ex.code() + "]: " + ex.error().safeMessage());
            } catch (RuntimeException ex) {
                String message = ex.getMessage();
                return failure(index, group, "[" + ex.getClass().getSimpleName() + "]: "
                        + (message == null ? "(no message)" : message));
            }
        }

        private GroupOutcome failure(int index, InventoryGroup group, String detail) {
            return new GroupOutcome(
                    index, group.seedName(), Optional.empty(), Optional.of(group.seedName() + " " + detail));
        }

        private Optional<TmdbBatchMatchResult.GroupMatch> resolveGroup(InventoryGroup group) {
            String seed = group.seedName() == null || group.seedName().isBlank()
                    ? group.items().get(0).filename()
                    : group.seedName();
            // Same cleanup as the manual search dialog, so both paths query TMDB
            // with the same string and land on the same candidates.
            String cleaned = TmdbQueryCleaner.clean(seed);
            String query = cleaned.isBlank() ? seed : cleaned;
            List<TmdbCandidate> pool = candidatePool(group.type(), query);
            if (pool.isEmpty()) {
                throw TmdbException.recoverable(
                        "TMDB_NO_CANDIDATE",
                        "No TMDB candidate found for \"" + query + "\".",
                        "Empty candidate pool after TMDB search.");
            }
            // Score the whole pool: TMDB's own ranking often puts a spin-off first.
            TmdbCandidateScorer.ScoredCandidate scored =
                    scorer.bestMatch(scoringQuery(seed, query), group.type(), pool).orElse(null);
            if (scored == null || !scored.suggestible()) {
                return Optional.empty();
            }
            return Optional.of(scored.candidate().identity().mediaType() == TmdbMediaType.MOVIE
                    ? buildMovieMatch(group, scored)
                    : buildSeriesMatch(group, scored));
        }

        /**
         * Queries the index the group already points at and falls back to the
         * other one only when it comes back empty. An unknown group has no such
         * hint and pays for both.
         */
        private List<TmdbCandidate> candidatePool(InventoryGroupType type, String query) {
            return switch (type) {
                case LIKELY_SERIES -> preferred(query, TmdbMediaType.SERIES, TmdbMediaType.MOVIE);
                case LIKELY_MOVIE -> preferred(query, TmdbMediaType.MOVIE, TmdbMediaType.SERIES);
                case UNKNOWN -> {
                    List<TmdbCandidate> merged = new ArrayList<>(candidates(query, TmdbMediaType.SERIES));
                    merged.addAll(candidates(query, TmdbMediaType.MOVIE));
                    yield merged;
                }
                default -> List.of();
            };
        }

        private List<TmdbCandidate> preferred(String query, TmdbMediaType first, TmdbMediaType second) {
            List<TmdbCandidate> pool = candidates(query, first);
            return pool.isEmpty() ? candidates(query, second) : pool;
        }

        private List<TmdbCandidate> candidates(String query, TmdbMediaType mediaType) {
            String key = mediaType.name() + ":" + TmdbCandidateScorer.normalizeTitle(query);
            return shared(searches, key, () -> select(
                    tmdbClient.search(TmdbSearchCriteria.title(query, mediaType), credentials), mediaType));
        }

        private TmdbBatchMatchResult.GroupMatch buildSeriesMatch(
                InventoryGroup group, TmdbCandidateScorer.ScoredCandidate scored) {
            TmdbCandidate candidate = scored.candidate();
            if (!scored.automatic()) {
                return suggestion(group, scored);
            }
            TmdbSeriesDetails details = shared(
                    seriesDetails,
                    candidate.identity().id(),
                    () -> tmdbClient.seriesDetails(candidate.identity(), credentials));
            TmdbSeriesMetadata metadata = new TmdbSeriesMetadata(
                    details.identity().id(),
                    details.identity().displayName(),
                    details.airedEpisodes().stream().map(TmdbBatchMatchService::toEpisodeMetadata).toList());
            List<MediaMatchProposal> proposals = matchService.proposeSeriesMatches(group.items(), metadata);
            return new TmdbBatchMatchResult.GroupMatch(
                    group.seedName(),
                    details.identity(),
                    Optional.of(details),
                    Optional.empty(),
                    Optional.of(candidate),
                    indexByPath(group.items(), proposals),
                    scored.score(),
                    true,
                    Optional.empty());
        }

        private TmdbBatchMatchResult.GroupMatch buildMovieMatch(
                InventoryGroup group, TmdbCandidateScorer.ScoredCandidate scored) {
            TmdbCandidate candidate = scored.candidate();
            if (!scored.automatic()) {
                return suggestion(group, scored);
            }
            // The search answer already carries the title and the release year,
            // which is all a movie proposal ever reads. Asking TMDB for the
            // detail record again would double the traffic of a film-heavy
            // library and return the same two fields.
            TmdbMovieDetails details = new TmdbMovieDetails(candidate.identity(), candidate.year());
            TmdbMovieMetadata metadata = new TmdbMovieMetadata(
                    details.identity().id(),
                    details.identity().displayName(),
                    details.releaseYear());
            List<MediaMatchProposal> proposals = matchService.proposeMovieMatches(group.items(), metadata);
            return new TmdbBatchMatchResult.GroupMatch(
                    group.seedName(),
                    details.identity(),
                    Optional.empty(),
                    Optional.of(details),
                    Optional.of(candidate),
                    indexByPath(group.items(), proposals),
                    scored.score(),
                    true,
                    Optional.empty());
        }

        private TmdbBatchMatchResult.GroupMatch suggestion(
                InventoryGroup group, TmdbCandidateScorer.ScoredCandidate scored) {
            return new TmdbBatchMatchResult.GroupMatch(
                    group.seedName(),
                    scored.candidate().identity(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(scored.candidate()),
                    Map.of(),
                    scored.score(),
                    false,
                    Optional.of("TMDB match suggested; user validation required."));
        }
    }

    /**
     * Runs {@code loader} once for a key, however many threads ask for it. A
     * failure is not remembered: the next group carrying the same title gets a
     * fresh attempt rather than inheriting a one-off network error.
     */
    private static <T> T shared(
            ConcurrentMap<String, CompletableFuture<T>> cache, String key, Supplier<T> loader) {
        CompletableFuture<T> pending = new CompletableFuture<>();
        CompletableFuture<T> running = cache.putIfAbsent(key, pending);
        if (running != null) {
            return join(running);
        }
        try {
            T value = loader.get();
            pending.complete(value);
            return value;
        } catch (RuntimeException | Error failure) {
            cache.remove(key, pending);
            pending.completeExceptionally(failure);
            throw failure;
        }
    }

    private static <T> T join(CompletableFuture<T> pending) {
        try {
            return pending.join();
        } catch (CompletionException wrapped) {
            Throwable cause = wrapped.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw wrapped;
        }
    }

    private static List<TmdbCandidate> select(TmdbSearchResult result, TmdbMediaType mediaType) {
        if (result == null) return List.of();
        return mediaType == TmdbMediaType.MOVIE ? result.movieCandidates() : result.seriesCandidates();
    }

    /**
     * The cleaned title is what we compare against TMDB names; the year is
     * dropped by the cleaner but is a decisive tie-breaker between remakes, so
     * we re-append it for the scorer (which strips it back out for the title
     * comparison).
     */
    private static String scoringQuery(String seed, String cleanedQuery) {
        return TmdbQueryCleaner.year(seed)
                .map(year -> cleanedQuery + " " + year)
                .orElse(cleanedQuery);
    }

    private static void publishBatchSummary(
            int total,
            Map<String, TmdbBatchMatchResult.GroupMatch> matches,
            List<String> errors) {
        StringBuilder body = new StringBuilder();
        body.append("groups=").append(total)
                .append("  matches=").append(matches.size())
                .append("  errors=").append(errors.size()).append('\n');
        if (!matches.isEmpty()) {
            body.append("\nMatches:\n");
            matches.forEach((seed, m) -> body.append("  • ").append(seed)
                    .append(" → ").append(m.identity().displayName())
                    .append(" [").append(m.identity().mediaType()).append(" ").append(m.identity().id()).append("]")
                    .append("  proposals=").append(m.proposalsByPath().size())
                    .append('\n'));
        }
        if (!errors.isEmpty()) {
            body.append("\nErrors:\n");
            for (String e : errors) body.append("  • ").append(e).append('\n');
        }
        try {
            TmdbRequestBus.get().publish(new TmdbRequestTrace(
                    Instant.now(), "BATCH", "summary", 0, 0L,
                    body.toString(), null, false));
        } catch (RuntimeException ignored) {
            // ignore
        }
    }

    private static Map<Path, MediaMatchProposal> indexByPath(
            List<InventoryItem> items, List<MediaMatchProposal> proposals) {
        Map<Path, MediaMatchProposal> byPath = new HashMap<>();
        for (MediaMatchProposal proposal : proposals) {
            byPath.put(proposal.sourcePath(), proposal);
        }
        for (InventoryItem item : items) {
            byPath.putIfAbsent(item.sourcePath(),
                    MediaMatchProposal.unmatched(item.sourcePath(), "No proposal returned."));
        }
        return byPath;
    }

    private static TmdbEpisodeMetadata toEpisodeMetadata(TmdbEpisode episode) {
        return new TmdbEpisodeMetadata(
                episode.id(),
                episode.seasonNumber(),
                episode.episodeNumber(),
                episode.absoluteNumber(),
                episode.title(),
                episode.special());
    }
}
