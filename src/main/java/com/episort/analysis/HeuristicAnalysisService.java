package com.episort.analysis;

import com.episort.filename.FilenameParser;
import com.episort.filename.FolderContext;
import com.episort.filename.MediaKindHint;
import com.episort.filename.ParseWarning;
import com.episort.filename.ParsedFilename;
import com.episort.scanner.InventoryGroupType;
import com.episort.scanner.InventoryItem;
import com.episort.scanner.InventoryItemType;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic, offline analysis of one inventory item.
 *
 * <p>All name understanding is delegated to {@link FilenameParser}; this service
 * decides what the application does with it: which media type wins when the
 * grouping and the name disagree, which parser warnings become user-visible
 * warnings, and which fields are safe to fill.
 *
 * <p>Nothing here invents data. A season that was assumed rather than read, a
 * bare number that may or may not be an episode, an air date instead of a
 * season/episode pair: each of those reaches the review screen as a warning, so
 * a guess can never be mistaken for a fact.
 */
public final class HeuristicAnalysisService {
    /**
     * Warnings a human must resolve. Everything not listed here is informative:
     * it is shown, but it does not hold the file back.
     */
    private static final Set<ParseWarning> BLOCKING_WARNINGS = EnumSet.of(
            ParseWarning.CONFLICTING_MARKERS,
            ParseWarning.AMBIGUOUS_ABSOLUTE_NUMBER,
            ParseWarning.ASSUMED_SEASON,
            ParseWarning.MULTI_EPISODE,
            ParseWarning.SPECIAL_EPISODE,
            ParseWarning.DATE_BASED_EPISODE,
            ParseWarning.EXTRA_MATERIAL,
            ParseWarning.MULTI_PART_FILE,
            ParseWarning.MISSING_TITLE,
            ParseWarning.OUT_OF_RANGE_NUMBER,
            ParseWarning.UNUSABLE_NAME);

    public AnalyzedVideoFile analyze(InventoryItem item, InventoryGroupType groupType) {
        return analyze(item, groupType, FolderContext.of(item.sourcePath()));
    }

    public AnalyzedVideoFile analyze(InventoryItem item, InventoryGroupType groupType, String parentFolderName) {
        return analyze(item, groupType, new FolderContext(parentFolderName, grandParentName(item)));
    }

    private AnalyzedVideoFile analyze(InventoryItem item, InventoryGroupType groupType, FolderContext folders) {
        AnalyzedVideoFile file = new AnalyzedVideoFile(item.sourcePath(), item.filename());
        file.set(AnalysisField.EXTENSION, normalizeExtension(item.extension()), FieldSource.HEURISTIC);
        if (item.type() != InventoryItemType.SUPPORTED_VIDEO) {
            file.set(AnalysisField.MEDIA_TYPE, VideoMediaType.IGNORED, FieldSource.HEURISTIC);
            file.set(AnalysisField.TVDB_ORDER, TvdbOrder.NOT_APPLICABLE, FieldSource.UNKNOWN);
            return file;
        }

        ParsedFilename parsed = FilenameParser.parse(item.filename(), folders);
        VideoMediaType mediaType = mediaType(groupType, parsed);
        file.set(AnalysisField.MEDIA_TYPE, mediaType, FieldSource.HEURISTIC);
        file.set(AnalysisField.INPUT_PATTERN, parsed.patternLabel(), FieldSource.HEURISTIC);
        parsed.title().ifPresent(title -> file.set(AnalysisField.DETECTED_TITLE, title, FieldSource.HEURISTIC));
        parsed.quality().ifPresent(value -> file.set(AnalysisField.QUALITY, value, FieldSource.HEURISTIC));
        parsed.source().ifPresent(value -> file.set(AnalysisField.SOURCE, value, FieldSource.HEURISTIC));
        parsed.codec().ifPresent(value -> file.set(AnalysisField.CODEC, value, FieldSource.HEURISTIC));
        parsed.language().ifPresent(value -> file.set(AnalysisField.LANGUAGE, value, FieldSource.HEURISTIC));
        parsed.releaseGroup().ifPresent(value -> file.set(AnalysisField.RELEASE_GROUP, value, FieldSource.HEURISTIC));
        file.set(AnalysisField.CONFIDENCE, parsed.confidence(), FieldSource.HEURISTIC);

        if (mediaType == VideoMediaType.MOVIE) {
            parsed.year().ifPresent(year -> file.set(AnalysisField.YEAR, year, FieldSource.HEURISTIC));
        } else if (mediaType == VideoMediaType.SERIES || mediaType == VideoMediaType.SPECIAL) {
            parsed.season().ifPresent(season -> file.set(AnalysisField.SEASON_NUMBER, season, FieldSource.HEURISTIC));
            parsed.firstEpisode().ifPresent(
                    episode -> file.set(AnalysisField.EPISODE_NUMBER, episode, FieldSource.HEURISTIC));
            parsed.episodeTitle().ifPresent(
                    title -> file.set(AnalysisField.EPISODE_TITLE, title, FieldSource.HEURISTIC));
            parsed.year().ifPresent(year -> file.set(AnalysisField.YEAR, year, FieldSource.HEURISTIC));
        }

        for (ParseWarning warning : parsed.warnings()) {
            describe(warning).ifPresent(message -> {
                if (BLOCKING_WARNINGS.contains(warning)) {
                    file.requireReview(message);
                } else {
                    file.addWarning(message);
                }
            });
        }
        file.set(AnalysisField.TVDB_ORDER,
                mediaType == VideoMediaType.MOVIE ? TvdbOrder.NOT_APPLICABLE : TvdbOrder.TO_DEFINE,
                FieldSource.UNKNOWN);
        return file;
    }

    /**
     * Reconciles the grouping verdict with what the file name says. The grouping
     * sees a whole folder and the name sees one file, so neither is always
     * right: an explicit, high-confidence episode marker beats a "likely movie"
     * group, and an unknown group is resolved by the name rather than left
     * unassigned.
     */
    private static VideoMediaType mediaType(InventoryGroupType groupType, ParsedFilename parsed) {
        if (parsed.extraMaterial()) {
            return VideoMediaType.IGNORED;
        }
        MediaKindHint hint = parsed.kind();
        if (hint == MediaKindHint.SPECIAL) {
            return VideoMediaType.SPECIAL;
        }
        if (groupType == InventoryGroupType.LIKELY_SERIES) {
            return VideoMediaType.SERIES;
        }
        if (groupType == InventoryGroupType.LIKELY_MOVIE) {
            boolean confidentEpisode = parsed.hasEpisode()
                    && parsed.confidence() >= 0.85
                    && !parsed.hasWarning(ParseWarning.AMBIGUOUS_ABSOLUTE_NUMBER);
            return confidentEpisode ? VideoMediaType.SERIES : VideoMediaType.MOVIE;
        }
        return switch (hint) {
            case SERIES -> VideoMediaType.SERIES;
            case MOVIE -> VideoMediaType.MOVIE;
            default -> VideoMediaType.UNKNOWN;
        };
    }

    private static Optional<String> describe(ParseWarning warning) {
        return Optional.ofNullable(switch (warning) {
            case CONFLICTING_MARKERS -> "The name contains two different season/episode markers.";
            case AMBIGUOUS_ABSOLUTE_NUMBER -> "The episode number comes from a bare number in the name.";
            case SEASON_FROM_FOLDER -> "The season was taken from the parent folder.";
            case ASSUMED_SEASON -> "No season was found in the name; season 1 was assumed.";
            case MULTI_EPISODE -> "The file covers several episodes.";
            case SPECIAL_EPISODE -> "The file looks like a special rather than a numbered episode.";
            case DATE_BASED_EPISODE -> "The episode is identified by an air date, not by a number.";
            case AMBIGUOUS_YEAR -> "Several years appear in the name.";
            case IMPLAUSIBLE_YEAR -> "A year-like number was found but is out of range.";
            case EXTRA_MATERIAL -> "The name matches sample/trailer/extra material.";
            case MULTI_PART_FILE -> "The file is one part of a multi-part release.";
            case MISSING_TITLE -> "No title could be read from the name or the folders.";
            case FOLDER_TITLE_MISMATCH ->
                    "The file name states a different series than its folder; the file name was kept.";
            case NUMERIC_TITLE -> "The title is made of digits only.";
            case OUT_OF_RANGE_NUMBER -> "A season or episode number is out of range.";
            case UNUSABLE_NAME -> "The file name carries no usable information.";
            case NO_EPISODE_TITLE -> null;
        });
    }

    private static String grandParentName(InventoryItem item) {
        if (item.parentFolder() == null || item.parentFolder().getParent() == null) {
            return "";
        }
        var name = item.parentFolder().getParent().getFileName();
        return name == null ? "" : name.toString();
    }

    private static String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return "";
        }
        String value = extension.startsWith(".") ? extension : "." + extension;
        return value.toLowerCase(Locale.ROOT);
    }
}
