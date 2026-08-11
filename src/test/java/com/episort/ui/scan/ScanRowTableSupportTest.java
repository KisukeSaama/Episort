package com.episort.ui.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScanRowTableSupportTest {
    @Test
    void naturalSortOrdersEpisodeNumbersNumerically() {
        List<String> values = new ArrayList<>(List.of("Episode 10.mkv", "Episode 2.mkv", "Episode 1.mkv"));

        values.sort(ScanRowTableSupport.NATURAL_TEXT);

        assertEquals(List.of("Episode 1.mkv", "Episode 2.mkv", "Episode 10.mkv"), values);
    }

    @Test
    void naturalSortOrdersSeasonEpisodeNumbersNumerically() {
        List<String> values = new ArrayList<>(List.of(
                "Show.S10E01.mkv",
                "Show.S01E10.mkv",
                "Show.S01E02.mkv",
                "Show.S01E01.mkv",
                "Show.S09E99.mkv",
                "Show.S09E100.mkv"));

        values.sort(ScanRowTableSupport.NATURAL_TEXT);

        assertEquals(List.of(
                "Show.S01E01.mkv",
                "Show.S01E02.mkv",
                "Show.S01E10.mkv",
                "Show.S09E99.mkv",
                "Show.S09E100.mkv",
                "Show.S10E01.mkv"), values);
    }

    @Test
    void mediaTypeSortUsesBusinessOrderThenNaturalFilename() {
        List<ScanRow> rows = new ArrayList<>(List.of(
                row("Movie 2.mkv", ScanMediaType.MOVIE, ScanRowStatus.OK),
                row("Unknown 1.mkv", ScanMediaType.UNKNOWN, ScanRowStatus.TYPE),
                row("Show.S01E10.mkv", ScanMediaType.SERIES, ScanRowStatus.OK),
                row("Show.S01E02.mkv", ScanMediaType.SERIES, ScanRowStatus.OK),
                row("Movie 10.mkv", ScanMediaType.MOVIE, ScanRowStatus.OK)));

        rows.sort(Comparator.comparing(ScanRow::mediaType, ScanRowTableSupport.MEDIA_TYPE)
                .thenComparing(ScanRow::originalFilename, ScanRowTableSupport.NATURAL_TEXT));

        assertEquals(List.of(
                "Show.S01E02.mkv",
                "Show.S01E10.mkv",
                "Movie 2.mkv",
                "Movie 10.mkv",
                "Unknown 1.mkv"), filenames(rows));
    }

    @Test
    void statusSortUsesBusinessOrderNotAlphabeticalOrder() {
        List<ScanRowStatus> statuses = new ArrayList<>(List.of(
                ScanRowStatus.ERROR,
                ScanRowStatus.LOW_CONFIDENCE,
                ScanRowStatus.OK,
                ScanRowStatus.CONFLICT,
                ScanRowStatus.TYPE,
                ScanRowStatus.REVIEW));

        statuses.sort(ScanRowTableSupport.STATUS);

        assertEquals(List.of(
                ScanRowStatus.OK,
                ScanRowStatus.REVIEW,
                ScanRowStatus.LOW_CONFIDENCE,
                ScanRowStatus.TYPE,
                ScanRowStatus.CONFLICT,
                ScanRowStatus.ERROR), statuses);
    }

    @Test
    void confidenceSortUsesNumericPercentValues() {
        List<String> values = new ArrayList<>(List.of("100%", "20%", "5%", "75%"));

        values.sort(ScanRowTableSupport.CONFIDENCE_PERCENT);

        assertEquals(List.of("5%", "20%", "75%", "100%"), values);
    }

    @Test
    void unknownFilterIncludesUnknownAndUnderstandingProblems() {
        assertTrue(ScanRowTableSupport.matchesFilter(
                row("Unknown.mkv", ScanMediaType.UNKNOWN, ScanRowStatus.OK), ScanRowFilter.UNKNOWN));
        assertTrue(ScanRowTableSupport.matchesFilter(
                row("Needs pattern.mkv", ScanMediaType.SERIES, ScanRowStatus.PATTERN), ScanRowFilter.UNKNOWN));
        assertTrue(ScanRowTableSupport.matchesFilter(
                row("Needs ai.mkv", ScanMediaType.MOVIE, ScanRowStatus.LOW_CONFIDENCE), ScanRowFilter.UNKNOWN));
        assertFalse(ScanRowTableSupport.matchesFilter(
                row("Known.mkv", ScanMediaType.SERIES, ScanRowStatus.OK), ScanRowFilter.UNKNOWN));
    }

    @Test
    void mediaFiltersMatchOnlyRequestedTypeAndAllMatchesEverything() {
        ScanRow movie = row("Movie.mkv", ScanMediaType.MOVIE, ScanRowStatus.OK);
        ScanRow series = row("Show.mkv", ScanMediaType.SERIES, ScanRowStatus.OK);

        assertTrue(ScanRowTableSupport.matchesFilter(movie, ScanRowFilter.MOVIES));
        assertFalse(ScanRowTableSupport.matchesFilter(series, ScanRowFilter.MOVIES));
        assertTrue(ScanRowTableSupport.matchesFilter(series, ScanRowFilter.SERIES));
        assertTrue(ScanRowTableSupport.matchesFilter(movie, ScanRowFilter.ALL));
    }

    @Test
    void statusFiltersCanFindIgnoredRowsButExcludeThemFromActiveBuckets() {
        ScanRow ignored = row("Ignored.mkv", ScanMediaType.SERIES, ScanRowStatus.IGNORED);
        ScanRow review = row("Review.mkv", ScanMediaType.SERIES, ScanRowStatus.REVIEW);
        ScanRow tmdb = row("Tmdb.mkv", ScanMediaType.SERIES, ScanRowStatus.TMDB);

        assertTrue(ScanRowTableSupport.matchesStatusFilter(ignored, ScanRowStatusFilter.IGNORED));
        assertFalse(ScanRowTableSupport.matchesStatusFilter(ignored, ScanRowStatusFilter.TO_PROCESS));
        assertTrue(ScanRowTableSupport.matchesStatusFilter(review, ScanRowStatusFilter.TO_PROCESS));
        assertTrue(ScanRowTableSupport.matchesStatusFilter(tmdb, ScanRowStatusFilter.TMDB));
    }

    @Test
    void ignoredRowsCannotBeActionSelected() {
        ScanRow ignored = row("Ignored.mkv", ScanMediaType.SERIES, ScanRowStatus.IGNORED);

        ignored.setSelected(true);

        assertFalse(ignored.isSelected());
    }

    @Test
    void alertDefinitionIsSharedByCounterAndAlertFilter() {
        ScanRow warning = row("Warning.mkv", ScanMediaType.SERIES, ScanRowStatus.OK);
        warning.setAlertText(Optional.of("Needs attention"));
        ScanRow error = row("Error.mkv", ScanMediaType.SERIES, ScanRowStatus.ERROR);
        ScanRow clean = row("Clean.mkv", ScanMediaType.SERIES, ScanRowStatus.OK);
        ScanRow ignoredWarning = row("Ignored.mkv", ScanMediaType.IGNORED, ScanRowStatus.IGNORED);
        ignoredWarning.setAlertText(Optional.of("Old warning"));

        assertTrue(ScanRowTableSupport.hasAlert(warning));
        assertTrue(ScanRowTableSupport.matchesStatusFilter(warning, ScanRowStatusFilter.ALERTS));
        assertTrue(ScanRowTableSupport.hasAlert(error));
        assertTrue(ScanRowTableSupport.matchesStatusFilter(error, ScanRowStatusFilter.ALERTS));
        assertFalse(ScanRowTableSupport.hasAlert(clean));
        assertFalse(ScanRowTableSupport.matchesStatusFilter(clean, ScanRowStatusFilter.ALERTS));
        assertFalse(ScanRowTableSupport.hasAlert(ignoredWarning));
        assertFalse(ScanRowTableSupport.matchesStatusFilter(ignoredWarning, ScanRowStatusFilter.ALERTS));
    }

    @Test
    void informativeNotesAreNotAlertsAndIgnoringRowsKeepsThemOutOfAlertBucket() {
        ScanRow informative = row("Informative.mkv", ScanMediaType.SERIES, ScanRowStatus.TMDB);
        informative.setNoteText(Optional.of("TMDB candidates loaded; select one to keep it for this session."));

        ScanRow ignored = row("Ignored.mkv", ScanMediaType.SERIES, ScanRowStatus.REVIEW);
        ignored.setAlertText(Optional.of("Needs attention"));
        ignored.setStatus(ScanRowStatus.IGNORED);

        assertFalse(ScanRowTableSupport.hasAlert(informative));
        assertFalse(ScanRowTableSupport.matchesStatusFilter(informative, ScanRowStatusFilter.ALERTS));
        assertFalse(ScanRowTableSupport.hasAlert(ignored));
        assertFalse(ScanRowTableSupport.matchesStatusFilter(ignored, ScanRowStatusFilter.ALERTS));
    }

    @Test
    void ignoreRoundTripRestoresActualPreviousStateWithoutDuplicatingAlerts() {
        ScanRow row = row("Suggested.mkv", ScanMediaType.SERIES, ScanRowStatus.REVIEW);
        row.setAlertText(Optional.of("TMDB suggestion needs validation"));

        row.markIgnored();
        row.markIgnored();

        assertTrue(row.isIgnored());
        assertFalse(ScanRowTableSupport.hasAlert(row));

        row.stopIgnoring();
        row.stopIgnoring();

        assertEquals(ScanMediaType.SERIES, row.mediaType());
        assertEquals(ScanRowStatus.REVIEW, row.status());
        assertTrue(ScanRowTableSupport.hasAlert(row));
        assertEquals("TMDB suggestion needs validation", row.alertText().orElseThrow());
    }

    @Test
    void toProcessCounterPredicateExcludesIgnoredOkAndTmdbRows() {
        assertTrue(ScanRowTableSupport.matchesStatusFilter(
                row("Review.mkv", ScanMediaType.SERIES, ScanRowStatus.REVIEW),
                ScanRowStatusFilter.TO_PROCESS));
        assertFalse(ScanRowTableSupport.matchesStatusFilter(
                row("Ignored.mkv", ScanMediaType.SERIES, ScanRowStatus.IGNORED),
                ScanRowStatusFilter.TO_PROCESS));
        assertFalse(ScanRowTableSupport.matchesStatusFilter(
                row("Ready.mkv", ScanMediaType.SERIES, ScanRowStatus.OK),
                ScanRowStatusFilter.TO_PROCESS));
        assertFalse(ScanRowTableSupport.matchesStatusFilter(
                row("Tmdb.mkv", ScanMediaType.SERIES, ScanRowStatus.TMDB),
                ScanRowStatusFilter.TO_PROCESS));
    }

    private static ScanRow row(String filename, ScanMediaType type, ScanRowStatus status) {
        return new ScanRow(Path.of(filename), filename, "mkv", type, status);
    }

    private static List<String> filenames(List<ScanRow> rows) {
        return rows.stream().map(ScanRow::originalFilename).toList();
    }
}
