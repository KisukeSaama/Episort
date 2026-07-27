package com.episort.ui.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.workflow.ReviewItem;
import com.episort.workflow.ReviewMatchState;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class ScanReviewMapperTest {
    @Test
    void everyReviewStateIsReachableFromARowStatus() {
        assertEquals(ReviewMatchState.READY,
                ScanReviewMapper.matchState(row("ok.mkv", ScanMediaType.SERIES, ScanRowStatus.OK)));
        assertEquals(ReviewMatchState.READY,
                ScanReviewMapper.matchState(row("tvdb.mkv", ScanMediaType.SERIES, ScanRowStatus.TVDB)));
        assertEquals(ReviewMatchState.NEEDS_REVIEW,
                ScanReviewMapper.matchState(row("review.mkv", ScanMediaType.SERIES, ScanRowStatus.REVIEW)));
        assertEquals(ReviewMatchState.UNKNOWN,
                ScanReviewMapper.matchState(row("type.mkv", ScanMediaType.UNKNOWN, ScanRowStatus.TYPE)));
        assertEquals(ReviewMatchState.CONFLICT,
                ScanReviewMapper.matchState(row("conflict.mkv", ScanMediaType.MOVIE, ScanRowStatus.CONFLICT)));
        assertEquals(ReviewMatchState.DUPLICATE,
                ScanReviewMapper.matchState(row("duplicate.mkv", ScanMediaType.SERIES, ScanRowStatus.DUPLICATE)));
        assertEquals(ReviewMatchState.UNSUPPORTED,
                ScanReviewMapper.matchState(row("clip.wmv", ScanMediaType.SERIES, ScanRowStatus.EXT)));
        assertEquals(ReviewMatchState.AMBIGUOUS,
                ScanReviewMapper.matchState(row("path.mkv", ScanMediaType.SERIES, ScanRowStatus.PATH)));
        assertEquals(ReviewMatchState.IGNORED,
                ScanReviewMapper.matchState(row("ignored.mkv", ScanMediaType.SERIES, ScanRowStatus.IGNORED)));
    }

    @Test
    void ignoringARowWinsOverItsPreviousStatus() {
        ScanRow row = row("conflict.mkv", ScanMediaType.MOVIE, ScanRowStatus.CONFLICT);
        row.markIgnored();

        assertEquals(ReviewMatchState.IGNORED, ScanReviewMapper.matchState(row));
        assertFalse(ScanReviewMapper.isBlockingConflict(row));
    }

    @Test
    void onlyConflictsAndDuplicatesBlockThePatternGate() {
        assertTrue(ScanReviewMapper.isBlockingConflict(row("c.mkv", ScanMediaType.MOVIE, ScanRowStatus.CONFLICT)));
        assertTrue(ScanReviewMapper.isBlockingConflict(row("d.mkv", ScanMediaType.SERIES, ScanRowStatus.DUPLICATE)));
        assertFalse(ScanReviewMapper.isBlockingConflict(row("e.mkv", ScanMediaType.SERIES, ScanRowStatus.ERROR)));
        assertFalse(ScanReviewMapper.isBlockingConflict(row("o.mkv", ScanMediaType.SERIES, ScanRowStatus.OK)));
    }

    @Test
    void highConfidenceRowsStayReadyAndAreNeverValidated() {
        ScanRow row = row("show.mkv", ScanMediaType.SERIES, ScanRowStatus.TVDB);
        row.setConfidence(OptionalDouble.of(0.99));
        row.setTvdbMatch(Optional.of("Show (2001)"));

        ReviewItem item = ScanReviewMapper.toReviewItem(row);

        assertEquals(ReviewMatchState.READY, item.matchState());
        assertEquals(OptionalDouble.of(0.99), item.confidence());
        assertEquals(Optional.of("Show (2001)"), item.proposedIdentity());
    }

    @Test
    void proposedIdentityFallsBackToTheProposedFilename() {
        ScanRow row = row("show.mkv", ScanMediaType.SERIES, ScanRowStatus.OK);
        row.setProposedFilename(Optional.of("Show - S01E01 - Pilot.mkv"));

        assertEquals(Optional.of("Show - S01E01 - Pilot.mkv"),
                ScanReviewMapper.toReviewItem(row).proposedIdentity());
    }

    @Test
    void mappingKeepsRowOrderAndSourcePaths() {
        List<ReviewItem> items = ScanReviewMapper.toReviewItems(List.of(
                row("a.mkv", ScanMediaType.SERIES, ScanRowStatus.OK),
                row("b.mkv", ScanMediaType.SERIES, ScanRowStatus.OK)));

        assertEquals(List.of(Path.of("a.mkv"), Path.of("b.mkv")),
                items.stream().map(ReviewItem::sourcePath).toList());
    }

    private static ScanRow row(String filename, ScanMediaType type, ScanRowStatus status) {
        return new ScanRow(Path.of(filename), filename, "mkv", type, status);
    }
}
