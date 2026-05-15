package com.episort.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
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
