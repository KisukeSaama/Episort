package com.episort.ui.scan;

import com.episort.scanner.InventoryGroup;
import com.episort.scanner.InventoryGroupType;
import com.episort.scanner.InventoryItem;
import com.episort.scanner.InventoryItemType;
import com.episort.scanner.InventoryScanResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps {@link InventoryItem}s into {@link ScanRow}s for the preview table.
 * Pure mapping logic — no JavaFX, fully unit-testable.
 */
public final class ScanRowFactory {
    private static final Pattern SXXEXX = Pattern.compile("[Ss](\\d{1,2})[\\s._-]?[Ee](\\d{1,3})");
    private static final Pattern NXNN = Pattern.compile("(?<![A-Za-z0-9])(\\d{1,2})[xX](\\d{1,3})(?![A-Za-z0-9])");

    private ScanRowFactory() {
    }

    public static List<ScanRow> from(InventoryScanResult result) {
        Objects.requireNonNull(result, "result");
        Map<String, InventoryGroupType> itemGroupTypes = indexItemsByGroup(result.groups());
        List<ScanRow> rows = new ArrayList<>(result.items().size());
        for (InventoryItem item : result.items()) {
            rows.add(toRow(item, itemGroupTypes));
        }
        return rows;
    }

    private static Map<String, InventoryGroupType> indexItemsByGroup(List<InventoryGroup> groups) {
        Map<String, InventoryGroupType> index = new HashMap<>();
        for (InventoryGroup group : groups) {
            for (InventoryItem item : group.items()) {
                index.put(item.sourcePath().toAbsolutePath().normalize().toString(), group.type());
            }
        }
        return index;
    }

    private static ScanRow toRow(InventoryItem item, Map<String, InventoryGroupType> itemGroupTypes) {
        InventoryGroupType groupType = itemGroupTypes.get(item.sourcePath().toAbsolutePath().normalize().toString());
        ScanMediaType mediaType = mediaType(item.type(), groupType);
        ScanRowStatus status = status(item.type());
        ScanRow row = new ScanRow(
                item.sourcePath(),
                item.filename(),
                normalizeExtension(item.extension()),
                mediaType,
                status);
        autoFillOrder(row, item.filename(), groupType);
        return row;
    }

    /**
     * Deterministic season/episode extraction. Runs on every folder load so the
     * Order column is pre-filled for unambiguous SxxExx-style names. The local
     * AI pattern-refinement pass still runs in parallel and remains advisory:
     * here we only act on what we can confirm with a regex, never on AI output.
     */
    private static void autoFillOrder(ScanRow row, String filename, InventoryGroupType groupType) {
        if (groupType != InventoryGroupType.LIKELY_SERIES && groupType != InventoryGroupType.UNKNOWN) {
            return;
        }
        Optional<String> extracted = extractSeasonEpisode(filename);
        if (extracted.isEmpty()) {
            return;
        }
        row.setOrder(extracted);
        row.setConfidence(OptionalDouble.of(0.9));
    }

    static Optional<String> extractSeasonEpisode(String filename) {
        if (filename == null || filename.isBlank()) {
            return Optional.empty();
        }
        Matcher m = SXXEXX.matcher(filename);
        if (m.find()) {
            return Optional.of(format(m.group(1), m.group(2)));
        }
        m = NXNN.matcher(filename);
        if (m.find()) {
            return Optional.of(format(m.group(1), m.group(2)));
        }
        return Optional.empty();
    }

    private static String format(String season, String episode) {
        int s = Integer.parseInt(season);
        int e = Integer.parseInt(episode);
        return String.format(Locale.ROOT, "S%02dE%02d", s, e);
    }

    private static ScanMediaType mediaType(InventoryItemType itemType, InventoryGroupType groupType) {
        return switch (itemType) {
            case SUPPORTED_VIDEO -> mediaTypeFromGroup(groupType);
            case SIDECAR, UNSUPPORTED, IGNORED -> ScanMediaType.IGNORED;
        };
    }

    private static ScanMediaType mediaTypeFromGroup(InventoryGroupType groupType) {
        if (groupType == null) {
            return ScanMediaType.UNKNOWN;
        }
        return switch (groupType) {
            case LIKELY_SERIES -> ScanMediaType.SERIES;
            case LIKELY_MOVIE -> ScanMediaType.MOVIE;
            case UNKNOWN -> ScanMediaType.UNKNOWN;
            case SIDECAR, UNSUPPORTED, IGNORED -> ScanMediaType.IGNORED;
        };
    }

    private static ScanRowStatus status(InventoryItemType itemType) {
        return switch (itemType) {
            case SUPPORTED_VIDEO -> ScanRowStatus.PREVIEW;
            case SIDECAR, UNSUPPORTED, IGNORED -> ScanRowStatus.IGNORED;
        };
    }

    private static String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return "";
        }
        String trimmed = extension.startsWith(".") ? extension.substring(1) : extension;
        return trimmed.toUpperCase(Locale.ROOT);
    }
}
