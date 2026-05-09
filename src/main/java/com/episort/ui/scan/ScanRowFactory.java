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

/**
 * Maps {@link InventoryItem}s into {@link ScanRow}s for the preview table.
 * Pure mapping logic — no JavaFX, fully unit-testable.
 */
public final class ScanRowFactory {
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
        return new ScanRow(
                item.sourcePath(),
                item.filename(),
                normalizeExtension(item.extension()),
                mediaType,
                status);
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
