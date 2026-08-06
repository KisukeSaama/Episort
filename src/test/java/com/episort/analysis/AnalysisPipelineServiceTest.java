package com.episort.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.scanner.InventoryGroupType;
import com.episort.scanner.InventoryItem;
import com.episort.scanner.InventoryItemType;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalysisPipelineServiceTest {
    private final HeuristicAnalysisService heuristics = new HeuristicAnalysisService();
    private final RenameProposalService rename = new RenameProposalService();
    private final AnalysisValidationService validation = new AnalysisValidationService();

    @Test
    void preTvdbSeriesCanBeOkWithoutTvdbMatch() {
        AnalyzedVideoFile file = heuristics.analyze(item("Show.S01E02.Title.mkv", ".mkv"), InventoryGroupType.LIKELY_SERIES);

        rename.generate(file);
        validation.validatePreTvdb(List.of(file));

        assertEquals(AnalysisStatus.OK, file.status());
        assertEquals(TvdbOrder.TO_DEFINE, file.tvdbOrder());
        assertEquals("Show - S01E02 - Title.mkv", file.proposedName().orElseThrow());
        assertContainsNoPathSeparator(file.proposedName().orElseThrow());
        assertTrue(file.proposedName().orElseThrow().endsWith(".mkv"));
        assertFalse(file.statusReasons().stream().anyMatch(reason -> reason.contains("TVDB")));
    }

    @Test
    void preTvdbMovieUsesNotApplicableOrder() {
        AnalyzedVideoFile file = heuristics.analyze(item("Movie.2024.mkv", ".mkv"), InventoryGroupType.LIKELY_MOVIE);

        rename.generate(file);
        validation.validatePreTvdb(List.of(file));

        assertEquals(AnalysisStatus.OK, file.status());
        assertEquals(TvdbOrder.NOT_APPLICABLE, file.tvdbOrder());
        assertEquals("Movie (2024).mkv", file.proposedName().orElseThrow());
        assertFalse(file.proposedName().orElseThrow().contains("Movies"));
        assertContainsNoPathSeparator(file.proposedName().orElseThrow());
        assertTrue(file.proposedName().orElseThrow().endsWith(".mkv"));
    }

    @Test
    void proposedNameStoresOnlyFileNameEvenWhenGivenPathLikeInput() {
        AnalyzedVideoFile file = heuristics.analyze(item("Movie.2024.mkv", ".mkv"), InventoryGroupType.LIKELY_MOVIE);

        file.set(AnalysisField.PROPOSED_NAME, "Movies/Movie (2024).mkv", FieldSource.HEURISTIC);

        assertEquals("Movie (2024).mkv", file.proposedName().orElseThrow());
        assertContainsNoPathSeparator(file.proposedName().orElseThrow());
    }

    @Test
    void invalidExtensionIsExtStatus() {
        AnalyzedVideoFile file = heuristics.analyze(item("Show.S01E02.part", ".part"), InventoryGroupType.LIKELY_SERIES);

        rename.generate(file);
        validation.validatePreTvdb(List.of(file));

        assertEquals(AnalysisStatus.EXT, file.status());
        assertTrue(file.statusReasons().get(0).contains("extension"));
    }

    @Test
    void duplicateProposedNamesAreDetected() {
        AnalyzedVideoFile first = heuristics.analyze(item("Show.S01E02.Title.mkv", ".mkv"), InventoryGroupType.LIKELY_SERIES);
        AnalyzedVideoFile second = heuristics.analyze(item("Show - 1x02 - Title.mkv", ".mkv"), InventoryGroupType.LIKELY_SERIES);

        rename.generate(first);
        rename.generate(second);
        validation.validatePreTvdb(List.of(first, second));

        assertEquals(AnalysisStatus.DUPLICATE, first.status());
        assertEquals(AnalysisStatus.DUPLICATE, second.status());
    }

    @Test
    void releaseNoiseNeverLeaksIntoTheProposedName() {
        AnalyzedVideoFile file = heuristics.analyze(
                item("Show.S01E02.Title.1080p.BluRay.x264-GROUP.mkv", ".mkv"), InventoryGroupType.LIKELY_SERIES);

        rename.generate(file);
        validation.validatePreTvdb(List.of(file));

        assertEquals("Show - S01E02 - Title.mkv", file.proposedName().orElseThrow());
        assertEquals("1080p", file.quality().orElseThrow());
        assertEquals("x264", file.codec().orElseThrow());
        assertEquals("GROUP", file.releaseGroup().orElseThrow());
        assertEquals(AnalysisStatus.OK, file.status());
    }

    @Test
    void aBareNumberNeverPassesAsAConfidentEpisode() {
        AnalyzedVideoFile file = heuristics.analyze(
                item("Show - 002 - Title.mkv", ".mkv"), InventoryGroupType.LIKELY_SERIES);

        rename.generate(file);
        validation.validatePreTvdb(List.of(file));

        assertEquals(AnalysisStatus.LOW_CONFIDENCE, file.status());
        assertTrue(file.warnings().stream().anyMatch(warning -> warning.contains("bare number")));
    }

    @Test
    void anAssumptionKeepsTheFileInReviewEvenWhenEverythingElseChecksOut() {
        AnalyzedVideoFile file = heuristics.analyze(
                item("Show.S01E02E03.mkv", ".mkv"), InventoryGroupType.LIKELY_SERIES);

        rename.generate(file);
        validation.validatePreTvdb(List.of(file));

        assertEquals(AnalysisStatus.REVIEW, file.status());
        assertTrue(file.statusReasons().stream().anyMatch(reason -> reason.contains("several episodes")));
    }

    @Test
    void sampleAndTrailerFilesAreNeverOrganized() {
        AnalyzedVideoFile file = heuristics.analyze(
                item("Show.S01E02.sample.mkv", ".mkv"), InventoryGroupType.LIKELY_SERIES);

        rename.generate(file);
        validation.validatePreTvdb(List.of(file));

        assertEquals(VideoMediaType.IGNORED, file.mediaType());
        assertEquals(AnalysisStatus.IGNORED, file.status());
    }

    @Test
    void explicitlyNumberedExtraEpisodeRequiresReviewInsteadOfBeingIgnored() {
        AnalyzedVideoFile file = heuristics.analyze(
                item("Initial D - S00E01 - Extra Stage.mkv", ".mkv"), InventoryGroupType.LIKELY_SERIES);

        rename.generate(file);
        validation.validatePreTvdb(List.of(file));

        assertEquals(VideoMediaType.SPECIAL, file.mediaType());
        assertEquals(AnalysisStatus.LOW_CONFIDENCE, file.status());
        assertTrue(file.statusReasons().stream().anyMatch(reason -> reason.contains("review threshold")));
    }

    @Test
    void aResolutionIsNeverMistakenForAMovieYear() {
        AnalyzedVideoFile file = heuristics.analyze(
                item("Movie.2160p.BluRay.mkv", ".mkv"), InventoryGroupType.LIKELY_MOVIE);

        rename.generate(file);
        validation.validatePreTvdb(List.of(file));

        assertTrue(file.year().isEmpty());
        assertEquals("Movie.mkv", file.proposedName().orElseThrow());
    }

    @Test
    void windowsUnsafeCharactersAndTrailingDotsAreStripped() {
        AnalyzedVideoFile file = heuristics.analyze(
                item("Show.S01E02.mkv", ".mkv"), InventoryGroupType.LIKELY_SERIES);
        file.set(AnalysisField.DETECTED_TITLE, "Show: The <Return>.", FieldSource.USER);

        rename.generate(file);

        assertEquals("Show The Return - S01E02.mkv", file.proposedName().orElseThrow());
    }

    private static InventoryItem item(String filename, String extension) {
        Path path = Path.of("C:/Media").resolve(filename).toAbsolutePath().normalize();
        return new InventoryItem(path, filename, extension, path.getParent(), InventoryItemType.SUPPORTED_VIDEO, true);
    }

    private static void assertContainsNoPathSeparator(String value) {
        assertFalse(value.contains("/"), value);
        assertFalse(value.contains("\\"), value);
    }
}
