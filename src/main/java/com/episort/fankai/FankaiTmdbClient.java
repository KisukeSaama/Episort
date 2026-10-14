package com.episort.fankai;

import com.episort.config.JanusConfiguration;
import com.episort.scanner.InventoryGroupType;
import com.episort.tmdb.OptionalDoubleScore;
import com.episort.tmdb.TmdbCandidate;
import com.episort.tmdb.TmdbCandidateScorer;
import com.episort.tmdb.TmdbClient;
import com.episort.tmdb.TmdbEpisode;
import com.episort.tmdb.TmdbEpisodeGroup;
import com.episort.tmdb.TmdbEpisodeOrder;
import com.episort.tmdb.TmdbException;
import com.episort.tmdb.TmdbIdentity;
import com.episort.tmdb.TmdbMediaType;
import com.episort.tmdb.TmdbMovieDetails;
import com.episort.tmdb.TmdbSearchCriteria;
import com.episort.tmdb.TmdbSearchResult;
import com.episort.tmdb.TmdbSeriesDetails;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Offers the kai of the Fankai catalog next to TMDB's own series, the way
 * FanKarr does, so that their files end up named
 * {@code Kai Title/Season 02/Kai Title - S02E08 - Title.mkv}: the show title,
 * season and episode numbers the Plex Fankai agent
 * ({@code https://metadata.fankai.fr/plex}) matches against.
 *
 * <p>A kai only appears when its title resembles the query, so ordinary shows
 * keep resolving to TMDB: "Black Lagoon" stays the TMDB series, "Black Lagoon
 * Henshū" becomes the kai. A kai identity carries a {@code fankai-} id that is
 * never numeric, so no TMDB link is offered for it.
 *
 * <p>The catalog is additive: when it cannot be reached, searches return what
 * TMDB returned.
 */
public final class FankaiTmdbClient implements TmdbClient {
    static final String SERIES_ID_PREFIX = "fankai-";
    static final String EPISODE_ID_PREFIX = "fankai-ep-";
    private static final String SOURCE_LABEL = "Fankai";
    private static final String KAI_SUFFIX = "Kaï";
    /**
     * Above the scorer's suggestion floor: kai titles share their suffix
     * (Henshū, Kaï, Yabai…), which alone lifts an unrelated kai to about 0.70
     * against any other kai's name. A title contained in the query, or the
     * other way around, scores 0.79 and up.
     */
    static final double KAI_THRESHOLD = 0.75;
    private static final System.Logger LOG = System.getLogger(FankaiTmdbClient.class.getName());

    private final TmdbClient delegate;
    private final FankaiClient fankai;
    private final TmdbCandidateScorer scorer = new TmdbCandidateScorer();

    public FankaiTmdbClient(TmdbClient delegate, FankaiClient fankai) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.fankai = Objects.requireNonNull(fankai, "fankai");
    }

    public static boolean isFankai(TmdbIdentity identity) {
        return identity != null && identity.id().startsWith(SERIES_ID_PREFIX);
    }

    /**
     * The series title as match lists show it: a kai ends with "Kaï" so it
     * reads apart from the show it was cut from, unless its own title already
     * says so ("Black Clover Kaï", "Dragon Ball Kai"). File names keep the
     * Fankai title as is.
     */
    public static String listingTitle(TmdbIdentity identity) {
        String title = identity.displayName();
        if (!isFankai(identity)) return title;
        String folded = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .strip();
        return folded.equals("kai") || folded.endsWith(" kai") ? title : title + " " + KAI_SUFFIX;
    }

    @Override
    public TmdbSearchResult search(String query, JanusConfiguration credentials) {
        return withKai(delegate.search(query, credentials), TmdbSearchCriteria.title(query), credentials);
    }

    @Override
    public TmdbSearchResult search(TmdbSearchCriteria criteria, JanusConfiguration credentials) {
        return withKai(delegate.search(criteria, credentials), criteria, credentials);
    }

    @Override
    public TmdbSeriesDetails seriesDetails(TmdbIdentity identity, JanusConfiguration credentials) {
        return isFankai(identity) ? kaiDetails(identity, credentials) : delegate.seriesDetails(identity, credentials);
    }

    /** A kai has a single order, Fankai's own; any requested order loads it. */
    @Override
    public TmdbSeriesDetails seriesDetails(
            TmdbIdentity identity, TmdbEpisodeOrder order, JanusConfiguration credentials) {
        return isFankai(identity) ? kaiDetails(identity, credentials) : delegate.seriesDetails(identity, order, credentials);
    }

    @Override
    public TmdbSeriesDetails seriesDetails(
            TmdbIdentity identity, TmdbEpisodeGroup group, JanusConfiguration credentials) {
        return isFankai(identity) ? kaiDetails(identity, credentials) : delegate.seriesDetails(identity, group, credentials);
    }

    @Override
    public TmdbMovieDetails movieDetails(TmdbIdentity identity, JanusConfiguration credentials) {
        return delegate.movieDetails(identity, credentials);
    }

    private TmdbSearchResult withKai(
            TmdbSearchResult tmdb, TmdbSearchCriteria criteria, JanusConfiguration credentials) {
        if (criteria.mediaType().filter(TmdbMediaType.MOVIE::equals).isPresent()) return tmdb;
        List<TmdbCandidate> kai;
        try {
            kai = matchingKai(criteria, credentials);
        } catch (FankaiException unreachable) {
            LOG.log(System.Logger.Level.WARNING, "Fankai catalog skipped: " + unreachable.getMessage());
            return tmdb;
        }
        if (kai.isEmpty()) return tmdb;
        List<TmdbCandidate> series = new ArrayList<>(kai);
        series.addAll(tmdb.seriesCandidates());
        return new TmdbSearchResult(series, tmdb.movieCandidates());
    }

    private List<TmdbCandidate> matchingKai(TmdbSearchCriteria criteria, JanusConfiguration credentials) {
        if (criteria.tmdbId().isPresent()) {
            String tmdbId = criteria.tmdbId().get();
            return fankai.series(credentials).stream()
                    .filter(series -> series.tmdbId().filter(tmdbId::equals).isPresent())
                    .map(FankaiTmdbClient::candidate)
                    .toList();
        }
        String query = criteria.query() + criteria.year().map(year -> " " + year).orElse("");
        return fankai.series(credentials).stream()
                .map(FankaiTmdbClient::candidate)
                .map(candidate -> scorer.score(query, InventoryGroupType.LIKELY_SERIES, candidate))
                .filter(scored -> scored.score() >= KAI_THRESHOLD)
                .sorted(Comparator.comparingDouble(TmdbCandidateScorer.ScoredCandidate::score).reversed())
                .map(TmdbCandidateScorer.ScoredCandidate::candidate)
                .toList();
    }

    private static TmdbCandidate candidate(FankaiSeries series) {
        return new TmdbCandidate(
                identity(series),
                series.posterUrl(),
                series.plot(),
                Optional.empty(),
                series.plot(),
                series.year(),
                series.country(),
                Optional.of(SOURCE_LABEL),
                OptionalDoubleScore.empty(),
                0,
                series.alternateTitles());
    }

    static TmdbIdentity identity(FankaiSeries series) {
        return new TmdbIdentity(SERIES_ID_PREFIX + series.id(), TmdbMediaType.SERIES, series.title());
    }

    private TmdbSeriesDetails kaiDetails(TmdbIdentity identity, JanusConfiguration credentials) {
        int seriesId = seriesId(identity);
        List<FankaiEpisode> episodes;
        try {
            episodes = fankai.episodes(seriesId, credentials);
        } catch (FankaiException failure) {
            throw TmdbException.recoverable(
                    "FANKAI_UNAVAILABLE",
                    "Fankai episodes for \"" + identity.displayName() + "\" could not be loaded.",
                    failure.getMessage());
        }
        return TmdbSeriesDetails.forGroup(
                identity, List.of(TmdbEpisodeGroup.aired()), TmdbEpisodeGroup.aired(), toTmdbEpisodes(episodes));
    }

    /**
     * Release files of a kai number episodes from the first one onwards
     * ("Naruto Shippuden Yabaï 08"), so every regular episode also carries
     * its rank across seasons as absolute number. Season 0 holds specials and
     * is left out of that count.
     */
    static List<TmdbEpisode> toTmdbEpisodes(List<FankaiEpisode> episodes) {
        List<FankaiEpisode> ordered = episodes.stream()
                .sorted(Comparator.comparingInt(FankaiEpisode::seasonNumber)
                        .thenComparingInt(FankaiEpisode::episodeNumber))
                .toList();
        List<TmdbEpisode> mapped = new ArrayList<>(ordered.size());
        int absolute = 0;
        for (FankaiEpisode episode : ordered) {
            boolean special = episode.seasonNumber() == 0;
            mapped.add(new TmdbEpisode(
                    EPISODE_ID_PREFIX + episode.id(),
                    episode.seasonNumber(),
                    episode.episodeNumber(),
                    special ? Optional.empty() : Optional.of(++absolute),
                    episode.title(),
                    special));
        }
        return List.copyOf(mapped);
    }

    private static int seriesId(TmdbIdentity identity) {
        try {
            return Integer.parseInt(identity.id().substring(SERIES_ID_PREFIX.length()));
        } catch (NumberFormatException malformed) {
            throw new IllegalArgumentException("Not a Fankai identity: " + identity.id(), malformed);
        }
    }
}
