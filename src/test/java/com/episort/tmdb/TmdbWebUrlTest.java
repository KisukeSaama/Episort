package com.episort.tmdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TmdbWebUrlTest {

    @Test
    void buildsCanonicalSeriesAndMoviePages() {
        assertEquals("https://www.themoviedb.org/tv/101",
                TmdbWebUrl.forIdentity(new TmdbIdentity("101", TmdbMediaType.SERIES, "Show"))
                        .orElseThrow());
        assertEquals("https://www.themoviedb.org/movie/202",
                TmdbWebUrl.forIdentity(new TmdbIdentity("202", TmdbMediaType.MOVIE, "Film"))
                        .orElseThrow());
    }

    @Test
    void refusesNonNumericIdsInsteadOfBuildingAnUnsafePath() {
        assertTrue(TmdbWebUrl.forIdentity(
                new TmdbIdentity("../account", TmdbMediaType.SERIES, "Bad")).isEmpty());
    }
}
