package com.episort.workflow;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Session-scoped review and validation state for Epic 5.
 *
 * <p>Two independent gates guard execution: the pattern gate (Epic 5) and the
 * exact-plan gate (Epic 6). Confidence never feeds either gate. Any change to
 * the reviewed items invalidates both gates and discards the frozen pattern
 * snapshot, so an operation plan can never outlive the reviewed state it was
 * generated from.
 */
public final class ReviewSession {
    public static final String BLOCKER_BLOCKING_CONFLICTS = "BLOCKING_CONFLICTS";
    public static final String BLOCKER_PATTERN_NOT_VALIDATED = "PATTERN_NOT_VALIDATED";
    public static final String BLOCKER_EXACT_PLAN_MISSING = "EXACT_PLAN_MISSING";
    public static final String BLOCKER_EXACT_PLAN_NOT_VALIDATED = "EXACT_PLAN_NOT_VALIDATED";

    private final Map<Path, ReviewItem> itemsByPath = new LinkedHashMap<>();
    private List<ReviewItem> validatedItems = List.of();
    private boolean patternValidated;
    private boolean exactPlanExists;
    private boolean exactPlanValidated;

    /**
     * Refreshes the reviewed items. A refresh that yields exactly the same items
     * in the same order is a no-op, so navigating away and back inside the same
     * session does not silently reset the validation gates.
     */
    public void replaceItems(Collection<ReviewItem> items) {
        Map<Path, ReviewItem> replacement = new LinkedHashMap<>();
        for (ReviewItem item : items == null ? List.<ReviewItem>of() : items) {
            replacement.put(item.sourcePath().toAbsolutePath().normalize(), item);
        }
        if (sameItemsInSameOrder(replacement)) {
            return;
        }
        itemsByPath.clear();
        itemsByPath.putAll(replacement);
        invalidateValidation();
    }

    /**
     * Drops every reviewed item and reopens both gates. Used when the session
     * starts over (folder reset, post-execution return to the start screen), so
     * no validation can survive the state it was granted for.
     */
    public void reset() {
        itemsByPath.clear();
        invalidateValidation();
    }

    public void upsert(ReviewItem item) {
        Objects.requireNonNull(item, "item");
        Path key = item.sourcePath().toAbsolutePath().normalize();
        ReviewItem previous = itemsByPath.put(key, item);
        if (!item.equals(previous)) {
            invalidateValidation();
        }
    }

    public List<ReviewItem> items() {
        return List.copyOf(itemsByPath.values());
    }

    /**
     * Records explicit pattern validation. Unresolved blocking conflicts always
     * refuse validation; confidence is never consulted. On success the current
     * groups, assignments, ignored files, unsupported files, ambiguities, and
     * conflicts are frozen so downstream planning reads a stable snapshot.
     */
    public boolean validatePattern() {
        if (hasBlockingConflicts()) {
            patternValidated = false;
            validatedItems = List.of();
            return false;
        }
        patternValidated = true;
        validatedItems = items();
        return true;
    }

    public boolean patternValidated() {
        return patternValidated;
    }

    /**
     * The items captured when the pattern was validated, or empty while the
     * pattern gate is open. Planning must read this instead of the live items.
     */
    public Optional<List<ReviewItem>> validatedPatternItems() {
        return patternValidated ? Optional.of(validatedItems) : Optional.empty();
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

    public boolean exactPlanExists() {
        return exactPlanExists;
    }

    public boolean exactPlanValidated() {
        return exactPlanValidated;
    }

    /**
     * Records exact-plan validation. Refused while no plan exists, and refused
     * while the pattern gate is still open, so the two gates can never be
     * satisfied out of order.
     */
    public boolean validateExactPlan() {
        if (!exactPlanExists || !patternValidated) {
            exactPlanValidated = false;
            return false;
        }
        exactPlanValidated = true;
        return true;
    }

    public ReviewValidationSnapshot snapshot() {
        return new ReviewValidationSnapshot(
                patternValidated, exactPlanExists, exactPlanValidated, items(), validatedItems);
    }

    /**
     * Explains which gate still blocks execution. Codes are stable so the UI can
     * map them to localized text without parsing messages.
     */
    public ExecutionEligibility executionEligibility() {
        List<String> blockers = new ArrayList<>();
        if (hasBlockingConflicts()) {
            blockers.add(BLOCKER_BLOCKING_CONFLICTS);
        }
        if (!patternValidated) {
            blockers.add(BLOCKER_PATTERN_NOT_VALIDATED);
        }
        if (!exactPlanExists) {
            blockers.add(BLOCKER_EXACT_PLAN_MISSING);
        } else if (!exactPlanValidated) {
            blockers.add(BLOCKER_EXACT_PLAN_NOT_VALIDATED);
        }
        return new ExecutionEligibility(blockers.isEmpty(), blockers);
    }

    private boolean sameItemsInSameOrder(Map<Path, ReviewItem> replacement) {
        return List.copyOf(itemsByPath.keySet()).equals(List.copyOf(replacement.keySet()))
                && List.copyOf(itemsByPath.values()).equals(List.copyOf(replacement.values()));
    }

    /**
     * A correction reopens both gates and drops the frozen snapshot: any plan
     * generated from the previous state is stale and must be regenerated.
     */
    private void invalidateValidation() {
        patternValidated = false;
        validatedItems = List.of();
        exactPlanExists = false;
        exactPlanValidated = false;
    }
}
