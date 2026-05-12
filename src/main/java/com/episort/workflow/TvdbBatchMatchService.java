package com.episort.workflow;

import com.episort.config.TvdbCredentials;
import com.episort.matching.EpisodeMovieMatchService;
import com.episort.matching.MediaMatchProposal;
import com.episort.matching.TvdbEpisodeMetadata;
import com.episort.matching.TvdbMovieMetadata;
import com.episort.matching.TvdbSeriesMetadata;
import com.episort.scanner.InventoryGroup;
import com.episort.scanner.InventoryGroupType;
import com.episort.scanner.InventoryItem;
import com.episort.scanner.InventoryScanResult;
import com.episort.tvdb.AiTvdbCandidateRanker;
import com.episort.tvdb.TvdbCandidate;
import com.episort.tvdb.TvdbClient;
import com.episort.tvdb.TvdbEpisode;
import com.episort.tvdb.TvdbException;
import com.episort.tvdb.TvdbMediaType;
import com.episort.tvdb.TvdbMovieDetails;
import com.episort.tvdb.TvdbSearchResult;
import com.episort.tvdb.TvdbSeriesDetails;
import com.episort.tvdb.TvdbCandidateScorer;
import com.episort.tvdb.debug.TvdbRequestBus;
import com.episort.tvdb.debug.TvdbRequestTrace;
import java.nio.file.Path;
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
 * Resolves every supported inventory group to a TVDB identity right after the
 * AI scan completes. The service relies on {@link TvdbClient} (which the app
 * wraps with a cache + rate guard), so successive scans of the same library
 * incur near-zero API traffic. Output is purely advisory: nothing is renamed
 * here, the UI applies it to rows and the user can override at the next step.
 */
public final class TvdbBatchMatchService {
    private static final Set<InventoryGroupType> CANDIDATE_TYPES = EnumSet.of(
            InventoryGroupType.LIKELY_SERIES,
            InventoryGroupType.LIKELY_MOVIE,
            InventoryGroupType.UNKNOWN);

    private final TvdbClient tvdbClient;
    private final EpisodeMovieMatchService matchService;
    private final Optional<AiTvdbCandidateRanker> ranker;
    private final TvdbCandidateScorer scorer = new TvdbCandidateScorer();

    public TvdbBatchMatchService(TvdbClient tvdbClient) {
        this(tvdbClient, new EpisodeMovieMatchService(), null);
    }

    public TvdbBatchMatchService(
            TvdbClient tvdbClient,
            EpisodeMovieMatchService matchService,
            AiTvdbCandidateRanker ranker) {
        this.tvdbClient = Objects.requireNonNull(tvdbClient, "tvdbClient");
        this.matchService = Objects.requireNonNull(matchService, "matchService");
        this.ranker = Optional.ofNullable(ranker);
    }

    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(int done, int total);
    }

    public TvdbBatchMatchResult run(
            InventoryScanResult inventory,
            TvdbCredentials credentials,
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

        Map<String, TvdbBatchMatchResult.GroupMatch> matches = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        Map<String, TvdbSearchResult> searchCache = new HashMap<>();

        int done = 0;
        for (InventoryGroup group : candidates) {
            try {
                resolveGroup(group, credentials, searchCache).ifPresent(match -> matches.put(group.seedName(), match));
            } catch (TvdbException ex) {
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
        return new TvdbBatchMatchResult(matches, errors);
    }

    private static void publishBatchSummary(
            int total,
            Map<String, TvdbBatchMatchResult.GroupMatch> matches,
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
            TvdbRequestBus.get().publish(new TvdbRequestTrace(
                    java.time.Instant.now(), "BATCH", "summary", 0, 0L,
                    body.toString(), null, false));
        } catch (RuntimeException ignored) {
            // ignore
        }
    }

    private Optional<TvdbBatchMatchResult.GroupMatch> resolveGroup(
            InventoryGroup group, TvdbCredentials credentials, Map<String, TvdbSearchResult> searchCache) {
        String query = group.seedName() == null || group.seedName().isBlank()
                ? group.items().get(0).filename()
                : group.seedName();
        String cacheKey = TvdbCandidateScorer.normalizeSearchKey(query, group.type());
        TvdbSearchResult search = searchCache.computeIfAbsent(cacheKey, key -> tvdbClient.search(query, credentials));
        List<TvdbCandidate> pool = pickCandidatePool(group.type(), search);
        if (pool.isEmpty()) {
            throw TvdbException.recoverable(
                    "TVDB_NO_CANDIDATE",
                    "No TVDB candidate found for \"" + query + "\".",
                    "Empty candidate pool after TVDB search.");
        }
        TvdbCandidate top = ranker.map(r -> r.rankAdvisory(query, pool))
                .filter(list -> !list.isEmpty())
                .map(List::getFirst)
                .orElse(pool.get(0));
        TvdbCandidateScorer.ScoredCandidate scored = scorer.score(query, group.type(), top);
        if (!scored.suggestible()) {
            return Optional.empty();
        }

        if (top.identity().mediaType() == TvdbMediaType.MOVIE) {
            return Optional.of(buildMovieMatch(group, scored, credentials));
        }
        return Optional.of(buildSeriesMatch(group, scored, credentials));
    }

    private TvdbBatchMatchResult.GroupMatch buildSeriesMatch(
            InventoryGroup group, TvdbCandidateScorer.ScoredCandidate scored, TvdbCredentials credentials) {
        TvdbCandidate candidate = scored.candidate();
        if (!scored.automatic()) {
            return new TvdbBatchMatchResult.GroupMatch(
                    group.seedName(),
                    candidate.identity(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(candidate),
                    Map.of(),
                    scored.score(),
                    false,
                    Optional.of("TVDB match suggested; user validation required."));
        }
        TvdbSeriesDetails details = tvdbClient.seriesDetails(candidate.identity(), credentials);
        TvdbSeriesMetadata metadata = new TvdbSeriesMetadata(
                details.identity().id(),
                details.identity().displayName(),
                details.airedEpisodes().stream().map(TvdbBatchMatchService::toEpisodeMetadata).toList());
        List<MediaMatchProposal> proposals = matchService.proposeSeriesMatches(group.items(), metadata);
        Map<Path, MediaMatchProposal> byPath = indexByPath(group.items(), proposals);
        return new TvdbBatchMatchResult.GroupMatch(
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

    private TvdbBatchMatchResult.GroupMatch buildMovieMatch(
            InventoryGroup group, TvdbCandidateScorer.ScoredCandidate scored, TvdbCredentials credentials) {
        TvdbCandidate candidate = scored.candidate();
        if (!scored.automatic()) {
            return new TvdbBatchMatchResult.GroupMatch(
                    group.seedName(),
                    candidate.identity(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(candidate),
                    Map.of(),
                    scored.score(),
                    false,
                    Optional.of("TVDB match suggested; user validation required."));
        }
        TvdbMovieDetails details = tvdbClient.movieDetails(candidate.identity(), credentials);
        TvdbMovieMetadata metadata = new TvdbMovieMetadata(
                details.identity().id(),
                details.identity().displayName(),
                details.releaseYear());
        List<MediaMatchProposal> proposals = matchService.proposeMovieMatches(group.items(), metadata);
        Map<Path, MediaMatchProposal> byPath = indexByPath(group.items(), proposals);
        return new TvdbBatchMatchResult.GroupMatch(
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

    private static List<TvdbCandidate> pickCandidatePool(InventoryGroupType type, TvdbSearchResult search) {
        return switch (type) {
            case LIKELY_SERIES -> search.seriesCandidates().isEmpty()
                    ? search.movieCandidates() : search.seriesCandidates();
            case LIKELY_MOVIE -> search.movieCandidates().isEmpty()
                    ? search.seriesCandidates() : search.movieCandidates();
            case UNKNOWN -> {
                List<TvdbCandidate> merged = new ArrayList<>(search.seriesCandidates());
                merged.addAll(search.movieCandidates());
                yield merged;
            }
            default -> List.of();
        };
    }

    private static TvdbEpisodeMetadata toEpisodeMetadata(TvdbEpisode episode) {
        return new TvdbEpisodeMetadata(
                episode.id(),
                episode.seasonNumber(),
                episode.episodeNumber(),
                episode.absoluteNumber(),
                episode.title(),
                episode.special());
    }
}
