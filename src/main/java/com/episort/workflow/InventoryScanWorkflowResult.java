package com.episort.workflow;

import com.episort.scanner.InventoryGroup;
import com.episort.scanner.InventoryItem;
import com.episort.scanner.InventorySummary;
import java.util.List;

public record InventoryScanWorkflowResult(
        List<InventoryItem> items,
        List<InventoryGroup> groups,
        InventorySummary summary) {
    public InventoryScanWorkflowResult {
        items = List.copyOf(items);
        groups = List.copyOf(groups);
    }
}
