package com.episort.ui.scan;

import java.util.List;
import java.util.function.Predicate;

/**
 * The scan headline counts, derived from the rows currently on screen.
 *
 * <p>Ignored rows are excluded from every media count: once a file is out of
 * the run, reporting it as a series or a movie to process is fabricated state.
 * Alerts are the deliberate exception — an alert on an ignored row still
 * describes something the user may want to look at.
 *
 * @param total      every row loaded, ignored ones included
 * @param series     active rows typed as a series
 * @param movies     active rows typed as a movie
 * @param unknown    active rows whose type or pattern is not understood yet
 * @param ignored    rows explicitly or implicitly out of the run
 * @param toProcess  active rows still awaiting work
 * @param conflicts  active rows blocked by a conflict or a duplicate
 * @param warnings   rows carrying an alert
 */
record ScanMetrics(
        int total,
        long series,
        long movies,
        long unknown,
        long ignored,
        long toProcess,
        long conflicts,
        long warnings) {

    static ScanMetrics from(List<ScanRow> rows) {
        return new ScanMetrics(
                rows.size(),
                countActive(rows, row -> row.mediaType() == ScanMediaType.SERIES),
                countActive(rows, row -> row.mediaType() == ScanMediaType.MOVIE),
                countActive(rows, ScanRowTableSupport::isUnknownOrNeedsUnderstanding),
                rows.stream().filter(ScanRow::isIgnored).count(),
                rows.stream()
                        .filter(row -> ScanRowTableSupport.matchesStatusFilter(row, ScanRowStatusFilter.TO_PROCESS))
                        .count(),
                countActive(rows, row -> row.status() == ScanRowStatus.CONFLICT
                        || row.status() == ScanRowStatus.DUPLICATE),
                rows.stream().filter(ScanRowTableSupport::hasAlert).count());
    }

    private static long countActive(List<ScanRow> rows, Predicate<ScanRow> predicate) {
        return rows.stream().filter(row -> !row.isIgnored()).filter(predicate).count();
    }
}
