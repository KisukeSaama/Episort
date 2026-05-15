package com.episort.workflow;

import java.util.List;

public record ReviewValidationSnapshot(
        boolean patternValidated,
        boolean exactPlanExists,
        boolean exactPlanValidated,
        List<ReviewItem> items) {
    public ReviewValidationSnapshot {
        items = List.copyOf(items);
    }
}
