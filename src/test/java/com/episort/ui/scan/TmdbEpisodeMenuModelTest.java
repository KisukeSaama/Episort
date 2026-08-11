package com.episort.ui.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.episort.tmdb.TmdbEpisode;
import com.episort.tmdb.TmdbEpisodeOrder;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class TmdbEpisodeMenuModelTest {

    @Test
    void airedAndDvdOrdersIncludeEverySeasonCategoryIncludingSpecials() {
        List<TmdbEpisode> episodes = List.of(
                episode("1", 1, 1, 1, false),
                episode("2", 1, 2, 2, false),
                episode("special", 0, 1, null, true),
                episode("3", 2, 1, 3, false));

        List<TmdbEpisodeMenuModel.Group> groups =
                TmdbEpisodeMenuModel.groups(episodes, TmdbEpisodeOrder.AIRED);

        assertEquals(List.of(1, 0, 2), groups.stream().map(TmdbEpisodeMenuModel.Group::start).toList());
        assertEquals(List.of("1", "2"), groups.getFirst().episodes().stream().map(TmdbEpisode::id).toList());
        assertEquals(List.of("special"), groups.get(1).episodes().stream().map(TmdbEpisode::id).toList());
        assertEquals(List.of("3"), groups.get(2).episodes().stream().map(TmdbEpisode::id).toList());
    }

    @Test
    void absoluteOrderUsesFiftyEpisodeRangesAndAbsoluteCodes() {
        TmdbEpisode episode50 = episode("50", 3, 10, 50, false);
        TmdbEpisode episode51 = episode("51", 3, 11, 51, false);

        List<TmdbEpisodeMenuModel.Group> groups = TmdbEpisodeMenuModel.groups(
                List.of(episode50, episode51), TmdbEpisodeOrder.ABSOLUTE);

        assertEquals(List.of(1, 51), groups.stream().map(TmdbEpisodeMenuModel.Group::start).toList());
        assertEquals("A050", TmdbEpisodeMenuModel.episodeCode(episode50, TmdbEpisodeOrder.ABSOLUTE));
        assertEquals("A051", TmdbEpisodeMenuModel.episodeCode(episode51, TmdbEpisodeOrder.ABSOLUTE));
    }

    @Test
    void absoluteOrderKeepsSpecialsInTheirOwnSeasonCategory() {
        TmdbEpisode special = episode("special", 0, 3, null, true);
        TmdbEpisode regular = episode("1", 1, 1, 1, false);

        List<TmdbEpisodeMenuModel.Group> groups = TmdbEpisodeMenuModel.groups(
                List.of(special, regular), TmdbEpisodeOrder.ABSOLUTE);

        assertEquals(List.of(TmdbEpisodeMenuModel.GroupKind.SEASON,
                TmdbEpisodeMenuModel.GroupKind.ABSOLUTE_RANGE),
                groups.stream().map(TmdbEpisodeMenuModel.Group::kind).toList());
        assertEquals("S00E03", TmdbEpisodeMenuModel.episodeCode(special, TmdbEpisodeOrder.ABSOLUTE));
    }

    private static TmdbEpisode episode(
            String id, int season, int number, Integer absolute, boolean special) {
        return new TmdbEpisode(id, season, number, Optional.ofNullable(absolute), "Title " + id, special);
    }
}
