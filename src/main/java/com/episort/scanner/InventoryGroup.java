package com.episort.scanner;

import java.util.List;
import java.util.Objects;

public record InventoryGroup(
        InventoryGroupType type,
        String seedName,
        List<InventoryItem> items,
        boolean tvdbIdentityFinal) {
    public InventoryGroup {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(seedName, "seedName");
        items = List.copyOf(items);
    }
}
