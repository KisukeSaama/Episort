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
        Optional<ScanInputParse> extracted = ScanInputPatternParser.parse(filename);
        if (extracted.isEmpty()) {
            return;
        }
        ScanInputParse parse = extracted.orElseThrow();
        row.setInputParse(Optional.of(parse));
        row.setInputPattern(Optional.of(parse.summary().isBlank() ? parse.label() : parse.summary()));
        parse.normalizedOrder().ifPresent(order -> row.setOrder(Optional.of(order)));
        if (parse.confidence().isPresent()) {
            row.setConfidence(parse.confidence());
        }
    }

    static Optional<String> extractSeasonEpisode(String filename) {
        return ScanInputPatternParser.parse(filename).flatMap(ScanInputParse::normalizedOrder);
    }

    static Optional<ScanInputParse> extractInputPattern(String filename) {
        return ScanInputPatternParser.parse(filename);
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
