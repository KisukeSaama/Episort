package com.episort.ai;

import com.episort.scanner.InventoryGroupType;
import java.util.Objects;

public record AiGroupSuggestion(String seedName, InventoryGroupType groupType, AiPatternSuggestion suggestion) {
    public AiGroupSuggestion {
        Objects.requireNonNull(seedName, "seedName");
        Objects.requireNonNull(groupType, "groupType");
        Objects.requireNonNull(suggestion, "suggestion");
        if (!suggestion.advisoryOnly() || suggestion.validationAuthority() || suggestion.executionAuthority()) {
            throw new IllegalArgumentException("Group suggestion must wrap an advisory-only AI suggestion.");
        }
    }
}
