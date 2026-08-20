package com.episort.ui.scan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.episort.tmdb.TmdbEpisodeGroup;
import com.episort.tmdb.TmdbEpisodeOrder;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScanScreenEpisodeOrderTest {

    @Test
    void remappingUsesTheNumberCurrentlyAppliedToTheRowBeforeTheOriginalFilename() {
        ScanRow row = new ScanRow(
                Path.of("Bleach.S01E15.mkv"),
                "Bleach.S01E15.mkv",
                "mkv",
                ScanMediaType.SERIES,
                ScanRowStatus.TMDB);
        row.setOrder(Optional.of("S02E08"));

        assertArrayEquals(new int[] {2, 8}, ScanScreen.currentSeasonEpisode(row).orElseThrow());
    }

    @Test
    void rememberingANamedGroupAlsoKeepsItsOrderSelected() {
        ScanRow row = new ScanRow(
                Path.of("Bleach.Kai.01.mkv"), "Bleach.Kai.01.mkv", "mkv",
                ScanMediaType.SERIES, ScanRowStatus.TMDB);
        TmdbEpisodeGroup kai = new TmdbEpisodeGroup(
                "62fbec9035818f007af92479", "Kaï", TmdbEpisodeOrder.DIGITAL, 28, 210);

        row.setAppliedTmdbGroup(Optional.of(kai));

        assertEquals(Optional.of(kai), row.appliedTmdbGroup());
        assertEquals(Optional.of(TmdbEpisodeOrder.DIGITAL), row.appliedTmdbOrder());
    }
}
