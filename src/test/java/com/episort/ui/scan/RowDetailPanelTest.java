package com.episort.ui.scan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.tmdb.OptionalDoubleScore;
import com.episort.tmdb.TmdbCandidate;
import com.episort.tmdb.TmdbIdentity;
import com.episort.tmdb.TmdbMediaType;
import com.episort.scanner.InventoryGroupType;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RowDetailPanelTest {

    @Test
    void existingAutomaticMatchIsReappliedInsteadOfTreatedAsFreshSearchSelection() {
        ScanRow row = row();
        row.setTmdbMatch(Optional.of("Detective Conan [TMDB conan]"));
        row.setTmdbCandidate(Optional.of(candidate()));

        assertTrue(RowDetailPanel.isApplyingExistingMatch(row, "Detective Conan [TMDB conan]"));
    }

    @Test
    void aDifferentSearchSelectionStillUsesFreshCandidateFlow() {
        ScanRow row = row();
        row.setTmdbMatch(Optional.of("Detective Conan [TMDB conan]"));
        row.setTmdbCandidate(Optional.of(candidate()));

        assertFalse(RowDetailPanel.isApplyingExistingMatch(row, "Case Closed [TMDB other]"));
    }

    @Test
    void reactivatedMediaGroupMakesManualTmdbSearchAvailableAgain() {
        BatchTmdbMatch ignored = new BatchTmdbMatch(
                "ignored", InventoryGroupType.IGNORED, 1, false);
        BatchTmdbMatch reactivated = new BatchTmdbMatch(
                "Detective Conan", InventoryGroupType.LIKELY_SERIES, 1, false);

        assertFalse(RowDetailPanel.isTmdbSearchable(ignored));
        assertTrue(RowDetailPanel.isTmdbSearchable(reactivated));
    }

    private static ScanRow row() {
        return new ScanRow(
                Path.of("Detective.Conan.S01E01.mkv"),
                "Detective.Conan.S01E01.mkv",
                "MKV",
                ScanMediaType.SERIES,
                ScanRowStatus.TMDB);
    }

    private static TmdbCandidate candidate() {
        return new TmdbCandidate(
                new TmdbIdentity("conan", TmdbMediaType.SERIES, "Detective Conan"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                OptionalDoubleScore.empty(),
                0);
    }
}
