package com.episort.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.planning.ApprovedPlan;
import com.episort.planning.OperationPlan;
import com.episort.planning.OperationPlanner;
import com.episort.planning.PlanExclusionReason;
import com.episort.planning.PlanMediaKind;
import com.episort.planning.PlanSourceItem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlanApprovalServiceTest {
    @TempDir
    Path tempDir;

    private final PlanApprovalService approval = new PlanApprovalService();
    private final OperationPlanner planner = new OperationPlanner();

    @Test
    void executionIsRefusedWhileThePatternGateIsOpen() throws IOException {
        ReviewSession session = new ReviewSession();
        session.replaceItems(List.of(readyItem("show.mkv")));
        OperationPlan plan = simplePlan();

        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> approval.approve(session, plan));

        assertTrue(failure.getMessage().contains(ReviewSession.BLOCKER_PATTERN_NOT_VALIDATED));
    }

    @Test
    void executionIsRefusedWhileTheExactPlanGateIsOpen() throws IOException {
        ReviewSession session = new ReviewSession();
        session.replaceItems(List.of(readyItem("show.mkv")));
        session.validatePattern();
        session.setExactPlanExists(true);
        OperationPlan plan = simplePlan();

        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> approval.approve(session, plan));

        assertTrue(failure.getMessage().contains(ReviewSession.BLOCKER_EXACT_PLAN_NOT_VALIDATED));
    }

    @Test
    void bothGatesClosedProducesAnImmutableApprovedPlan() throws IOException {
        ReviewSession session = validatedSession();
        OperationPlan plan = simplePlan();

        ApprovedPlan approved = approval.approve(session, plan);

        assertEquals(1, approved.size());
        assertEquals(plan.workspaceRoot(), approved.workspaceRoot());
        assertThrows(UnsupportedOperationException.class,
                () -> approved.operations().add(plan.executableOperations().getFirst()));
    }

    @Test
    void approvalDropsIgnoredUnsupportedDuplicateAndUnassignedItems() throws IOException {
        Path workspace = workspace();
        Path executable = file(workspace, "show.s01e01.mkv");
        Path ignored = file(workspace, "ignored.mkv");
        Path unassigned = file(workspace, "mystery.mkv");
        OperationPlan plan = planner.plan(workspace, List.of(
                PlanSourceItem.forSource(executable, ".mkv", PlanMediaKind.SERIES_EPISODE)
                        .series("Show", 1, 1).build(),
                PlanSourceItem.forSource(ignored, ".mkv", PlanMediaKind.SERIES_EPISODE)
                        .series("Show", 1, 2).excluded(PlanExclusionReason.IGNORED).build(),
                PlanSourceItem.forSource(unassigned, ".mkv", PlanMediaKind.SERIES_EPISODE).build()));

        ApprovedPlan approved = approval.approve(validatedSession(), plan);

        assertEquals(1, approved.size());
        assertEquals(executable.toRealPath(), approved.operations().getFirst().sourcePath());
    }

    @Test
    void aPlanWithBlockingConflictsCanNeverBeLocked() throws IOException {
        Path workspace = workspace();
        Path first = file(workspace, "a.mkv");
        Path second = file(workspace, "b.mkv");
        OperationPlan plan = planner.plan(workspace, List.of(
                PlanSourceItem.forSource(first, ".mkv", PlanMediaKind.SERIES_EPISODE).series("Show", 1, 1).build(),
                PlanSourceItem.forSource(second, ".mkv", PlanMediaKind.SERIES_EPISODE).series("Show", 1, 1).build()));

        assertThrows(IllegalStateException.class, () -> ApprovedPlan.lock(plan));
    }

    @Test
    void eligibilitySurfacesPlanConflictsEvenWhenTheSessionGatesLookClosed() throws IOException {
        Path workspace = workspace();
        Path first = file(workspace, "a.mkv");
        Path second = file(workspace, "b.mkv");
        OperationPlan conflicting = planner.plan(workspace, List.of(
                PlanSourceItem.forSource(first, ".mkv", PlanMediaKind.SERIES_EPISODE).series("Show", 1, 1).build(),
                PlanSourceItem.forSource(second, ".mkv", PlanMediaKind.SERIES_EPISODE).series("Show", 1, 1).build()));

        ExecutionEligibility eligibility = approval.eligibility(validatedSession(), conflicting);

        assertFalse(eligibility.executable());
        assertTrue(eligibility.blockers().contains(ReviewSession.BLOCKER_BLOCKING_CONFLICTS));
    }

    private ReviewSession validatedSession() {
        ReviewSession session = new ReviewSession();
        session.replaceItems(List.of(readyItem("show.mkv")));
        session.validatePattern();
        session.setExactPlanExists(true);
        session.validateExactPlan();
        return session;
    }

    private OperationPlan simplePlan() throws IOException {
        Path workspace = workspace();
        Path source = file(workspace, "show.s01e01.mkv");
        return planner.plan(workspace, List.of(
                PlanSourceItem.forSource(source, ".mkv", PlanMediaKind.SERIES_EPISODE)
                        .series("Show", 1, 1).build()));
    }

    private Path workspace() throws IOException {
        return Files.createDirectories(tempDir.resolve("workspace"));
    }

    private static Path file(Path workspace, String name) throws IOException {
        Path path = workspace.resolve(name);
        Files.writeString(path, "video-bytes");
        return path;
    }

    private static ReviewItem readyItem(String name) {
        return new ReviewItem(
                Path.of("C:/Media").resolve(name),
                Optional.of(name),
                ReviewMatchState.READY,
                OptionalDouble.of(0.9),
                false,
                false,
                false);
    }
}
