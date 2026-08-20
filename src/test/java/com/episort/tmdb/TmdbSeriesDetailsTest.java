package com.episort.tmdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TmdbSeriesDetailsTest {

    @Test
    void mergesOrdersLoadedBySeparateRequestsForOneSeries() {
        TmdbIdentity identity = new TmdbIdentity("101", TmdbMediaType.SERIES, "Show");
        TmdbEpisode aired = episode("aired");
        TmdbEpisode digital = episode("digital");
        TmdbSeriesDetails first = TmdbSeriesDetails.forOrder(
                identity,
                Set.of(TmdbEpisodeOrder.AIRED, TmdbEpisodeOrder.DIGITAL),
                TmdbEpisodeOrder.AIRED,
                List.of(aired));
        TmdbSeriesDetails second = TmdbSeriesDetails.forOrder(
                identity,
                Set.of(TmdbEpisodeOrder.AIRED, TmdbEpisodeOrder.DIGITAL),
                TmdbEpisodeOrder.DIGITAL,
                List.of(digital));

        TmdbSeriesDetails merged = first.merge(second);

        assertEquals(List.of(aired), merged.episodesFor(TmdbEpisodeOrder.AIRED));
        assertEquals(List.of(digital), merged.episodesFor(TmdbEpisodeOrder.DIGITAL));
        assertTrue(merged.supportedOrders().containsAll(Set.of(
                TmdbEpisodeOrder.AIRED, TmdbEpisodeOrder.DIGITAL)));
    }

    @Test
    void keepsSeveralNamedGroupsOfTheSameTypeInTheSeriesCache() {
        TmdbIdentity identity = new TmdbIdentity("30984", TmdbMediaType.SERIES, "Bleach");
        TmdbEpisodeGroup crunchyroll = new TmdbEpisodeGroup(
                "crunchyroll", "Crunchyroll Season Split", TmdbEpisodeOrder.DIGITAL, 16, 392);
        TmdbEpisodeGroup netflix = new TmdbEpisodeGroup(
                "netflix", "Netflix", TmdbEpisodeOrder.DIGITAL, 6, 131);

        TmdbSeriesDetails merged = TmdbSeriesDetails.forGroup(
                        identity, List.of(TmdbEpisodeGroup.aired(), crunchyroll, netflix),
                        crunchyroll, List.of(episode("crunchyroll-1")))
                .merge(TmdbSeriesDetails.forGroup(
                        identity, List.of(TmdbEpisodeGroup.aired(), crunchyroll, netflix),
                        netflix, List.of(episode("netflix-1"))));

        assertEquals(List.of(episode("crunchyroll-1")), merged.episodesFor(crunchyroll));
        assertEquals(List.of(episode("netflix-1")), merged.episodesFor(netflix));
        assertEquals(3, merged.availableGroups().size());
    }

    private static TmdbEpisode episode(String id) {
        return new TmdbEpisode(id, 1, 1, Optional.empty(), id, false);
    }
}
