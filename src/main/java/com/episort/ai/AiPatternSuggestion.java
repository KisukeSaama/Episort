package com.episort.ai;

import java.util.List;

public record AiPatternSuggestion(
        String explanation,
        List<String> suggestedPatterns,
        List<String> selectedItemContext,
        boolean advisoryOnly,
        boolean validationAuthority,
        boolean executionAuthority) {
    public AiPatternSuggestion {
        explanation = explanation == null ? "" : explanation;
        suggestedPatterns = suggestedPatterns == null ? List.of() : List.copyOf(suggestedPatterns);
        selectedItemContext = selectedItemContext == null ? List.of() : List.copyOf(selectedItemContext);
        if (!advisoryOnly || validationAuthority || executionAuthority) {
            throw new IllegalArgumentException("AI suggestions must remain advisory and cannot validate or execute.");
        }
    }

    public static AiPatternSuggestion advisory(
            String explanation,
            List<String> suggestedPatterns,
            List<String> selectedItemContext) {
        return new AiPatternSuggestion(explanation, suggestedPatterns, selectedItemContext, true, false, false);
    }
}
