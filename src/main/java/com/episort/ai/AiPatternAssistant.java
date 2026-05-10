package com.episort.ai;

@FunctionalInterface
public interface AiPatternAssistant {
    AiPatternSuggestion suggestPattern(AiPatternSuggestionRequest request);

    /**
     * Variant that invokes {@code promptTick} once per LLM prompt completed.
     * Default ignores the tick — only progress-aware implementations override.
     */
    default AiPatternSuggestion suggestPattern(
            AiPatternSuggestionRequest request, Runnable promptTick) {
        return suggestPattern(request);
    }
}
