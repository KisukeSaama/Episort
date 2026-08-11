package com.episort.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.tmdb.TmdbEpisode;
import com.episort.tmdb.TmdbEpisodeOrder;
import com.episort.tmdb.TmdbIdentity;
import com.episort.tmdb.TmdbMediaType;
import com.episort.tmdb.TmdbSeriesDetails;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TmdbEpisodeOrderMapperTest {
    private final TmdbEpisodeOrderMapper mapper = new TmdbEpisodeOrderMapper();

    @Test
    void remapsDetectiveConanStyleDvdSeasonOverflowToAiredNextSeason() {
        var result = mapper.map(details(), TmdbEpisodeOrder.DVD, TmdbEpisodeOrder.AIRED,
                Path.of("Detective.Conan.S01E29.mkv"), 1, 29, Optional.empty());

        MediaMatchProposal proposal = result.proposal().orElseThrow();
        assertEquals(Optional.of(2), proposal.seasonNumber());
        assertEquals(Optional.of(1), proposal.episodeNumber());
        assertEquals(Optional.of("Aired 29"), proposal.title());
    }

    @Test
    void remapsDvdSeasonEndToAiredSeasonTwoEpisodeFourteen() {
        var result = mapper.map(details(), TmdbEpisodeOrder.DVD, TmdbEpisodeOrder.AIRED,
                Path.of("Detective.Conan.S01E42.mkv"), 1, 42, Optional.empty());

        MediaMatchProposal proposal = result.proposal().orElseThrow();
        assertEquals(Optional.of(2), proposal.seasonNumber());
        assertEquals(Optional.of(14), proposal.episodeNumber());
    }

    @Test
    void remapsAiredBackToDvd() {
        var result = mapper.map(details(), TmdbEpisodeOrder.AIRED, TmdbEpisodeOrder.DVD,
                Path.of("Detective.Conan.S02E01.mkv"), 2, 1, Optional.empty());

        MediaMatchProposal proposal = result.proposal().orElseThrow();
        assertEquals(Optional.of(1), proposal.seasonNumber());
        assertEquals(Optional.of(29), proposal.episodeNumber());
    }

    @Test
    void reportsUnavailableOrder() {
        TmdbSeriesDetails unavailable = new TmdbSeriesDetails(
                new TmdbIdentity("1", TmdbMediaType.SERIES, "Show"),
                EnumSet.of(TmdbEpisodeOrder.AIRED),
                List.of(new TmdbEpisode("e1", 1, 1, Optional.of(1), "Pilot", false)),
                List.of(),
                List.of());

        var result = mapper.map(unavailable, TmdbEpisodeOrder.AIRED, TmdbEpisodeOrder.DVD,
                Path.of("Show.S01E01.mkv"), 1, 1, Optional.empty());

        assertTrue(result.proposal().isEmpty());
        assertEquals(Optional.of("TMDB order unavailable for this series"), result.error());
    }

    private static TmdbSeriesDetails details() {
        List<TmdbEpisode> aired = new ArrayList<>();
        List<TmdbEpisode> dvd = new ArrayList<>();
        for (int logical = 1; logical <= 42; logical++) {
            int airedSeason = logical <= 28 ? 1 : 2;
            int airedEpisode = logical <= 28 ? logical : logical - 28;
            aired.add(new TmdbEpisode("e" + logical, airedSeason, airedEpisode,
                    Optional.of(logical), "Aired " + logical, false));
            dvd.add(new TmdbEpisode("e" + logical, 1, logical,
                    Optional.of(logical), "DVD " + logical, false));
        }
        return new TmdbSeriesDetails(
                new TmdbIdentity("conan", TmdbMediaType.SERIES, "Detective Conan"),
                EnumSet.of(TmdbEpisodeOrder.AIRED, TmdbEpisodeOrder.DVD),
                aired,
                dvd,
                aired);
    }
}
