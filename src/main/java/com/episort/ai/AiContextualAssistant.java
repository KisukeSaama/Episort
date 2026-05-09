package com.episort.ai;

@FunctionalInterface
public interface AiContextualAssistant {
    AiExplanation explain(AiContextualRequest request);
}
