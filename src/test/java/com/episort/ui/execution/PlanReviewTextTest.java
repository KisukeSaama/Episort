package com.episort.ui.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.episort.planning.ConflictResolution;
import com.episort.planning.PlanConflictType;
import com.episort.planning.PlanOperationStatus;
import com.episort.ui.AppLanguage;
import com.episort.ui.UiText;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlanReviewTextTest {

    private static final AppLanguage FR = AppLanguage.FRENCH;

    @Test
    void everyStatusHasWordingAndAPill() {
        for (PlanOperationStatus status : PlanOperationStatus.values()) {
            assertNotNull(PlanReviewText.status(status, FR), "unmapped status: " + status);
            assertNotNull(PlanReviewText.pillVariant(status), "unmapped pill: " + status);
        }
    }

    /**
     * A plan is mostly rows with nothing to decide. They state themselves as
     * plain text so that the rows which do need a decision keep the only shapes
     * in the column.
     */
    @Test
    void onlyRowsNeedingADecisionKeepAPill() {
        assertEquals("quiet", PlanReviewText.pillVariant(PlanOperationStatus.EXECUTABLE));
        assertEquals("quiet-muted", PlanReviewText.pillVariant(PlanOperationStatus.ALREADY_IN_PLACE));
        assertEquals("quiet-muted", PlanReviewText.pillVariant(PlanOperationStatus.EXCLUDED));
        assertEquals("conflict", PlanReviewText.pillVariant(PlanOperationStatus.CONFLICT));
        assertEquals("danger", PlanReviewText.pillVariant(PlanOperationStatus.DELETE));
    }

    /**
     * Replacing reads as replacing everywhere something is actually overwritten.
     * On a duplicate nothing is: the two copies have different names, so the
     * answer has to say what it really does — keep this one, drop the other.
     */
    @Test
    void replaceReadsAsReplaceExceptOnADuplicateWhereNothingIsOverwritten() {
        for (PlanConflictType type : PlanConflictType.values()) {
            String expected = type == PlanConflictType.DUPLICATE_MEDIA
                    ? UiText.conflictOptionKeepThis(FR)
                    : UiText.conflictOptionReplace(FR);
            assertEquals(expected, PlanReviewText.decisionOption(ConflictResolution.REPLACE, type, FR),
                    "wrong replace wording for " + type);
        }
        assertNotEquals(UiText.conflictOptionKeepThis(FR), UiText.conflictOptionReplace(FR),
                "keeping a copy must not read like overwriting a file");
    }

    /**
     * Skipping says a different thing per conflict: keep the file already at the
     * destination, ignore one of two files claiming a destination nobody holds
     * yet, or leave a row that could never run out of the plan.
     */
    @Test
    void skipWordingDependsOnWhatIsActuallyBeingKept() {
        for (PlanConflictType type : PlanConflictType.values()) {
            String expected = switch (type) {
                case DESTINATION_FILE_EXISTS, MEDIA_ALREADY_IN_LIBRARY -> UiText.conflictOptionKeepExisting(FR);
                case DUPLICATE_DESTINATION, DUPLICATE_MEDIA -> UiText.conflictOptionIgnore(FR);
                default -> UiText.conflictOptionDrop(FR);
            };
            assertEquals(expected, PlanReviewText.decisionOption(ConflictResolution.SKIP, type, FR),
                    "wrong skip wording for " + type);
        }
    }

    @Test
    void theThreeSkipMeaningsActuallyReadDifferently() {
        assertNotEquals(UiText.conflictOptionKeepExisting(FR), UiText.conflictOptionDrop(FR),
                "the two skip meanings must not read identically");
        assertNotEquals(UiText.conflictOptionIgnore(FR), UiText.conflictOptionKeepExisting(FR),
                "ignoring a duplicate is not keeping an existing file");
        assertNotEquals(UiText.conflictOptionIgnore(FR), UiText.conflictOptionDrop(FR),
                "ignoring a file is not dropping a row that could never run");
    }

    @Test
    void occupiedDestinationChoicesNameBothFilesAndTheirOutcome() {
        assertEquals("Remplacer l'existant par le fichier source",
                PlanReviewText.decisionOption(
                        ConflictResolution.REPLACE, PlanConflictType.DESTINATION_FILE_EXISTS, FR));
        assertEquals("Conserver les deux fichiers (aucune modification)",
                PlanReviewText.decisionOption(
                        ConflictResolution.SKIP, PlanConflictType.DESTINATION_FILE_EXISTS, FR));
        assertEquals("Supprimer le fichier source et conserver l'existant",
                PlanReviewText.decisionOption(
                        ConflictResolution.DELETE_SOURCE, PlanConflictType.DESTINATION_FILE_EXISTS, FR));
    }

    @Test
    void anOccupiedDestinationOffersAllThreeExplicitOutcomes() {
        assertEquals(
                List.of(ConflictResolution.REPLACE, ConflictResolution.SKIP, ConflictResolution.DELETE_SOURCE),
                PlanReviewPane.offeredFor(PlanConflictType.DESTINATION_FILE_EXISTS));
    }

    @Test
    void aStructuralConflictNeverOffersSourceDeletion() {
        assertEquals(
                List.of(ConflictResolution.SKIP),
                PlanReviewPane.offeredFor(PlanConflictType.PATH_TOO_LONG));
    }

    /** The destructive decision names the existing file when one is identified. */
    @Test
    void deletingAlwaysNamesTheSourceAndAnyExistingFileThatWillRemain() {
        for (PlanConflictType type : PlanConflictType.values()) {
            String expected = switch (type) {
                case DESTINATION_FILE_EXISTS, MEDIA_ALREADY_IN_LIBRARY ->
                        UiText.conflictOptionDeleteSourceKeepExisting(FR);
                default -> UiText.conflictOptionDeleteSource(FR);
            };
            assertEquals(
                    expected,
                    PlanReviewText.decisionOption(ConflictResolution.DELETE_SOURCE, type, FR));
        }
    }

    @Test
    void everyDecisionHasWordingInBothLanguages() {
        for (ConflictResolution resolution : ConflictResolution.values()) {
            for (PlanConflictType type : PlanConflictType.values()) {
                for (AppLanguage language : AppLanguage.values()) {
                    assertNotNull(PlanReviewText.decisionOption(resolution, type, language),
                            "unmapped decision: " + resolution + " on " + type);
                }
            }
        }
    }

    /** A row about to destroy a file must not wear the same pill as the rest. */
    @Test
    void aPlannedDeletionGetsAPillOfItsOwn() {
        String deletion = PlanReviewText.pillVariant(PlanOperationStatus.DELETE);
        assertEquals("danger", deletion);
        for (PlanOperationStatus status : PlanOperationStatus.values()) {
            if (status != PlanOperationStatus.DELETE) {
                assertNotEquals(deletion, PlanReviewText.pillVariant(status),
                        status + " must not look like a deletion");
            }
        }
    }

    /* ---- banner --------------------------------------------------------- */

    @Test
    void anEmptyPlanSaysSoRatherThanClaimingReadiness() {
        assertEquals(UiText.planEmpty(FR), PlanReviewText.banner(true, true, false, FR));
    }

    @Test
    void nothingExecutableAndNotBlockedStillReadsAsEmpty() {
        assertEquals(UiText.planEmpty(FR), PlanReviewText.banner(false, true, false, FR));
    }

    @Test
    void blockedWinsOverHavingNothingExecutable() {
        assertEquals(UiText.planBlocked(FR), PlanReviewText.banner(false, true, true, FR));
    }

    @Test
    void aPlanWithWorkAndNoBlockersReadsAsReady() {
        assertEquals(UiText.planReady(FR), PlanReviewText.banner(false, false, false, FR));
    }
}
