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
import com.episort.tmdb.TmdbSearchResult;
import com.episort.tmdb.TmdbSeriesDetails;
import com.episort.tmdb.debug.TmdbRequestBus;
import com.episort.tmdb.debug.TmdbRequestTrace;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves every supported inventory group to a TMDB identity right after the
 * AI scan completes. The service relies on {@link TmdbClient} (which the app
 * wraps with a cache + rate guard), so successive scans of the same library
 * incur near-zero API traffic. Output is purely advisory: nothing is renamed
 * here, the UI applies it to rows and the user can override at the next step.
 */
public final class TmdbBatchMatchService {
    private static final Set<InventoryGroupType> CANDIDATE_TYPES = EnumSet.of(
            InventoryGroupType.LIKELY_SERIES,
            InventoryGroupType.LIKELY_MOVIE,
            InventoryGroupType.UNKNOWN);

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
        Map<String, TmdbSearchResult> searchCache = new HashMap<>();

        int done = 0;
        for (InventoryGroup group : candidates) {
            try {
                resolveGroup(group, credentials, searchCache).ifPresent(match -> matches.put(group.seedName(), match));
            } catch (TmdbException ex) {
                errors.add(group.seedName() + " [" + ex.code() + "]: " + ex.error().safeMessage());
            } catch (RuntimeException ex) {
                String msg = ex.getMessage();
                errors.add(group.seedName() + " [" + ex.getClass().getSimpleName() + "]: "
                        + (msg == null ? "(no message)" : msg));
            }
            done++;
            safeProgress.onProgress(done, total);
        }
        publishBatchSummary(total, matches, errors);
        return new TmdbBatchMatchResult(matches, errors);
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

    private Optional<TmdbBatchMatchResult.GroupMatch> resolveGroup(
            InventoryGroup group, JanusConfiguration credentials, Map<String, TmdbSearchResult> searchCache) {
        String seed = group.seedName() == null || group.seedName().isBlank()
                ? group.items().get(0).filename()
                : group.seedName();
        // Same cleanup as the manual search dialog, so both paths query TMDB
        // with the same string and land on the same candidates.
        String cleaned = TmdbQueryCleaner.clean(seed);
        String query = cleaned.isBlank() ? seed : cleaned;
        String cacheKey = TmdbCandidateScorer.normalizeSearchKey(query, group.type());
        TmdbSearchResult search = searchCache.computeIfAbsent(cacheKey, key -> tmdbClient.search(query, credentials));
        List<TmdbCandidate> pool = pickCandidatePool(group.type(), search);
        if (pool.isEmpty()) {
            throw TmdbException.recoverable(
                    "TMDB_NO_CANDIDATE",
                    "No TMDB candidate found for \"" + query + "\".",
                    "Empty candidate pool after TMDB search.");
        }
        // Score the whole pool: TMDB's own ranking often puts a spin-off first.
        TmdbCandidateScorer.ScoredCandidate scored = scorer.bestMatch(scoringQuery(seed, query), group.type(), pool)
                .orElse(null);
        if (scored == null || !scored.suggestible()) {
            return Optional.empty();
        }

        if (scored.candidate().identity().mediaType() == TmdbMediaType.MOVIE) {
            return Optional.of(buildMovieMatch(group, scored, credentials));
        }
        return Optional.of(buildSeriesMatch(group, scored, credentials));
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

    private TmdbBatchMatchResult.GroupMatch buildSeriesMatch(
            InventoryGroup group, TmdbCandidateScorer.ScoredCandidate scored, JanusConfiguration credentials) {
        TmdbCandidate candidate = scored.candidate();
        if (!scored.automatic()) {
            return new TmdbBatchMatchResult.GroupMatch(
                    group.seedName(),
                    candidate.identity(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(candidate),
                    Map.of(),
                    scored.score(),
                    false,
                    Optional.of("TMDB match suggested; user validation required."));
        }
        TmdbSeriesDetails details = tmdbClient.seriesDetails(candidate.identity(), credentials);
        TmdbSeriesMetadata metadata = new TmdbSeriesMetadata(
                details.identity().id(),
                details.identity().displayName(),
                details.airedEpisodes().stream().map(TmdbBatchMatchService::toEpisodeMetadata).toList());
        List<MediaMatchProposal> proposals = matchService.proposeSeriesMatches(group.items(), metadata);
        Map<Path, MediaMatchProposal> byPath = indexByPath(group.items(), proposals);
        return new TmdbBatchMatchResult.GroupMatch(
                group.seedName(),
                details.identity(),
                Optional.of(details),
                Optional.empty(),
                Optional.of(candidate),
                byPath,
                scored.score(),
                true,
                Optional.empty());
    }

    private TmdbBatchMatchResult.GroupMatch buildMovieMatch(
            InventoryGroup group, TmdbCandidateScorer.ScoredCandidate scored, JanusConfiguration credentials) {
        TmdbCandidate candidate = scored.candidate();
        if (!scored.automatic()) {
            return new TmdbBatchMatchResult.GroupMatch(
                    group.seedName(),
                    candidate.identity(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(candidate),
                    Map.of(),
                    scored.score(),
                    false,
                    Optional.of("TMDB match suggested; user validation required."));
        }
        TmdbMovieDetails details = tmdbClient.movieDetails(candidate.identity(), credentials);
        TmdbMovieMetadata metadata = new TmdbMovieMetadata(
                details.identity().id(),
                details.identity().displayName(),
                details.releaseYear());
        List<MediaMatchProposal> proposals = matchService.proposeMovieMatches(group.items(), metadata);
        Map<Path, MediaMatchProposal> byPath = indexByPath(group.items(), proposals);
        return new TmdbBatchMatchResult.GroupMatch(
                group.seedName(),
                details.identity(),
                Optional.empty(),
                Optional.of(details),
                Optional.of(candidate),
                byPath,
                scored.score(),
                true,
                Optional.empty());
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

    private static List<TmdbCandidate> pickCandidatePool(InventoryGroupType type, TmdbSearchResult search) {
        return switch (type) {
            case LIKELY_SERIES -> search.seriesCandidates().isEmpty()
                    ? search.movieCandidates() : search.seriesCandidates();
            case LIKELY_MOVIE -> search.movieCandidates().isEmpty()
                    ? search.seriesCandidates() : search.movieCandidates();
            case UNKNOWN -> {
                List<TmdbCandidate> merged = new ArrayList<>(search.seriesCandidates());
                merged.addAll(search.movieCandidates());
                yield merged;
            }
            default -> List.of();
        };
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
