package com.episort.ai;

import com.episort.scanner.InventoryGroup;
import com.episort.scanner.InventoryGroupType;
import com.episort.scanner.InventoryItem;
import com.episort.scanner.InventoryScanResult;
import com.episort.workflow.AiWorkflowGate;
import com.episort.workflow.AiWorkflowGateResult;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Refines Epic 2 inventory groupings with advisory AI pattern suggestions. The
 * refinement is gated by {@link AiWorkflowGate}: if local AI is unavailable the
 * service returns a skipped result and never invokes the assistant. Output is
 * always advisory and carries no validation or execution authority.
 */
public final class AiPatternRefinementService {
    private static final Set<InventoryGroupType> CANDIDATE_TYPES = EnumSet.of(
            InventoryGroupType.LIKELY_SERIES,
            InventoryGroupType.LIKELY_MOVIE,
            InventoryGroupType.UNKNOWN);

    private final AiWorkflowGate gate;
    private final AiPatternAssistant assistant;

    public AiPatternRefinementService(AiWorkflowGate gate, AiPatternAssistant assistant) {
        this.gate = Objects.requireNonNull(gate, "gate");
        this.assistant = Objects.requireNonNull(assistant, "assistant");
    }

    public AiPatternRefinementResult refine(InventoryScanResult inventory) {
        Objects.requireNonNull(inventory, "inventory");
        AiWorkflowGateResult gateResult = gate.requireAiAvailable();
        if (!gateResult.allowed()) {
            return AiPatternRefinementResult.skipped(gateResult.error().orElse(null));
        }

        List<AiGroupSuggestion> suggestions = new ArrayList<>();
        for (InventoryGroup group : inventory.groups()) {
            if (!CANDIDATE_TYPES.contains(group.type()) || group.items().isEmpty()) {
                continue;
            }
            List<String> names = group.items().stream().map(InventoryItem::filename).toList();
            AiPatternSuggestion suggestion = assistant.suggestPattern(
                    new AiPatternSuggestionRequest(names, ""));
            suggestions.add(new AiGroupSuggestion(group.seedName(), group.type(), suggestion));
        }
        return AiPatternRefinementResult.advisory(suggestions);
    }
}
