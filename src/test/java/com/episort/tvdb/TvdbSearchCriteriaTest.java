package com.episort.tvdb;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class TvdbSearchCriteriaTest {
    @Test
    void requiresATitleOrTvdbId() {
        assertThrows(IllegalArgumentException.class,
                () -> new TvdbSearchCriteria(" ", Optional.empty(), Optional.empty()));
    }

    @Test
    void rejectsYearsOutsideFourDigitTvdbRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new TvdbSearchCriteria("Show", Optional.of(999), Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new TvdbSearchCriteria("Show", Optional.of(10000), Optional.empty()));
    }

    @Test
    void rejectsNonNumericTvdbIds() {
        assertThrows(IllegalArgumentException.class,
                () -> new TvdbSearchCriteria("", Optional.empty(), Optional.of("series-123")));
    }
}
