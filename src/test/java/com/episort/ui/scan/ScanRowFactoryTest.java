package com.episort.ui.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import javafx.scene.input.KeyCode;
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
        assertSame(ScanRowStatus.PREVIEW, row.status());
        assertTrue(row.proposedFilename().isEmpty());
        assertTrue(row.tvdbMatch().isEmpty());
        assertEquals("S01E01", row.order().orElseThrow());
        assertEquals(0.9, row.confidence().orElseThrow(), 1e-9);
        assertTrue(row.destination().isEmpty());
        assertTrue(row.alertText().isEmpty());
        assertTrue(row.noteText().isEmpty());
        assertTrue(row.conflictText().isEmpty());
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
        assertSame(ScanRowStatus.PREVIEW, rows.get(0).status());
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
    void patternFormatterGeneratesProposedNameFromAvailableMetadata() {
        ScanRow row = new ScanRow(
                Path.of("C:/Media/Ma Serie - 01x02 - Titre.mkv"),
                "Ma Serie - 01x02 - Titre.mkv",
                "MKV",
                ScanMediaType.SERIES,
                ScanRowStatus.PREVIEW);

        String proposed = ScanPatternFormatter.format(
                row,
                "{series}/Season {season}/{series} - S{season}E{episode} - {title}")
                .orElseThrow();

        assertEquals("Ma Serie/Season 01/Ma Serie - S01E02 - Titre.mkv", proposed);
    }

    @Test
    void applyingPatternStoresPatternAndUpdatesProposedName() {
        ScanRow row = new ScanRow(
                Path.of("C:/Media/Show.S01E03.Name.mp4"),
                "Show.S01E03.Name.mp4",
                "MP4",
                ScanMediaType.SERIES,
                ScanRowStatus.PREVIEW);

        ScanRowToolbox.applyPattern(row, "{series} - S{season}E{episode} - {title}");

        assertEquals("{series} - S{season}E{episode} - {title}", row.pattern().orElseThrow());
        assertEquals("Show - S01E03 - Name.mp4", row.proposedFilename().orElseThrow());
    }

    @Test
    void markdownRendererCreatesBlocksForSupportedSubsetAndIgnoresBlankInput() {
        assertTrue(AiChatMarkdownRenderer.blocks("   ").isEmpty());

        var blocks = AiChatMarkdownRenderer.blocks("""
                # Title
                - **Bold** item
                Plain *italic*
                ```
                code();
                ```
                """);

        assertEquals("heading", blocks.get(0).type());
        assertEquals("Title", blocks.get(0).text());
        assertEquals("bullet", blocks.get(1).type());
        assertEquals("• Bold item", blocks.get(1).text());
        assertEquals("paragraph", blocks.get(2).type());
        assertEquals("Plain italic", blocks.get(2).text());
        assertEquals("code", blocks.get(3).type());
        assertEquals("code();", blocks.get(3).text());
    }

    @Test
    void chatEnterSendsAndShiftEnterKeepsNewline() {
        assertTrue(AiChatPanel.shouldSendOnEnter(KeyCode.ENTER, false));
        assertEquals(false, AiChatPanel.shouldSendOnEnter(KeyCode.ENTER, true));
    }

    private static InventorySummary summary(
            int supported, int sidecar, int unsupported, int ignored,
            int series, int movies, int unknown) {
        return new InventorySummary(supported, sidecar, unsupported, ignored, series, movies, unknown, false, false);
    }
}
