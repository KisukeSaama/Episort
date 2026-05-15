package com.episort.ui.scan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.tvdb.OptionalDoubleScore;
import com.episort.tvdb.TvdbCandidate;
import com.episort.tvdb.TvdbIdentity;
import com.episort.tvdb.TvdbMediaType;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RowDetailPanelTest {

    @Test
    void existingAutomaticMatchIsReappliedInsteadOfTreatedAsFreshSearchSelection() {
        ScanRow row = row();
        row.setTvdbMatch(Optional.of("Detective Conan [TVDB conan]"));
        row.setTvdbCandidate(Optional.of(candidate()));

        assertTrue(RowDetailPanel.isApplyingExistingMatch(row, "Detective Conan [TVDB conan]"));
    }

    @Test
    void aDifferentSearchSelectionStillUsesFreshCandidateFlow() {
        ScanRow row = row();
        row.setTvdbMatch(Optional.of("Detective Conan [TVDB conan]"));
        row.setTvdbCandidate(Optional.of(candidate()));

        assertFalse(RowDetailPanel.isApplyingExistingMatch(row, "Case Closed [TVDB other]"));
    }

    private static ScanRow row() {
        return new ScanRow(
                Path.of("Detective.Conan.S01E01.mkv"),
                "Detective.Conan.S01E01.mkv",
                "MKV",
                ScanMediaType.SERIES,
                ScanRowStatus.TVDB);
    }

    private static TvdbCandidate candidate() {
        return new TvdbCandidate(
                new TvdbIdentity("conan", TvdbMediaType.SERIES, "Detective Conan"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                OptionalDoubleScore.empty(),
                0);
    }
}
