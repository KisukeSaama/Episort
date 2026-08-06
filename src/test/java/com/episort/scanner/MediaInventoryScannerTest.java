package com.episort.scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaInventoryScannerTest {
    @TempDir
    Path tempDir;

    @Test
    void detectsSupportedMediaFilesWithoutMutatingFilesystem() throws Exception {
        Path input = Files.createDirectory(tempDir.resolve("input"));
        Path season = Files.createDirectory(input.resolve("Season 01"));
        Path avi = Files.createFile(season.resolve("Show.Name.S01E01.avi"));
        Path mp4 = Files.createFile(input.resolve("Movie.Name.2020.mp4"));
        Path mkv = Files.createFile(input.resolve("Other.Show.1x02.mkv"));
        List<Path> before = snapshot(input);

        InventoryScanResult result = new MediaInventoryScanner().scan(input, progress -> {});

        assertEquals(3, result.summary().supportedVideoCount());
        assertEquals(List.of(mp4, mkv, avi), result.supportedVideos().stream().map(InventoryItem::sourcePath).toList());
        InventoryItem nestedAvi = result.supportedVideos().stream()
                .filter(item -> item.sourcePath().equals(avi))
                .findFirst()
                .orElseThrow();
        assertEquals("Show.Name.S01E01.avi", nestedAvi.filename());
        assertEquals(".avi", nestedAvi.extension());
        assertEquals(season, nestedAvi.parentFolder());
        assertEquals(before, snapshot(input));
    }

    @Test
    void ignoresEveryNonVideoFileCompletely() throws Exception {
        Path input = Files.createDirectory(tempDir.resolve("input"));
        Files.createFile(input.resolve("Show.Name.S01E01.srt"));
        Files.createFile(input.resolve("Show.Name.nfo"));
        Files.createFile(input.resolve("poster.jpg"));
        Files.createFile(input.resolve("clip.mov"));
        Files.createFile(input.resolve("archive.iso"));
        Files.createFile(input.resolve(".DS_Store"));

        InventoryScanResult result = new MediaInventoryScanner().scan(input, progress -> {});

        assertTrue(result.items().isEmpty());
        assertTrue(result.groups().isEmpty());
        assertEquals(0, result.summary().sidecarCount());
        assertEquals(0, result.summary().unsupportedCount());
    }

    @Test
    void doesNotTraverseSymbolicLinkDirectoriesEvenWhenTheirTargetIsInternal() throws Exception {
        Path input = Files.createDirectory(tempDir.resolve("input"));
        Path realSeason = Files.createDirectory(input.resolve("real-season"));
        Files.createFile(realSeason.resolve("Show.S01E01.mkv"));
        Path linkedSeason = input.resolve("linked-season");
        assumeSymbolicLink(linkedSeason, realSeason);

        InventoryScanResult result = new MediaInventoryScanner().scan(input, progress -> {});

        assertEquals(1, result.summary().supportedVideoCount());
        assertTrue(result.supportedVideos().stream()
                .noneMatch(item -> item.sourcePath().startsWith(linkedSeason)));
    }

    @Test
    void seedsMultipleSeriesMoviesUnknownAndIgnoredGroupsWithoutFinalTvdbIdentity() throws Exception {
        Path input = Files.createDirectory(tempDir.resolve("input"));
        Files.createFile(input.resolve("Alpha.Show.S01E01.mkv"));
        Files.createFile(input.resolve("Alpha.Show.S01E02.mkv"));
        Files.createFile(input.resolve("Beta Show - 1x01.mp4"));
        Files.createFile(input.resolve("Some Movie (2021).mkv"));
        Files.createFile(input.resolve("loose-video.mp4"));
        Files.createFile(input.resolve("notes.txt"));
        Files.createFile(input.resolve(".hidden"));

        InventoryScanResult result = new MediaInventoryScanner().scan(input, progress -> {});

        assertEquals(2, result.summary().likelySeriesGroupCount());
        assertEquals(1, result.summary().likelyMovieGroupCount());
        assertEquals(1, result.summary().unknownItemCount());
        assertTrue(result.groups().stream().allMatch(group -> group.tvdbIdentityFinal() == false));
        assertTrue(result.groups().stream().noneMatch(group -> group.type() == InventoryGroupType.UNSUPPORTED));
        assertTrue(result.groups().stream().noneMatch(group -> group.type() == InventoryGroupType.IGNORED));
    }

    @Test
    void promotesUnknownFilenamesToSeriesWhenParentFolderCarriesSeasonMarker() throws Exception {
        Path input = Files.createDirectory(tempDir.resolve("input"));
        Path season = Files.createDirectory(input.resolve("Haikyu.S01.MULTi.1080p.BluRay.x264-SHiNiGAMi"));
        Files.createFile(season.resolve("sgi-hkyu01.1080p.multi.mkv"));
        Files.createFile(season.resolve("sgi-hkyu02.1080p.multi.mkv"));
        Files.createFile(season.resolve("sgi-hkyu03.1080p.multi.mkv"));

        InventoryScanResult result = new MediaInventoryScanner().scan(input, progress -> {});

        List<InventoryGroup> seriesGroups = result.groups().stream()
                .filter(group -> group.type() == InventoryGroupType.LIKELY_SERIES)
                .toList();
        assertEquals(1, seriesGroups.size(), "all three episodes should land in a single series group");
        assertEquals(3, seriesGroups.getFirst().items().size());
        assertEquals(0, result.summary().unknownItemCount());
    }

    @Test
    void splitsTwoSeriesSharingOneReleaseFolder() throws Exception {
        Path input = Files.createDirectory(tempDir.resolve("input"));
        Path season = Files.createDirectory(input.resolve("Haikyu.S01.MULTi.1080p.BluRay.x264-SHiNiGAMi"));
        Files.createFile(season.resolve("sgi-hkyu01.1080p.multi.mkv"));
        Files.createFile(season.resolve("sgi-hkyu02.1080p.multi.mkv"));
        Files.createFile(season.resolve("My.Hero.Academia.Vigilantes.S01E01.MULTi.1080p.mkv"));
        Files.createFile(season.resolve("My.Hero.Academia.Vigilantes.S01E02.MULTi.1080p.mkv"));

        InventoryScanResult result = new MediaInventoryScanner().scan(input, progress -> {});

        List<InventoryGroup> seriesGroups = result.groups().stream()
                .filter(group -> group.type() == InventoryGroupType.LIKELY_SERIES)
                .toList();
        assertEquals(2, seriesGroups.size(), "the folder holds two series, not one");
        assertTrue(seriesGroups.stream().anyMatch(group -> "Haikyu".equals(group.seedName())));
        assertTrue(seriesGroups.stream()
                .anyMatch(group -> "My Hero Academia Vigilantes".equals(group.seedName())));
        assertTrue(seriesGroups.stream().allMatch(group -> group.items().size() == 2));
    }

    @Test
    void groupsTheSameSeriesWhateverTheCaseOfItsName() throws Exception {
        Path input = Files.createDirectory(tempDir.resolve("input"));
        Files.createFile(input.resolve("Alpha.Show.S01E01.mkv"));
        Files.createFile(input.resolve("alpha.show.S01E02.mkv"));
        Files.createFile(input.resolve("ALPHA SHOW - 1x03.mkv"));

        InventoryScanResult result = new MediaInventoryScanner().scan(input, progress -> {});

        assertEquals(1, result.summary().likelySeriesGroupCount());
    }

    @Test
    void keepsSampleFilesOutOfSeriesAndMovieGroups() throws Exception {
        Path input = Files.createDirectory(tempDir.resolve("input"));
        Files.createFile(input.resolve("Alpha.Show.S01E01.mkv"));
        Files.createFile(input.resolve("Alpha.Show.S01E01.sample.mkv"));

        InventoryScanResult result = new MediaInventoryScanner().scan(input, progress -> {});

        assertEquals(1, result.summary().likelySeriesGroupCount());
        assertTrue(result.groups().stream()
                .filter(group -> group.type() == InventoryGroupType.LIKELY_SERIES)
                .allMatch(group -> group.items().size() == 1));
    }

    @Test
    void keepsNumberedExtraEpisodesInSeriesReviewFlow() throws Exception {
        Path input = Files.createDirectory(tempDir.resolve("input"));
        Path special = Files.createFile(input.resolve("Initial D - S00E01 - Extra Stage.mkv"));

        InventoryScanResult result = new MediaInventoryScanner().scan(input, progress -> {});

        InventoryGroup group = result.groups().getFirst();
        assertEquals(InventoryGroupType.LIKELY_SERIES, group.type());
        assertEquals("Initial D", group.seedName());
        assertEquals(List.of(special), group.items().stream().map(InventoryItem::sourcePath).toList());
    }

    @Test
    void aResolutionTagNeverSeedsAMovieGroupAsIfItWereAYear() throws Exception {
        Path input = Files.createDirectory(tempDir.resolve("input"));
        Files.createFile(input.resolve("Mystery.2160p.BluRay.mkv"));

        InventoryScanResult result = new MediaInventoryScanner().scan(input, progress -> {});

        assertEquals(0, result.summary().likelyMovieGroupCount());
        assertEquals(1, result.summary().unknownItemCount());
    }

    @Test
    void reportsProgressAndSummaryForLargeInventory() throws Exception {
        Path input = Files.createDirectory(tempDir.resolve("input"));
        for (int index = 1; index <= 2000; index++) {
            Files.createFile(input.resolve("Show.Name.S01E" + index + ".mkv"));
        }
        List<InventoryScanProgress> progressEvents = new ArrayList<>();

        InventoryScanResult result = new MediaInventoryScanner().scan(input, progressEvents::add);

        assertFalse(progressEvents.isEmpty());
        InventoryScanProgress completed = progressEvents.getLast();
        assertEquals(2000, completed.processedFiles());
        assertEquals(2000, completed.totalFiles());
        assertTrue(completed.complete());
        assertEquals(2000, result.summary().supportedVideoCount());
        assertFalse(result.summary().patternValidated());
        assertFalse(result.summary().operationPlanApproved());
    }

    private static List<Path> snapshot(Path root) throws Exception {
        try (var stream = Files.walk(root)) {
            return stream.sorted().toList();
        }
    }

    private static void assumeSymbolicLink(Path link, Path target) throws Exception {
        boolean symlinkSupported;
        try {
            Files.createSymbolicLink(link, target);
            symlinkSupported = true;
        } catch (UnsupportedOperationException | IOException exception) {
            symlinkSupported = false;
        }
        assumeTrue(symlinkSupported, "Symlink creation not supported on this platform/permission set");
    }
}
