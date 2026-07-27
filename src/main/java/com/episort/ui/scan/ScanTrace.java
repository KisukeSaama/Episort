package com.episort.ui.scan;

import com.episort.tvdb.debug.TvdbRequestBus;
import com.episort.tvdb.debug.TvdbRequestTrace;
import java.time.Instant;

/**
 * Developer-only diagnostics for the scan screen, published on the shared
 * {@link TvdbRequestBus} so the debug view shows business events next to the
 * HTTP traffic that caused them.
 *
 * <p>Every publish is best-effort: a failing trace must never affect the scan
 * workflow, which is why each entry point swallows runtime failures.
 */
final class ScanTrace {

    private ScanTrace() {
    }

    /** Why a batch TVDB apply touched the rows it did — or touched none. */
    static void publishApply(int totalRows, int rowsWithGroup, int matches, int rowsApplied, String detail) {
        publish("APPLY", "scan-table", "totalRows=" + totalRows
                + "  rowsWithGroup=" + rowsWithGroup
                + "  matches=" + matches
                + "  rowsApplied=" + rowsApplied
                + "\n" + detail);
    }

    /** The before/after of an ignore toggle, including its effect on alerts. */
    static void publishIgnore(
            ScanRow row,
            boolean previousIgnored,
            ScanRowStatus previousStatus,
            int previousAlerts) {
        publish("ROW_STATE", row.originalFilename(), "rowId=" + normalizedPath(row)
                + "\nfileName=" + row.originalFilename()
                + "\npreviousIgnored=" + previousIgnored
                + "\nnewIgnored=" + row.isIgnored()
                + "\npreviousStatus=" + previousStatus
                + "\nnewStatus=" + row.status()
                + "\npreviousActiveAlerts=" + previousAlerts
                + "\nnewActiveAlerts=" + (ScanRowTableSupport.hasAlert(row) ? 1 : 0)
                + "\nalertMessage=" + row.alertText().orElse("")
                + "\nsource=USER"
                + "\nreason=ignored state toggled");
    }

    /** Why an alert was raised, recalculated or cleared on a row. */
    static void publishAlert(ScanRow row, String action, String source, String message, String reason) {
        publish("ALERT", row.originalFilename(), "rowId=" + normalizedPath(row)
                + "\nfileName=" + row.originalFilename()
                + "\nignored=" + row.isIgnored()
                + "\nstatus=" + row.status()
                + "\naction=" + action
                + "\nactiveAlert=" + ScanRowTableSupport.hasAlert(row)
                + "\nalertMessage=" + message
                + "\nsource=" + source
                + "\nreason=" + reason);
    }

    private static String normalizedPath(ScanRow row) {
        return row.sourcePath().toAbsolutePath().normalize().toString();
    }

    private static void publish(String method, String path, String body) {
        try {
            TvdbRequestBus.get().publish(
                    new TvdbRequestTrace(Instant.now(), method, path, 0, 0L, body, null, false));
        } catch (RuntimeException ignored) {
            // Developer-only diagnostics must never affect the scan workflow.
        }
    }
}
