package com.episort.fankai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.config.JanusConfiguration;
import com.episort.matching.MediaMatchProposal;
import com.episort.scanner.InventoryScanResult;
import com.episort.scanner.MediaInventoryScanner;
import com.episort.tmdb.OptionalDoubleScore;
import com.episort.tmdb.TmdbCandidate;
import com.episort.tmdb.TmdbClient;
import com.episort.tmdb.TmdbEpisode;
import com.episort.tmdb.TmdbEpisodeGroup;
import com.episort.tmdb.TmdbIdentity;
import com.episort.tmdb.TmdbMediaType;
import com.episort.tmdb.TmdbMovieDetails;
import com.episort.tmdb.TmdbSearchCriteria;
import com.episort.tmdb.TmdbSearchResult;
import com.episort.tmdb.TmdbSeriesDetails;
import com.episort.workflow.TmdbBatchMatchResult;
import com.episort.workflow.TmdbBatchMatchService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A folder of Fankai release files resolves to the kai, with Fankai's own numbering. */
class FankaiBatchMatchTest {
    @TempDir
    Path tempDir;

    @Test
    void releaseFilesOfAKaiResolveToItsSeasonsAndEpisodes() throws Exception {
        Path folder = Files.createDirectory(tempDir.resolve("Naruto Shippuden Yabaï"));
        Path first = Files.createFile(folder.resolve("[Triggerforce]Naruto Shippuden Yabaï 01 - Le retour au pays.mkv"));
        Path eighth = Files.createFile(folder.resolve("[Triggerforce]Naruto Shippuden Yabaï 08 - Rituel.mkv"));
        InventoryScanResult scan = new MediaInventoryScanner().scan(tempDir, progress -> {});

        List<FankaiEpisode> episodes = new ArrayList<>();
        IntStream.rangeClosed(1, 7).forEach(n -> episodes.add(new FankaiEpisode(n, 1, n, "Épisode " + n)));
        episodes.add(new FankaiEpisode(8, 2, 8, "Rituel"));
        FankaiClient fankai = new FankaiClient() {
            @Override
            public List<FankaiSeries> series() {
                return List.of(new FankaiSeries(12, "Naruto Shippuden Yabai", List.of(), Optional.of("31910"),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
            }

            @Override
            public List<FankaiEpisode> episodes(int seriesId) {
                return episodes;
            }
        };
        TmdbBatchMatchService service = new TmdbBatchMatchService(new FankaiTmdbClient(new NarutoOnTmdb(), fankai));

        TmdbBatchMatchResult result = service.run(scan, new JanusConfiguration("key", Optional.empty()), null);

        assertTrue(result.errors().isEmpty(), result.errors().toString());
        TmdbBatchMatchResult.GroupMatch match = result.matchesBySeed().values().iterator().next();
        assertEquals("fankai-12", match.identity().id());
        assertEquals("Naruto Shippuden Yabai", match.identity().displayName());
        assertTrue(match.automatic());
        MediaMatchProposal firstProposal = match.proposalsByPath().get(first);
        assertEquals(Optional.of(1), firstProposal.seasonNumber());
        assertEquals(Optional.of(1), firstProposal.episodeNumber());
        MediaMatchProposal eighthProposal = match.proposalsByPath().get(eighth);
        assertEquals(Optional.of(2), eighthProposal.seasonNumber());
        assertEquals(Optional.of(8), eighthProposal.episodeNumber());
        assertEquals(Optional.of("Rituel"), eighthProposal.title());
    }

    /** TMDB knows the original show, numbered its own way. */
    private static final class NarutoOnTmdb implements TmdbClient {
        private static final TmdbIdentity NARUTO = new TmdbIdentity("31910", TmdbMediaType.SERIES, "Naruto Shippūden");

        @Override
        public TmdbSearchResult search(String query, JanusConfiguration credentials) {
            return new TmdbSearchResult(List.of(new TmdbCandidate(NARUTO, Optional.empty(), Optional.empty(),
                    Optional.of(2007), Optional.empty(), Optional.empty(), OptionalDoubleScore.empty(), 0)), List.of());
        }

        @Override
        public TmdbSearchResult search(TmdbSearchCriteria criteria, JanusConfiguration credentials) {
            return search(criteria.query(), credentials);
        }

        @Override
        public TmdbSeriesDetails seriesDetails(TmdbIdentity identity, JanusConfiguration credentials) {
            return TmdbSeriesDetails.forGroup(identity, List.of(), TmdbEpisodeGroup.aired(),
                    List.of(new TmdbEpisode("t1", 1, 1, Optional.of(1), "Homecoming", false)));
        }

        @Override
        public TmdbMovieDetails movieDetails(TmdbIdentity identity, JanusConfiguration credentials) {
            throw new UnsupportedOperationException();
        }
    }
}
