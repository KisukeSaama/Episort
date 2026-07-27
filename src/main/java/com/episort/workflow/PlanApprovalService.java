package com.episort.workflow;

import com.episort.planning.ApprovedPlan;
import com.episort.planning.OperationPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The single door to execution (Story 7.1).
 *
 * <p>Both validation gates must be closed before a plan can be locked, and the
 * lock drops everything that is not an approved move — ignored, unsupported,
 * duplicate-excluded, unassigned, and already-in-place files can never reach the
 * executor.
 */
public final class PlanApprovalService {

    /**
     * @throws IllegalStateException when a gate is still open or conflicts
     *         remain; the message lists the blocker codes so the UI can explain
     *         the refusal instead of silently doing nothing
     */
    public ApprovedPlan approve(ReviewSession session, OperationPlan plan) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(plan, "plan");
        ExecutionEligibility eligibility = session.executionEligibility();
        if (!eligibility.executable()) {
            throw new IllegalStateException("Execution is blocked by " + eligibility.blockers());
        }
        return ApprovedPlan.lock(plan);
    }

    /** Non-throwing check, for enabling or disabling the execute action. */
    public ExecutionEligibility eligibility(ReviewSession session, OperationPlan plan) {
        Objects.requireNonNull(session, "session");
        ExecutionEligibility sessionEligibility = session.executionEligibility();
        if (plan == null || !plan.hasBlockingConflicts()) {
            return sessionEligibility;
        }
        List<String> blockers = new ArrayList<>(sessionEligibility.blockers());
        if (!blockers.contains(ReviewSession.BLOCKER_BLOCKING_CONFLICTS)) {
            blockers.addFirst(ReviewSession.BLOCKER_BLOCKING_CONFLICTS);
        }
        return new ExecutionEligibility(false, blockers);
    }
}
