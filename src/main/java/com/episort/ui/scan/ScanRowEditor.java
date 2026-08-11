package com.episort.ui.scan;

import com.episort.ui.UiText;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Row-level editing rules for the scan table: which token value a column shows,
 * what a user edit does to the parse, and how the proposed filename is derived
 * from it.
 *
 * <p>Extracted from the scan screen so the rules can be tested without a
 * JavaFX toolkit. Nothing here touches the table or the detail panel — callers
 * own the refresh.
 */
final class ScanRowEditor {

    /** Applied when a row carries no pattern of its own. */
    static final String DEFAULT_PATTERN = "{series} - S{season}E{episode} - {title}";

    private ScanRowEditor() {
    }

    /**
     * The value a role column displays.
     *
     * <p>Series/season/episode are meaningless for a movie, and the year is
     * meaningless for an episode (series ordering is owned by TMDB downstream),
     * so those combinations read blank rather than stale.
     */
    static String roleValue(ScanRow row, ScanInputRole role) {
        boolean isMovie = row.mediaType() == ScanMediaType.MOVIE;
        if (isMovie && (role == ScanInputRole.SERIES
                || role == ScanInputRole.SEASON
                || role == ScanInputRole.EPISODE)) {
            return "";
        }
        if (!isMovie && role == ScanInputRole.YEAR) {
            return "";
        }
        Optional<String> tagged = row.inputParse().flatMap(parse -> parse.tokenValue(role));
        if (tagged.isPresent()) {
            return tagged.get();
        }
        // For movies the formatter already derives title and year from the
        // filename when the parser did not tag them; the column shows the same
        // value rather than staying blank next to a populated proposed name.
        if (isMovie && role == ScanInputRole.TITLE) {
            return derivedMovieTitle(row);
        }
        if (isMovie && role == ScanInputRole.YEAR) {
            return derivedMovieYear(row);
        }
        return "";
    }

    static String derivedMovieTitle(ScanRow row) {
        return ScanPlanMapper.derivedMovieTitle(row.originalFilename());
    }

    static String derivedMovieYear(ScanRow row) {
        return ScanPlanMapper.derivedMovieYear(row.originalFilename());
    }

    /** Rebuilds the proposed filename from the row's pattern, if it yields one. */
    static void recomputeProposedName(ScanRow row) {
        ScanPatternFormatter.format(row, patternOf(row))
                .ifPresent(name -> row.setProposedFilename(Optional.of(name)));
    }

    /**
     * Writes one edited token back into the row's parse, then refreshes the
     * derived state: a blank value removes the token, season/episode edits
     * recompute the normalized order, and the proposed name follows.
     *
     * <p>The parse is re-sourced to {@code USER} so later TMDB metadata does not
     * silently overwrite what the user typed.
     */
    static void applyTokenEdit(ScanRow row, ScanInputRole role, String value) {
        ScanInputParse base = row.inputParse().orElseGet(() -> parseOrEmpty(row));
        List<ScanInputToken> tokens = replaceToken(base.tokens(), role, value);
        Optional<String> normalizedOrder = (role == ScanInputRole.SEASON || role == ScanInputRole.EPISODE)
                ? recomputeOrder(tokens)
                : base.normalizedOrder();
        ScanInputParse updated = new ScanInputParse(
                base.label(), tokens, normalizedOrder, base.confidence(), ScanInputParseSource.USER);
        row.setInputParse(Optional.of(updated));
        String label = updated.summary();
        row.setInputPattern(label.isBlank() ? Optional.empty() : Optional.of(label));
        if (role == ScanInputRole.SEASON || role == ScanInputRole.EPISODE) {
            row.setOrder(normalizedOrder);
        }
        recomputeProposedName(row);
    }

    /** Replaces the manual input pattern, re-parsing the filename behind it. */
    static void applyManualInputPattern(ScanRow row, String value) {
        if (value == null || value.isBlank() || UiText.EMPTY.equals(value)) {
            row.setInputPattern(Optional.empty());
            row.setInputParse(Optional.empty());
            return;
        }
        row.setInputPattern(Optional.of(value));
        ScanInputPatternParser.parse(row.originalFilename())
                .map(parse -> parse.withLabel(firstLine(value)).withSource(ScanInputParseSource.USER))
                .ifPresent(parse -> {
                    row.setInputParse(Optional.of(parse));
                    if (parse.confidence().isPresent()) {
                        row.setConfidence(parse.confidence());
                    }
                });
    }

    /** Clears every value derived from a TMDB match for all targeted rows. */
    static void resetTmdbMatches(List<ScanRow> rows) {
        for (ScanRow row : rows) {
            row.setTmdbMatch(Optional.empty());
            row.setTmdbCandidate(Optional.empty());
            row.setTmdbSelectedByUser(false);
            row.setAppliedTmdbOrder(Optional.empty());
            row.setOrder(Optional.empty());
            row.setProposedFilename(Optional.empty());
            row.setDestination(Optional.empty());
            row.setAlertText(Optional.empty());
            row.setNoteText(Optional.empty());
        }
    }

    /** What a role cell reveals on hover: the parse, its positions and its origin. */
    static String patternTooltip(ScanRow row) {
        if (row.inputParse().isEmpty()) {
            return row.inputPattern().orElse(UiText.EMPTY);
        }
        ScanInputParse parse = row.inputParse().orElseThrow();
        return (parse.summary().isBlank() ? parse.label() : parse.summary())
                + "\n" + parse.positionsSummary()
                + "\nsource=" + parse.source();
    }

    private static String patternOf(ScanRow row) {
        return row.pattern().filter(pattern -> !pattern.isBlank()).orElse(DEFAULT_PATTERN);
    }

    private static ScanInputParse parseOrEmpty(ScanRow row) {
        return ScanInputPatternParser.parse(row.originalFilename())
                .orElseGet(() -> new ScanInputParse(
                        "", List.of(), Optional.empty(), OptionalDouble.empty(), ScanInputParseSource.USER));
    }

    private static List<ScanInputToken> replaceToken(
            List<ScanInputToken> current, ScanInputRole role, String value) {
        List<ScanInputToken> tokens = new ArrayList<>(current);
        for (int index = 0; index < tokens.size(); index++) {
            if (tokens.get(index).role() != role) {
                continue;
            }
            if (value.isBlank()) {
                tokens.remove(index);
            } else {
                ScanInputToken previous = tokens.get(index);
                tokens.set(index, new ScanInputToken(role, value, value, previous.start(), previous.end()));
            }
            return tokens;
        }
        if (!value.isBlank()) {
            tokens.add(new ScanInputToken(role, value, value, 0, 0));
        }
        return tokens;
    }

    private static Optional<String> recomputeOrder(List<ScanInputToken> tokens) {
        String season = tokenValue(tokens, ScanInputRole.SEASON);
        String episode = tokenValue(tokens, ScanInputRole.EPISODE);
        if (season.isBlank() || episode.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(String.format("S%02dE%02d",
                    Integer.parseInt(season.trim()), Integer.parseInt(episode.trim())));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private static String tokenValue(List<ScanInputToken> tokens, ScanInputRole role) {
        return tokens.stream()
                .filter(token -> token.role() == role)
                .findFirst()
                .map(ScanInputToken::normalizedValue)
                .orElse("");
    }

    private static String firstLine(String value) {
        int newline = value.indexOf('\n');
        return newline >= 0 ? value.substring(0, newline).trim() : value.trim();
    }
}
