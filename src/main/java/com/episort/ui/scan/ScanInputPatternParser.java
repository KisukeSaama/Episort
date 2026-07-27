package com.episort.ui.scan;

import com.episort.filename.FilenameParser;
import com.episort.filename.FilenameSpan;
import com.episort.filename.FolderContext;
import com.episort.filename.ParsedFilename;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Adapter turning the analysis-side {@link ParsedFilename} into the token model
 * the scan table, the row detail panel and the pattern formatter consume.
 *
 * <p>All parsing rules live in {@link FilenameParser}; this class only maps
 * roles and keeps the token offsets aligned with the original file name so the
 * UI can highlight the exact characters a value came from.
 */
public final class ScanInputPatternParser {
    private ScanInputPatternParser() {
    }

    public static Optional<ScanInputParse> parse(String filename) {
        return parse(filename, FolderContext.none());
    }

    /** Parses {@code file} using its surrounding folders as extra evidence. */
    public static Optional<ScanInputParse> parse(Path file) {
        if (file == null || file.getFileName() == null) {
            return Optional.empty();
        }
        return parse(file.getFileName().toString(), FolderContext.of(file));
    }

    public static Optional<ScanInputParse> parse(String filename, FolderContext folders) {
        ParsedFilename parsed = FilenameParser.parse(filename, folders);
        if (parsed.title().isEmpty() && !parsed.hasEpisode() && parsed.airDate().isEmpty()) {
            return Optional.empty();
        }
        List<ScanInputToken> tokens = new ArrayList<>();
        boolean episodic = parsed.hasEpisode() || parsed.airDate().isPresent();
        for (FilenameSpan span : parsed.spans()) {
            toToken(span, parsed, episodic).ifPresent(tokens::add);
        }
        tokens.sort((left, right) -> Integer.compare(left.start(), right.start()));
        return Optional.of(new ScanInputParse(
                parsed.patternLabel(),
                tokens,
                parsed.normalizedOrder(),
                OptionalDouble.of(parsed.confidence()),
                ScanInputParseSource.HEURISTIC));
    }

    private static Optional<ScanInputToken> toToken(
            FilenameSpan span, ParsedFilename parsed, boolean episodic) {
        return switch (span.role()) {
            case SERIES -> token(episodic ? ScanInputRole.SERIES : ScanInputRole.TITLE,
                    span, parsed.title().orElse(span.normalized()));
            case SEASON -> parsed.season().isPresent()
                    ? token(ScanInputRole.SEASON, span, twoDigits(parsed.season().getAsInt()))
                    : Optional.empty();
            case EPISODE -> parsed.firstEpisode().isPresent()
                    ? token(ScanInputRole.EPISODE, span, twoDigits(parsed.firstEpisode().getAsInt()))
                    : Optional.empty();
            case ABSOLUTE -> parsed.firstEpisode().isPresent()
                    ? token(ScanInputRole.EPISODE, span, twoDigits(parsed.firstEpisode().getAsInt()))
                    : Optional.empty();
            case TITLE -> token(ScanInputRole.TITLE, span, span.normalized());
            case YEAR -> token(ScanInputRole.YEAR, span, span.normalized());
            case EXTENSION -> token(ScanInputRole.EXTENSION, span, span.normalized());
            case EPISODE_END, AIR_DATE, TAG -> token(ScanInputRole.NOISE, span, span.normalized());
        };
    }

    private static Optional<ScanInputToken> token(ScanInputRole role, FilenameSpan span, String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ScanInputToken(role, span.raw(), value, span.start(), span.end()));
    }

    private static String twoDigits(int value) {
        return String.format(Locale.ROOT, "%02d", value);
    }
}
