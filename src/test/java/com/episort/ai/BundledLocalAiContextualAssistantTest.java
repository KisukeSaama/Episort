package com.episort.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BundledLocalAiContextualAssistantTest {

    private final List<AiPatternSuggestionRequest> capturedPatternRequests = new ArrayList<>();
    private final AiPatternAssistant capturingPatternAssistant = request -> {
        capturedPatternRequests.add(request);
        return AiPatternSuggestion.advisory(
                "explain",
                List.of("SxxExx"),
                request.minimizedSelectedItemContext());
    };

    private final BundledLocalAiContextualAssistant assistant =
            new BundledLocalAiContextualAssistant(capturingPatternAssistant);

    @Test
    void fileSelectionPassesOnlyTheSelectedFilenameToPatternAssistant() {
        AiExplanation explanation = assistant.explain(new AiContextualRequest(
                new AiContextualSelection.File("Show.S01E01.mkv")));

        assertEquals(1, capturedPatternRequests.size());
        assertEquals(List.of("Show.S01E01.mkv"),
                capturedPatternRequests.get(0).selectedItemNames());
        assertTrue(explanation.advisoryOnly());
        assertFalse(explanation.validationAuthority());
        assertFalse(explanation.executionAuthority());
        assertTrue(explanation.suggestedCorrection().isPresent());
    }

    @Test
    void groupSelectionForwardsOnlyGroupFilenamesNotInventory() {
        AiExplanation explanation = assistant.explain(new AiContextualRequest(
                new AiContextualSelection.Group("Show", List.of("Show.S01E01.mkv", "Show.S01E02.mkv"))));

        assertEquals(List.of("Show.S01E01.mkv", "Show.S01E02.mkv"),
                capturedPatternRequests.get(0).selectedItemNames());
        assertNotNull(explanation.explanation());
        assertTrue(explanation.advisoryOnly());
    }

    @Test
    void matchSelectionExposesProposedTitleAndConfidenceInExplanation() {
        AiExplanation explanation = assistant.explain(new AiContextualRequest(
                new AiContextualSelection.Match("Show.S01E01.mkv", "Show — S01E01", 0.42)));

        assertTrue(explanation.explanation().contains("Show.S01E01.mkv"));
        assertTrue(explanation.explanation().contains("Show — S01E01"));
        assertTrue(explanation.explanation().contains("42%"));
        assertTrue(explanation.suggestedCorrection().isPresent());
    }

    @Test
    void conflictSelectionAlwaysReturnsAdvisoryCorrectionGuidance() {
        AiExplanation explanation = assistant.explain(new AiContextualRequest(
                new AiContextualSelection.Conflict("Show.S01E01.mkv", "duplicate destination")));

        assertTrue(explanation.suggestedCorrection().isPresent());
        assertTrue(explanation.advisoryOnly());
    }

    @Test
    void ambiguitySelectionListsCandidatesInCorrection() {
        AiExplanation explanation = assistant.explain(new AiContextualRequest(
                new AiContextualSelection.Ambiguity("The Show.mkv", List.of("Show A", "Show B"))));

        assertTrue(explanation.suggestedCorrection().orElseThrow().contains("Show A"));
        assertTrue(explanation.suggestedCorrection().orElseThrow().contains("Show B"));
    }

    @Test
    void explanationCannotBeConstructedWithValidationOrExecutionAuthority() {
        assertThrows(IllegalArgumentException.class, () -> new AiExplanation(
                "x", Optional.empty(), false, true, false));
        assertThrows(IllegalArgumentException.class, () -> new AiExplanation(
                "x", Optional.empty(), true, false, true));
    }

    @Test
    void constructorRejectsNullPatternAssistant() {
        assertThrows(NullPointerException.class, () -> new BundledLocalAiContextualAssistant(null));
    }
}
