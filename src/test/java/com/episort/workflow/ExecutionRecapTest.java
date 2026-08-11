package com.episort.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.persistence.FileExecutionJournal;
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
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecutionRecapTest {
    @TempDir
    Path tempDir;

    private final OperationPlanner planner = new OperationPlanner();

    @Test
    void theRecapListsEveryOutcomeIncludingFilesTheRunNeverOpened() throws IOException {
        Path workspace = workspace();
        Path moving = file(workspace, "show.s01e01.mkv");
        Path ignored = file(workspace, "sample.mkv");
        Path unsupported = file(workspace, "trailer.wmv");
        Path duplicate = file(workspace, "show.s01e01.dup.mkv");
        Path unassigned = file(workspace, "mystery.mkv");

        OperationPlan plan = planner.plan(workspace, List.of(
                episode(moving, "Show", 1, 1),
                excluded(ignored, PlanExclusionReason.IGNORED),
                excluded(unsupported, PlanExclusionReason.UNSUPPORTED),
                excluded(duplicate, PlanExclusionReason.DUPLICATE),
                PlanSourceItem.forSource(unassigned, ".mkv", PlanMediaKind.SERIES_EPISODE).build()));

        ExecutionReport report = new ExecutionService(journal()).execute(ApprovedPlan.lock(plan));
        ExecutionRecap recap = new ExecutionRecap(plan, report);

        assertEquals(1, recap.moved().size());
        assertEquals(List.of(ignored.toRealPath()), recap.ignoredFiles());
        assertEquals(List.of(unsupported.toRealPath()), recap.unsupportedFiles());
        assertEquals(List.of(duplicate.toRealPath()), recap.duplicateExcludedFiles());
        assertEquals(List.of(unassigned.toRealPath()), recap.unassignedFiles());
        assertEquals(4, recap.untouched().size());
        assertTrue(recap.completeSuccess());
    }

    @Test
    void aPartialRunIsNeverPresentedAsACompleteSuccess() throws IOException {
        Path workspace = workspace();
        Path healthy = file(workspace, "show.s01e01.mkv");
        Path vanishing = file(workspace, "show.s01e02.mkv");
        OperationPlan plan = planner.plan(workspace, List.of(
                episode(healthy, "Show", 1, 1),
                episode(vanishing, "Show", 1, 2)));
        Files.delete(vanishing);

        ExecutionRecap recap = new ExecutionRecap(
                plan, new ExecutionService(journal()).execute(ApprovedPlan.lock(plan)));

        assertFalse(recap.completeSuccess());
        assertTrue(recap.partialSuccess());
        assertEquals(1, recap.failed().size());
    }

    @Test
    void failuresCarryARecoveryHintAndADiagnosticLocation() throws IOException {
        Path workspace = workspace();
        Path vanishing = file(workspace, "show.s01e01.mkv");
        OperationPlan plan = planner.plan(workspace, List.of(episode(vanishing, "Show", 1, 1)));
        Files.delete(vanishing);

        ExecutionRecap recap = new ExecutionRecap(
                plan, new ExecutionService(journal()).execute(ApprovedPlan.lock(plan)));

        assertEquals(1, recap.recoveryHints().size());
        assertTrue(recap.recoveryHints().getFirst().startsWith(ExecutionService.ERROR_SOURCE_MISSING));
        assertTrue(recap.diagnosticLocation().isPresent());
    }

    @Test
    void theRecapNeverCarriesCredentialsOrTokens() throws IOException {
        Path workspace = workspace();
        Path source = file(workspace, "show.s01e01.mkv");
        OperationPlan plan = planner.plan(workspace, List.of(episode(source, "Show", 1, 1)));

        ExecutionRecap recap = new ExecutionRecap(
                plan, new ExecutionService(journal()).execute(ApprovedPlan.lock(plan)));

        String rendered = recap.toString().toLowerCase(Locale.ROOT);
        assertFalse(rendered.contains("apikey"));
        assertFalse(rendered.contains("api-key"));
        assertFalse(rendered.contains("bearer"));
        assertFalse(rendered.contains("pin="));
        assertFalse(rendered.contains("token"));
    }

    @Test
    void filesAlreadyAtTheirDestinationAreReportedAsUntouchedNotMoved() throws IOException {
        Path workspace = workspace();
        Path destination = workspace.resolve(Path.of("Show", "Season 01", "Show - S01E01.mkv"));
        Files.createDirectories(destination.getParent());
        Files.writeString(destination, "already sorted");
        OperationPlan plan = planner.plan(workspace, List.of(episode(destination, "Show", 1, 1)));

        ExecutionRecap recap = new ExecutionRecap(
                plan, new ExecutionService(journal()).execute(ApprovedPlan.lock(plan)));

        assertTrue(recap.moved().isEmpty());
        assertEquals(1, recap.untouched().size());
        assertTrue(Files.exists(destination));
    }

    private FileExecutionJournal journal() {
        return new FileExecutionJournal(tempDir.resolve("diagnostics").resolve("journal.jsonl"));
    }

    private Path workspace() throws IOException {
        return Files.createDirectories(tempDir.resolve("workspace"));
    }

    private static Path file(Path workspace, String name) throws IOException {
        Path path = workspace.resolve(name);
        Files.writeString(path, "video-bytes");
        return path;
    }

    private static PlanSourceItem episode(Path source, String series, int season, int episode) {
        return PlanSourceItem.forSource(source, ".mkv", PlanMediaKind.SERIES_EPISODE)
                .series(series, season, episode)
                .build();
    }

    private static PlanSourceItem excluded(Path source, PlanExclusionReason reason) {
        return PlanSourceItem.forSource(source, ".mkv", PlanMediaKind.SERIES_EPISODE)
                .series("Show", 9, 9)
                .excluded(reason)
                .build();
    }
}
