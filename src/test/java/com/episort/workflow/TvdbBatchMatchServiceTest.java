package com.episort.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.config.TvdbCredentials;
import com.episort.scanner.InventoryGroup;
import com.episort.scanner.InventoryGroupType;
import com.episort.scanner.InventoryItem;
import com.episort.scanner.InventoryItemType;
import com.episort.scanner.InventoryScanResult;
import com.episort.scanner.InventorySummary;
import com.episort.tvdb.OptionalDoubleScore;
import com.episort.tvdb.TvdbCandidate;
import com.episort.tvdb.TvdbClient;
import com.episort.tvdb.TvdbEpisode;
import com.episort.tvdb.TvdbEpisodeOrder;
import com.episort.tvdb.TvdbIdentity;
import com.episort.tvdb.TvdbMediaType;
import com.episort.tvdb.TvdbMovieDetails;
import com.episort.tvdb.TvdbSearchResult;
import com.episort.tvdb.TvdbSeriesDetails;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TvdbBatchMatchServiceTest {

    @Test
    void resolvesSeriesGroupWithEpisodeProposals() {
        StubClient client = new StubClient();
        client.searchResult = new TvdbSearchResult(
                List.of(new TvdbCandidate(
                        new TvdbIdentity("9001", TvdbMediaType.SERIES, "Haikyu!!"),
                        Optional.empty(),
                        Optional.empty(), Optional.of(2014), Optional.empty(), Optional.empty(),
                        OptionalDoubleScore.empty(), 0)),
                List.of());
        client.seriesDetails = new TvdbSeriesDetails(
                new TvdbIdentity("9001", TvdbMediaType.SERIES, "Haikyu!!"),
                Set.of(TvdbEpisodeOrder.AIRED),
                List.of(new TvdbEpisode("e1", 1, 1, Optional.of(1), "First Match", false)),
                List.of(),
                List.of());

        InventoryItem item = sample("Haikyu.S01E01.mkv");
        InventoryGroup group = new InventoryGroup(
                InventoryGroupType.LIKELY_SERIES, "Haikyu", List.of(item), false);
        InventoryScanResult scan = new InventoryScanResult(
                List.of(item), List.of(group),
                new InventorySummary(1, 0, 0, 0, 1, 0, 0, false, false));

        TvdbBatchMatchService service = new TvdbBatchMatchService(client);
        TvdbBatchMatchResult result = service.run(scan, credentials(), (a, b) -> {});

        TvdbBatchMatchResult.GroupMatch match = result.matchesBySeed().get("Haikyu");
        assertNotNull(match);
        assertEquals("9001", match.identity().id());
        assertTrue(match.automatic());
        assertTrue(match.series().isPresent());
        assertEquals(1, match.proposalsByPath().get(item.sourcePath()).seasonNumber().orElse(0));
    }

    @Test
    void resolvesMovieGroup() {
        StubClient client = new StubClient();
        client.searchResult = new TvdbSearchResult(
                List.of(),
                List.of(new TvdbCandidate(
                        new TvdbIdentity("42", TvdbMediaType.MOVIE, "Inception"),
                        Optional.empty(),
                        Optional.empty(), Optional.of(2010), Optional.empty(), Optional.empty(),
                        OptionalDoubleScore.empty(), 0)));
        client.movieDetails = new TvdbMovieDetails(
                new TvdbIdentity("42", TvdbMediaType.MOVIE, "Inception"), Optional.of(2010));

        InventoryItem item = sample("Inception.2010.1080p.mkv");
        InventoryGroup group = new InventoryGroup(
                InventoryGroupType.LIKELY_MOVIE, "Inception", List.of(item), false);
        InventoryScanResult scan = new InventoryScanResult(
                List.of(item), List.of(group),
                new InventorySummary(1, 0, 0, 0, 0, 1, 0, false, false));

        TvdbBatchMatchResult result = new TvdbBatchMatchService(client)
                .run(scan, credentials(), (a, b) -> {});

        TvdbBatchMatchResult.GroupMatch match = result.matchesBySeed().get("Inception");
        assertNotNull(match);
        assertTrue(match.movie().isPresent());
        assertEquals("42", match.identity().id());
    }

    @Test
    void normalizesHaikyuVariantsForAutomaticSeriesSuggestion() {
        StubClient client = new StubClient();
        client.searchResult = new TvdbSearchResult(
                List.of(
                        new TvdbCandidate(
                                new TvdbIdentity("278157", TvdbMediaType.SERIES, "Haikyu!!"),
                                Optional.empty(), Optional.empty(), Optional.of(2014),
                                Optional.empty(), Optional.empty(), OptionalDoubleScore.empty(), 0),
                        new TvdbCandidate(
                                new TvdbIdentity("other", TvdbMediaType.SERIES, "Hyper Projection Play Haikyuu!!"),
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty(), OptionalDoubleScore.empty(), 1)),
                List.of());
        client.seriesDetails = new TvdbSeriesDetails(
                new TvdbIdentity("278157", TvdbMediaType.SERIES, "Haikyu!!"),
                Set.of(TvdbEpisodeOrder.AIRED),
                List.of(new TvdbEpisode("e8", 1, 8, Optional.of(8), "He Who Is Called \"Ace\"", false)),
                List.of(), List.of());

        InventoryItem item = sample("sgi-hkyu08.1080p.multi.mkv");
        InventoryScanResult scan = new InventoryScanResult(
                List.of(item),
                List.of(new InventoryGroup(InventoryGroupType.LIKELY_SERIES, "Haikyuu", List.of(item), false)),
                new InventorySummary(1, 0, 0, 0, 1, 0, 0, false, false));

        TvdbBatchMatchResult result = new TvdbBatchMatchService(client).run(scan, credentials(), (a, b) -> {});

        TvdbBatchMatchResult.GroupMatch match = result.matchesBySeed().get("Haikyuu");
        assertNotNull(match);
        assertEquals("278157", match.identity().id());
        assertTrue(match.automatic());
        assertEquals(8, match.proposalsByPath().get(item.sourcePath()).episodeNumber().orElse(0));
    }

    @Test
    void reusesOneSearchForEquivalentGroupQueriesButKeepsPerFileEpisodes() {
        StubClient client = new StubClient();
        client.searchResult = new TvdbSearchResult(
                List.of(new TvdbCandidate(
                        new TvdbIdentity("278157", TvdbMediaType.SERIES, "Haikyu!!"),
                        Optional.empty(), Optional.empty(), Optional.of(2014),
                        Optional.empty(), Optional.empty(), OptionalDoubleScore.empty(), 0)),
                List.of());
        client.seriesDetails = new TvdbSeriesDetails(
                new TvdbIdentity("278157", TvdbMediaType.SERIES, "Haikyu!!"),
                Set.of(TvdbEpisodeOrder.AIRED),
                List.of(
                        new TvdbEpisode("e8", 1, 8, Optional.of(8), "Eight", false),
                        new TvdbEpisode("e9", 1, 9, Optional.of(9), "Nine", false)),
                List.of(), List.of());
        InventoryItem first = sample("sgi-hkyu08.1080p.multi.mkv");
        InventoryItem second = sample("sgi-hkyu09.720p.multi.mkv");
        InventoryScanResult scan = new InventoryScanResult(
                List.of(first, second),
                List.of(
                        new InventoryGroup(InventoryGroupType.LIKELY_SERIES, "Haikyu 1080p MULTi", List.of(first), false),
                        new InventoryGroup(InventoryGroupType.LIKELY_SERIES, "Haikyu 720p multi", List.of(second), false)),
                new InventorySummary(2, 0, 0, 0, 2, 0, 0, false, false));

        TvdbBatchMatchResult result = new TvdbBatchMatchService(client).run(scan, credentials(), (a, b) -> {});

        assertEquals(1, client.searchCalls);
        assertEquals(8, result.matchesBySeed().get("Haikyu 1080p MULTi")
                .proposalsByPath().get(first.sourcePath()).episodeNumber().orElse(0));
        assertEquals(9, result.matchesBySeed().get("Haikyu 720p multi")
                .proposalsByPath().get(second.sourcePath()).episodeNumber().orElse(0));
    }

    @Test
    void ambiguousCandidateIsSuggestedWithoutMetadataApplication() {
        StubClient client = new StubClient();
        client.searchResult = new TvdbSearchResult(
                List.of(new TvdbCandidate(
                        new TvdbIdentity("111", TvdbMediaType.SERIES, "Haikyu!! Figure Animation"),
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), OptionalDoubleScore.empty(), 0)),
                List.of());

        InventoryItem item = sample("Haikyu.S01E01.mkv");
        InventoryScanResult scan = new InventoryScanResult(
                List.of(item),
                List.of(new InventoryGroup(InventoryGroupType.LIKELY_SERIES, "Haikyu", List.of(item), false)),
                new InventorySummary(1, 0, 0, 0, 1, 0, 0, false, false));

        TvdbBatchMatchResult result = new TvdbBatchMatchService(client).run(scan, credentials(), (a, b) -> {});

        TvdbBatchMatchResult.GroupMatch match = result.matchesBySeed().get("Haikyu");
        assertNotNull(match);
        assertFalse(match.automatic());
        assertTrue(match.series().isEmpty());
        assertTrue(match.proposalsByPath().isEmpty());
    }

    @Test
    void recordsErrorWhenNoCandidate() {
        StubClient client = new StubClient();
        client.searchResult = new TvdbSearchResult(List.of(), List.of());

        InventoryItem item = sample("Mystery.S01E01.mkv");
        InventoryGroup group = new InventoryGroup(
                InventoryGroupType.LIKELY_SERIES, "Mystery", List.of(item), false);
        InventoryScanResult scan = new InventoryScanResult(
                List.of(item), List.of(group),
                new InventorySummary(1, 0, 0, 0, 1, 0, 0, false, false));

        TvdbBatchMatchResult result = new TvdbBatchMatchService(client)
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

    private static TvdbCredentials credentials() {
        return new TvdbCredentials("key", Optional.empty());
    }

    private static final class StubClient implements TvdbClient {
        TvdbSearchResult searchResult;
        TvdbSeriesDetails seriesDetails;
        TvdbMovieDetails movieDetails;
        int searchCalls;

        @Override
        public TvdbSearchResult search(String query, TvdbCredentials credentials) {
            searchCalls++;
            return searchResult;
        }

        @Override
        public TvdbSeriesDetails seriesDetails(TvdbIdentity identity, TvdbCredentials credentials) {
            return seriesDetails;
        }

        @Override
        public TvdbMovieDetails movieDetails(TvdbIdentity identity, TvdbCredentials credentials) {
            return movieDetails;
        }
    }
}
