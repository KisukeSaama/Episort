package com.episort.tmdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TmdbEpisodeOrderTest {

    @Test
    void mapsEveryEpisodeGroupTypeDeclaredByTmdb() {
        assertEquals(
                List.of(
                        TmdbEpisodeOrder.AIRED,
                        TmdbEpisodeOrder.ABSOLUTE,
                        TmdbEpisodeOrder.DVD,
                        TmdbEpisodeOrder.DIGITAL,
                        TmdbEpisodeOrder.STORY_ARC,
                        TmdbEpisodeOrder.PRODUCTION,
                        TmdbEpisodeOrder.TV),
                java.util.stream.IntStream.rangeClosed(1, 7)
                        .mapToObj(TmdbEpisodeOrder::fromGroupType)
                        .flatMap(java.util.Optional::stream)
                        .toList());
        assertTrue(TmdbEpisodeOrder.fromGroupType(99).isEmpty());
    }
}
