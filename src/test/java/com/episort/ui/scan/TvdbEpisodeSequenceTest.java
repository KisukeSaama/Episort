package com.episort.ui.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.episort.tvdb.TvdbEpisode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TvdbEpisodeSequenceTest {
    @Test
    void startsAtTheChosenEpisodeAndFollowsTvdbOrder() {
        List<TvdbEpisode> result = TvdbEpisodeSequence.startingAt(List.of(
                episode("3", 17, 3), episode("1", 17, 1), episode("2", 17, 2)), "1", 3);
        assertEquals(List.of("1", "2", "3"), result.stream().map(TvdbEpisode::id).toList());
    }

    @Test
    void continuesIntoTheNextSeasonWhenTvdbDoes() {
        List<TvdbEpisode> result = TvdbEpisodeSequence.startingAt(List.of(
                episode("a", 17, 13), episode("b", 18, 1), episode("c", 18, 2)), "a", 3);
        assertEquals(List.of("a", "b", "c"), result.stream().map(TvdbEpisode::id).toList());
    }

    @Test
    void stopsAtTheEndOfAvailableTvdbMetadata() {
        assertEquals(2, TvdbEpisodeSequence.startingAt(
                List.of(episode("1", 17, 1), episode("2", 17, 2)), "1", 5).size());
    }

    @Test
    void canStartASequentialRunFromSpecials() {
        TvdbEpisode special1 = new TvdbEpisode("s1", 0, 1, Optional.empty(), "Special 1", true);
        TvdbEpisode special2 = new TvdbEpisode("s2", 0, 2, Optional.empty(), "Special 2", true);
        List<TvdbEpisode> result = TvdbEpisodeSequence.startingAt(
                List.of(episode("1", 1, 1), special2, special1), "s1", 2);
        assertEquals(List.of("s1", "s2"), result.stream().map(TvdbEpisode::id).toList());
    }

    private static TvdbEpisode episode(String id, int season, int number) {
        return new TvdbEpisode(id, season, number, Optional.empty(), "Episode " + number, false);
    }
}
