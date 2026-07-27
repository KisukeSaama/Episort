package com.episort.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlanConflictResolverTest {
    @TempDir
    Path tempDir;

    private final OperationPlanner planner = new OperationPlanner();
    private final PlanConflictResolver resolver = new PlanConflictResolver();

    @Test
    void aMoreRecentSourceWinsTheOccupiedDestinationAndIsAllowedToReplaceIt() throws IOException {
        Path workspace = workspace();
        Path source = file(workspace, "show.s01e01.mkv", "2026-01-10T00:00:00Z");
        Path occupied = destination(workspace, "2024-01-01T00:00:00Z");

        OperationPlan plan = planner.plan(workspace, List.of(episode(source)));
        OperationPlan resolved = resolver.resolve(plan, resolver.mostRecentWins(plan));

        assertFalse(resolved.hasBlockingConflicts());
        assertEquals(1, resolved.executableOperations().size());
        PlannedOperation operation = resolved.executableOperations().getFirst();
        assertTrue(operation.replaceExisting());
        assertEquals(occupied.toRealPath(), operation.destinationPath().orElseThrow());
    }

    @Test
    void anOlderSourceLosesAndStaysWhereItIsInsteadOfBeingDeleted() throws IOException {
        Path workspace = workspace();
        Path source = file(workspace, "show.s01e01.mkv", "2020-01-01T00:00:00Z");
        Path occupied = destination(workspace, "2026-01-10T00:00:00Z");

        OperationPlan plan = planner.plan(workspace, List.of(episode(source)));
        OperationPlan resolved = resolver.resolve(plan, resolver.mostRecentWins(plan));

        assertFalse(resolved.hasBlockingConflicts());
        assertTrue(resolved.executableOperations().isEmpty());
        assertEquals(PlanExclusionReason.CONFLICT_SKIPPED,
                resolved.excludedOperations().getFirst().exclusionReason());
        assertTrue(Files.exists(source), "the losing file must stay untouched");
        assertEquals("already there", Files.readString(occupied));
    }

    @Test
    void inADuplicateGroupOnlyTheMostRecentFileKeepsTheDestination() throws IOException {
        Path workspace = workspace();
        Path older = file(workspace, "show.s01e01.720p.mkv", "2021-01-01T00:00:00Z");
        Path newer = file(workspace, "show.s01e01.1080p.mkv", "2026-01-01T00:00:00Z");

        OperationPlan plan = planner.plan(workspace, List.of(episode(older), episode(newer)));
        OperationPlan resolved = resolver.resolve(plan, resolver.mostRecentWins(plan));

        assertFalse(resolved.hasBlockingConflicts());
        assertEquals(1, resolved.executableOperations().size());
        assertEquals(newer.toRealPath(), resolved.executableOperations().getFirst().sourcePath());
        // Nothing occupies the destination yet, so no overwrite permission is granted.
        assertFalse(resolved.executableOperations().getFirst().replaceExisting());
        assertEquals(1, resolved.excludedOperations().size());
        assertTrue(Files.exists(older));
    }

    @Test
    void aConflictThatReplacingCannotFixIsDroppedFromThePlan() throws IOException {
        Path workspace = workspace();
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Path source = outside.resolve("show.s01e01.mkv");
        Files.writeString(source, "video");

        OperationPlan plan = planner.plan(workspace, List.of(episode(source)));
        Map<Path, ConflictResolution> decisions = resolver.mostRecentWins(plan);

        assertEquals(List.of(ConflictResolution.SKIP), List.copyOf(decisions.values()));
        OperationPlan resolved = resolver.resolve(plan, decisions);
        assertFalse(resolved.hasBlockingConflicts());
        assertTrue(resolved.executableOperations().isEmpty());
        assertTrue(Files.exists(source));
    }

    @Test
    void replacingIsRefusedOnAConflictItCannotFix() throws IOException {
        Path workspace = workspace();
        Path source = file(workspace, "show.s01e01.mkv", "2026-01-01T00:00:00Z");
        Files.writeString(workspace.resolve("Show"), "not a folder");

        OperationPlan plan = planner.plan(workspace, List.of(episode(source)));
        Path conflicting = plan.conflicts().getFirst().sourcePath();

        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(plan, Map.of(conflicting, ConflictResolution.REPLACE)));
    }

    /**
     * The answer to a duplicate the user does not want to keep: the loser is
     * planned for removal instead of being left behind to block the next run.
     * Planning it still touches nothing — the file only goes at execution.
     */
    @Test
    void aDuplicateCanBePlannedForDeletionInsteadOfBeingLeftBehind() throws IOException {
        Path workspace = workspace();
        Path older = file(workspace, "show.s01e01.720p.mkv", "2021-01-01T00:00:00Z");
        Path newer = file(workspace, "show.s01e01.1080p.mkv", "2026-01-01T00:00:00Z");

        OperationPlan plan = planner.plan(workspace, List.of(episode(older), episode(newer)));
        OperationPlan resolved = resolver.resolve(plan, Map.of(
                older.toRealPath(), ConflictResolution.DELETE_SOURCE,
                newer.toRealPath(), ConflictResolution.REPLACE));

        assertFalse(resolved.hasBlockingConflicts());
        assertEquals(1, resolved.executableOperations().size());
        assertEquals(newer.toRealPath(), resolved.executableOperations().getFirst().sourcePath());
        assertEquals(1, resolved.deletions().size());
        assertEquals(older.toRealPath(), resolved.deletions().getFirst().sourcePath());
        assertTrue(resolved.deletions().getFirst().destinationPath().isEmpty(),
                "a deletion lands nowhere and must not read as a move");
        assertEquals(2, resolved.mutatingOperations().size());
        assertTrue(Files.exists(older), "resolving must not delete anything itself");
    }

    /** Both mutations reach the executor, in plan order. */
    @Test
    void anApprovedPlanCarriesTheDeletionAlongsideTheMove() throws IOException {
        Path workspace = workspace();
        Path older = file(workspace, "show.s01e01.720p.mkv", "2021-01-01T00:00:00Z");
        Path newer = file(workspace, "show.s01e01.1080p.mkv", "2026-01-01T00:00:00Z");

        OperationPlan plan = planner.plan(workspace, List.of(episode(older), episode(newer)));
        OperationPlan resolved = resolver.resolve(plan, Map.of(
                older.toRealPath(), ConflictResolution.DELETE_SOURCE,
                newer.toRealPath(), ConflictResolution.REPLACE));

        ApprovedPlan approved = ApprovedPlan.lock(resolved);

        assertEquals(2, approved.size());
        assertTrue(approved.operations().stream().anyMatch(PlannedOperation::deletesFile));
        assertTrue(approved.operations().stream().anyMatch(PlannedOperation::movesFile));
    }

    /**
     * A path Windows cannot hold, or a source outside the workspace, is never a
     * reason to destroy the user's file: only a real duplicate can be deleted.
     */
    @Test
    void deletingIsRefusedOnAConflictThatIsNotAboutADuplicate() throws IOException {
        Path workspace = workspace();
        Path source = file(workspace, "show.s01e01.mkv", "2026-01-01T00:00:00Z");
        Files.writeString(workspace.resolve("Show"), "not a folder");

        OperationPlan plan = planner.plan(workspace, List.of(episode(source)));
        Path conflicting = plan.conflicts().getFirst().sourcePath();

        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(plan, Map.of(conflicting, ConflictResolution.DELETE_SOURCE)));
        assertTrue(Files.exists(source));
    }

    @Test
    void deletingIsRefusedForASourceOutsideTheWorkspace() throws IOException {
        Path workspace = workspace();
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Path source = outside.resolve("show.s01e01.mkv");
        Files.writeString(source, "video");

        OperationPlan plan = planner.plan(workspace, List.of(episode(source)));
        Path conflicting = plan.conflicts().getFirst().sourcePath();

        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(plan, Map.of(conflicting, ConflictResolution.DELETE_SOURCE)));
        assertTrue(Files.exists(source));
    }

    /** The automatic answer stays non-destructive: it never proposes a deletion. */
    @Test
    void mostRecentWinsNeverDecidesToDeleteAFile() throws IOException {
        Path workspace = workspace();
        Path older = file(workspace, "show.s01e01.720p.mkv", "2021-01-01T00:00:00Z");
        Path newer = file(workspace, "show.s01e01.1080p.mkv", "2026-01-01T00:00:00Z");

        OperationPlan plan = planner.plan(workspace, List.of(episode(older), episode(newer)));

        assertFalse(resolver.mostRecentWins(plan).containsValue(ConflictResolution.DELETE_SOURCE));
    }

    @Test
    void twoFilesBothLettingThemselvesWinTheSameDestinationStayInConflict() throws IOException {
        Path workspace = workspace();
        Path first = file(workspace, "show.s01e01.720p.mkv", "2021-01-01T00:00:00Z");
        Path second = file(workspace, "show.s01e01.1080p.mkv", "2026-01-01T00:00:00Z");

        OperationPlan plan = planner.plan(workspace, List.of(episode(first), episode(second)));
        OperationPlan resolved = resolver.resolve(plan, Map.of(
                first.toRealPath(), ConflictResolution.REPLACE,
                second.toRealPath(), ConflictResolution.REPLACE));

        assertTrue(resolved.hasBlockingConflicts(), "a resolution must never silently discard a file");
        assertEquals(2, resolved.conflicts().size());
    }

    @Test
    void anUndecidedConflictKeepsBlockingThePlan() throws IOException {
        Path workspace = workspace();
        Path source = file(workspace, "show.s01e01.mkv", "2026-01-01T00:00:00Z");
        destination(workspace, "2024-01-01T00:00:00Z");

        OperationPlan plan = planner.plan(workspace, List.of(episode(source)));
        OperationPlan resolved = resolver.resolve(plan, Map.of());

        assertTrue(resolved.hasBlockingConflicts());
    }

    @Test
    void resolvingCreatesMovesAndDeletesNothingOnDisk() throws IOException {
        Path workspace = workspace();
        Path source = file(workspace, "show.s01e01.mkv", "2026-01-01T00:00:00Z");
        Path occupied = destination(workspace, "2024-01-01T00:00:00Z");

        OperationPlan plan = planner.plan(workspace, List.of(episode(source)));
        resolver.resolve(plan, resolver.mostRecentWins(plan));

        assertTrue(Files.exists(source));
        assertEquals("already there", Files.readString(occupied));
    }

    @Test
    void replacingADuplicateTheLibraryHoldsUnderAnotherNameRetiresThatCopy() throws IOException {
        Path workspace = workspace();
        Path source = file(workspace, "show.s01e01.1080p.mkv", "2026-01-10T00:00:00Z");
        Path existing = libraryCopy(workspace, "Show - S01E01.mkv", "2020-01-01T00:00:00Z");

        OperationPlan plan = planner.plan(workspace, List.of(episode(source)));
        OperationPlan resolved = resolver.resolve(plan, resolver.mostRecentWins(plan));

        assertFalse(resolved.hasBlockingConflicts());
        PlannedOperation operation = resolved.executableOperations().getFirst();
        assertEquals(existing.toRealPath(), operation.supersedes().orElseThrow());
        // Nothing stands at the destination itself, so no overwrite is granted.
        assertFalse(operation.replaceExisting());
        assertTrue(Files.exists(existing), "resolving must not delete anything on its own");
    }

    @Test
    void anOlderIncomingCopyLosesToTheOneTheLibraryAlreadyHolds() throws IOException {
        Path workspace = workspace();
        Path source = file(workspace, "show.s01e01.480p.mkv", "2020-01-01T00:00:00Z");
        Path existing = libraryCopy(workspace, "Show - S01E01.mkv", "2026-01-10T00:00:00Z");

        OperationPlan plan = planner.plan(workspace, List.of(episode(source)));
        OperationPlan resolved = resolver.resolve(plan, resolver.mostRecentWins(plan));

        assertFalse(resolved.hasBlockingConflicts());
        assertTrue(resolved.executableOperations().isEmpty());
        assertTrue(Files.exists(source));
        assertTrue(Files.exists(existing));
    }

    /**
     * Letting the extra copy win used to move it onto the name it already had:
     * the plan ran, reported success, and both copies were still on disk. Winning
     * has to retire the other copy, or the answer means nothing.
     */
    @Test
    void lettingTheExtraCopyWinRetiresTheOtherOne() throws IOException {
        Path workspace = workspace();
        Path titled = file(workspace, "show.s01e01.720p.mkv", "2021-01-01T00:00:00Z");
        Path untitled = file(workspace, "show-01-untitled.mkv", "2026-01-01T00:00:00Z");

        OperationPlan plan = planner.plan(workspace, List.of(
                episode(titled),
                PlanSourceItem.forSource(untitled, ".mkv", PlanMediaKind.SERIES_EPISODE)
                        .series("Show", 1, 1)
                        .build()));
        PlannedOperation extra = plan.conflicts().getFirst();
        assertEquals(untitled.toRealPath(), extra.sourcePath());

        OperationPlan resolved = resolver.resolve(
                plan, Map.of(extra.sourcePath(), ConflictResolution.REPLACE));

        assertFalse(resolved.hasBlockingConflicts());
        assertEquals(1, resolved.deletions().size(), "the copy it beat is planned for removal");
        assertEquals(titled.toRealPath(), resolved.deletions().getFirst().sourcePath());
        Path winner = untitled.toRealPath();
        assertTrue(resolved.executableOperations().stream()
                        .anyMatch(operation -> operation.sourcePath().equals(winner)),
                "the winning copy is the one that gets sorted");
    }

    /**
     * Keeping the most recent, applied to a pair, has to end with one file: the
     * extra copy is planned for deletion, not merely dropped from the run.
     */
    @Test
    void keepingTheMostRecentPlansTheRemovalOfTheExtraCopy() throws IOException {
        Path workspace = workspace();
        Path titled = file(workspace, "show.s01e01.720p.mkv", "2026-01-01T00:00:00Z");
        Path untitled = file(workspace, "show-01-untitled.mkv", "2026-01-01T00:00:00Z");

        OperationPlan plan = planner.plan(workspace, List.of(
                episode(titled),
                PlanSourceItem.forSource(untitled, ".mkv", PlanMediaKind.SERIES_EPISODE)
                        .series("Show", 1, 1)
                        .build()));
        OperationPlan resolved = resolver.resolve(plan, resolver.mostRecentWins(plan));

        assertFalse(resolved.hasBlockingConflicts());
        assertEquals(1, resolved.deletions().size(), "the extra copy is planned for removal");
        assertEquals(untitled.toRealPath(), resolved.deletions().getFirst().sourcePath());
        assertEquals(1, resolved.executableOperations().size());
        assertEquals(titled.toRealPath(), resolved.executableOperations().getFirst().sourcePath());
        // Planning is not executing: both files are still there until the run.
        assertTrue(Files.exists(untitled));
    }

    /**
     * The field case: both copies already sit in the season folder, each at its
     * own name. Keeping the extra one must not plan a move of that file onto
     * itself — the only thing its win changes is that the other copy goes.
     */
    @Test
    void aWinningCopyAlreadyAtItsOwnNameIsNotPlannedToMove() throws IOException {
        Path workspace = workspace();
        Path seasonFolder = Files.createDirectories(workspace.resolve(Path.of("Show", "Season 01")));
        Path titled = seasonFolder.resolve("Show - S01E01 - Episode Title.mkv");
        Files.writeString(titled, "video-bytes");
        Path untitled = seasonFolder.resolve("Show - S01E01.mkv");
        Files.writeString(untitled, "video-bytes");

        OperationPlan plan = planner.plan(workspace, List.of(
                episode(titled),
                PlanSourceItem.forSource(untitled, ".mkv", PlanMediaKind.SERIES_EPISODE)
                        .series("Show", 1, 1)
                        .build()));
        PlannedOperation extra = plan.conflicts().getFirst();
        assertEquals(untitled.toRealPath(), extra.sourcePath());

        OperationPlan resolved = resolver.resolve(
                plan, Map.of(extra.sourcePath(), ConflictResolution.REPLACE));

        assertFalse(resolved.hasBlockingConflicts());
        assertTrue(resolved.executableOperations().isEmpty(), "no file has anywhere to go");
        assertEquals(List.of(titled.toRealPath()),
                resolved.deletions().stream().map(PlannedOperation::sourcePath).toList());
    }

    /**
     * The two batch answers are mirrors of each other: whichever the user picks,
     * a duplicate pair ends as one file, and it is the one they asked for.
     */
    @Test
    void theTwoAgeAnswersKeepOppositeCopiesOfTheSamePair() throws IOException {
        Path workspace = workspace();
        Path titled = file(workspace, "show.s01e01.720p.mkv", "2021-01-01T00:00:00Z");
        Path untitled = file(workspace, "show-01-untitled.mkv", "2026-01-01T00:00:00Z");

        // The second copy carries no episode title, so it targets a different
        // destination — same episode, two valid names.
        OperationPlan plan = planner.plan(workspace, List.of(
                episode(titled),
                PlanSourceItem.forSource(untitled, ".mkv", PlanMediaKind.SERIES_EPISODE)
                        .series("Show", 1, 1)
                        .build()));

        OperationPlan newestWins = resolver.resolve(plan, resolver.mostRecentWins(plan));
        assertFalse(newestWins.hasBlockingConflicts());
        assertEquals(List.of(titled.toRealPath()),
                newestWins.deletions().stream().map(PlannedOperation::sourcePath).toList(),
                "keeping the most recent retires the older copy");

        OperationPlan oldestWins = resolver.resolve(plan, resolver.oldestWins(plan));
        assertFalse(oldestWins.hasBlockingConflicts());
        assertEquals(List.of(untitled.toRealPath()),
                oldestWins.deletions().stream().map(PlannedOperation::sourcePath).toList(),
                "keeping the oldest retires the fresher copy");

        // Planning is not executing: nothing has gone yet, either way.
        assertTrue(Files.exists(titled));
        assertTrue(Files.exists(untitled));
    }

    private Path workspace() throws IOException {
        return Files.createDirectories(tempDir.resolve("workspace"));
    }

    /** A file already in the library folder, under a name that is not the destination. */
    private static Path libraryCopy(Path workspace, String name, String modifiedAt) throws IOException {
        Path folder = Files.createDirectories(workspace.resolve(Path.of("Show", "Season 01")));
        Path copy = folder.resolve(name);
        Files.writeString(copy, "already there");
        Files.setLastModifiedTime(copy, FileTime.from(Instant.parse(modifiedAt)));
        return copy;
    }

    private static Path file(Path workspace, String name, String modifiedAt) throws IOException {
        Path path = workspace.resolve(name);
        Files.writeString(path, "video-bytes");
        Files.setLastModifiedTime(path, FileTime.from(Instant.parse(modifiedAt)));
        return path;
    }

    /** The destination the fixture episode resolves to, already occupied. */
    private static Path destination(Path workspace, String modifiedAt) throws IOException {
        Path occupied = workspace.resolve(Path.of("Show", "Season 01", "Show - S01E01 - Pilot.mkv"));
        Files.createDirectories(occupied.getParent());
        Files.writeString(occupied, "already there");
        Files.setLastModifiedTime(occupied, FileTime.from(Instant.parse(modifiedAt)));
        return occupied;
    }

    private static PlanSourceItem episode(Path source) {
        return PlanSourceItem.forSource(source, ".mkv", PlanMediaKind.SERIES_EPISODE)
                .series("Show", 1, 1)
                .episodeTitle("Pilot")
                .build();
    }
}
