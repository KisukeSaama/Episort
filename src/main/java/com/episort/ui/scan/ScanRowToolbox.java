package com.episort.ui.scan;

import com.episort.ai.AiChatToolCall;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Applies confirmed AI tool calls to ScanRow(s). Every structured tool follows
 * the same contract: mutate the row's structured fields (mediaType, the
 * relevant token in {@link ScanInputParse}, optionally row.order/row.pattern),
 * then recompute the proposed filename from those fields via
 * {@link ScanPatternFormatter}. This keeps the row internally consistent — the
 * structured tokens drive the proposed name, never the other way around.
 *
 * <p>{@code adjustProposedName} is the lone exception: it overrides the
 * proposed name as a raw string without touching the structured fields. It's
 * reserved for edge cases that cannot be expressed via the structured tools.
 */
public final class ScanRowToolbox {
    private static final String CANONICAL_SERIES_PATTERN = "{series} - S{season}E{episode} - {title}";

    private ScanRowToolbox() {
    }

    public static String describe(AiChatToolCall call, ScanRow target) {
        return switch (call.name()) {
            case "adjustProposedName" -> "Renommer en : « " + call.stringArg("newName", "?") + " »";
            case "setSeries" -> "Définir la série : « " + call.stringArg("series", "?") + " »";
            case "setTitle" -> "Définir le titre : « " + call.stringArg("title", "?") + " »";
            case "setYear" -> "Corriger l'année : " + call.stringArg("year", "?");
            case "setMediaType" -> "Reclassifier comme : "
                    + ("series".equalsIgnoreCase(call.stringArg("type", "")) ? "Série" : "Film");
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
            case "adjustProposedName" -> {
                String newName = call.stringArg("newName", "");
                if (newName.isBlank()) yield false;
                target.setProposedFilename(Optional.of(newName));
                yield true;
            }
            case "setSeries" -> {
                String series = call.stringArg("series", "");
                if (series.isBlank()) yield false;
                upsertToken(target, ScanInputRole.SERIES, series);
                recompute(target);
                yield true;
            }
            case "setTitle" -> {
                String title = call.stringArg("title", "");
                if (title.isBlank()) yield false;
                upsertToken(target, ScanInputRole.TITLE, title);
                recompute(target);
                yield true;
            }
            case "setYear" -> {
                String year = call.stringArg("year", "").trim();
                if (!year.matches("(19|20)\\d{2}")) yield false;
                upsertToken(target, ScanInputRole.YEAR, year);
                recompute(target);
                yield true;
            }
            case "setMediaType" -> {
                String type = call.stringArg("type", "").toLowerCase(Locale.ROOT);
                ScanMediaType newType = switch (type) {
                    case "series", "show", "tv" -> ScanMediaType.SERIES;
                    case "movie", "film" -> ScanMediaType.MOVIE;
                    default -> null;
                };
                if (newType == null) yield false;
                target.setMediaType(newType);
                if (newType == ScanMediaType.MOVIE) {
                    // Movies have no pattern template; formatMovie drives the name.
                    target.setPattern(Optional.empty());
                } else if (target.pattern().isEmpty()) {
                    target.setPattern(Optional.of(CANONICAL_SERIES_PATTERN));
                }
                recompute(target);
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

    /**
     * Replaces (or inserts) a token of the given role in the row's input parse,
     * keeping the other tokens. New tokens are stored with {@code start=0,
     * end=0} (out-of-band) because they did not come from a span in the
     * original filename — same convention as folder-derived SERIES tokens.
     */
    private static void upsertToken(ScanRow row, ScanInputRole role, String value) {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(role, "role");
        String clean = value == null ? "" : value.trim();
        ScanInputParse current = row.inputParse().orElseGet(() ->
                new ScanInputParse("unknown",
                        List.of(),
                        Optional.empty(),
                        OptionalDouble.empty(),
                        ScanInputParseSource.AI));
        List<ScanInputToken> tokens = new ArrayList<>(current.tokens());
        tokens.removeIf(t -> t.role() == role);
        if (!clean.isEmpty()) {
            tokens.add(new ScanInputToken(role, clean, clean, 0, 0));
        }
        row.setInputParse(Optional.of(new ScanInputParse(
                current.label(),
                tokens,
                current.normalizedOrder(),
                current.confidence(),
                ScanInputParseSource.AI)));
    }

    /**
     * Re-derives the proposed name from the row's current structured state.
     * For movies we leave the pattern template empty so {@code formatMovie}
     * (extension + year-aware) drives the rename; for series we use the row's
     * own pattern if any, falling back to the canonical template.
     */
    private static void recompute(ScanRow row) {
        if (row.mediaType() == ScanMediaType.MOVIE) {
            // formatMovie ignores the template argument when mediaType is MOVIE;
            // any non-blank string works to satisfy the formatter's contract.
            ScanPatternFormatter.format(row, CANONICAL_SERIES_PATTERN)
                    .ifPresent(name -> row.setProposedFilename(Optional.of(name)));
            return;
        }
        String pattern = row.pattern().filter(p -> !p.isBlank()).orElse(CANONICAL_SERIES_PATTERN);
        ScanPatternFormatter.format(row, pattern)
                .ifPresent(name -> row.setProposedFilename(Optional.of(name)));
    }

    private static String padTwo(String value) {
        if (value == null) return "??";
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return "??";
        try {
            return String.format(Locale.ROOT, "%02d", Integer.parseInt(trimmed));
        } catch (NumberFormatException ex) {
            return "??";
        }
    }
}
