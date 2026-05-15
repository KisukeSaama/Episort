package com.episort.workflow;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ReviewSession {
    private final Map<Path, ReviewItem> itemsByPath = new LinkedHashMap<>();
    private boolean patternValidated;
    private boolean exactPlanExists;
    private boolean exactPlanValidated;

    public void replaceItems(Collection<ReviewItem> items) {
        Map<Path, ReviewItem> replacement = new LinkedHashMap<>();
        for (ReviewItem item : items == null ? List.<ReviewItem>of() : items) {
            replacement.put(item.sourcePath().toAbsolutePath().normalize(), item);
        }
        if (itemsByPath.equals(replacement)) {
            return;
        }
        itemsByPath.clear();
        itemsByPath.putAll(replacement);
        invalidatePatternValidation();
    }

    public void upsert(ReviewItem item) {
        Objects.requireNonNull(item, "item");
        Path key = item.sourcePath().toAbsolutePath().normalize();
        ReviewItem previous = itemsByPath.put(key, item);
        if (!item.equals(previous)) {
            invalidatePatternValidation();
        }
    }

    public List<ReviewItem> items() {
        return List.copyOf(itemsByPath.values());
    }

    public boolean validatePattern() {
        if (hasBlockingConflicts()) {
            patternValidated = false;
            return false;
        }
        patternValidated = true;
        return true;
    }

    public boolean patternValidated() {
        return patternValidated;
    }

    public boolean hasBlockingConflicts() {
        return itemsByPath.values().stream().anyMatch(ReviewItem::blockingConflict);
    }

    public void setExactPlanExists(boolean exactPlanExists) {
        this.exactPlanExists = exactPlanExists;
        if (!exactPlanExists) {
            exactPlanValidated = false;
        }
    }

    public boolean validateExactPlan() {
        if (!exactPlanExists) {
            exactPlanValidated = false;
            return false;
        }
        exactPlanValidated = true;
        return true;
    }

    public ReviewValidationSnapshot snapshot() {
        return new ReviewValidationSnapshot(patternValidated, exactPlanExists, exactPlanValidated, items());
    }

    public ExecutionEligibility executionEligibility() {
        List<String> blockers = new ArrayList<>();
        if (!patternValidated) {
            blockers.add("PATTERN_NOT_VALIDATED");
        }
        if (!exactPlanExists) {
            blockers.add("EXACT_PLAN_MISSING");
        } else if (!exactPlanValidated) {
            blockers.add("EXACT_PLAN_NOT_VALIDATED");
        }
        return new ExecutionEligibility(blockers.isEmpty(), blockers);
    }

    private void invalidatePatternValidation() {
        patternValidated = false;
        exactPlanValidated = false;
    }
}
