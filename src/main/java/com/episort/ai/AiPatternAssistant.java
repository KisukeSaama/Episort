package com.episort.ai;

@FunctionalInterface
public interface AiPatternAssistant {
    AiPatternSuggestion suggestPattern(AiPatternSuggestionRequest request);
}
