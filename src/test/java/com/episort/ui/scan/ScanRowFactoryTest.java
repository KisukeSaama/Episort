package com.episort.ui.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.scanner.InventoryGroup;
import com.episort.scanner.InventoryGroupType;
import com.episort.scanner.InventoryItem;
import com.episort.scanner.InventoryItemType;
import com.episort.scanner.InventoryScanResult;
import com.episort.scanner.InventorySummary;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScanRowFactoryTest {

    @Test
    void mapsSupportedVideoInLikelySeriesGroupToSeriesPreviewRow() {
        Path source = Path.of("C:/Media/Show.S01E01.mkv").toAbsolutePath().normalize();
        InventoryItem video = new InventoryItem(
                source, "Show.S01E01.mkv", "mkv", source.getParent(), InventoryItemType.SUPPORTED_VIDEO, true);
        InventoryScanResult result = new InventoryScanResult(
                List.of(video),
                List.of(new InventoryGroup(InventoryGroupType.LIKELY_SERIES, "Show", List.of(video), false)),
                summary(1, 0, 0, 0, 1, 0, 0));

        List<ScanRow> rows = ScanRowFactory.from(result);

        assertEquals(1, rows.size());
        ScanRow row = rows.get(0);
        assertEquals("Show.S01E01.mkv", row.originalFilename());
        assertEquals("MKV", row.extension());
        assertSame(ScanMediaType.SERIES, row.mediaType());
        assertSame(ScanRowStatus.OK, row.status());
        assertEquals("Show - S01E01.mkv", row.proposedFilename().orElseThrow());
        assertContainsNoPathSeparator(row.proposedFilename().orElseThrow());
        assertEquals("Series: Show | S:01 | E:01 | Ext:mkv", row.inputPattern().orElseThrow());
        assertEquals("SxxExx", row.inputParse().orElseThrow().label());
        assertTrue(row.tvdbMatch().isEmpty());
        assertEquals("TO_DEFINE", row.order().orElseThrow());
        assertEquals(0.95, row.confidence().orElseThrow(), 1e-9);
        assertTrue(row.destination().isEmpty());
        assertTrue(row.alertText().isEmpty());
        assertTrue(row.noteText().isEmpty());
        assertTrue(row.conflictText().isEmpty());
    }

    @Test
    void buildsProposedNameWhenSeasonMarkerLivesOnlyInParentFolder() {
        Path source = Path.of("C:/Media/Haikyu.S01.MULTi.1080p.BluRay.x264-SHiNiGAMi/sgi-hkyu01.1080p.multi.mkv")
                .toAbsolutePath().normalize();
        InventoryItem video = new InventoryItem(
                source, "sgi-hkyu01.1080p.multi.mkv", "mkv", source.getParent(), InventoryItemType.SUPPORTED_VIDEO, true);
        InventoryScanResult result = new InventoryScanResult(
                List.of(video),
                List.of(new InventoryGroup(
                        InventoryGroupType.LIKELY_SERIES,
                        "Haikyu S01 MULTi 1080p BluRay x264-SHiNiGAMi",
                        List.of(video),
                        false)),
                summary(1, 0, 0, 0, 1, 0, 0));

        ScanRow row = ScanRowFactory.from(result).get(0);

        assertSame(ScanMediaType.SERIES, row.mediaType());
        assertTrue(row.proposedFilename().isPresent(), "proposed name should be generated from folder metadata");
        assertTrue(row.proposedFilename().orElseThrow().startsWith("Haikyu - S01E01"),
                "expected 'Haikyu - S01E01...' but got: " + row.proposedFilename().orElseThrow());
    }

    @Test
    void mapsBatchTvdbGroupsFromRealInventoryGroups() {
        Path source = Path.of("C:/Media/Show.S01E01.mkv").toAbsolutePath().normalize();
        InventoryItem video = new InventoryItem(
                source, "Show.S01E01.mkv", "mkv", source.getParent(), InventoryItemType.SUPPORTED_VIDEO, true);
        InventoryScanResult result = new InventoryScanResult(
                List.of(video),
                List.of(new InventoryGroup(InventoryGroupType.LIKELY_SERIES, "Show", List.of(video), true)),
                summary(1, 0, 0, 0, 1, 0, 0));

        List<BatchTvdbMatch> matches = BatchTvdbMatch.from(result);

        assertEquals(1, matches.size());
        assertEquals("Show", matches.get(0).seedName());
        assertSame(InventoryGroupType.LIKELY_SERIES, matches.get(0).type());
        assertEquals(1, matches.get(0).itemCount());
        assertTrue(matches.get(0).finalMatch());
    }

    @Test
    void mapsSupportedVideoInLikelyMovieGroupToMovieRow() {
        Path source = Path.of("C:/Media/Movie.2025.mkv").toAbsolutePath().normalize();
        InventoryItem video = new InventoryItem(
                source, "Movie.2025.mkv", "mkv", source.getParent(), InventoryItemType.SUPPORTED_VIDEO, true);
        InventoryScanResult result = new InventoryScanResult(
                List.of(video),
                List.of(new InventoryGroup(InventoryGroupType.LIKELY_MOVIE, "Movie 2025", List.of(video), false)),
                summary(1, 0, 0, 0, 0, 1, 0));

        List<ScanRow> rows = ScanRowFactory.from(result);

        assertSame(ScanMediaType.MOVIE, rows.get(0).mediaType());
        assertSame(ScanRowStatus.OK, rows.get(0).status());
        assertEquals("N/A", rows.get(0).order().orElseThrow());
        assertEquals("Movie (2025).mkv", rows.get(0).proposedFilename().orElseThrow());
        assertContainsNoPathSeparator(rows.get(0).proposedFilename().orElseThrow());
    }

    @Test
    void supportedVideoWithoutGroupFallsBackToUnknown() {
        Path source = Path.of("C:/Media/Mystery.mkv").toAbsolutePath().normalize();
        InventoryItem video = new InventoryItem(
                source, "Mystery.mkv", "mkv", source.getParent(), InventoryItemType.SUPPORTED_VIDEO, true);
        InventoryScanResult result = new InventoryScanResult(
                List.of(video),
                List.of(),
                summary(1, 0, 0, 0, 0, 0, 1));

        ScanRow row = ScanRowFactory.from(result).get(0);

        assertSame(ScanMediaType.UNKNOWN, row.mediaType());
    }

    @Test
    void sidecarsAndIgnoredItemsBecomeIgnoredRows() {
        Path subtitlePath = Path.of("C:/Media/Show.srt").toAbsolutePath().normalize();
        Path archivePath = Path.of("C:/Media/Pack.zip").toAbsolutePath().normalize();
        InventoryItem subtitle = new InventoryItem(
                subtitlePath, "Show.srt", "srt", subtitlePath.getParent(), InventoryItemType.SIDECAR, false);
        InventoryItem archive = new InventoryItem(
                archivePath, "Pack.zip", "zip", archivePath.getParent(), InventoryItemType.UNSUPPORTED, false);
        InventoryScanResult result = new InventoryScanResult(
                List.of(subtitle, archive),
                List.of(),
                summary(0, 1, 1, 0, 0, 0, 0));

        List<ScanRow> rows = ScanRowFactory.from(result);

        assertSame(ScanRowStatus.IGNORED, rows.get(0).status());
        assertSame(ScanMediaType.IGNORED, rows.get(0).mediaType());
        assertSame(ScanRowStatus.IGNORED, rows.get(1).status());
        assertSame(ScanMediaType.IGNORED, rows.get(1).mediaType());
    }

    @Test
    void activeReviewReasonBecomesAVisibleAlert() {
        Path source = Path.of("C:/Media/Initial D - S00E01 - Extra Stage.mkv").toAbsolutePath().normalize();
        InventoryItem video = new InventoryItem(
                source, source.getFileName().toString(), "mkv", source.getParent(),
                InventoryItemType.SUPPORTED_VIDEO, true);
        InventoryScanResult result = new InventoryScanResult(
                List.of(video),
                List.of(new InventoryGroup(InventoryGroupType.LIKELY_SERIES, "Initial D", List.of(video), false)),
                summary(1, 0, 0, 0, 1, 0, 0));

        ScanRow row = ScanRowFactory.from(result).getFirst();

        assertFalse(row.isIgnored());
        assertSame(ScanRowStatus.LOW_CONFIDENCE, row.status());
        assertTrue(row.alertText().isPresent());
    }

    @Test
    void normalizesExtensionToUppercaseAndStripsLeadingDot() {
        Path source = Path.of("C:/Media/Episode.MP4").toAbsolutePath().normalize();
        InventoryItem video = new InventoryItem(
                source, "Episode.MP4", ".mp4", source.getParent(), InventoryItemType.SUPPORTED_VIDEO, true);
        InventoryScanResult result = new InventoryScanResult(
                List.of(video),
                List.of(new InventoryGroup(InventoryGroupType.LIKELY_SERIES, "Episode", List.of(video), false)),
                summary(1, 0, 0, 0, 1, 0, 0));

        ScanRow row = ScanRowFactory.from(result).get(0);

        assertEquals("MP4", row.extension());
    }

    @Test
    void recordsNxnnInputPatternWhenDetected() {
        Path source = Path.of("C:/Media/Show - 1x02 - Title.mkv").toAbsolutePath().normalize();
        InventoryItem video = new InventoryItem(
                source, "Show - 1x02 - Title.mkv", "mkv", source.getParent(), InventoryItemType.SUPPORTED_VIDEO, true);
        InventoryScanResult result = new InventoryScanResult(
                List.of(video),
                List.of(new InventoryGroup(InventoryGroupType.LIKELY_SERIES, "Show", List.of(video), false)),
                summary(1, 0, 0, 0, 1, 0, 0));

        ScanRow row = ScanRowFactory.from(result).get(0);

        assertEquals("Series: Show | S:01 | E:02 | Title: Title | Ext:mkv", row.inputPattern().orElseThrow());
        assertEquals("TO_DEFINE", row.order().orElseThrow());
    }

    @Test
    void parserReadsSxxExxWithTitleAndPositions() {
        ScanInputParse parse = ScanInputPatternParser.parse("Show.S01E02.Title.mkv").orElseThrow();

        assertEquals("SxxExx", parse.label());
        assertEquals("S01E02", parse.normalizedOrder().orElseThrow());
        assertEquals("Series: Show | S:01 | E:02 | Title: Title | Ext:mkv", parse.summary());
        assertEquals("series[0..5] season[6..8] episode[9..11] title[11..17] extension[18..21]",
                parse.positionsSummary());
    }

    @Test
    void parserReadsShortSxxExxAndNxnnAndAbsolute() {
        assertEquals("S01E02", ScanInputPatternParser.parse("Show - S1E2 - Title.mkv")
                .flatMap(ScanInputParse::normalizedOrder).orElseThrow());
        assertEquals("S01E02", ScanInputPatternParser.parse("Show - 1x02 - Title.mkv")
                .flatMap(ScanInputParse::normalizedOrder).orElseThrow());
        ScanInputParse absolute = ScanInputPatternParser.parse("Show - 002 - Title.mkv").orElseThrow();
        assertEquals("Absolute", absolute.label());
        assertEquals("S01E02", absolute.normalizedOrder().orElseThrow());
        assertTrue(absolute.confidence().orElseThrow() <= 0.5,
                "a bare number is a guess and must not look confident");
    }

    @Test
    void parserPreservesExtensionAndHandlesMissingTitleOrSeries() {
        ScanInputParse noTitle = ScanInputPatternParser.parse("Show.S01E02.mkv").orElseThrow();
        assertEquals("mkv", noTitle.tokenValue(ScanInputRole.EXTENSION).orElseThrow());
        assertTrue(noTitle.tokenValue(ScanInputRole.TITLE).isEmpty());

        ScanInputParse noSeries = ScanInputPatternParser.parse("S01E02 - Pilot.mp4").orElseThrow();
        assertTrue(noSeries.tokenValue(ScanInputRole.SERIES).isEmpty());
        assertEquals("Pilot", noSeries.tokenValue(ScanInputRole.TITLE).orElseThrow());
    }

    @Test
    void patternFormatterGeneratesProposedNameFromAvailableMetadata() {
        ScanRow row = new ScanRow(
                Path.of("C:/Media/Ma Serie - 01x02 - Titre.mkv"),
                "Ma Serie - 01x02 - Titre.mkv",
                "MKV",
                ScanMediaType.SERIES,
                ScanRowStatus.REVIEW);

        String proposed = ScanPatternFormatter.format(
                row,
                "{series}/Season {season}/{series} - S{season}E{episode} - {title}")
                .orElseThrow();

        assertEquals("Ma Serie/Season 01/Ma Serie - S01E02 - Titre.mkv", proposed);
    }

    @Test
    void scanRowProposedFilenameStoresOnlyFileNameForTableDisplay() {
        ScanRow row = new ScanRow(
                Path.of("C:/Media/Show.S01E02.Name.mkv"),
                "Show.S01E02.Name.mkv",
                "MKV",
                ScanMediaType.SERIES,
                ScanRowStatus.REVIEW);

        row.setProposedFilename(Optional.of("Show/Season 01/Show - S01E02 - Name.mkv"));

        assertEquals("Show - S01E02 - Name.mkv", row.proposedFilename().orElseThrow());
        assertContainsNoPathSeparator(row.proposedFilename().orElseThrow());
    }

    @Test
    void patternFormatterNeutralizesSeparatorsInsideMetadataValues() {
        ScanRow row = new ScanRow(
                Path.of("C:/Media/My.Hero.Academia.Vigilantes.S01E07.mkv"),
                "My.Hero.Academia.Vigilantes.S01E07.mkv",
                "MKV",
                ScanMediaType.SERIES,
                ScanRowStatus.REVIEW);
        row.setInputParse(ScanInputPatternParser.parse(row.originalFilename()));
        ScanRowToolbox.setSeries(row, "My Hero Academia: Vigilantes");
        ScanRowToolbox.setTitle(row, "Makoto/Truth");

        String proposed = ScanPatternFormatter.format(
                row, "{series} - S{season}E{episode} - {title}").orElseThrow();

        assertEquals("My Hero Academia: Vigilantes - S01E07 - Makoto Truth.mkv", proposed);
        assertContainsNoPathSeparator(proposed);
    }

    @Test
    void applyingPatternStoresPatternAndUpdatesProposedName() {
        ScanRow row = new ScanRow(
                Path.of("C:/Media/Show.S01E03.Name.mp4"),
                "Show.S01E03.Name.mp4",
                "MP4",
                ScanMediaType.SERIES,
                ScanRowStatus.REVIEW);
        row.setInputParse(ScanInputPatternParser.parse(row.originalFilename()));

        ScanRowToolbox.applyPattern(row, "{series} - S{season}E{episode} - {title}");

        assertEquals("{series} - S{season}E{episode} - {title}", row.pattern().orElseThrow());
        assertEquals("Show - S01E03 - Name.mp4", row.proposedFilename().orElseThrow());
    }

    @Test
    void applyingShortSxxExxHintUsesCanonicalRenamePattern() {
        ScanRow row = new ScanRow(
                Path.of("C:/Media/Show.S01E04.Title.mkv"),
                "Show.S01E04.Title.mkv",
                "MKV",
                ScanMediaType.SERIES,
                ScanRowStatus.REVIEW);
        row.setInputParse(ScanInputPatternParser.parse(row.originalFilename()));

        ScanRowToolbox.applyPattern(row, "SxxExx");

        assertEquals("{series} - S{season}E{episode} - {title}", row.pattern().orElseThrow());
        assertEquals("Show - S01E04 - Title.mkv", row.proposedFilename().orElseThrow());
    }

    private static InventorySummary summary(
            int supported, int sidecar, int unsupported, int ignored,
            int series, int movies, int unknown) {
        return new InventorySummary(supported, sidecar, unsupported, ignored, series, movies, unknown, false, false);
    }

    private static void assertContainsNoPathSeparator(String value) {
        assertTrue(!value.contains("/"), value);
        assertTrue(!value.contains("\\"), value);
    }
}
