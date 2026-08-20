package com.episort.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.persistence.ExecutionJournalEntry;
import com.episort.persistence.ExecutionRunState;
import com.episort.persistence.FileExecutionJournal;
import com.episort.planning.ApprovedPlan;
import com.episort.planning.ConflictResolution;
import com.episort.planning.OperationPlan;
import com.episort.planning.OperationPlanner;
import com.episort.planning.PlanConflictResolver;
import com.episort.planning.PlanMediaKind;
import com.episort.planning.PlanSourceItem;
import com.episort.planning.PlannedOperation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecutionServiceTest {
    @TempDir
    Path tempDir;

    private final OperationPlanner planner = new OperationPlanner();

    @Test
    void approvedFilesAreMovedAndTheRunIsReportedAsCompleteSuccess() throws IOException {
        Path workspace = workspace();
        Path first = file(workspace, "show.s01e01.mkv");
        Path second = file(workspace, "show.s01e02.mkv");
        ApprovedPlan plan = ApprovedPlan.lock(planner.plan(workspace, List.of(
                episode(first, "Show", 1, 1, "Pilot"),
                episode(second, "Show", 1, 2, "Second"))));

        ExecutionReport report = service().execute(plan);

        assertTrue(report.completeSuccess());
        assertFalse(report.partialSuccess());
        assertEquals(2, report.moved().size());
        assertTrue(Files.exists(workspace.resolve(Path.of("Show", "Season 01", "Show - S01E01 - Pilot.mkv"))));
        assertFalse(Files.exists(first));
    }

    @Test
    void aRenameInsideTheSameFolderIsReportedAsRenamedNotMoved() throws IOException {
        Path workspace = workspace();
        // Movies stay at the workspace root, so cleaning up a movie's name never
        // crosses a folder boundary.
        Path source = file(workspace, "dune.2021.mkv");

        ApprovedPlan plan = ApprovedPlan.lock(planner.plan(workspace, List.of(
                PlanSourceItem.forSource(source, ".mkv", PlanMediaKind.MOVIE).movie("Dune", 2021).build())));

        ExecutionReport report = service().execute(plan);

        assertEquals(1, report.renamed().size());
        assertTrue(report.moved().isEmpty());
    }

    @Test
    void oneFailedFileIsNeverReportedAsFullSuccess() throws IOException {
        Path workspace = workspace();
        Path healthy = file(workspace, "show.s01e01.mkv");
        Path vanishing = file(workspace, "show.s01e02.mkv");
        ApprovedPlan plan = ApprovedPlan.lock(planner.plan(workspace, List.of(
                episode(healthy, "Show", 1, 1, "Pilot"),
                episode(vanishing, "Show", 1, 2, "Second"))));
        Files.delete(vanishing);

        ExecutionReport report = service().execute(plan);

        assertFalse(report.completeSuccess());
        assertTrue(report.partialSuccess());
        assertEquals(1, report.succeeded().size());
        assertEquals(1, report.failed().size());
        assertEquals(ExecutionService.ERROR_SOURCE_MISSING,
                report.failed().getFirst().error().orElseThrow().code());
    }

    @Test
    void aRecoverableFailureCanBeRetriedAndThenSucceed() throws IOException {
        Path workspace = workspace();
        Path source = file(workspace, "show.s01e01.mkv");
        Path blocker = workspace.resolve(Path.of("Show", "Season 01", "Show - S01E01 - Pilot.mkv"));
        ApprovedPlan plan = ApprovedPlan.lock(planner.plan(workspace, List.of(
                episode(source, "Show", 1, 1, "Pilot"))));
        Files.createDirectories(blocker.getParent());
        Files.writeString(blocker, "temporarily in the way");

        List<Integer> attempts = new ArrayList<>();
        ExecutionReport report = service().execute(plan, (operation, error, attempt) -> {
            attempts.add(attempt);
            if (attempt == 1) {
                unblock(blocker);
                return ExecutionFailureDecision.RETRY;
            }
            return ExecutionFailureDecision.CONTINUE;
        }, ExecutionProgressListener.noop());

        assertEquals(List.of(1), attempts);
        assertTrue(report.completeSuccess());
    }

    @Test
    void abortingLeavesEveryRemainingApprovedFileUntouchedAndReportedAsSkipped() throws IOException {
        Path workspace = workspace();
        Path missing = file(workspace, "show.s01e01.mkv");
        Path untouched = file(workspace, "show.s01e02.mkv");
        ApprovedPlan plan = ApprovedPlan.lock(planner.plan(workspace, List.of(
                episode(missing, "Show", 1, 1, "Pilot"),
                episode(untouched, "Show", 1, 2, "Second"))));
        Files.delete(missing);

        ExecutionReport report = service().execute(
                plan,
                (operation, error, attempt) -> ExecutionFailureDecision.ABORT,
                ExecutionProgressListener.noop());

        assertTrue(report.aborted());
        assertEquals(1, report.failed().size());
        assertEquals(1, report.skipped().size());
        assertTrue(Files.exists(untouched), "an aborted run must leave the remaining files alone");
    }

    @Test
    void progressIsReportedWithTheFileBeingWorkedOn() throws IOException {
        Path workspace = workspace();
        Path source = file(workspace, "show.s01e01.mkv");
        ApprovedPlan plan = ApprovedPlan.lock(planner.plan(workspace, List.of(
                episode(source, "Show", 1, 1, "Pilot"))));

        List<ExecutionProgress> updates = new ArrayList<>();
        service().execute(plan, ExecutionFailureHandler.alwaysContinue(), updates::add);

        assertEquals(0, updates.getFirst().completed());
        assertEquals(1, updates.getLast().completed());
        assertEquals(1.0, updates.getLast().fraction());
        assertTrue(updates.stream().anyMatch(progress -> progress.currentSource().isPresent()));
    }

    @Test
    void theJournalRecordsTheRunOutsideTheWorkspace() throws IOException {
        Path workspace = workspace();
        Path journalFile = tempDir.resolve("diagnostics").resolve("execution-journal.jsonl");
        Path source = file(workspace, "show.s01e01.mkv");
        ApprovedPlan plan = ApprovedPlan.lock(planner.plan(workspace, List.of(
                episode(source, "Show", 1, 1, "Pilot"))));

        FileExecutionJournal journal = new FileExecutionJournal(journalFile);
        ExecutionReport report = new ExecutionService(journal).execute(plan);

        assertFalse(journalFile.startsWith(workspace), "diagnostics must not live inside the media workspace");
        assertEquals(journalFile, report.journalLocation().orElseThrow());
        List<ExecutionJournalEntry> entries = journal.readAll();
        assertEquals(ExecutionRunState.RUNNING, entries.getFirst().state());
        assertEquals(ExecutionRunState.COMPLETED, entries.getLast().state());
        assertTrue(journal.interruptedRun().isEmpty());
    }

    @Test
    void aRunThatNeverFinishesIsDetectedAsInterrupted() {
        Path journalFile = tempDir.resolve("diagnostics").resolve("execution-journal.jsonl");
        FileExecutionJournal journal = new FileExecutionJournal(journalFile);
        UUID runId = UUID.randomUUID();

        journal.started(runId, tempDir.resolve("workspace"), 10);
        journal.progressed(runId, tempDir.resolve("workspace"), 10, 4);

        ExecutionJournalEntry interrupted = journal.interruptedRun().orElseThrow();
        assertEquals(runId, interrupted.runId());
        assertEquals(4, interrupted.completedOperations());
        assertEquals(10, interrupted.totalOperations());
    }

    @Test
    void anExecutedFailureRecordsTheRunAsAborted() throws IOException {
        Path workspace = workspace();
        Path journalFile = tempDir.resolve("diagnostics").resolve("execution-journal.jsonl");
        Path source = file(workspace, "show.s01e01.mkv");
        ApprovedPlan plan = ApprovedPlan.lock(planner.plan(workspace, List.of(
                episode(source, "Show", 1, 1, "Pilot"))));
        Files.delete(source);

        FileExecutionJournal journal = new FileExecutionJournal(journalFile);
        new ExecutionService(journal).execute(plan);

        assertEquals(ExecutionRunState.ABORTED, journal.readAll().getLast().state());
    }

    @Test
    void executingAnEmptyPlanIsHarmlessAndNotReportedAsSuccess() throws IOException {
        Path workspace = workspace();
        ApprovedPlan plan = ApprovedPlan.lock(OperationPlan.empty(workspace));

        ExecutionReport report = service().execute(plan);

        assertTrue(report.results().isEmpty());
        assertFalse(report.completeSuccess());
    }

    @Test
    void sourceFoldersWithIgnoredSidecarsAreTaggedForManualSortingAfterMoves() throws IOException {
        Path workspace = workspace();
        Path container = Files.createDirectories(workspace.resolve("86 Eighty Six [MULTI]"));
        Path release = Files.createDirectories(container.resolve("Show.S01.1080p"));
        Path source = file(release, "show.s01e01.mkv");
        Files.writeString(release.resolve("show.nfo"), "leftover metadata");
        Files.createDirectories(release.resolve("Sample"));
        Path canonicalContainer = container.toRealPath();
        ApprovedPlan plan = ApprovedPlan.lock(planner.plan(workspace, List.of(
                episode(source, "Show", 1, 1, "Pilot"))));
        ExecutionReport report = service().execute(plan);

        assertTrue(report.completeSuccess());
        Path tagged = workspace.resolve("[TRI]86 Eighty Six [MULTI]");
        assertFalse(Files.exists(container));
        assertTrue(Files.exists(tagged.resolve("Show.S01.1080p/show.nfo")));
        assertTrue(Files.isDirectory(tagged.resolve("Show.S01.1080p/Sample")));
        assertTrue(report.deletedSourceFolders().isEmpty());
        assertEquals(List.of(new FolderRenameResult(canonicalContainer, tagged.toRealPath())),
                report.renamedSourceFolders());
        assertTrue(Files.exists(workspace), "the workspace root is never a candidate");
    }

    @Test
    void aSourceFolderStillHoldingAFailedFileIsKept() throws IOException {
        Path workspace = workspace();
        Path release = Files.createDirectories(workspace.resolve("Show.S01.1080p"));
        Path moving = file(release, "show.s01e01.mkv");
        Path failing = file(release, "show.s01e02.mkv");
        ApprovedPlan plan = ApprovedPlan.lock(planner.plan(workspace, List.of(
                episode(moving, "Show", 1, 1, "Pilot"),
                episode(failing, "Show", 1, 2, "Second"))));
        Path blocker = workspace.resolve(Path.of("Show", "Season 01", "Show - S01E02 - Second.mkv"));
        Files.createDirectories(blocker.getParent());
        Files.writeString(blocker, "in the way");

        ExecutionReport report = service().execute(plan);

        assertEquals(1, report.failed().size());
        assertTrue(report.deletedSourceFolders().isEmpty());
        assertTrue(report.renamedSourceFolders().isEmpty());
        assertTrue(Files.exists(failing), "the file that never moved must still be where it was");
    }

    @Test
    void anExistingTriFolderLeavesTheNonEmptySourceFolderUntouched() throws IOException {
        Path workspace = workspace();
        Path container = Files.createDirectories(workspace.resolve("86 Eighty Six [MULTI]"));
        Path release = Files.createDirectories(container.resolve("Show.S01.1080p"));
        Path source = file(release, "show.s01e01.mkv");
        Files.writeString(release.resolve("show.nfo"), "leftover metadata");
        Files.createDirectory(workspace.resolve("[TRI]86 Eighty Six [MULTI]"));
        ApprovedPlan plan = ApprovedPlan.lock(planner.plan(workspace, List.of(
                episode(source, "Show", 1, 1, "Pilot"))));

        ExecutionReport report = service().execute(plan);

        assertTrue(Files.exists(release.resolve("show.nfo")));
        assertTrue(report.renamedSourceFolders().isEmpty());
    }

    @Test
    void aFolderAlreadyTaggedForSortingIsNeverPrefixedAgain() throws IOException {
        Path workspace = workspace();
        Path container = Files.createDirectories(workspace.resolve("[TRI]86 Eighty Six [MULTI]"));
        Path release = Files.createDirectories(container.resolve("Show.S01.1080p"));
        Path source = file(release, "show.s01e01.mkv");
        Files.writeString(release.resolve("show.nfo"), "leftover metadata");
        ApprovedPlan plan = ApprovedPlan.lock(planner.plan(workspace, List.of(
                episode(source, "Show", 1, 1, "Pilot"))));

        ExecutionReport report = service().execute(plan);

        assertTrue(Files.exists(release.resolve("show.nfo")));
        assertFalse(Files.exists(workspace.resolve("[TRI][TRI]86 Eighty Six [MULTI]")));
        assertTrue(report.renamedSourceFolders().isEmpty());
    }

    @Test
    void filesSittingAtTheWorkspaceRootLeaveTheRootAlone() throws IOException {
        Path workspace = workspace();
        Path source = file(workspace, "show.s01e01.mkv");
        ApprovedPlan plan = ApprovedPlan.lock(planner.plan(workspace, List.of(
                episode(source, "Show", 1, 1, "Pilot"))));

        ExecutionReport report = service().execute(plan);

        assertTrue(report.deletedSourceFolders().isEmpty());
        assertTrue(Files.exists(workspace));
    }

    @Test
    void emptySourceFoldersAndTheirEmptyParentsAreDeleted() throws IOException {
        Path workspace = workspace();
        Path release = Files.createDirectories(workspace.resolve(Path.of("Downloads", "Show.S01")));
        Path source = file(release, "show.s01e01.mkv");
        ApprovedPlan plan = ApprovedPlan.lock(planner.plan(workspace, List.of(
                episode(source, "Show", 1, 1, "Pilot"))));

        ExecutionReport report = service().execute(plan);

        assertFalse(Files.exists(workspace.resolve("Downloads")), "the now-empty parent must go too");
        assertFalse(Files.exists(release));
        assertEquals(2, report.deletedSourceFolders().size());
        assertTrue(Files.exists(workspace.resolve(Path.of("Show", "Season 01", "Show - S01E01 - Pilot.mkv"))));
    }

    @Test
    void anAbortedRunNeverDeletesAnything() throws IOException {
        Path workspace = workspace();
        Path release = Files.createDirectories(workspace.resolve("Show.S01"));
        Path missing = file(release, "show.s01e01.mkv");
        ApprovedPlan plan = ApprovedPlan.lock(planner.plan(workspace, List.of(
                episode(missing, "Show", 1, 1, "Pilot"))));
        Files.delete(missing);

        ExecutionReport report = service().execute(
                plan,
                (operation, error, attempt) -> ExecutionFailureDecision.ABORT,
                ExecutionProgressListener.noop());

        assertTrue(report.deletedSourceFolders().isEmpty());
        assertTrue(Files.exists(release));
    }

    /**
     * The duplicate the user chose to throw away goes, the file that won its
     * destination moves, and the run reports both without calling the deletion a
     * failure or a skip.
    */
    @Test
    void approvedDuplicateDeletionRemovesOnlyTheChosenSource() throws IOException {
        Path workspace = workspace();
        Path duplicate = file(workspace, "show.s01e01.720p.mkv");
        Path keeper = file(workspace, "show.s01e01.1080p.mkv");

        OperationPlan planned = planner.plan(workspace, List.of(
                episode(duplicate, "Show", 1, 1, "Pilot"),
                episode(keeper, "Show", 1, 1, "Pilot")));
        Path discarded = planned.conflicts().getFirst().sourcePath();
        Map<Path, ConflictResolution> decisions = planned.conflicts().stream().collect(
                java.util.stream.Collectors.toMap(
                        PlannedOperation::sourcePath,
                        operation -> operation.sourcePath().equals(discarded)
                                ? ConflictResolution.DELETE_SOURCE
                                : ConflictResolution.REPLACE));
        OperationPlan resolved = new PlanConflictResolver().resolve(planned, decisions);
        PlannedOperation kept = resolved.executableOperations().getFirst();
        Path destination = kept.destinationPath().orElseThrow();

        // ApprovedPlan is the second validation gate: execution cannot receive
        // the deletion before the exact rebuilt plan has been locked.
        ExecutionReport report = service().execute(ApprovedPlan.lock(resolved));

        assertEquals(List.of(discarded), report.deleted().stream()
                .map(FileExecutionResult::sourcePath)
                .toList());
        assertFalse(Files.exists(discarded));
        assertTrue(Files.exists(destination));
    }

    /**
     * The case that used to double the size of a library: the same episode
     * already on disk under a name without its episode title. The move lands and
     * the copy it supersedes goes with it, so the season folder holds one file.
     */
    @Test
    void libraryDuplicateCanOnlyBeIgnored() throws IOException {
        Path workspace = workspace();
        Path incoming = file(workspace, "show.s01e01.1080p.mkv");
        Path seasonFolder = Files.createDirectories(workspace.resolve(Path.of("Show", "Season 01")));
        Path existing = seasonFolder.resolve("Show - S01E01.mkv");
        Files.writeString(existing, "older copy");

        OperationPlan planned = planner.plan(workspace, List.of(episode(incoming, "Show", 1, 1, "Pilot")));
        assertThrows(IllegalArgumentException.class, () -> new PlanConflictResolver().resolve(
                planned, Map.of(incoming.toRealPath(), ConflictResolution.REPLACE)));
        assertTrue(Files.exists(incoming));
        assertTrue(Files.exists(existing));
    }

    @Test
    void ignoredDuplicateIsNeverTouchedByExecution() throws IOException {
        Path workspace = workspace();
        Path duplicate = file(workspace, "show.s01e01.720p.mkv");
        Path keeper = file(workspace, "show.s01e01.1080p.mkv");

        OperationPlan planned = planner.plan(workspace, List.of(
                episode(duplicate, "Show", 1, 1, "Pilot"),
                episode(keeper, "Show", 1, 1, "Pilot")));
        Map<Path, ConflictResolution> ignored = planned.conflicts().stream().collect(
                java.util.stream.Collectors.toMap(
                        operation -> operation.sourcePath(), operation -> ConflictResolution.SKIP));
        OperationPlan resolved = new PlanConflictResolver().resolve(planned, ignored);

        ExecutionReport report = service().execute(ApprovedPlan.lock(resolved));

        assertTrue(report.deleted().isEmpty());
        assertTrue(report.failed().isEmpty());
        assertTrue(Files.exists(duplicate));
    }

    private ExecutionService service() {
        return new ExecutionService(new FileExecutionJournal(tempDir.resolve("journal.jsonl")));
    }

    private static void unblock(Path blocker) {
        try {
            Files.delete(blocker);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Path workspace() throws IOException {
        return Files.createDirectories(tempDir.resolve("workspace"));
    }

    private static Path file(Path workspace, String name) throws IOException {
        Path path = workspace.resolve(name);
        Files.writeString(path, "video-bytes");
        return path;
    }

    private static PlanSourceItem episode(Path source, String series, int season, int episode, String title) {
        return PlanSourceItem.forSource(source, ".mkv", PlanMediaKind.SERIES_EPISODE)
                .series(series, season, episode)
                .episodeTitle(title)
                .build();
    }
}
