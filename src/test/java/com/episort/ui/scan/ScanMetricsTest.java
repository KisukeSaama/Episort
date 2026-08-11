package com.episort.ui.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScanMetricsTest {

    private static ScanRow row(String name, ScanMediaType mediaType, ScanRowStatus status) {
        return new ScanRow(Path.of("C:", "media", name), name, "mkv", mediaType, status);
    }

    @Test
    void countsRowsByMediaType() {
        ScanMetrics metrics = ScanMetrics.from(List.of(
                row("a.mkv", ScanMediaType.SERIES, ScanRowStatus.OK),
                row("b.mkv", ScanMediaType.SERIES, ScanRowStatus.OK),
                row("c.mkv", ScanMediaType.MOVIE, ScanRowStatus.OK)));

        assertEquals(3, metrics.total());
        assertEquals(2, metrics.series());
        assertEquals(1, metrics.movies());
    }

    @Test
    void ignoredRowsCountAsIgnoredAndNotAsMedia() {
        ScanRow ignored = row("a.mkv", ScanMediaType.SERIES, ScanRowStatus.OK);
        ignored.markIgnored();

        ScanMetrics metrics = ScanMetrics.from(List.of(
                ignored,
                row("b.mkv", ScanMediaType.SERIES, ScanRowStatus.OK)));

        assertEquals(2, metrics.total(), "an ignored row is still a loaded row");
        assertEquals(1, metrics.ignored());
        assertEquals(1, metrics.series(), "an ignored row must not be reported as a series to process");
    }

    @Test
    void conflictsAndDuplicatesBothCountAsConflicts() {
        ScanMetrics metrics = ScanMetrics.from(List.of(
                row("a.mkv", ScanMediaType.SERIES, ScanRowStatus.CONFLICT),
                row("b.mkv", ScanMediaType.SERIES, ScanRowStatus.DUPLICATE),
                row("c.mkv", ScanMediaType.SERIES, ScanRowStatus.OK)));

        assertEquals(2, metrics.conflicts());
    }

    @Test
    void anAlertTextRaisesTheWarningCount() {
        ScanRow alerting = row("a.mkv", ScanMediaType.SERIES, ScanRowStatus.OK);
        alerting.setAlertText(Optional.of("something is off"));

        ScanMetrics metrics = ScanMetrics.from(List.of(
                alerting,
                row("b.mkv", ScanMediaType.SERIES, ScanRowStatus.OK)));

        assertEquals(1, metrics.warnings());
    }

    @Test
    void unknownTypeCountsAsUnknown() {
        ScanMetrics metrics = ScanMetrics.from(List.of(
                row("a.mkv", ScanMediaType.UNKNOWN, ScanRowStatus.REVIEW),
                row("b.mkv", ScanMediaType.SERIES, ScanRowStatus.OK)));

        assertEquals(1, metrics.unknown());
    }

    @Test
    void resolvedRowsDropOutOfToProcess() {
        ScanMetrics metrics = ScanMetrics.from(List.of(
                row("a.mkv", ScanMediaType.SERIES, ScanRowStatus.OK),
                row("b.mkv", ScanMediaType.SERIES, ScanRowStatus.TMDB),
                row("c.mkv", ScanMediaType.SERIES, ScanRowStatus.REVIEW)));

        assertEquals(1, metrics.toProcess());
    }

    @Test
    void everyCountIsZeroWithoutRows() {
        ScanMetrics metrics = ScanMetrics.from(List.of());

        assertEquals(0, metrics.total());
        assertEquals(0, metrics.series());
        assertEquals(0, metrics.movies());
        assertEquals(0, metrics.unknown());
        assertEquals(0, metrics.ignored());
        assertEquals(0, metrics.toProcess());
        assertEquals(0, metrics.conflicts());
        assertEquals(0, metrics.warnings());
    }
}
