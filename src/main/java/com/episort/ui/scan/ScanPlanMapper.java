package com.episort.ui.scan;

import com.episort.filename.SeasonEpisodePattern;
import com.episort.filename.FilenameParser;
import com.episort.planning.PlanExclusionReason;
import com.episort.planning.PlanMediaKind;
import com.episort.planning.PlanSourceItem;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;

/**
 * Maps reviewed preview rows to planner input. Pure mapping logic — no JavaFX
 * scene graph, fully unit-testable.
 *
 * <p>Nothing is invented here: a row whose identity is incomplete becomes an
 * unassigned item rather than a guessed destination.
 */
public final class ScanPlanMapper {

    private ScanPlanMapper() {
    }

    public static List<PlanSourceItem> toPlanItems(List<ScanRow> rows) {
        Objects.requireNonNull(rows, "rows");
        return rows.stream().map(ScanPlanMapper::toPlanItem).toList();
    }

    public static PlanSourceItem toPlanItem(ScanRow row) {
        Objects.requireNonNull(row, "row");
        PlanMediaKind kind = kindOf(row);
        PlanSourceItem.Builder builder = PlanSourceItem
                .forSource(row.sourcePath(), extensionOf(row), kind)
                .excluded(exclusionReasonOf(row));
        if (kind == PlanMediaKind.MOVIE) {
            return builder
                    .movieTitle(movieTitle(row))
                    .movieYear(movieYear(row))
                    .build();
        }
        return builder
                .seriesTitle(token(row, ScanInputRole.SERIES))
                .seasonNumber(seasonNumber(row))
                .episodeNumber(episodeNumber(row))
                .episodeTitle(token(row, ScanInputRole.TITLE))
                .build();
    }

    static PlanMediaKind kindOf(ScanRow row) {
        if (row.mediaType() == ScanMediaType.MOVIE) {
            return PlanMediaKind.MOVIE;
        }
        return seasonNumber(row).orElse(-1) == 0 ? PlanMediaKind.SPECIAL : PlanMediaKind.SERIES_EPISODE;
    }

    /**
     * The extension comes from the real filename, never from the uppercased
     * display value in the table: Story 6.1 requires it preserved exactly.
     */
    static String extensionOf(ScanRow row) {
        String name = row.sourcePath().getFileName() == null
                ? row.originalFilename()
                : row.sourcePath().getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            return name.substring(dot);
        }
        return row.extension().isBlank() ? "" : "." + row.extension().toLowerCase(Locale.ROOT);
    }

    static PlanExclusionReason exclusionReasonOf(ScanRow row) {
        if (row.isIgnored()) {
            return PlanExclusionReason.IGNORED;
        }
        return switch (row.status()) {
            case EXT -> PlanExclusionReason.UNSUPPORTED;
            case DUPLICATE -> PlanExclusionReason.DUPLICATE;
            case CONFLICT, PATH, ERROR -> PlanExclusionReason.AMBIGUOUS;
            case TYPE -> PlanExclusionReason.UNASSIGNED;
            default -> row.mediaType() == ScanMediaType.UNKNOWN
                    ? PlanExclusionReason.UNASSIGNED
                    : PlanExclusionReason.NONE;
        };
    }

    static OptionalInt seasonNumber(ScanRow row) {
        OptionalInt fromToken = intToken(row, ScanInputRole.SEASON);
        if (fromToken.isPresent()) {
            return fromToken;
        }
        return orderGroup(row, 1);
    }

    static OptionalInt episodeNumber(ScanRow row) {
        OptionalInt fromToken = intToken(row, ScanInputRole.EPISODE);
        if (fromToken.isPresent()) {
            return fromToken;
        }
        return orderGroup(row, 2);
    }

    static Optional<String> movieTitle(ScanRow row) {
        Optional<String> tagged = token(row, ScanInputRole.TITLE);
        if (tagged.isPresent()) {
            return tagged;
        }
        String derived = derivedMovieTitle(row.originalFilename());
        return derived.isBlank() ? Optional.empty() : Optional.of(derived);
    }

    static OptionalInt movieYear(ScanRow row) {
        OptionalInt tagged = intToken(row, ScanInputRole.YEAR);
        if (tagged.isPresent()) {
            return tagged;
        }
        String derived = derivedMovieYear(row.originalFilename());
        return derived.isBlank() ? OptionalInt.empty() : OptionalInt.of(Integer.parseInt(derived));
    }

    /**
     * Movie title from a release-style filename. Delegates to the shared parser
     * so an untagged row is read with exactly the same rules as a tagged one —
     * resolutions and codecs excluded, year separated from the title.
     */
    static String derivedMovieTitle(String filename) {
        return FilenameParser.parse(filename).title().orElse("");
    }

    /** Release year from a release-style filename, noise excluded. */
    static String derivedMovieYear(String filename) {
        var year = FilenameParser.parse(filename).year();
        return year.isPresent() ? String.valueOf(year.getAsInt()) : "";
    }

    private static Optional<String> token(ScanRow row, ScanInputRole role) {
        return row.inputParse()
                .flatMap(parse -> parse.tokenValue(role))
                .map(String::trim)
                .filter(value -> !value.isEmpty());
    }

    private static OptionalInt intToken(ScanRow row, ScanInputRole role) {
        Optional<String> value = token(row, role);
        if (value.isEmpty()) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(value.orElseThrow()));
        } catch (NumberFormatException exception) {
            return OptionalInt.empty();
        }
    }

    private static OptionalInt orderGroup(ScanRow row, int group) {
        Optional<String> order = row.order().filter(value -> !value.isBlank());
        if (order.isEmpty()) {
            return OptionalInt.empty();
        }
        Matcher matcher = SeasonEpisodePattern.LENIENT.matcher(order.orElseThrow());
        return matcher.find() ? OptionalInt.of(Integer.parseInt(matcher.group(group))) : OptionalInt.empty();
    }
}
