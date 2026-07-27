package com.episort.ui.scan;

import com.episort.workflow.ReviewItem;
import com.episort.workflow.ReviewMatchState;
import java.util.List;
import java.util.Objects;

/**
 * Maps preview rows to the workflow-layer review model. Pure mapping logic — no
 * JavaFX scene graph, fully unit-testable.
 *
 * <p>Match state and confidence stay strictly separate: a high-confidence row is
 * still only {@code READY}, never validated.
 */
public final class ScanReviewMapper {
    private ScanReviewMapper() {
    }

    public static List<ReviewItem> toReviewItems(List<ScanRow> rows) {
        Objects.requireNonNull(rows, "rows");
        return rows.stream().map(ScanReviewMapper::toReviewItem).toList();
    }

    public static ReviewItem toReviewItem(ScanRow row) {
        Objects.requireNonNull(row, "row");
        return new ReviewItem(
                row.sourcePath(),
                row.tvdbMatch().or(row::proposedFilename),
                matchState(row),
                row.confidence(),
                row.isIgnored(),
                isUnsupported(row),
                isBlockingConflict(row));
    }

    static ReviewMatchState matchState(ScanRow row) {
        if (row.isIgnored()) {
            return ReviewMatchState.IGNORED;
        }
        ReviewMatchState state = switch (row.status()) {
            case OK, TVDB -> ReviewMatchState.READY;
            case REVIEW, LOW_CONFIDENCE, TYPE, PATTERN, META -> ReviewMatchState.NEEDS_REVIEW;
            case CONFLICT -> ReviewMatchState.CONFLICT;
            case DUPLICATE -> ReviewMatchState.DUPLICATE;
            case IGNORED -> ReviewMatchState.IGNORED;
            case EXT -> ReviewMatchState.UNSUPPORTED;
            case PATH, ERROR -> ReviewMatchState.AMBIGUOUS;
        };
        if (row.mediaType() == ScanMediaType.UNKNOWN && state == ReviewMatchState.NEEDS_REVIEW) {
            return ReviewMatchState.UNKNOWN;
        }
        return state;
    }

    static boolean isUnsupported(ScanRow row) {
        return row.status() == ScanRowStatus.EXT;
    }

    /**
     * Only unresolved conflicts and duplicates block the pattern gate. Ignored
     * rows are user decisions, so they never block.
     */
    static boolean isBlockingConflict(ScanRow row) {
        return !row.isIgnored()
                && (row.status() == ScanRowStatus.CONFLICT || row.status() == ScanRowStatus.DUPLICATE);
    }
}
