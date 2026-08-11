package com.episort.ui.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.planning.PlanExclusionReason;
import com.episort.planning.PlanMediaKind;
import com.episort.planning.PlanSourceItem;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class ScanPlanMapperTest {
    @Test
    void aTmdbMatchedEpisodeCarriesItsFullIdentity() {
        ScanRow row = row("show.s02e05.mkv", ScanMediaType.SERIES, ScanRowStatus.TMDB);
        row.setInputParse(Optional.of(episodeParse("Firefly", "02", "05", "Out of Gas")));

        PlanSourceItem item = ScanPlanMapper.toPlanItem(row);

        assertEquals(PlanMediaKind.SERIES_EPISODE, item.kind());
        assertEquals(Optional.of("Firefly"), item.seriesTitle());
        assertEquals(OptionalInt.of(2), item.seasonNumber());
        assertEquals(OptionalInt.of(5), item.episodeNumber());
        assertEquals(Optional.of("Out of Gas"), item.episodeTitle());
        assertEquals(PlanExclusionReason.NONE, item.exclusionReason());
        assertTrue(item.hasCompleteIdentity());
    }

    @Test
    void seasonZeroIsPlannedAsASpecial() {
        ScanRow row = row("show.ova.mkv", ScanMediaType.SERIES, ScanRowStatus.TMDB);
        row.setInputParse(Optional.of(episodeParse("Show", "00", "03", "Extra")));

        assertEquals(PlanMediaKind.SPECIAL, ScanPlanMapper.toPlanItem(row).kind());
    }

    @Test
    void theSeasonAndEpisodeFallBackToTheOrderColumn() {
        ScanRow row = row("show.mkv", ScanMediaType.SERIES, ScanRowStatus.OK);
        row.setInputParse(Optional.of(new ScanInputParse(
                "heuristic",
                List.of(new ScanInputToken(ScanInputRole.SERIES, "Show", "Show", 0, 0)),
                Optional.empty(),
                OptionalDouble.empty(),
                ScanInputParseSource.HEURISTIC)));
        row.setOrder(Optional.of("S03E12"));

        PlanSourceItem item = ScanPlanMapper.toPlanItem(row);

        assertEquals(OptionalInt.of(3), item.seasonNumber());
        assertEquals(OptionalInt.of(12), item.episodeNumber());
    }

    @Test
    void aNonNumericOrderLabelDoesNotFabricateASeasonOrEpisode() {
        ScanRow row = row("show.mkv", ScanMediaType.SERIES, ScanRowStatus.OK);
        row.setOrder(Optional.of("Aired"));

        PlanSourceItem item = ScanPlanMapper.toPlanItem(row);

        assertEquals(OptionalInt.empty(), item.seasonNumber());
        assertFalse(item.hasCompleteIdentity());
    }

    @Test
    void aMovieCarriesItsTitleAndYear() {
        ScanRow row = row("blade.runner.1982.mkv", ScanMediaType.MOVIE, ScanRowStatus.TMDB);
        row.setInputParse(Optional.of(movieParse("Blade Runner", "1982")));

        PlanSourceItem item = ScanPlanMapper.toPlanItem(row);

        assertEquals(PlanMediaKind.MOVIE, item.kind());
        assertEquals(Optional.of("Blade Runner"), item.movieTitle());
        assertEquals(OptionalInt.of(1982), item.movieYear());
    }

    @Test
    void anUntaggedMovieFallsBackToTheFilenameIdentity() {
        ScanRow row = row("blade.runner.1982.1080p.mkv", ScanMediaType.MOVIE, ScanRowStatus.OK);

        PlanSourceItem item = ScanPlanMapper.toPlanItem(row);

        assertEquals(Optional.of("blade runner"), item.movieTitle());
        assertEquals(OptionalInt.of(1982), item.movieYear());
    }

    @Test
    void theOriginalExtensionIsReadFromTheRealFilename() {
        assertEquals(".MKV", ScanPlanMapper.extensionOf(
                row("Show.MKV", ScanMediaType.SERIES, ScanRowStatus.OK)));
        assertEquals(".mp4", ScanPlanMapper.extensionOf(
                row("Show.mp4", ScanMediaType.SERIES, ScanRowStatus.OK)));
    }

    @Test
    void reviewStatesMapOntoTheMatchingExclusionReasons() {
        assertEquals(PlanExclusionReason.UNSUPPORTED, ScanPlanMapper.exclusionReasonOf(
                row("clip.wmv", ScanMediaType.SERIES, ScanRowStatus.EXT)));
        assertEquals(PlanExclusionReason.DUPLICATE, ScanPlanMapper.exclusionReasonOf(
                row("dup.mkv", ScanMediaType.SERIES, ScanRowStatus.DUPLICATE)));
        assertEquals(PlanExclusionReason.AMBIGUOUS, ScanPlanMapper.exclusionReasonOf(
                row("bad.mkv", ScanMediaType.SERIES, ScanRowStatus.ERROR)));
        assertEquals(PlanExclusionReason.UNASSIGNED, ScanPlanMapper.exclusionReasonOf(
                row("mystery.mkv", ScanMediaType.UNKNOWN, ScanRowStatus.REVIEW)));
        assertEquals(PlanExclusionReason.NONE, ScanPlanMapper.exclusionReasonOf(
                row("show.mkv", ScanMediaType.SERIES, ScanRowStatus.OK)));
    }

    @Test
    void anIgnoredRowIsExcludedWhateverItsPreviousStatusWas() {
        ScanRow row = row("show.mkv", ScanMediaType.SERIES, ScanRowStatus.TMDB);
        row.markIgnored();

        assertEquals(PlanExclusionReason.IGNORED, ScanPlanMapper.toPlanItem(row).exclusionReason());
    }

    @Test
    void mappingPreservesRowOrder() {
        List<PlanSourceItem> items = ScanPlanMapper.toPlanItems(List.of(
                row("a.mkv", ScanMediaType.SERIES, ScanRowStatus.OK),
                row("b.mkv", ScanMediaType.SERIES, ScanRowStatus.OK)));

        assertEquals(List.of(Path.of("C:/Media/a.mkv"), Path.of("C:/Media/b.mkv")),
                items.stream().map(PlanSourceItem::sourcePath).toList());
    }

    private static ScanRow row(String filename, ScanMediaType type, ScanRowStatus status) {
        int dot = filename.lastIndexOf('.');
        String extension = dot > 0 ? filename.substring(dot + 1).toUpperCase(Locale.ROOT) : "";
        return new ScanRow(Path.of("C:/Media").resolve(filename), filename, extension, type, status);
    }

    private static ScanInputParse episodeParse(String series, String season, String episode, String title) {
        return new ScanInputParse(
                "TMDB",
                List.of(
                        new ScanInputToken(ScanInputRole.SERIES, series, series, 0, 0),
                        new ScanInputToken(ScanInputRole.SEASON, season, season, 0, 0),
                        new ScanInputToken(ScanInputRole.EPISODE, episode, episode, 0, 0),
                        new ScanInputToken(ScanInputRole.TITLE, title, title, 0, 0)),
                Optional.of("S" + season + "E" + episode),
                OptionalDouble.empty(),
                ScanInputParseSource.TMDB);
    }

    private static ScanInputParse movieParse(String title, String year) {
        return new ScanInputParse(
                "TMDB",
                List.of(
                        new ScanInputToken(ScanInputRole.TITLE, title, title, 0, 0),
                        new ScanInputToken(ScanInputRole.YEAR, year, year, 0, 0)),
                Optional.empty(),
                OptionalDouble.empty(),
                ScanInputParseSource.TMDB);
    }
}
