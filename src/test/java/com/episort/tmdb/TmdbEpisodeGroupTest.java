package com.episort.tmdb;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TmdbEpisodeGroupTest {

    @Test
    void groupsOfTheSameTypeRemainDistinctByTheirTmdbIdentity() {
        TmdbEpisodeGroup crunchyroll = new TmdbEpisodeGroup(
                "crunchyroll", "Crunchyroll Season Split", TmdbEpisodeOrder.DIGITAL, 16, 392);
        TmdbEpisodeGroup netflix = new TmdbEpisodeGroup(
                "netflix", "Netflix", TmdbEpisodeOrder.DIGITAL, 6, 131);

        assertNotEquals(crunchyroll, netflix);
        assertTrue(TmdbEpisodeGroup.aired().isAired());
    }
}
