package com.episort.fankai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.config.JanusConfiguration;
import com.episort.tmdb.OptionalDoubleScore;
import com.episort.tmdb.TmdbCandidate;
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
import com.episort.tmdb.TmdbWebUrl;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FankaiTmdbClientTest {
    private static final JanusConfiguration CREDENTIALS = new JanusConfiguration("key", Optional.empty());
    private static final FankaiSeries BLACK_LAGOON = new FankaiSeries(
            1, "Black Lagoon Henshū", List.of("Black Lagoon Henshu"), Optional.of("12598"),
            Optional.of("https://metadata.fankai.fr/series/1/image/poster"), Optional.of("Rock rejoint les pirates."),
            Optional.of(2019), Optional.of("Japon"));
    private static final FankaiSeries LASTMAN = new FankaiSeries(
            14, "Lastman Henshū", List.of(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    private static final FankaiSeries YABAI = new FankaiSeries(
            12, "Naruto Shippuden Yabai", List.of(), Optional.of("31910"),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    private static final TmdbCandidate TMDB_BLACK_LAGOON = new TmdbCandidate(
            new TmdbIdentity("12598", TmdbMediaType.SERIES, "Black Lagoon"),
            Optional.empty(), Optional.empty(), Optional.of(2006), Optional.empty(), Optional.empty(),
            OptionalDoubleScore.empty(), 0);

    private final StubTmdb tmdb = new StubTmdb();
    private final StubFankai fankai = new StubFankai();
    private final FankaiTmdbClient client = new FankaiTmdbClient(tmdb, fankai);

    @Test
    void offersTheKaiWhoseTitleResemblesTheQueryBeforeTmdbSeries() {
        tmdb.searchResult = new TmdbSearchResult(List.of(TMDB_BLACK_LAGOON), List.of());
        fankai.series = List.of(YABAI, LASTMAN, BLACK_LAGOON);

        TmdbSearchResult result = client.search(TmdbSearchCriteria.title("Black Lagoon Henshu"), CREDENTIALS);

        List<String> ids = result.seriesCandidates().stream().map(candidate -> candidate.identity().id()).toList();
        assertEquals(List.of("fankai-1", "12598"), ids);
        TmdbCandidate kai = result.seriesCandidates().getFirst();
        assertEquals("Black Lagoon Henshū", kai.identity().displayName());
        assertEquals(Optional.of("Fankai"), kai.network());
        assertEquals(Optional.of("Rock rejoint les pirates."), kai.frenchOverview());
        assertEquals(List.of("Black Lagoon Henshu"), kai.alternateTitles());
        assertEquals(Optional.empty(), TmdbWebUrl.forIdentity(kai.identity()));
        assertSame(CREDENTIALS, fankai.lastCredentials);
    }

    @Test
    void offersEveryKaiOfTheShowNamedByTheQuery() {
        fankai.series = List.of(YABAI, LASTMAN, BLACK_LAGOON);

        TmdbSearchResult result = client.search("Naruto Shippuden", CREDENTIALS);

        assertEquals("fankai-12", result.seriesCandidates().getFirst().identity().id());
        assertEquals(1, result.seriesCandidates().size());
    }

    @Test
    void leavesUnrelatedSearchesAsTmdbAnsweredThem() {
        tmdb.searchResult = new TmdbSearchResult(List.of(TMDB_BLACK_LAGOON), List.of());
        fankai.series = List.of(YABAI);

        TmdbSearchResult result = client.search("Black Lagoon", CREDENTIALS);

        assertSame(tmdb.searchResult, result);
    }

    @Test
    void listingTitlesEndWithKaiExactlyOnce() {
        assertEquals("Naruto Shippuden Yabai Kaï", FankaiTmdbClient.listingTitle(FankaiTmdbClient.identity(YABAI)));
        assertEquals("Black Clover Kaï", FankaiTmdbClient.listingTitle(
                new TmdbIdentity("fankai-90", TmdbMediaType.SERIES, "Black Clover Kaï")));
        assertEquals("Dragon Ball Kai", FankaiTmdbClient.listingTitle(
                new TmdbIdentity("fankai-7", TmdbMediaType.SERIES, "Dragon Ball Kai")));
        assertEquals("Black Lagoon", FankaiTmdbClient.listingTitle(TMDB_BLACK_LAGOON.identity()));
    }

    @Test
    void movieSearchesNeverReadTheCatalog() {
        tmdb.searchResult = new TmdbSearchResult(List.of(), List.of());
        fankai.failure = new FankaiException("must not be called");

        client.search(TmdbSearchCriteria.title("Black Lagoon Henshu", TmdbMediaType.MOVIE), CREDENTIALS);

        assertEquals(0, fankai.seriesCalls);
    }

    @Test
    void aTmdbIdAlsoFindsTheKaiBuiltOnThatSeries() {
        tmdb.searchResult = new TmdbSearchResult(List.of(TMDB_BLACK_LAGOON), List.of());
        fankai.series = List.of(YABAI, BLACK_LAGOON);

        TmdbSearchResult result = client.search(
                new TmdbSearchCriteria("", Optional.empty(), Optional.of("12598")), CREDENTIALS);

        assertEquals("fankai-1", result.seriesCandidates().getFirst().identity().id());
        assertEquals(2, result.seriesCandidates().size());
    }

    @Test
    void anUnreachableCatalogFallsBackToTmdb() {
        tmdb.searchResult = new TmdbSearchResult(List.of(TMDB_BLACK_LAGOON), List.of());
        fankai.failure = new FankaiException("down");

        TmdbSearchResult result = client.search(TmdbSearchCriteria.title("Black Lagoon Henshu"), CREDENTIALS);

        assertSame(tmdb.searchResult, result);
    }

    @Test
    void kaiEpisodesKeepFankaiNumberingAndCountAbsoluteAcrossSeasons() {
        fankai.series = List.of(YABAI);
        fankai.episodes = List.of(
                new FankaiEpisode(8, 2, 8, "Rituel"),
                new FankaiEpisode(1, 1, 1, "Le retour au pays"),
                new FankaiEpisode(90, 0, 1, "Film"),
                new FankaiEpisode(2, 1, 2, "Le réceptacle"));
        TmdbIdentity identity = FankaiTmdbClient.identity(YABAI);

        TmdbSeriesDetails details = client.seriesDetails(identity, TmdbEpisodeOrder.DVD, CREDENTIALS);

        assertEquals(identity, details.identity());
        assertEquals(List.of(TmdbEpisodeGroup.aired()), details.availableGroups());
        assertEquals(List.of(
                new TmdbEpisode("fankai-ep-90", 0, 1, Optional.empty(), "Film", true),
                new TmdbEpisode("fankai-ep-1", 1, 1, Optional.of(1), "Le retour au pays", false),
                new TmdbEpisode("fankai-ep-2", 1, 2, Optional.of(2), "Le réceptacle", false),
                new TmdbEpisode("fankai-ep-8", 2, 8, Optional.of(3), "Rituel", false)), details.airedEpisodes());
        assertEquals(12, fankai.lastEpisodesSeriesId);
        assertSame(CREDENTIALS, fankai.lastCredentials);
        assertEquals(0, tmdb.detailCalls);
    }

    @Test
    void kaiEpisodeFailureSurfacesAsARecoverableTmdbError() {
        fankai.failure = new FankaiException("down");

        TmdbException failure = assertThrows(TmdbException.class,
                () -> client.seriesDetails(FankaiTmdbClient.identity(YABAI), CREDENTIALS));

        assertTrue(failure.recoverable());
    }

    @Test
    void tmdbIdentitiesStillLoadFromTmdb() {
        client.seriesDetails(TMDB_BLACK_LAGOON.identity(), TmdbEpisodeGroup.aired(), CREDENTIALS);

        assertEquals(1, tmdb.detailCalls);
        assertFalse(FankaiTmdbClient.isFankai(TMDB_BLACK_LAGOON.identity()));
    }

    private static final class StubTmdb implements TmdbClient {
        TmdbSearchResult searchResult = new TmdbSearchResult(List.of(), List.of());
        int detailCalls;

        @Override
        public TmdbSearchResult search(String query, JanusConfiguration credentials) {
            return searchResult;
        }

        @Override
        public TmdbSearchResult search(TmdbSearchCriteria criteria, JanusConfiguration credentials) {
            return searchResult;
        }

        @Override
        public TmdbSeriesDetails seriesDetails(TmdbIdentity identity, JanusConfiguration credentials) {
            detailCalls++;
            return TmdbSeriesDetails.forGroup(identity, List.of(), TmdbEpisodeGroup.aired(), List.of());
        }

        @Override
        public TmdbSeriesDetails seriesDetails(
                TmdbIdentity identity, TmdbEpisodeGroup group, JanusConfiguration credentials) {
            return seriesDetails(identity, credentials);
        }

        @Override
        public TmdbMovieDetails movieDetails(TmdbIdentity identity, JanusConfiguration credentials) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StubFankai implements FankaiClient {
        List<FankaiSeries> series = new ArrayList<>();
        List<FankaiEpisode> episodes = new ArrayList<>();
        FankaiException failure;
        int seriesCalls;
        int lastEpisodesSeriesId = -1;
        JanusConfiguration lastCredentials;

        @Override
        public List<FankaiSeries> series(JanusConfiguration credentials) {
            seriesCalls++;
            lastCredentials = credentials;
            if (failure != null) throw failure;
            return series;
        }

        @Override
        public List<FankaiEpisode> episodes(int seriesId, JanusConfiguration credentials) {
            if (failure != null) throw failure;
            lastEpisodesSeriesId = seriesId;
            lastCredentials = credentials;
            return episodes;
        }
    }
}
