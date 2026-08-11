package com.episort.workflow;

import java.util.List;

/**
 * Immutable view of the review gates.
 *
 * @param items          the live reviewed items
 * @param validatedItems the items frozen when the pattern gate was closed,
 *                       empty while the pattern is not validated
 */
public record ReviewValidationSnapshot(
        boolean patternValidated,
        boolean exactPlanExists,
        boolean exactPlanValidated,
        List<ReviewItem> items,
        List<ReviewItem> validatedItems) {
    public ReviewValidationSnapshot {
        items = List.copyOf(items);
        validatedItems = List.copyOf(validatedItems);
    }
}
