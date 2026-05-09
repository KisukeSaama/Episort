package com.episort.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AiPatternAssistantTest {
    @Test
    void assistantProvidesAdvisorySuggestionsWithMinimizedSelectedItemContext() {
        AiPatternAssistant assistant = request -> AiPatternSuggestion.advisory(
                "Episodes appear to use S01E02 naming.",
                List.of("S01E02"),
                request.minimizedSelectedItemContext());
        AiPatternSuggestionRequest request = new AiPatternSuggestionRequest(
                List.of("Show.Name.S01E02.mkv"),
                "C:\\Users\\private\\Videos\\Show.Name.S01E02.mkv");

        AiPatternSuggestion suggestion = assistant.suggestPattern(request);

        assertTrue(suggestion.advisoryOnly());
        assertFalse(suggestion.validationAuthority());
        assertFalse(suggestion.executionAuthority());
        assertEquals(List.of("Show.Name.S01E02.mkv"), suggestion.selectedItemContext());
    }

    @Test
    void suggestionCannotClaimValidationOrExecutionAuthority() {
        assertThrows(IllegalArgumentException.class, () -> new AiPatternSuggestion(
                "Approved",
                List.of("S01E02"),
                List.of("episode.mkv"),
                false,
                true,
                false));
        assertThrows(IllegalArgumentException.class, () -> new AiPatternSuggestion(
                "Execute",
                List.of("S01E02"),
                List.of("episode.mkv"),
                false,
                false,
                true));
    }
}
