package com.episort.ai;

import com.episort.scanner.InventoryGroup;
import com.episort.scanner.InventoryGroupType;
import com.episort.scanner.InventoryItem;
import com.episort.scanner.InventoryScanResult;
import com.episort.workflow.AiWorkflowGate;
import com.episort.workflow.AiWorkflowGateResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
        return refine(inventory, Optional.empty(), (done, total) -> {});
    }

    /**
     * Progress callback receives (done, total) where total counts only the
     * candidate groups that will be analyzed. Called once before any work
     * with done=0, then after each group completes.
     */
    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(int done, int total);
    }

    public AiPatternRefinementResult refine(InventoryScanResult inventory, ProgressListener progress) {
        return refine(inventory, Optional.empty(), progress);
    }

    public AiPatternRefinementResult refine(
            InventoryScanResult inventory, Optional<Path> workspaceRoot, ProgressListener progress) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        Objects.requireNonNull(progress, "progress");
        AiWorkflowGateResult gateResult = gate.requireAiAvailable();
        if (!gateResult.allowed()) {
            return AiPatternRefinementResult.skipped(gateResult.error().orElse(null));
        }

        List<InventoryGroup> candidates = new ArrayList<>();
        int total = 0;
        for (InventoryGroup group : inventory.groups()) {
            if (CANDIDATE_TYPES.contains(group.type()) && !group.items().isEmpty()) {
                candidates.add(group);
                total += BundledLocalAiPatternAssistant.promptCountFor(group.items().size());
            }
        }
        progress.onProgress(0, total);

        List<AiGroupSuggestion> suggestions = new ArrayList<>();
        int[] done = {0};
        int finalTotal = total;
        Runnable tick = () -> {
            done[0]++;
            progress.onProgress(done[0], finalTotal);
        };
        for (InventoryGroup group : candidates) {
            List<String> names = group.items().stream().map(InventoryItem::filename).toList();
            List<String> parentChain = parentFolderChainFor(group, workspaceRoot);
            AiPatternSuggestion suggestion = assistant.suggestPattern(
                    new AiPatternSuggestionRequest(names, "", parentChain), tick);
            suggestions.add(new AiGroupSuggestion(group.seedName(), group.type(), suggestion));
        }
        return AiPatternRefinementResult.advisory(suggestions);
    }

    /**
     * Computes the chain of parent folder names for a group's files, from the
     * outermost folder under the workspace down to the immediate parent. The
     * workspace root itself is excluded. When no workspace root is configured,
     * we fall back to the immediate parent folder name (single element).
     */
    static List<String> parentFolderChainFor(InventoryGroup group, Optional<Path> workspaceRoot) {
        if (group.items().isEmpty()) {
            return List.of();
        }
        Path parent = group.items().get(0).parentFolder();
        if (parent == null) {
            return List.of();
        }
        Path normalizedParent = parent.toAbsolutePath().normalize();
        if (workspaceRoot.isPresent()) {
            Path workspace = workspaceRoot.get().toAbsolutePath().normalize();
            if (normalizedParent.startsWith(workspace) && !normalizedParent.equals(workspace)) {
                Path relative = workspace.relativize(normalizedParent);
                List<String> chain = new ArrayList<>(relative.getNameCount());
                for (Path segment : relative) {
                    String name = segment.toString();
                    if (!name.isEmpty()) {
                        chain.add(name);
                    }
                }
                return List.copyOf(chain);
            }
        }
        Path leaf = normalizedParent.getFileName();
        return leaf == null ? List.of() : List.of(leaf.toString());
    }
}
