package com.episort.ui.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.episort.tvdb.TvdbEpisode;
import com.episort.tvdb.TvdbEpisodeOrder;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class TvdbEpisodeMenuModelTest {

    @Test
    void airedAndDvdOrdersIncludeEverySeasonCategoryIncludingSpecials() {
        List<TvdbEpisode> episodes = List.of(
                episode("1", 1, 1, 1, false),
                episode("2", 1, 2, 2, false),
                episode("special", 0, 1, null, true),
                episode("3", 2, 1, 3, false));

        List<TvdbEpisodeMenuModel.Group> groups =
                TvdbEpisodeMenuModel.groups(episodes, TvdbEpisodeOrder.AIRED);

        assertEquals(List.of(1, 0, 2), groups.stream().map(TvdbEpisodeMenuModel.Group::start).toList());
        assertEquals(List.of("1", "2"), groups.getFirst().episodes().stream().map(TvdbEpisode::id).toList());
        assertEquals(List.of("special"), groups.get(1).episodes().stream().map(TvdbEpisode::id).toList());
        assertEquals(List.of("3"), groups.get(2).episodes().stream().map(TvdbEpisode::id).toList());
    }

    @Test
    void absoluteOrderUsesFiftyEpisodeRangesAndAbsoluteCodes() {
        TvdbEpisode episode50 = episode("50", 3, 10, 50, false);
        TvdbEpisode episode51 = episode("51", 3, 11, 51, false);

        List<TvdbEpisodeMenuModel.Group> groups = TvdbEpisodeMenuModel.groups(
                List.of(episode50, episode51), TvdbEpisodeOrder.ABSOLUTE);

        assertEquals(List.of(1, 51), groups.stream().map(TvdbEpisodeMenuModel.Group::start).toList());
        assertEquals("A050", TvdbEpisodeMenuModel.episodeCode(episode50, TvdbEpisodeOrder.ABSOLUTE));
        assertEquals("A051", TvdbEpisodeMenuModel.episodeCode(episode51, TvdbEpisodeOrder.ABSOLUTE));
    }

    @Test
    void absoluteOrderKeepsSpecialsInTheirOwnSeasonCategory() {
        TvdbEpisode special = episode("special", 0, 3, null, true);
        TvdbEpisode regular = episode("1", 1, 1, 1, false);

        List<TvdbEpisodeMenuModel.Group> groups = TvdbEpisodeMenuModel.groups(
                List.of(special, regular), TvdbEpisodeOrder.ABSOLUTE);

        assertEquals(List.of(TvdbEpisodeMenuModel.GroupKind.SEASON,
                TvdbEpisodeMenuModel.GroupKind.ABSOLUTE_RANGE),
                groups.stream().map(TvdbEpisodeMenuModel.Group::kind).toList());
        assertEquals("S00E03", TvdbEpisodeMenuModel.episodeCode(special, TvdbEpisodeOrder.ABSOLUTE));
    }

    private static TvdbEpisode episode(
            String id, int season, int number, Integer absolute, boolean special) {
        return new TvdbEpisode(id, season, number, Optional.ofNullable(absolute), "Title " + id, special);
    }
}
