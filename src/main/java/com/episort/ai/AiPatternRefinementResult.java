package com.episort.ai;

import com.episort.workflow.ApplicationError;
import java.util.List;
import java.util.Optional;

public record AiPatternRefinementResult(
        boolean refined,
        List<AiGroupSuggestion> suggestions,
        Optional<ApplicationError> skipReason) {
    public AiPatternRefinementResult {
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
        skipReason = skipReason == null ? Optional.empty() : skipReason;
    }

    public static AiPatternRefinementResult advisory(List<AiGroupSuggestion> suggestions) {
        return new AiPatternRefinementResult(true, suggestions, Optional.empty());
    }

    public static AiPatternRefinementResult skipped(ApplicationError reason) {
        return new AiPatternRefinementResult(false, List.of(), Optional.ofNullable(reason));
    }
}
