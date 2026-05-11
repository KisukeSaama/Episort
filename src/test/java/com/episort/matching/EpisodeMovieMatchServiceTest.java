package com.episort.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.scanner.InventoryItem;
import com.episort.scanner.InventoryItemType;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EpisodeMovieMatchServiceTest {
    private final EpisodeMovieMatchService service = new EpisodeMovieMatchService();

    @Test
    void proposesAtMostOneSeriesEpisodeMatchPerSupportedFile() {
        InventoryItem file = video("Show.S01E02.Title.mkv");
        TvdbSeriesMetadata series = new TvdbSeriesMetadata("series-1", "Show", List.of(
                new TvdbEpisodeMetadata("episode-1", 1, 1, Optional.of(1), "Pilot", false),
                new TvdbEpisodeMetadata("episode-2", 1, 2, Optional.of(2), "Title", false)));

        List<MediaMatchProposal> proposals = service.proposeSeriesMatches(List.of(file), series);

        assertEquals(1, proposals.size());
        assertEquals(MediaMatchType.SERIES_EPISODE, proposals.get(0).type());
        assertEquals(Optional.of("episode-2"), proposals.get(0).tvdbId());
        assertEquals(Optional.of(1), proposals.get(0).seasonNumber());
        assertEquals(Optional.of(2), proposals.get(0).episodeNumber());
    }

    @Test
    void proposesSpecialsWhenSeasonZeroEpisodeExistsInTvdb() {
        InventoryItem file = video("Show.S00E03.OVA.mkv");
        TvdbSeriesMetadata series = new TvdbSeriesMetadata("series-1", "Show", List.of(
                new TvdbEpisodeMetadata("special-3", 0, 3, Optional.empty(), "OVA", true)));

        List<MediaMatchProposal> proposals = service.proposeSeriesMatches(List.of(file), series);

        assertEquals(MediaMatchType.SERIES_SPECIAL, proposals.get(0).type());
        assertEquals(Optional.of("special-3"), proposals.get(0).tvdbId());
    }

    @Test
    void duplicateFilesCannotClaimSameEpisodeTwice() {
        InventoryItem first = video("Show.S01E01.A.mkv");
        InventoryItem second = video("Show.1x01.B.mkv");
        TvdbSeriesMetadata series = new TvdbSeriesMetadata("series-1", "Show", List.of(
                new TvdbEpisodeMetadata("episode-1", 1, 1, Optional.of(1), "Pilot", false)));

        List<MediaMatchProposal> proposals = service.proposeSeriesMatches(List.of(first, second), series);

        assertEquals(MediaMatchType.SERIES_EPISODE, proposals.get(0).type());
        assertEquals(MediaMatchType.UNMATCHED, proposals.get(1).type());
    }

    @Test
    void absoluteEpisodeNumbersCanMatchWhenTvdbProvidesThem() {
        InventoryItem file = video("Anime.042.mkv");
        TvdbSeriesMetadata series = new TvdbSeriesMetadata("series-1", "Anime", List.of(
                new TvdbEpisodeMetadata("episode-42", 2, 16, Optional.of(42), "Forty Two", false)));

        List<MediaMatchProposal> proposals = service.proposeSeriesMatches(List.of(file), series);

        assertEquals(Optional.of("episode-42"), proposals.get(0).tvdbId());
        assertTrue(proposals.get(0).confidence().isPresent());
    }

    @Test
    void movieIdentityCanOnlyBeAssignedToOneFileInV1() {
        InventoryItem first = video("Movie.2024.mkv");
        InventoryItem second = video("Movie.2024.duplicate.mkv");
        TvdbMovieMetadata movie = new TvdbMovieMetadata("movie-1", "Movie", Optional.of(2024));

        List<MediaMatchProposal> proposals = service.proposeMovieMatches(List.of(first, second), movie);

        assertEquals(MediaMatchType.MOVIE, proposals.get(0).type());
        assertEquals(MediaMatchType.UNMATCHED, proposals.get(1).type());
    }

    private static InventoryItem video(String filename) {
        Path path = Path.of("/workspace/media").resolve(filename);
        return new InventoryItem(path, filename, "mkv", path.getParent(), InventoryItemType.SUPPORTED_VIDEO, true);
    }
}
