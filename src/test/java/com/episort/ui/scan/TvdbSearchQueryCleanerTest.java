package com.episort.ui.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TvdbSearchQueryCleanerTest {
    @Test
    void removesSeasonAndReleaseTagsFromSeriesQueries() {
        assertEquals("Haikyu", TvdbSearchQueryCleaner.clean("Haikyu S01 MULTi 1080p BluRay x264 SHiNiGAMi"));
        assertEquals("Detective Conan", TvdbSearchQueryCleaner.clean(
                "Detective Conan - S01E01 - Roller Coaster Murder Case.mkv"));
    }

    @Test
    void keepsMovieTitleBeforeYearAndTechnicalTags() {
        assertEquals("28 Years Later", TvdbSearchQueryCleaner.clean(
                "28.Years.Later.2025.MULTi.VF2.2160p.WEBRip.HDR10.mkv"));
    }
}
