package com.episort.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.config.JanusConfiguration;
import com.episort.scanner.InventoryGroup;
import com.episort.scanner.InventoryGroupType;
import com.episort.scanner.InventoryItem;
import com.episort.scanner.InventoryItemType;
import com.episort.scanner.InventoryScanResult;
import com.episort.scanner.InventorySummary;
import com.episort.tmdb.OptionalDoubleScore;
import com.episort.tmdb.TmdbCandidate;
import com.episort.tmdb.TmdbClient;
import com.episort.tmdb.TmdbEpisode;
import com.episort.tmdb.TmdbEpisodeOrder;
import com.episort.tmdb.TmdbIdentity;
import com.episort.tmdb.TmdbMediaType;
import com.episort.tmdb.TmdbMovieDetails;
import com.episort.tmdb.TmdbSearchResult;
import com.episort.tmdb.TmdbSeriesDetails;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TmdbBatchMatchServiceTest {

    @Test
    void resolvesSeriesGroupWithEpisodeProposals() {
        StubClient client = new StubClient();
        client.searchResult = new TmdbSearchResult(
                List.of(new TmdbCandidate(
                        new TmdbIdentity("9001", TmdbMediaType.SERIES, "Haikyu!!"),
                        Optional.empty(),
                        Optional.empty(), Optional.of(2014), Optional.empty(), Optional.empty(),
                        OptionalDoubleScore.empty(), 0)),
                List.of());
        client.seriesDetails = new TmdbSeriesDetails(
                new TmdbIdentity("9001", TmdbMediaType.SERIES, "Haikyu!!"),
                Set.of(TmdbEpisodeOrder.AIRED),
                List.of(new TmdbEpisode("e1", 1, 1, Optional.of(1), "First Match", false)),
                List.of(),
                List.of());

        InventoryItem item = sample("Haikyu.S01E01.mkv");
        InventoryGroup group = new InventoryGroup(
                InventoryGroupType.LIKELY_SERIES, "Haikyu", List.of(item), false);
        InventoryScanResult scan = new InventoryScanResult(
                List.of(item), List.of(group),
                new InventorySummary(1, 0, 0, 0, 1, 0, 0, false, false));

        TmdbBatchMatchService service = new TmdbBatchMatchService(client);
        TmdbBatchMatchResult result = service.run(scan, credentials(), (a, b) -> {});

        TmdbBatchMatchResult.GroupMatch match = result.matchesBySeed().get("Haikyu");
        assertNotNull(match);
        assertEquals("9001", match.identity().id());
        assertTrue(match.automatic());
        assertTrue(match.series().isPresent());
        assertEquals(1, match.proposalsByPath().get(item.sourcePath()).seasonNumber().orElse(0));
    }

    @Test
    void resolvesMovieGroup() {
        StubClient client = new StubClient();
        client.searchResult = new TmdbSearchResult(
                List.of(),
                List.of(new TmdbCandidate(
                        new TmdbIdentity("42", TmdbMediaType.MOVIE, "Inception"),
                        Optional.empty(),
                        Optional.empty(), Optional.of(2010), Optional.empty(), Optional.empty(),
                        OptionalDoubleScore.empty(), 0)));
        client.movieDetails = new TmdbMovieDetails(
                new TmdbIdentity("42", TmdbMediaType.MOVIE, "Inception"), Optional.of(2010));

        InventoryItem item = sample("Inception.2010.1080p.mkv");
        InventoryGroup group = new InventoryGroup(
                InventoryGroupType.LIKELY_MOVIE, "Inception", List.of(item), false);
        InventoryScanResult scan = new InventoryScanResult(
                List.of(item), List.of(group),
                new InventorySummary(1, 0, 0, 0, 0, 1, 0, false, false));

        TmdbBatchMatchResult result = new TmdbBatchMatchService(client)
                .run(scan, credentials(), (a, b) -> {});

        TmdbBatchMatchResult.GroupMatch match = result.matchesBySeed().get("Inception");
        assertNotNull(match);
        assertTrue(match.movie().isPresent());
        assertEquals("42", match.identity().id());
    }

    @Test
    void normalizesHaikyuVariantsForAutomaticSeriesSuggestion() {
        StubClient client = new StubClient();
        client.searchResult = new TmdbSearchResult(
                List.of(
                        new TmdbCandidate(
                                new TmdbIdentity("278157", TmdbMediaType.SERIES, "Haikyu!!"),
                                Optional.empty(), Optional.empty(), Optional.of(2014),
                                Optional.empty(), Optional.empty(), OptionalDoubleScore.empty(), 0),
                        new TmdbCandidate(
                                new TmdbIdentity("other", TmdbMediaType.SERIES, "Hyper Projection Play Haikyuu!!"),
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty(), OptionalDoubleScore.empty(), 1)),
                List.of());
        client.seriesDetails = new TmdbSeriesDetails(
                new TmdbIdentity("278157", TmdbMediaType.SERIES, "Haikyu!!"),
                Set.of(TmdbEpisodeOrder.AIRED),
                List.of(new TmdbEpisode("e8", 1, 8, Optional.of(8), "He Who Is Called \"Ace\"", false)),
                List.of(), List.of());

        InventoryItem item = sample("sgi-hkyu08.1080p.multi.mkv");
        InventoryScanResult scan = new InventoryScanResult(
                List.of(item),
                List.of(new InventoryGroup(InventoryGroupType.LIKELY_SERIES, "Haikyuu", List.of(item), false)),
                new InventorySummary(1, 0, 0, 0, 1, 0, 0, false, false));

        TmdbBatchMatchResult result = new TmdbBatchMatchService(client).run(scan, credentials(), (a, b) -> {});

        TmdbBatchMatchResult.GroupMatch match = result.matchesBySeed().get("Haikyuu");
        assertNotNull(match);
        assertEquals("278157", match.identity().id());
        assertTrue(match.automatic());
        assertEquals(8, match.proposalsByPath().get(item.sourcePath()).episodeNumber().orElse(0));
    }

    @Test
    void reusesOneSearchForEquivalentGroupQueriesButKeepsPerFileEpisodes() {
        StubClient client = new StubClient();
        client.searchResult = new TmdbSearchResult(
                List.of(new TmdbCandidate(
                        new TmdbIdentity("278157", TmdbMediaType.SERIES, "Haikyu!!"),
                        Optional.empty(), Optional.empty(), Optional.of(2014),
                        Optional.empty(), Optional.empty(), OptionalDoubleScore.empty(), 0)),
                List.of());
        client.seriesDetails = new TmdbSeriesDetails(
                new TmdbIdentity("278157", TmdbMediaType.SERIES, "Haikyu!!"),
                Set.of(TmdbEpisodeOrder.AIRED),
                List.of(
                        new TmdbEpisode("e8", 1, 8, Optional.of(8), "Eight", false),
                        new TmdbEpisode("e9", 1, 9, Optional.of(9), "Nine", false)),
                List.of(), List.of());
        InventoryItem first = sample("sgi-hkyu08.1080p.multi.mkv");
        InventoryItem second = sample("sgi-hkyu09.720p.multi.mkv");
        InventoryScanResult scan = new InventoryScanResult(
                List.of(first, second),
                List.of(
                        new InventoryGroup(InventoryGroupType.LIKELY_SERIES, "Haikyu 1080p MULTi", List.of(first), false),
                        new InventoryGroup(InventoryGroupType.LIKELY_SERIES, "Haikyu 720p multi", List.of(second), false)),
                new InventorySummary(2, 0, 0, 0, 2, 0, 0, false, false));

        TmdbBatchMatchResult result = new TmdbBatchMatchService(client).run(scan, credentials(), (a, b) -> {});

        assertEquals(1, client.searchCalls);
        assertEquals(8, result.matchesBySeed().get("Haikyu 1080p MULTi")
                .proposalsByPath().get(first.sourcePath()).episodeNumber().orElse(0));
        assertEquals(9, result.matchesBySeed().get("Haikyu 720p multi")
                .proposalsByPath().get(second.sourcePath()).episodeNumber().orElse(0));
    }

    @Test
    void ambiguousCandidateIsSuggestedWithoutMetadataApplication() {
        StubClient client = new StubClient();
        client.searchResult = new TmdbSearchResult(
                List.of(new TmdbCandidate(
                        new TmdbIdentity("111", TmdbMediaType.SERIES, "Haikyu!! Figure Animation"),
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), OptionalDoubleScore.empty(), 0)),
                List.of());

        InventoryItem item = sample("Haikyu.S01E01.mkv");
        InventoryScanResult scan = new InventoryScanResult(
                List.of(item),
                List.of(new InventoryGroup(InventoryGroupType.LIKELY_SERIES, "Haikyu", List.of(item), false)),
                new InventorySummary(1, 0, 0, 0, 1, 0, 0, false, false));

        TmdbBatchMatchResult result = new TmdbBatchMatchService(client).run(scan, credentials(), (a, b) -> {});

        TmdbBatchMatchResult.GroupMatch match = result.matchesBySeed().get("Haikyu");
        assertNotNull(match);
        assertFalse(match.automatic());
        assertTrue(match.series().isEmpty());
        assertTrue(match.proposalsByPath().isEmpty());
    }

    @Test
    void appliesExactTitleEvenWhenTmdbRanksAnotherRecordFirst() {
        StubClient client = new StubClient();
        client.searchResult = new TmdbSearchResult(
                List.of(
                        new TmdbCandidate(
                                new TmdbIdentity("mini", TmdbMediaType.SERIES, "Breaking Bad: Original Minisodes"),
                                Optional.empty(), Optional.empty(), Optional.of(2009),
                                Optional.empty(), Optional.empty(), OptionalDoubleScore.empty(), 0),
                        new TmdbCandidate(
                                new TmdbIdentity("81189", TmdbMediaType.SERIES, "Breaking Bad"),
                                Optional.empty(), Optional.empty(), Optional.of(2008),
                                Optional.empty(), Optional.empty(), OptionalDoubleScore.empty(), 1)),
                List.of());
        client.seriesDetails = new TmdbSeriesDetails(
                new TmdbIdentity("81189", TmdbMediaType.SERIES, "Breaking Bad"),
                Set.of(TmdbEpisodeOrder.AIRED),
                List.of(new TmdbEpisode("e1", 1, 1, Optional.of(1), "Pilot", false)),
                List.of(), List.of());

        InventoryItem item = sample("Breaking.Bad.S01E01.1080p.mkv");
        InventoryScanResult scan = new InventoryScanResult(
                List.of(item),
                List.of(new InventoryGroup(
                        InventoryGroupType.LIKELY_SERIES, "Breaking Bad S01 1080p", List.of(item), false)),
                new InventorySummary(1, 0, 0, 0, 1, 0, 0, false, false));

        TmdbBatchMatchResult result = new TmdbBatchMatchService(client).run(scan, credentials(), (a, b) -> {});

        TmdbBatchMatchResult.GroupMatch match = result.matchesBySeed().get("Breaking Bad S01 1080p");
        assertNotNull(match);
        assertEquals("81189", match.identity().id());
        assertTrue(match.automatic());
        assertEquals(1, match.proposalsByPath().get(item.sourcePath()).episodeNumber().orElse(0));
    }

    @Test
    void recordsErrorWhenNoCandidate() {
        StubClient client = new StubClient();
        client.searchResult = new TmdbSearchResult(List.of(), List.of());

        InventoryItem item = sample("Mystery.S01E01.mkv");
        InventoryGroup group = new InventoryGroup(
                InventoryGroupType.LIKELY_SERIES, "Mystery", List.of(item), false);
        InventoryScanResult scan = new InventoryScanResult(
                List.of(item), List.of(group),
                new InventorySummary(1, 0, 0, 0, 1, 0, 0, false, false));

        TmdbBatchMatchResult result = new TmdbBatchMatchService(client)
                .run(scan, credentials(), (a, b) -> {});

        assertTrue(result.matchesBySeed().isEmpty());
        assertEquals(1, result.errors().size());
    }

    private static InventoryItem sample(String filename) {
        Path path = Paths.get("/tmp", filename);
        return new InventoryItem(path, filename, extension(filename), path.getParent(),
                InventoryItemType.SUPPORTED_VIDEO, true);
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }

    private static JanusConfiguration credentials() {
        return new JanusConfiguration("key", Optional.empty());
    }

    private static final class StubClient implements TmdbClient {
        TmdbSearchResult searchResult;
        TmdbSeriesDetails seriesDetails;
        TmdbMovieDetails movieDetails;
        int searchCalls;

        @Override
        public TmdbSearchResult search(String query, JanusConfiguration credentials) {
            searchCalls++;
            return searchResult;
        }

        @Override
        public TmdbSeriesDetails seriesDetails(TmdbIdentity identity, JanusConfiguration credentials) {
            return seriesDetails;
        }

        @Override
        public TmdbMovieDetails movieDetails(TmdbIdentity identity, JanusConfiguration credentials) {
            return movieDetails;
        }
    }
}
