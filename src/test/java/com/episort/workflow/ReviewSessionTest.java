package com.episort.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class ReviewSessionTest {
    @Test
    void confidenceAndValidationRemainSeparate() {
        ReviewSession session = new ReviewSession();
        session.replaceItems(List.of(item("ready.mkv", ReviewMatchState.READY, 0.99, false)));

        assertFalse(session.patternValidated());
        assertEquals(OptionalDouble.of(0.99), session.items().getFirst().confidence());
    }

    @Test
    void blockingConflictsPreventPatternValidation() {
        ReviewSession session = new ReviewSession();
        session.replaceItems(List.of(item("duplicate.mkv", ReviewMatchState.DUPLICATE, 0.95, true)));

        assertFalse(session.validatePattern());
        assertFalse(session.patternValidated());
    }

    @Test
    void manualCorrectionInvalidatesPreviousValidationWithoutTouchingNeighbors() {
        ReviewSession session = new ReviewSession();
        session.replaceItems(List.of(
                item("first.mkv", ReviewMatchState.READY, 0.95, false),
                item("second.mkv", ReviewMatchState.READY, 0.92, false)));
        assertTrue(session.validatePattern());

        session.upsert(item("first.mkv", ReviewMatchState.NEEDS_REVIEW, 0.95, false));

        assertFalse(session.patternValidated());
        assertEquals(ReviewMatchState.NEEDS_REVIEW, session.items().get(0).matchState());
        assertEquals(ReviewMatchState.READY, session.items().get(1).matchState());
    }

    @Test
    void exactPlanValidationCannotExistBeforePlanAndBothGatesBlockExecution() {
        ReviewSession session = new ReviewSession();
        session.replaceItems(List.of(item("ready.mkv", ReviewMatchState.READY, 0.95, false)));

        assertFalse(session.validateExactPlan());
        assertEquals(List.of("PATTERN_NOT_VALIDATED", "EXACT_PLAN_MISSING"),
                session.executionEligibility().blockers());

        assertTrue(session.validatePattern());
        assertEquals(List.of("EXACT_PLAN_MISSING"), session.executionEligibility().blockers());

        session.setExactPlanExists(true);
        assertEquals(List.of("EXACT_PLAN_NOT_VALIDATED"), session.executionEligibility().blockers());
        assertTrue(session.validateExactPlan());
        assertTrue(session.executionEligibility().executable());
    }

    @Test
    void replacingWithSameReviewStatePreservesValidationAcrossNavigation() {
        ReviewSession session = new ReviewSession();
        ReviewItem ready = item("ready.mkv", ReviewMatchState.READY, 0.95, false);
        session.replaceItems(List.of(ready));
        assertTrue(session.validatePattern());

        session.replaceItems(List.of(ready));

        assertTrue(session.patternValidated());
    }

    @Test
    void reorderingReviewedItemsReopensThePatternGate() {
        ReviewSession session = new ReviewSession();
        ReviewItem first = item("first.mkv", ReviewMatchState.READY, 0.95, false);
        ReviewItem second = item("second.mkv", ReviewMatchState.READY, 0.92, false);
        session.replaceItems(List.of(first, second));
        assertTrue(session.validatePattern());

        session.replaceItems(List.of(second, first));

        assertFalse(session.patternValidated());
        assertTrue(session.validatedPatternItems().isEmpty());
    }

    @Test
    void validationFreezesTheReviewedItemsUsedForPlanning() {
        ReviewSession session = new ReviewSession();
        session.replaceItems(List.of(item("first.mkv", ReviewMatchState.READY, 0.95, false)));
        assertTrue(session.validatePattern());

        List<ReviewItem> frozen = session.validatedPatternItems().orElseThrow();
        session.upsert(item("second.mkv", ReviewMatchState.READY, 0.90, false));

        assertEquals(1, frozen.size());
        assertTrue(session.validatedPatternItems().isEmpty());
    }

    @Test
    void blockedPatternValidationLeavesNoFrozenSnapshot() {
        ReviewSession session = new ReviewSession();
        session.replaceItems(List.of(item("duplicate.mkv", ReviewMatchState.DUPLICATE, 0.95, true)));

        assertFalse(session.validatePattern());
        assertTrue(session.validatedPatternItems().isEmpty());
        assertEquals(
                List.of(
                        ReviewSession.BLOCKER_BLOCKING_CONFLICTS,
                        ReviewSession.BLOCKER_PATTERN_NOT_VALIDATED,
                        ReviewSession.BLOCKER_EXACT_PLAN_MISSING),
                session.executionEligibility().blockers());
    }

    @Test
    void exactPlanValidationRequiresThePatternGateFirst() {
        ReviewSession session = new ReviewSession();
        session.replaceItems(List.of(item("ready.mkv", ReviewMatchState.READY, 0.95, false)));
        session.setExactPlanExists(true);

        assertFalse(session.validateExactPlan());
        assertFalse(session.exactPlanValidated());
    }

    @Test
    void correctingAnItemDiscardsTheGeneratedPlan() {
        ReviewSession session = new ReviewSession();
        session.replaceItems(List.of(item("ready.mkv", ReviewMatchState.READY, 0.95, false)));
        assertTrue(session.validatePattern());
        session.setExactPlanExists(true);
        assertTrue(session.validateExactPlan());
        assertTrue(session.executionEligibility().executable());

        session.upsert(item("ready.mkv", ReviewMatchState.NEEDS_REVIEW, 0.95, false));

        assertFalse(session.exactPlanExists());
        assertFalse(session.exactPlanValidated());
        assertEquals(
                List.of(
                        ReviewSession.BLOCKER_PATTERN_NOT_VALIDATED,
                        ReviewSession.BLOCKER_EXACT_PLAN_MISSING),
                session.executionEligibility().blockers());
    }

    @Test
    void snapshotExposesFrozenItemsSeparatelyFromLiveItems() {
        ReviewSession session = new ReviewSession();
        session.replaceItems(List.of(item("ready.mkv", ReviewMatchState.READY, 0.95, false)));
        assertTrue(session.validatePattern());

        ReviewValidationSnapshot snapshot = session.snapshot();

        assertTrue(snapshot.patternValidated());
        assertFalse(snapshot.exactPlanExists());
        assertEquals(snapshot.items(), snapshot.validatedItems());
    }

    @Test
    void twoThousandReviewedItemsStayWorkable() {
        ReviewSession session = new ReviewSession();
        List<ReviewItem> items = new ArrayList<>(2_000);
        for (int index = 0; index < 2_000; index++) {
            items.add(item("show-" + index + ".mkv", ReviewMatchState.READY, 0.9, false));
        }

        session.replaceItems(items);
        assertTrue(session.validatePattern());
        session.replaceItems(items);

        assertTrue(session.patternValidated());
        assertEquals(2_000, session.validatedPatternItems().orElseThrow().size());
    }

    private static ReviewItem item(String name, ReviewMatchState state, double confidence, boolean blocking) {
        return new ReviewItem(
                Path.of("C:/Media").resolve(name),
                Optional.of(name),
                state,
                OptionalDouble.of(confidence),
                state == ReviewMatchState.IGNORED,
                state == ReviewMatchState.UNSUPPORTED,
                blocking);
    }
}
