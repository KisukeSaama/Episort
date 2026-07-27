package com.episort.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OperationPlannerTest {
    @TempDir
    Path tempDir;

    private final OperationPlanner planner = new OperationPlanner();

    @Test
    void everyExecutableItemGetsASourceAndDestinationInsideTheWorkspace() throws IOException {
        Path workspace = workspace();
        Path source = file(workspace, "firefly.s01e01.mkv");

        OperationPlan plan = planner.plan(workspace, List.of(episode(source, "Firefly", 1, 1, "Serenity")));

        assertEquals(1, plan.executableOperations().size());
        PlannedOperation operation = plan.executableOperations().getFirst();
        assertEquals(source.toRealPath(), operation.sourcePath());
        assertEquals(
                workspace.toRealPath().resolve(Path.of("Firefly", "Season 01", "Firefly - S01E01 - Serenity.mkv")),
                operation.destinationPath().orElseThrow());
        assertTrue(operation.destinationPath().orElseThrow().startsWith(workspace.toRealPath()));
    }

    @Test
    void planGenerationCreatesNoFolderAndMovesNoFile() throws IOException {
        Path workspace = workspace();
        Path source = file(workspace, "firefly.s01e01.mkv");

        planner.plan(workspace, List.of(episode(source, "Firefly", 1, 1, "Serenity")));

        assertTrue(Files.exists(source), "source must stay untouched");
        assertFalse(Files.exists(workspace.resolve("Firefly")), "plan generation must not create folders");
        try (var entries = Files.list(workspace)) {
            assertEquals(List.of("firefly.s01e01.mkv"),
                    entries.map(path -> path.getFileName().toString()).sorted().toList());
        }
    }

    @Test
    void ignoredUnsupportedDuplicateAndUnassignedItemsAreExcludedFromExecutableOperations() throws IOException {
        Path workspace = workspace();
        Path ignored = file(workspace, "ignored.mkv");
        Path unsupported = file(workspace, "sample.wmv");
        Path duplicate = file(workspace, "duplicate.mkv");
        Path unassigned = file(workspace, "mystery.mkv");

        OperationPlan plan = planner.plan(workspace, List.of(
                PlanSourceItem.forSource(ignored, ".mkv", PlanMediaKind.SERIES_EPISODE)
                        .series("Show", 1, 1)
                        .excluded(PlanExclusionReason.IGNORED).build(),
                PlanSourceItem.forSource(unsupported, ".wmv", PlanMediaKind.SERIES_EPISODE)
                        .series("Show", 1, 2)
                        .excluded(PlanExclusionReason.UNSUPPORTED).build(),
                PlanSourceItem.forSource(duplicate, ".mkv", PlanMediaKind.SERIES_EPISODE)
                        .series("Show", 1, 3)
                        .excluded(PlanExclusionReason.DUPLICATE).build(),
                PlanSourceItem.forSource(unassigned, ".mkv", PlanMediaKind.SERIES_EPISODE).build()));

        assertTrue(plan.executableOperations().isEmpty());
        assertEquals(4, plan.excludedOperations().size());
        assertEquals(
                List.of(PlanExclusionReason.IGNORED, PlanExclusionReason.UNSUPPORTED,
                        PlanExclusionReason.DUPLICATE, PlanExclusionReason.UNASSIGNED),
                plan.excludedOperations().stream().map(PlannedOperation::exclusionReason).toList());
        assertTrue(plan.excludedOperations().stream().allMatch(op -> op.destinationPath().isEmpty()));
    }

    @Test
    void twoFilesTargetingTheSameDestinationBlockBothOfThem() throws IOException {
        Path workspace = workspace();
        Path first = file(workspace, "show.s01e01.720p.mkv");
        Path second = file(workspace, "show.s01e01.1080p.mkv");

        OperationPlan plan = planner.plan(workspace, List.of(
                episode(first, "Show", 1, 1, "Pilot"),
                episode(second, "Show", 1, 1, "Pilot")));

        assertTrue(plan.hasBlockingConflicts());
        assertEquals(2, plan.conflicts().size());
        assertTrue(plan.conflicts().stream().allMatch(
                op -> op.conflict().orElseThrow().type() == PlanConflictType.DUPLICATE_DESTINATION));
        assertTrue(plan.executableOperations().isEmpty());
    }

    @Test
    void anExistingFileAtTheDestinationIsABlockingConflict() throws IOException {
        Path workspace = workspace();
        Path source = file(workspace, "show.s01e01.mkv");
        Path occupied = workspace.resolve(Path.of("Show", "Season 01", "Show - S01E01 - Pilot.mkv"));
        Files.createDirectories(occupied.getParent());
        Files.writeString(occupied, "already there");

        OperationPlan plan = planner.plan(workspace, List.of(episode(source, "Show", 1, 1, "Pilot")));

        assertTrue(plan.hasBlockingConflicts());
        assertEquals(PlanConflictType.DESTINATION_FILE_EXISTS,
                plan.conflicts().getFirst().conflict().orElseThrow().type());
    }

    @Test
    void aFileAlreadyAtItsDestinationIsNotAConflictAndIsNotMoved() throws IOException {
        Path workspace = workspace();
        Path destination = workspace.resolve(Path.of("Show", "Season 01", "Show - S01E01 - Pilot.mkv"));
        Files.createDirectories(destination.getParent());
        Files.writeString(destination, "already sorted");

        OperationPlan plan = planner.plan(workspace, List.of(episode(destination, "Show", 1, 1, "Pilot")));

        assertFalse(plan.hasBlockingConflicts());
        assertTrue(plan.executableOperations().isEmpty());
        assertEquals(1, plan.alreadyInPlaceOperations().size());
    }

    @Test
    void existingSeriesAndSeasonFoldersAreReusedInsteadOfRecreated() throws IOException {
        Path workspace = workspace();
        Path source = file(workspace, "show.s01e02.mkv");
        Path seasonFolder = workspace.resolve(Path.of("Show", "Season 01"));
        Files.createDirectories(seasonFolder);

        OperationPlan plan = planner.plan(workspace, List.of(episode(source, "Show", 1, 2, "Second")));

        assertTrue(plan.reusedFolders().contains(seasonFolder.toRealPath()));
        assertTrue(plan.reusedFolders().contains(seasonFolder.getParent().toRealPath()));
        assertTrue(plan.foldersToCreate().isEmpty());
    }

    @Test
    void missingDestinationFoldersAreListedParentsFirst() throws IOException {
        Path workspace = workspace();
        Path source = file(workspace, "show.s02e05.mkv");

        OperationPlan plan = planner.plan(workspace, List.of(episode(source, "Show", 2, 5, "Fifth")));

        Path root = workspace.toRealPath();
        assertEquals(
                List.of(root.resolve("Show"), root.resolve(Path.of("Show", "Season 02"))),
                plan.foldersToCreate());
    }

    @Test
    void aSourceOutsideTheWorkspaceIsRefusedRatherThanPlanned() throws IOException {
        Path workspace = workspace();
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Path source = outside.resolve("show.s01e01.mkv");
        Files.writeString(source, "video");

        OperationPlan plan = planner.plan(workspace, List.of(episode(source, "Show", 1, 1, "Pilot")));

        assertTrue(plan.hasBlockingConflicts());
        assertEquals(PlanConflictType.SOURCE_OUTSIDE_WORKSPACE,
                plan.conflicts().getFirst().conflict().orElseThrow().type());
    }

    @Test
    void aFileBlockingADestinationFolderNameIsABlockingConflict() throws IOException {
        Path workspace = workspace();
        Path source = file(workspace, "show.s01e01.mkv");
        Files.writeString(workspace.resolve("Show"), "not a folder");

        OperationPlan plan = planner.plan(workspace, List.of(episode(source, "Show", 1, 1, "Pilot")));

        assertEquals(PlanConflictType.DESTINATION_FOLDER_BLOCKED,
                plan.conflicts().getFirst().conflict().orElseThrow().type());
    }

    @Test
    void specialsAndMoviesArePlannedAlongsideRegularEpisodes() throws IOException {
        Path workspace = workspace();
        Path episode = file(workspace, "show.s01e01.mkv");
        Path special = file(workspace, "show.ova.mkv");
        Path movie = file(workspace, "dune.2021.mkv");

        OperationPlan plan = planner.plan(workspace, List.of(
                episode(episode, "Show", 1, 1, "Pilot"),
                PlanSourceItem.forSource(special, ".mkv", PlanMediaKind.SPECIAL)
                        .special("Show", 1).episodeTitle("Behind the scenes").build(),
                PlanSourceItem.forSource(movie, ".mkv", PlanMediaKind.MOVIE).movie("Dune", 2021).build()));

        Path root = workspace.toRealPath();
        assertEquals(
                List.of(
                        root.resolve(Path.of("Show", "Season 01", "Show - S01E01 - Pilot.mkv")),
                        root.resolve(Path.of("Show", "Specials", "Show - S00E01 - Behind the scenes.mkv")),
                        root.resolve("Dune (2021).mkv")),
                plan.executableOperations().stream()
                        .map(operation -> operation.destinationPath().orElseThrow())
                        .toList());
    }

    /**
     * One duplicate is one question, asked about the extra copy. Blocking both
     * copies would double the review and, worse, make a select-all-then-delete
     * destroy the episode entirely.
     */
    @Test
    void ofTwoCopiesOfOneEpisodeOnlyTheLesserNamedOneIsAskedAbout() throws IOException {
        Path workspace = workspace();
        Path titled = file(workspace, "vigilantes.s01e06.1080p.mkv");
        Path untitled = file(workspace, "vigilantes-06.mkv");

        OperationPlan plan = planner.plan(workspace, List.of(
                episode(titled, "Vigilantes", 1, 6, "Crossing Lines"),
                PlanSourceItem.forSource(untitled, ".mkv", PlanMediaKind.SERIES_EPISODE)
                        .series("Vigilantes", 1, 6)
                        .build()));

        assertEquals(1, plan.conflicts().size(), "one duplicate, one question");
        PlannedOperation blocked = plan.conflicts().getFirst();
        assertEquals(untitled.toRealPath(), blocked.sourcePath(), "the copy without a title is the extra one");
        PlanConflict conflict = blocked.conflict().orElseThrow();
        assertEquals(PlanConflictType.DUPLICATE_MEDIA, conflict.type());
        assertEquals(titled.toRealPath(), conflict.duplicateOf().orElseThrow(), "it points at the copy kept");

        assertEquals(1, plan.executableOperations().size(), "the copy that is kept still gets sorted");
        assertEquals(titled.toRealPath(), plan.executableOperations().getFirst().sourcePath());
    }

    /**
     * The library case observed in the field: an episode already sorted under its
     * TVDB title, with the older untitled copy still beside it. Only the copy in
     * the wrong place is a question — asking twice about one pair is asking the
     * user to answer for a file that has nothing wrong with it.
     */
    @Test
    void theCopyAlreadyCorrectlyPlacedIsNotAskedAboutAgain() throws IOException {
        Path workspace = workspace();
        Path seasonFolder = Files.createDirectories(
                workspace.resolve(Path.of("Vigilantes", "Season 01")));
        Path settled = seasonFolder.resolve("Vigilantes - S01E06 - Crossing Lines.mkv");
        Files.writeString(settled, "video-bytes");
        Path leftover = seasonFolder.resolve("Vigilantes - S01E06.mkv");
        Files.writeString(leftover, "video-bytes");

        OperationPlan plan = planner.plan(workspace, List.of(
                episode(settled, "Vigilantes", 1, 6, "Crossing Lines"),
                episode(leftover, "Vigilantes", 1, 6, "Crossing Lines")));

        assertEquals(1, plan.conflicts().size(), "one pair is one question");
        PlannedOperation blocked = plan.conflicts().getFirst();
        assertEquals(leftover.toRealPath(), blocked.sourcePath(), "the leftover is the one to decide about");
        assertEquals(PlanConflictType.DESTINATION_FILE_EXISTS,
                blocked.conflict().orElseThrow().type());
        assertEquals(1, plan.alreadyInPlaceOperations().size(), "the settled copy stays settled");
    }

    @Test
    void anEpisodeTheLibraryAlreadyHoldsUnderAnotherNameIsBlocked() throws IOException {
        Path workspace = workspace();
        Path incoming = file(workspace, "vigilantes.s01e06.1080p.mkv");
        Path seasonFolder = Files.createDirectories(
                workspace.resolve(Path.of("Vigilantes", "Season 01")));
        Path existing = seasonFolder.resolve("Vigilantes - S01E06.mkv");
        Files.writeString(existing, "video-bytes");

        OperationPlan plan = planner.plan(workspace, List.of(
                episode(incoming, "Vigilantes", 1, 6, "Crossing Lines")));

        assertTrue(plan.executableOperations().isEmpty());
        PlanConflict conflict = plan.conflicts().getFirst().conflict().orElseThrow();
        assertEquals(PlanConflictType.MEDIA_ALREADY_IN_LIBRARY, conflict.type());
        assertEquals(existing.toRealPath(), conflict.duplicateOf().orElseThrow());
    }

    @Test
    void aMovieTheLibraryAlreadyHoldsUnderAReleaseNameIsBlocked() throws IOException {
        Path workspace = workspace();
        Path incoming = file(workspace, "blade.runner.1982.remux.mkv");
        // Deliberately not the destination name: the exact-path case is already
        // covered by DESTINATION_FILE_EXISTS.
        Path existing = workspace.resolve("Blade Runner 1982 1080p.mkv");
        Files.writeString(existing, "video-bytes");

        OperationPlan plan = planner.plan(workspace, List.of(
                PlanSourceItem.forSource(incoming, ".mkv", PlanMediaKind.MOVIE)
                        .movie("Blade Runner", 1982)
                        .build()));

        assertEquals(PlanConflictType.MEDIA_ALREADY_IN_LIBRARY,
                plan.conflicts().getFirst().conflict().orElseThrow().type());
    }

    @Test
    void anEpisodeAlreadySittingAtItsOwnDestinationIsNotADuplicateOfItself() throws IOException {
        Path workspace = workspace();
        Path seasonFolder = Files.createDirectories(workspace.resolve(Path.of("Firefly", "Season 01")));
        Path inPlace = seasonFolder.resolve("Firefly - S01E01 - Serenity.mkv");
        Files.writeString(inPlace, "video-bytes");

        OperationPlan plan = planner.plan(workspace, List.of(
                episode(inPlace, "Firefly", 1, 1, "Serenity")));

        assertFalse(plan.hasBlockingConflicts());
        assertEquals(1, plan.alreadyInPlaceOperations().size());
    }

    @Test
    void adifferentEpisodeOfTheSameSeasonIsNeverTreatedAsADuplicate() throws IOException {
        Path workspace = workspace();
        Path incoming = file(workspace, "firefly.s01e02.mkv");
        Path seasonFolder = Files.createDirectories(workspace.resolve(Path.of("Firefly", "Season 01")));
        Files.writeString(seasonFolder.resolve("Firefly - S01E01 - Serenity.mkv"), "video-bytes");

        OperationPlan plan = planner.plan(workspace, List.of(
                episode(incoming, "Firefly", 1, 2, "The Train Job")));

        assertFalse(plan.hasBlockingConflicts());
        assertEquals(1, plan.executableOperations().size());
    }

    @Test
    void anEmptyItemListProducesAnEmptyPlanWithoutConflicts() throws IOException {
        OperationPlan plan = planner.plan(workspace(), List.of());

        assertTrue(plan.isEmpty());
        assertFalse(plan.hasBlockingConflicts());
        assertTrue(plan.foldersToCreate().isEmpty());
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
