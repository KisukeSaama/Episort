package com.episort.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.scanner.InventoryGroupType;
import com.episort.scanner.InventoryItem;
import com.episort.scanner.InventoryItemType;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
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
    void aiMapperDoesNotWriteTvdbFieldsAndDoesNotOverrideUserValues() {
        AnalyzedVideoFile file = heuristics.analyze(item("Show.S01E02.Title.mkv", ".mkv"), InventoryGroupType.LIKELY_SERIES);
        file.set(AnalysisField.DETECTED_TITLE, "User Title", FieldSource.USER);

        new AiResultMapper().apply(new AiVideoAnalysisResult(
                Optional.of(VideoMediaType.SERIES),
                Optional.of("Title.SxxEyy.Quality.Source.Codec-Group"),
                Optional.of("AI Title"),
                Optional.of(1),
                Optional.of(2),
                Optional.empty(),
                Optional.empty(),
                Optional.of("1080p"),
                Optional.of("WEB-DL"),
                Optional.of("x265"),
                Optional.of("FR"),
                Optional.of("Group"),
                OptionalDouble.of(0.92),
                List.of(),
                List.of(),
                true), file);

        assertEquals("User Title", file.detectedTitle().orElseThrow());
        assertEquals(FieldSource.USER, file.fieldSources().get(AnalysisField.DETECTED_TITLE));
        assertEquals(TvdbOrder.TO_DEFINE, file.tvdbOrder());
        assertEquals(FieldSource.UNKNOWN, file.fieldSources().get(AnalysisField.TVDB_ORDER));
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
