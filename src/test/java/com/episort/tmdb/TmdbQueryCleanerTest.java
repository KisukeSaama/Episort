package com.episort.tmdb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TmdbQueryCleanerTest {
    @Test
    void removesSeasonAndReleaseTagsFromSeriesQueries() {
        assertEquals("Haikyu", TmdbQueryCleaner.clean("Haikyu S01 MULTi 1080p BluRay x264 SHiNiGAMi"));
        assertEquals("Detective Conan", TmdbQueryCleaner.clean(
                "Detective Conan - S01E01 - Roller Coaster Murder Case.mkv"));
    }

    @Test
    void keepsMovieTitleBeforeYearAndTechnicalTags() {
        assertEquals("28 Years Later", TmdbQueryCleaner.clean(
                "28.Years.Later.2025.MULTi.VF2.2160p.WEBRip.HDR10.mkv"));
    }
}
