package com.episort.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.tvdb.TvdbEpisode;
import com.episort.tvdb.TvdbEpisodeOrder;
import com.episort.tvdb.TvdbIdentity;
import com.episort.tvdb.TvdbMediaType;
import com.episort.tvdb.TvdbSeriesDetails;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TvdbEpisodeOrderMapperTest {
    private final TvdbEpisodeOrderMapper mapper = new TvdbEpisodeOrderMapper();

    @Test
    void remapsDetectiveConanStyleDvdSeasonOverflowToAiredNextSeason() {
        var result = mapper.map(details(), TvdbEpisodeOrder.DVD, TvdbEpisodeOrder.AIRED,
                Path.of("Detective.Conan.S01E29.mkv"), 1, 29, Optional.empty());

        MediaMatchProposal proposal = result.proposal().orElseThrow();
        assertEquals(Optional.of(2), proposal.seasonNumber());
        assertEquals(Optional.of(1), proposal.episodeNumber());
        assertEquals(Optional.of("Aired 29"), proposal.title());
    }

    @Test
    void remapsDvdSeasonEndToAiredSeasonTwoEpisodeFourteen() {
        var result = mapper.map(details(), TvdbEpisodeOrder.DVD, TvdbEpisodeOrder.AIRED,
                Path.of("Detective.Conan.S01E42.mkv"), 1, 42, Optional.empty());

        MediaMatchProposal proposal = result.proposal().orElseThrow();
        assertEquals(Optional.of(2), proposal.seasonNumber());
        assertEquals(Optional.of(14), proposal.episodeNumber());
    }

    @Test
    void remapsAiredBackToDvd() {
        var result = mapper.map(details(), TvdbEpisodeOrder.AIRED, TvdbEpisodeOrder.DVD,
                Path.of("Detective.Conan.S02E01.mkv"), 2, 1, Optional.empty());

        MediaMatchProposal proposal = result.proposal().orElseThrow();
        assertEquals(Optional.of(1), proposal.seasonNumber());
        assertEquals(Optional.of(29), proposal.episodeNumber());
    }

    @Test
    void reportsUnavailableOrder() {
        TvdbSeriesDetails unavailable = new TvdbSeriesDetails(
                new TvdbIdentity("1", TvdbMediaType.SERIES, "Show"),
                EnumSet.of(TvdbEpisodeOrder.AIRED),
                List.of(new TvdbEpisode("e1", 1, 1, Optional.of(1), "Pilot", false)),
                List.of(),
                List.of());

        var result = mapper.map(unavailable, TvdbEpisodeOrder.AIRED, TvdbEpisodeOrder.DVD,
                Path.of("Show.S01E01.mkv"), 1, 1, Optional.empty());

        assertTrue(result.proposal().isEmpty());
        assertEquals(Optional.of("TVDB order unavailable for this series"), result.error());
    }

    private static TvdbSeriesDetails details() {
        List<TvdbEpisode> aired = new ArrayList<>();
        List<TvdbEpisode> dvd = new ArrayList<>();
        for (int logical = 1; logical <= 42; logical++) {
            int airedSeason = logical <= 28 ? 1 : 2;
            int airedEpisode = logical <= 28 ? logical : logical - 28;
            aired.add(new TvdbEpisode("e" + logical, airedSeason, airedEpisode,
                    Optional.of(logical), "Aired " + logical, false));
            dvd.add(new TvdbEpisode("e" + logical, 1, logical,
                    Optional.of(logical), "DVD " + logical, false));
        }
        return new TvdbSeriesDetails(
                new TvdbIdentity("conan", TvdbMediaType.SERIES, "Detective Conan"),
                EnumSet.of(TvdbEpisodeOrder.AIRED, TvdbEpisodeOrder.DVD),
                aired,
                dvd,
                aired);
    }
}
