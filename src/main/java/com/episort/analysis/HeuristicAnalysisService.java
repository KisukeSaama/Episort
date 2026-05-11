package com.episort.analysis;

import com.episort.scanner.InventoryGroupType;
import com.episort.scanner.InventoryItem;
import com.episort.scanner.InventoryItemType;
import com.episort.ui.scan.ScanInputParse;
import com.episort.ui.scan.ScanInputPatternParser;
import com.episort.ui.scan.ScanInputRole;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HeuristicAnalysisService {
    private static final Pattern MOVIE_YEAR = Pattern.compile("(?i)^(.*?)[ ._\\-\\[(](19\\d{2}|20\\d{2})[)\\] ._\\-]?.*");
    private static final Pattern FOLDER_TITLE_SEASON = Pattern.compile(
            "(?i)(.*?)[ ._\\-]+(?:S(\\d{1,2})|Season[ ._\\-]?(\\d{1,2}))(?:[ ._\\-].*)?");

    public AnalyzedVideoFile analyze(InventoryItem item, InventoryGroupType groupType) {
        return analyze(item, groupType, parentFolderName(item));
    }

    public AnalyzedVideoFile analyze(InventoryItem item, InventoryGroupType groupType, String parentFolderName) {
        AnalyzedVideoFile file = new AnalyzedVideoFile(item.sourcePath(), item.filename());
        file.set(AnalysisField.EXTENSION, normalizeExtension(item.extension()), FieldSource.HEURISTIC);
        file.set(AnalysisField.MEDIA_TYPE, mediaType(item.type(), groupType), FieldSource.HEURISTIC);
        Optional<ScanInputParse> parse = file.mediaType() == VideoMediaType.MOVIE
                ? Optional.empty()
                : ScanInputPatternParser.parse(item.filename());
        parse.ifPresent(value -> {
            file.set(AnalysisField.INPUT_PATTERN,
                    value.summary().isBlank() ? value.label() : value.summary(),
                    FieldSource.HEURISTIC);
            value.tokenValue(ScanInputRole.SERIES)
                    .ifPresent(title -> file.set(AnalysisField.DETECTED_TITLE, title, FieldSource.HEURISTIC));
            value.tokenValue(ScanInputRole.SEASON)
                    .ifPresent(season -> file.set(AnalysisField.SEASON_NUMBER, Integer.parseInt(season), FieldSource.HEURISTIC));
            value.tokenValue(ScanInputRole.EPISODE)
                    .ifPresent(episode -> file.set(AnalysisField.EPISODE_NUMBER, Integer.parseInt(episode), FieldSource.HEURISTIC));
            value.tokenValue(ScanInputRole.TITLE)
                    .ifPresent(title -> file.set(AnalysisField.EPISODE_TITLE, title, FieldSource.HEURISTIC));
            if (value.confidence().isPresent()) {
                file.set(AnalysisField.CONFIDENCE, value.confidence().orElseThrow(), FieldSource.HEURISTIC);
            }
        });
        applyFolderFallback(file, parentFolderName);
        if (parse.isEmpty() && file.mediaType() == VideoMediaType.MOVIE) {
            Matcher matcher = MOVIE_YEAR.matcher(removeExtension(item.filename()));
            if (matcher.matches()) {
                file.set(AnalysisField.INPUT_PATTERN, "Title.Year", FieldSource.HEURISTIC);
                file.set(AnalysisField.DETECTED_TITLE, clean(matcher.group(1)), FieldSource.HEURISTIC);
                file.set(AnalysisField.YEAR, Integer.parseInt(matcher.group(2)), FieldSource.HEURISTIC);
                file.set(AnalysisField.CONFIDENCE, 0.85, FieldSource.HEURISTIC);
            }
        }
        file.set(AnalysisField.TVDB_ORDER,
                file.mediaType() == VideoMediaType.MOVIE ? TvdbOrder.NOT_APPLICABLE : TvdbOrder.TO_DEFINE,
                FieldSource.UNKNOWN);
        return file;
    }

    private static void applyFolderFallback(AnalyzedVideoFile file, String parentFolderName) {
        if (file.mediaType() != VideoMediaType.SERIES && file.mediaType() != VideoMediaType.SPECIAL) {
            return;
        }
        if (parentFolderName == null || parentFolderName.isBlank()) {
            return;
        }
        Matcher matcher = FOLDER_TITLE_SEASON.matcher(parentFolderName);
        if (!matcher.matches()) {
            return;
        }
        String folderTitle = clean(matcher.group(1));
        if (folderTitle != null && !folderTitle.isBlank()) {
            file.set(AnalysisField.DETECTED_TITLE, folderTitle, FieldSource.HEURISTIC);
        }
        if (file.seasonNumber().isEmpty()) {
            String seasonRaw = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            if (seasonRaw != null) {
                try {
                    file.set(AnalysisField.SEASON_NUMBER, Integer.parseInt(seasonRaw), FieldSource.HEURISTIC);
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    private static String parentFolderName(InventoryItem item) {
        if (item.parentFolder() == null) {
            return "";
        }
        var name = item.parentFolder().getFileName();
        return name == null ? "" : name.toString();
    }

    private static VideoMediaType mediaType(InventoryItemType itemType, InventoryGroupType groupType) {
        if (itemType != InventoryItemType.SUPPORTED_VIDEO) {
            return VideoMediaType.IGNORED;
        }
        if (groupType == InventoryGroupType.LIKELY_SERIES) {
            return VideoMediaType.SERIES;
        }
        if (groupType == InventoryGroupType.LIKELY_MOVIE) {
            return VideoMediaType.MOVIE;
        }
        return VideoMediaType.UNKNOWN;
    }

    private static String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return "";
        }
        String value = extension.startsWith(".") ? extension : "." + extension;
        return value.toLowerCase(Locale.ROOT);
    }

    private static String removeExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private static String clean(String value) {
        return value.replaceAll("[._-]+", " ").replaceAll("\\s+", " ").trim();
    }
}
