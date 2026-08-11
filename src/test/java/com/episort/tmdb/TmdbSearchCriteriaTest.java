package com.episort.tmdb;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class TmdbSearchCriteriaTest {
    @Test
    void requiresATitleOrTmdbId() {
        assertThrows(IllegalArgumentException.class,
                () -> new TmdbSearchCriteria(" ", Optional.empty(), Optional.empty()));
    }

    @Test
    void rejectsYearsOutsideFourDigitTmdbRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new TmdbSearchCriteria("Show", Optional.of(999), Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new TmdbSearchCriteria("Show", Optional.of(10000), Optional.empty()));
    }

    @Test
    void rejectsNonNumericTmdbIds() {
        assertThrows(IllegalArgumentException.class,
                () -> new TmdbSearchCriteria("", Optional.empty(), Optional.of("series-123")));
    }
}
