package com.episort.scanner;


public record InventorySummary(
        int supportedVideoCount,
        int sidecarCount,
        int unsupportedCount,
        int ignoredCount,
        int likelySeriesGroupCount,
        int likelyMovieGroupCount,
        int unknownItemCount,
        boolean patternValidated,
        boolean operationPlanApproved) {
}
