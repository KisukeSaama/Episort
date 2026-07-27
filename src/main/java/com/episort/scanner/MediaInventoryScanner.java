package com.episort.scanner;

import com.episort.filename.FilenameParser;
import com.episort.filename.FolderContext;
import com.episort.filename.ParsedFilename;
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

public final class MediaInventoryScanner {
    private static final Set<String> SUPPORTED_VIDEO_EXTENSIONS = Set.of(".avi", ".mp4", ".mkv");
    private static final Set<String> SIDECAR_EXTENSIONS = Set.of(
            ".srt", ".ass", ".ssa", ".sub", ".vtt", ".nfo", ".jpg", ".jpeg", ".png", ".webp", ".gif");

    public InventoryScanResult scan(Path inputFolder, InventoryProgressListener progressListener) throws IOException {
        return scan(ScanSource.folder(inputFolder), progressListener);
    }

    public InventoryScanResult scanFiles(List<Path> selectedFiles, InventoryProgressListener progressListener) throws IOException {
        return scan(ScanSource.files(selectedFiles), progressListener);
    }

    public InventoryScanResult scan(ScanSource source, InventoryProgressListener progressListener) throws IOException {
        List<Path> files;
        if (source.type() == ScanSourceType.FOLDER) {
            try (var stream = Files.walk(source.paths().getFirst())) {
                files = stream.filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(MediaInventoryScanner::scanCategoryOrder)
                                .thenComparing(Comparator.naturalOrder()))
                        .toList();
            }
        } else {
            files = source.paths().stream()
                    .filter(Files::isRegularFile)
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
        Map<String, GroupSeed> seeds = new LinkedHashMap<>();
        for (InventoryItem item : items) {
            GroupSeed seed = seedFor(item);
            grouped.computeIfAbsent(seed.key(), ignored -> new ArrayList<>()).add(item);
            seeds.putIfAbsent(seed.key(), seed);
        }
        return grouped.entrySet().stream()
                .map(entry -> {
                    GroupSeed seed = seeds.get(entry.getKey());
                    return new InventoryGroup(seed.type(), seed.name(), entry.getValue(), false);
                })
                .toList();
    }

    private static GroupSeed seedFor(InventoryItem item) {
        return switch (item.type()) {
            case SIDECAR -> new GroupSeed(InventoryGroupType.SIDECAR, "sidecar");
            case UNSUPPORTED -> new GroupSeed(InventoryGroupType.UNSUPPORTED, "unsupported");
            case IGNORED -> new GroupSeed(InventoryGroupType.IGNORED, "ignored");
            case SUPPORTED_VIDEO -> seedForSupported(item);
        };
    }

    /**
     * Grouping uses the same parser as the analysis stage on purpose. When the
     * two disagreed, a file could be grouped as a movie and then analysed as an
     * episode (or the reverse), and the review screen showed a row whose media
     * type contradicted its own metadata.
     */
    private static GroupSeed seedForSupported(InventoryItem item) {
        ParsedFilename parsed = FilenameParser.parse(item.filename(), FolderContext.of(item.sourcePath()));
        String title = parsed.title().map(MediaInventoryScanner::normalizeSeed).orElse("");
        return switch (parsed.kind()) {
            case SERIES, SPECIAL -> new GroupSeed(InventoryGroupType.LIKELY_SERIES,
                    title.isBlank() ? removeExtension(item.filename()) : title);
            case MOVIE -> new GroupSeed(InventoryGroupType.LIKELY_MOVIE,
                    title.isBlank() ? removeExtension(item.filename()) : title);
            case EXTRA -> new GroupSeed(InventoryGroupType.IGNORED, "ignored");
            case UNKNOWN -> new GroupSeed(InventoryGroupType.UNKNOWN, removeExtension(item.filename()));
        };
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
        /** Case-insensitive: "Show" and "show" are one series, not two. */
        String key() {
            return type.name() + ' ' + name.toLowerCase(Locale.ROOT);
        }
    }
}
