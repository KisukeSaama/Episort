package com.episort.ui.scan;

import com.episort.ai.AiChatToolCall;
import java.util.List;
import java.util.Optional;

/** Applies confirmed AI tool calls to ScanRow(s). All operations are advisory and reversible. */
public final class ScanRowToolbox {
    private static final String CANONICAL_SERIES_PATTERN = "{series} - S{season}E{episode} - {title}";

    private ScanRowToolbox() {
    }

    public static String describe(AiChatToolCall call, ScanRow target) {
        return switch (call.name()) {
            case "selectTvdbMatch" -> "Choisir la correspondance TVDB : « "
                    + call.stringArg("candidate", "?") + " »";
            case "adjustProposedName" -> "Renommer en : « " + call.stringArg("newName", "?") + " »";
            case "setOrder" -> "Définir l'ordre : " + call.stringArg("order", "?");
            case "applyPatternToGroup" -> {
                String pattern = call.stringArg("pattern", "?");
                String series = call.stringArg("series", "");
                yield series.isBlank()
                        ? "Appliquer le pattern « " + pattern + " » à tout le groupe"
                        : "Appliquer le pattern « " + pattern + " » à tout le groupe (série : « "
                                + series + " »)";
            }
            default -> "Outil inconnu : " + call.name();
        };
    }

    public static boolean apply(AiChatToolCall call, ScanRow target, List<ScanRow> groupRows) {
        if (target == null) {
            return false;
        }
        return switch (call.name()) {
            case "selectTvdbMatch" -> {
                String candidate = call.stringArg("candidate", "");
                if (candidate.isBlank()) yield false;
                target.setTvdbMatch(Optional.of(candidate));
                yield true;
            }
            case "adjustProposedName" -> {
                String newName = call.stringArg("newName", "");
                if (newName.isBlank()) yield false;
                target.setProposedFilename(Optional.of(newName));
                yield true;
            }
            case "setOrder" -> {
                String order = call.stringArg("order", "");
                if (order.isBlank()) yield false;
                target.setOrder(Optional.of(order));
                yield true;
            }
            case "applyPatternToGroup" -> {
                String pattern = call.stringArg("pattern", "");
                if (pattern.isBlank() || groupRows == null) yield false;
                String seriesOverride = call.stringArg("series", "");
                for (ScanRow row : groupRows) {
                    applyPattern(row, pattern, seriesOverride);
                }
                yield true;
            }
            default -> false;
        };
    }

    static void applyPattern(ScanRow row, String pattern) {
        applyPattern(row, pattern, null);
    }

    static void applyPattern(ScanRow row, String pattern, String seriesOverride) {
        String normalized = normalizePattern(pattern);
        if (row == null || normalized.isBlank()) {
            return;
        }
        row.setPattern(Optional.of(normalized));
        ScanPatternFormatter.format(row, normalized, seriesOverride)
                .ifPresent(name -> row.setProposedFilename(Optional.of(name)));
        row.setNoteText(Optional.of("Pattern proposé : " + normalized));
    }

    static String normalizePattern(String pattern) {
        String trimmed = pattern == null ? "" : pattern.trim();
        return switch (trimmed.toLowerCase(java.util.Locale.ROOT)) {
            case "sxxexx", "s{season}e{episode}", "sxxexxx", "1xnn", "1x01", "absolute" ->
                    CANONICAL_SERIES_PATTERN;
            default -> trimmed;
        };
    }
}
