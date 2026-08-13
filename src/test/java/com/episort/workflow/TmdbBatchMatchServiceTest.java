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
import com.episort.tmdb.TmdbSearchCriteria;
import com.episort.tmdb.TmdbSearchResult;
import com.episort.tmdb.TmdbSeriesDetails;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
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

        assertEquals(1, client.searchCalls.get());
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

    @Test
    void resolvesAMovieFromTheSearchAnswerWithoutAskingForItsDetailRecord() {
        StubClient client = new StubClient();
        client.searchResult = new TmdbSearchResult(
                List.of(),
                List.of(new TmdbCandidate(
                        new TmdbIdentity("42", TmdbMediaType.MOVIE, "Inception"),
                        Optional.empty(),
                        Optional.empty(), Optional.of(2010), Optional.empty(), Optional.empty(),
                        OptionalDoubleScore.empty(), 0)));

        InventoryItem item = sample("Inception.2010.1080p.mkv");
        InventoryScanResult scan = new InventoryScanResult(
                List.of(item),
                List.of(new InventoryGroup(InventoryGroupType.LIKELY_MOVIE, "Inception", List.of(item), false)),
                new InventorySummary(1, 0, 0, 0, 0, 1, 0, false, false));

        TmdbBatchMatchResult result = new TmdbBatchMatchService(client)
                .run(scan, credentials(), (a, b) -> {});

        TmdbBatchMatchResult.GroupMatch match = result.matchesBySeed().get("Inception");
        assertEquals(0, client.movieDetailCalls.get());
        assertEquals(1, client.searchCalls.get());
        assertEquals(Optional.of(2010), match.movie().orElseThrow().releaseYear());
        assertEquals("Inception", match.identity().displayName());
    }

    @Test
    void queriesOnlyTheIndexTheGroupAlreadyPointsAt() {
        StubClient client = new StubClient();
        client.searchResult = new TmdbSearchResult(
                List.of(new TmdbCandidate(
                        new TmdbIdentity("9001", TmdbMediaType.SERIES, "Haikyu!!"),
                        Optional.empty(), Optional.empty(), Optional.of(2014),
                        Optional.empty(), Optional.empty(), OptionalDoubleScore.empty(), 0)),
                List.of());
        client.seriesDetails = new TmdbSeriesDetails(
                new TmdbIdentity("9001", TmdbMediaType.SERIES, "Haikyu!!"),
                Set.of(TmdbEpisodeOrder.AIRED),
                List.of(new TmdbEpisode("e1", 1, 1, Optional.of(1), "First Match", false)),
                List.of(), List.of());

        InventoryItem item = sample("Haikyu.S01E01.mkv");
        InventoryScanResult scan = new InventoryScanResult(
                List.of(item),
                List.of(new InventoryGroup(InventoryGroupType.LIKELY_SERIES, "Haikyu", List.of(item), false)),
                new InventorySummary(1, 0, 0, 0, 1, 0, 0, false, false));

        new TmdbBatchMatchService(client).run(scan, credentials(), (a, b) -> {});

        assertEquals(List.of(Optional.of(TmdbMediaType.SERIES)), client.searchedIndexes);
    }

    @Test
    void loadsEpisodesOnceWhenSeveralGroupsLandOnTheSameShow() {
        StubClient client = new StubClient();
        client.searchResult = new TmdbSearchResult(
                List.of(new TmdbCandidate(
                        new TmdbIdentity("81189", TmdbMediaType.SERIES, "Breaking Bad"),
                        Optional.empty(), Optional.empty(), Optional.of(2008),
                        Optional.empty(), Optional.empty(), OptionalDoubleScore.empty(), 0)),
                List.of());
        client.seriesDetails = new TmdbSeriesDetails(
                new TmdbIdentity("81189", TmdbMediaType.SERIES, "Breaking Bad"),
                Set.of(TmdbEpisodeOrder.AIRED),
                List.of(
                        new TmdbEpisode("e1", 1, 1, Optional.of(1), "Pilot", false),
                        new TmdbEpisode("e2", 2, 1, Optional.of(2), "Seven Thirty-Seven", false)),
                List.of(), List.of());

        InventoryItem first = sample("Breaking.Bad.S01E01.mkv");
        InventoryItem second = sample("Breaking.Bad.S02E01.mkv");
        InventoryScanResult scan = new InventoryScanResult(
                List.of(first, second),
                List.of(
                        new InventoryGroup(InventoryGroupType.LIKELY_SERIES, "Breaking Bad S01", List.of(first), false),
                        new InventoryGroup(InventoryGroupType.LIKELY_SERIES, "Breaking Bad S02", List.of(second), false)),
                new InventorySummary(2, 0, 0, 0, 2, 0, 0, false, false));

        TmdbBatchMatchResult result = new TmdbBatchMatchService(client).run(scan, credentials(), (a, b) -> {});

        assertEquals(1, client.seriesDetailCalls.get());
        assertEquals(2, result.matchesBySeed().size());
        assertEquals(1, result.matchesBySeed().get("Breaking Bad S01")
                .proposalsByPath().get(first.sourcePath()).seasonNumber().orElse(0));
        assertEquals(2, result.matchesBySeed().get("Breaking Bad S02")
                .proposalsByPath().get(second.sourcePath()).seasonNumber().orElse(0));
    }

    @Test
    void keepsScanOrderAndCountsEveryGroupWhenTheyResolveOutOfOrder() {
        StubClient client = new StubClient();
        client.searchResult = new TmdbSearchResult(List.of(), List.of());
        // The first group answers last, so anything ordered by completion would
        // report the groups the wrong way round.
        client.beforeSearch = query -> {
            if (query.startsWith("Alpha")) {
                try {
                    Thread.sleep(120);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        };

        List<InventoryItem> items = new ArrayList<>();
        List<InventoryGroup> groups = new ArrayList<>();
        for (String seed : List.of("Alpha", "Bravo", "Charlie", "Delta")) {
            InventoryItem item = sample(seed + ".S01E01.mkv");
            items.add(item);
            groups.add(new InventoryGroup(InventoryGroupType.LIKELY_SERIES, seed, List.of(item), false));
        }
        InventoryScanResult scan = new InventoryScanResult(
                items, groups, new InventorySummary(4, 0, 0, 0, 4, 0, 0, false, false));

        List<Integer> progress = Collections.synchronizedList(new ArrayList<>());
        TmdbBatchMatchResult result = new TmdbBatchMatchService(client)
                .run(scan, credentials(), (done, total) -> progress.add(done));

        assertEquals(List.of(0, 1, 2, 3, 4), progress);
        assertEquals(4, result.errors().size());
        assertTrue(result.errors().get(0).startsWith("Alpha"), result.errors().toString());
        assertTrue(result.errors().get(3).startsWith("Delta"), result.errors().toString());
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
        final AtomicInteger searchCalls = new AtomicInteger();
        final AtomicInteger seriesDetailCalls = new AtomicInteger();
        final AtomicInteger movieDetailCalls = new AtomicInteger();
        final List<Optional<TmdbMediaType>> searchedIndexes = Collections.synchronizedList(new ArrayList<>());
        volatile Consumer<String> beforeSearch = query -> {};

        @Override
        public TmdbSearchResult search(String query, JanusConfiguration credentials) {
            return search(TmdbSearchCriteria.title(query), credentials);
        }

        @Override
        public TmdbSearchResult search(TmdbSearchCriteria criteria, JanusConfiguration credentials) {
            searchCalls.incrementAndGet();
            searchedIndexes.add(criteria.mediaType());
            beforeSearch.accept(criteria.query());
            return searchResult;
        }

        @Override
        public TmdbSeriesDetails seriesDetails(TmdbIdentity identity, JanusConfiguration credentials) {
            seriesDetailCalls.incrementAndGet();
            return seriesDetails;
        }

        @Override
        public TmdbMovieDetails movieDetails(TmdbIdentity identity, JanusConfiguration credentials) {
            movieDetailCalls.incrementAndGet();
            return movieDetails;
        }
    }
}
