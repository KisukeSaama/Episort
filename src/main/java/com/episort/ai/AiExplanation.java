package com.episort.ai;

import java.util.Optional;

public record AiExplanation(
        String explanation,
        Optional<String> suggestedCorrection,
        boolean advisoryOnly,
        boolean validationAuthority,
        boolean executionAuthority) {
    public AiExplanation {
        explanation = explanation == null ? "" : explanation;
        suggestedCorrection = suggestedCorrection == null ? Optional.empty() : suggestedCorrection;
        if (!advisoryOnly || validationAuthority || executionAuthority) {
            throw new IllegalArgumentException(
                    "Contextual explanation must remain advisory and cannot validate or execute.");
        }
    }

    public static AiExplanation advisory(String explanation, Optional<String> suggestedCorrection) {
        return new AiExplanation(explanation, suggestedCorrection, true, false, false);
    }
}
