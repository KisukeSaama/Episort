package com.episort.ui.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.episort.tmdb.TmdbEpisode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TmdbEpisodeSequenceTest {
    @Test
    void startsAtTheChosenEpisodeAndFollowsTmdbOrder() {
        List<TmdbEpisode> result = TmdbEpisodeSequence.startingAt(List.of(
                episode("3", 17, 3), episode("1", 17, 1), episode("2", 17, 2)), "1", 3);
        assertEquals(List.of("1", "2", "3"), result.stream().map(TmdbEpisode::id).toList());
    }

    @Test
    void continuesIntoTheNextSeasonWhenTmdbDoes() {
        List<TmdbEpisode> result = TmdbEpisodeSequence.startingAt(List.of(
                episode("a", 17, 13), episode("b", 18, 1), episode("c", 18, 2)), "a", 3);
        assertEquals(List.of("a", "b", "c"), result.stream().map(TmdbEpisode::id).toList());
    }

    @Test
    void stopsAtTheEndOfAvailableTmdbMetadata() {
        assertEquals(2, TmdbEpisodeSequence.startingAt(
                List.of(episode("1", 17, 1), episode("2", 17, 2)), "1", 5).size());
    }

    @Test
    void canStartASequentialRunFromSpecials() {
        TmdbEpisode special1 = new TmdbEpisode("s1", 0, 1, Optional.empty(), "Special 1", true);
        TmdbEpisode special2 = new TmdbEpisode("s2", 0, 2, Optional.empty(), "Special 2", true);
        List<TmdbEpisode> result = TmdbEpisodeSequence.startingAt(
                List.of(episode("1", 1, 1), special2, special1), "s1", 2);
        assertEquals(List.of("s1", "s2"), result.stream().map(TmdbEpisode::id).toList());
    }

    private static TmdbEpisode episode(String id, int season, int number) {
        return new TmdbEpisode(id, season, number, Optional.empty(), "Episode " + number, false);
    }
}
