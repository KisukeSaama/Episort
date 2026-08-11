package com.episort.ui.scan;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Structured edits applied to {@link ScanRow}s from the review UI. Every entry
 * point follows the same contract: mutate the row's structured fields
 * (mediaType, the relevant token in {@link ScanInputParse}, optionally
 * row.order/row.pattern), then recompute the proposed filename from those
 * fields via {@link ScanPatternFormatter}. This keeps the row internally
 * consistent — the structured tokens drive the proposed name, never the other
 * way around.
 */
public final class ScanRowToolbox {
    private static final String CANONICAL_SERIES_PATTERN = "{series} - S{season}E{episode} - {title}";

    private ScanRowToolbox() {
    }

    /** Sets the series name and re-derives the proposed filename. */
    public static void setSeries(ScanRow row, String series) {
        if (row == null || series == null || series.isBlank()) {
            return;
        }
        upsertToken(row, ScanInputRole.SERIES, series);
        recompute(row);
    }

    /** Sets the episode/movie title and re-derives the proposed filename. */
    public static void setTitle(ScanRow row, String title) {
        if (row == null || title == null || title.isBlank()) {
            return;
        }
        upsertToken(row, ScanInputRole.TITLE, title);
        recompute(row);
    }

    /** Sets a four-digit release year; anything else is rejected. */
    public static boolean setYear(ScanRow row, String year) {
        String clean = year == null ? "" : year.trim();
        if (row == null || !clean.matches("(19|20)\\d{2}")) {
            return false;
        }
        upsertToken(row, ScanInputRole.YEAR, clean);
        recompute(row);
        return true;
    }

    /**
     * Reclassifies the row. Movies drop their pattern template because
     * {@code formatMovie} drives their name; series get the canonical template
     * when they had none.
     */
    public static void setMediaType(ScanRow row, ScanMediaType newType) {
        if (row == null || newType == null) {
            return;
        }
        row.setMediaType(newType);
        if (newType == ScanMediaType.MOVIE) {
            row.setPattern(Optional.empty());
        } else if (row.pattern().isEmpty()) {
            row.setPattern(Optional.of(CANONICAL_SERIES_PATTERN));
        }
        recompute(row);
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
        return switch (trimmed.toLowerCase(Locale.ROOT)) {
            case "sxxexx", "s{season}e{episode}", "sxxexxx", "1xnn", "1x01", "absolute" ->
                    CANONICAL_SERIES_PATTERN;
            default -> trimmed;
        };
    }

    /**
     * Replaces (or inserts) a token of the given role in the row's input parse,
     * keeping the other tokens. New tokens are stored with {@code start=0,
     * end=0} (out-of-band) because they did not come from a span in the
     * original filename — same convention as folder-derived SERIES tokens. The
     * parse is stamped {@code USER} so later automated passes never clobber it.
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
                        ScanInputParseSource.USER));
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
                ScanInputParseSource.USER)));
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
}
