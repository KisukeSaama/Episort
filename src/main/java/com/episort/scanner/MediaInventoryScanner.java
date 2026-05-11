package com.episort.scanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class MediaInventoryScanner {
    private static final Set<String> SUPPORTED_VIDEO_EXTENSIONS = Set.of(".avi", ".mp4", ".mkv");
    private static final Set<String> SIDECAR_EXTENSIONS = Set.of(
            ".srt", ".ass", ".ssa", ".sub", ".vtt", ".nfo", ".jpg", ".jpeg", ".png", ".webp", ".gif");
    private static final Pattern SERIES_PATTERN = Pattern.compile(
            "(?i)(.*?)(?:[ ._\\-]+S\\d{1,2}E\\d{1,4}|[ ._\\-]+\\d{1,2}x\\d{1,4}).*");
    private static final Pattern MOVIE_PATTERN = Pattern.compile("(?i)(.*?)[ ._\\-\\[(](19\\d{2}|20\\d{2})[)\\] ._\\-]?.*");
    private static final Pattern FOLDER_SEASON_PATTERN = Pattern.compile(
            "(?i)(.*?)[ ._\\-]+(?:S\\d{1,2}|Season[ ._\\-]?\\d{1,2})(?:[ ._\\-].*)?");

    public InventoryScanResult scan(Path inputFolder, InventoryProgressListener progressListener) throws IOException {
        List<Path> files;
        try (var stream = Files.walk(inputFolder)) {
            files = stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(MediaInventoryScanner::scanCategoryOrder)
                            .thenComparing(Comparator.naturalOrder()))
                    .toList();
        }

        List<InventoryItem> items = new ArrayList<>();
        for (int index = 0; index < files.size(); index++) {
            Path file = files.get(index);
            items.add(classify(file));
            progressListener.onProgress(new InventoryScanProgress(index + 1, files.size(), false));
        }

        List<InventoryGroup> groups = group(items);
        InventorySummary summary = summarize(items, groups);
        progressListener.onProgress(new InventoryScanProgress(files.size(), files.size(), true));
        return new InventoryScanResult(items, groups, summary);
    }

    private static InventoryItem classify(Path file) {
        String filename = file.getFileName().toString();
        String extension = extension(filename);
        if (filename.startsWith(".")) {
            return item(file, filename, extension, InventoryItemType.IGNORED, false);
        }
        if (SUPPORTED_VIDEO_EXTENSIONS.contains(extension)) {
            return item(file, filename, extension, InventoryItemType.SUPPORTED_VIDEO, true);
        }
        if (SIDECAR_EXTENSIONS.contains(extension)) {
            return item(file, filename, extension, InventoryItemType.SIDECAR, false);
        }
        return item(file, filename, extension, InventoryItemType.UNSUPPORTED, false);
    }

    private static InventoryItem item(
            Path file, String filename, String extension, InventoryItemType type, boolean operationCandidate) {
        return new InventoryItem(file, filename, extension, file.getParent(), type, operationCandidate);
    }

    private static List<InventoryGroup> group(List<InventoryItem> items) {
        Map<String, List<InventoryItem>> grouped = new LinkedHashMap<>();
        for (InventoryItem item : items) {
            GroupSeed seed = seedFor(item);
            grouped.computeIfAbsent(seed.key(), ignored -> new ArrayList<>()).add(item);
        }
        return grouped.entrySet().stream()
                .map(entry -> {
                    GroupSeed seed = GroupSeed.fromKey(entry.getKey());
                    return new InventoryGroup(seed.type(), seed.name(), entry.getValue(), false);
                })
                .toList();
    }

    private static GroupSeed seedFor(InventoryItem item) {
        return switch (item.type()) {
            case SIDECAR -> new GroupSeed(InventoryGroupType.SIDECAR, "sidecar");
            case UNSUPPORTED -> new GroupSeed(InventoryGroupType.UNSUPPORTED, "unsupported");
            case IGNORED -> new GroupSeed(InventoryGroupType.IGNORED, "ignored");
            case SUPPORTED_VIDEO -> seedForSupported(item.filename(), parentFolderName(item));
        };
    }

    private static String parentFolderName(InventoryItem item) {
        if (item.parentFolder() == null) {
            return "";
        }
        Path name = item.parentFolder().getFileName();
        return name == null ? "" : name.toString();
    }

    private static GroupSeed seedForSupported(String filename, String parentFolderName) {
        String nameWithoutExtension = removeExtension(filename);
        var seriesMatcher = SERIES_PATTERN.matcher(nameWithoutExtension);
        if (seriesMatcher.matches()) {
            return new GroupSeed(InventoryGroupType.LIKELY_SERIES, normalizeSeed(seriesMatcher.group(1)));
        }
        if (parentFolderName != null && !parentFolderName.isBlank()) {
            var folderMatcher = FOLDER_SEASON_PATTERN.matcher(parentFolderName);
            if (folderMatcher.matches()) {
                return new GroupSeed(InventoryGroupType.LIKELY_SERIES, normalizeSeed(parentFolderName));
            }
        }
        var movieMatcher = MOVIE_PATTERN.matcher(nameWithoutExtension);
        if (movieMatcher.matches()) {
            return new GroupSeed(InventoryGroupType.LIKELY_MOVIE, normalizeSeed(movieMatcher.group(1)));
        }
        return new GroupSeed(InventoryGroupType.UNKNOWN, nameWithoutExtension);
    }

    private static InventorySummary summarize(List<InventoryItem> items, List<InventoryGroup> groups) {
        return new InventorySummary(
                countItems(items, InventoryItemType.SUPPORTED_VIDEO),
                countItems(items, InventoryItemType.SIDECAR),
                countItems(items, InventoryItemType.UNSUPPORTED),
                countItems(items, InventoryItemType.IGNORED),
                countGroups(groups, InventoryGroupType.LIKELY_SERIES),
                countGroups(groups, InventoryGroupType.LIKELY_MOVIE),
                countItemsInGroups(groups, InventoryGroupType.UNKNOWN),
                false,
                false);
    }

    private static int countItems(List<InventoryItem> items, InventoryItemType type) {
        return (int) items.stream().filter(item -> item.type() == type).count();
    }

    private static int countGroups(List<InventoryGroup> groups, InventoryGroupType type) {
        return (int) groups.stream().filter(group -> group.type() == type).count();
    }

    private static int countItemsInGroups(List<InventoryGroup> groups, InventoryGroupType type) {
        return groups.stream()
                .filter(group -> group.type() == type)
                .mapToInt(group -> group.items().size())
                .sum();
    }

    private static String extension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex < 0 ? "" : filename.substring(dotIndex).toLowerCase(Locale.ROOT);
    }

    private static int scanCategoryOrder(Path file) {
        String filename = file.getFileName().toString();
        String extension = extension(filename);
        if (filename.startsWith(".")) {
            return 3;
        }
        if (SUPPORTED_VIDEO_EXTENSIONS.contains(extension)) {
            return 0;
        }
        if (SIDECAR_EXTENSIONS.contains(extension)) {
            return 1;
        }
        return 2;
    }

    private static String removeExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex < 0 ? filename : filename.substring(0, dotIndex);
    }

    private static String normalizeSeed(String value) {
        String normalized = value.replaceAll("[._-]+", " ").trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? "unknown" : normalized;
    }

    private record GroupSeed(InventoryGroupType type, String name) {
        String key() {
            return type.name() + '\u0000' + name;
        }

        static GroupSeed fromKey(String key) {
            int separator = key.indexOf('\u0000');
            return new GroupSeed(
                    InventoryGroupType.valueOf(key.substring(0, separator)),
                    key.substring(separator + 1));
        }
    }
}
