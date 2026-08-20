package com.episort.ui.execution;

import com.episort.planning.ConflictResolution;
import com.episort.planning.PlanConflictType;
import com.episort.planning.PlanOperationStatus;
import com.episort.planning.PlannedOperation;
import com.episort.ui.AppLanguage;
import com.episort.ui.UiText;

/**
 * How a planned operation reads on the plan-review screen: its status wording,
 * its pill colour, and the wording of each conflict decision.
 *
 * <p>Pure mapping, extracted from the pane so the copy shown before files are
 * moved can be tested without a JavaFX toolkit — this is the last screen the
 * user sees before anything on disk changes.
 */
final class PlanReviewText {

    private PlanReviewText() {
    }

    static String status(PlanOperationStatus status, AppLanguage language) {
        return switch (status) {
            case EXECUTABLE -> UiText.planStatusExecutable(language);
            case ALREADY_IN_PLACE -> UiText.planStatusInPlace(language);
            case EXCLUDED -> UiText.planStatusExcluded(language);
            case CONFLICT -> UiText.planStatusConflict(language);
            case DELETE -> UiText.planStatusDelete(language);
        };
    }

    /**
     * The shape a status takes in the table.
     *
     * <p>A pill is a claim on the reader's attention. When every row makes it,
     * the one row that has to be read is the one that disappears: a plan is
     * mostly rows with nothing to decide, so those state themselves as plain
     * text and only what needs a decision keeps a shape.
     */
    static String pillVariant(PlanOperationStatus status) {
        return switch (status) {
            // Nothing to decide: the plan will move the file, or it is already
            // where it belongs. Both are the expected outcome, and the second is
            // even less of an event than the first.
            case EXECUTABLE -> "quiet";
            case ALREADY_IN_PLACE -> "quiet-muted";
            case EXCLUDED -> "quiet-muted";
            case CONFLICT -> "conflict";
            // The one row that destroys something must never read like the rows
            // around it: it gets the loudest pill in the table.
            case DELETE -> "danger";
        };
    }

    /**
     * The most specific thing that can be said about one operation: an explicit
     * replacement, then the conflict behind it, then why it was excluded, and
     * only failing all of those, its bare status.
     */
    static String statusDetail(PlannedOperation operation, AppLanguage language) {
        if (operation.replaceExisting()) {
            return UiText.planStatusReplaceDetail(language);
        }
        if (operation.deletesFile()) {
            return UiText.planStatusDeleteDetail(language);
        }
        if (operation.conflict().isPresent()) {
            return UiText.planConflict(language, operation.conflict().orElseThrow().type().name());
        }
        if (operation.status() == PlanOperationStatus.EXCLUDED) {
            return UiText.planExclusion(language, operation.exclusionReason().name());
        }
        return status(operation.status(), language);
    }

    /**
     * What a decision does, in the user's terms. Skipping says three different
     * things depending on the conflict: it keeps the file already occupying the
     * destination, it ignores one of two files claiming a destination nobody
     * holds yet, or — where replacing was never an option — it simply drops the
     * row from the run. Naming the wrong one would describe a choice the user
     * never had.
     *
     * <p>On a duplicate, replacing overwrites nothing: the two copies sit under
     * different names, so the answer means this copy is the one kept and the
     * other one goes. It is worded that way.
     */
    static String decisionOption(ConflictResolution resolution, PlanConflictType type, AppLanguage language) {
        return switch (resolution) {
            case REPLACE -> type == PlanConflictType.DUPLICATE_MEDIA
                    ? UiText.conflictOptionKeepThis(language)
                    : UiText.conflictOptionReplace(language);
            case DELETE_SOURCE -> switch (type) {
                case DESTINATION_FILE_EXISTS, MEDIA_ALREADY_IN_LIBRARY ->
                        UiText.conflictOptionDeleteSourceKeepExisting(language);
                default -> UiText.conflictOptionDeleteSource(language);
            };
            case SKIP -> switch (type) {
                case DESTINATION_FILE_EXISTS, MEDIA_ALREADY_IN_LIBRARY ->
                        UiText.conflictOptionKeepExisting(language);
                case DUPLICATE_DESTINATION, DUPLICATE_MEDIA -> UiText.conflictOptionIgnore(language);
                default -> UiText.conflictOptionDrop(language);
            };
        };
    }

    /** The headline above the table: nothing to do, blocked, or ready. */
    static String banner(boolean planEmpty, boolean noExecutableOperations, boolean blocked,
            AppLanguage language) {
        if (planEmpty || (noExecutableOperations && !blocked)) {
            return UiText.planEmpty(language);
        }
        return blocked ? UiText.planBlocked(language) : UiText.planReady(language);
    }
}
