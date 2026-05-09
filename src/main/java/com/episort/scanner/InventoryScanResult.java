package com.episort.scanner;

import java.util.List;

public record InventoryScanResult(
        List<InventoryItem> items,
        List<InventoryGroup> groups,
        InventorySummary summary) {
    public InventoryScanResult {
        items = List.copyOf(items);
        groups = List.copyOf(groups);
    }

    public List<InventoryItem> supportedVideos() {
        return itemsOfType(InventoryItemType.SUPPORTED_VIDEO);
    }

    public List<InventoryItem> sidecars() {
        return itemsOfType(InventoryItemType.SIDECAR);
    }

    public List<InventoryItem> unsupported() {
        return itemsOfType(InventoryItemType.UNSUPPORTED);
    }

    public List<InventoryItem> ignored() {
        return itemsOfType(InventoryItemType.IGNORED);
    }

    private List<InventoryItem> itemsOfType(InventoryItemType type) {
        return items.stream()
                .filter(item -> item.type() == type)
                .toList();
    }
}
