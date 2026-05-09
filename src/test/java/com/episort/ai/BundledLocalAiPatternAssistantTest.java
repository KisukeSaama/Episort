package com.episort.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.ai.embedded.LlamaServerClient;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BundledLocalAiPatternAssistantTest {

    private FakeLlamaServer fake;

    @BeforeEach
    void start() throws Exception {
        fake = FakeLlamaServer.start();
    }

    @AfterEach
    void stop() {
        fake.close();
    }

    private BundledLocalAiPatternAssistant assistantPointingTo(String envelope) {
        fake.setNextContent(envelope);
        LlamaServerClient client = fake.client();
        return new BundledLocalAiPatternAssistant(() -> Optional.of(client));
    }

    @Test
    void parsesPatternEnvelopeFromLlamaServerResponse() {
        BundledLocalAiPatternAssistant assistant = assistantPointingTo(
                "{\"patterns\":[\"SxxExx\"],\"explanation\":\"Episodes look like S01E02.\"}");

        AiPatternSuggestion suggestion = assistant.suggestPattern(new AiPatternSuggestionRequest(
                List.of("Show.S01E02.mkv", "Show.S01E03.mkv"), ""));

        assertTrue(suggestion.advisoryOnly());
        assertFalse(suggestion.validationAuthority());
        assertFalse(suggestion.executionAuthority());
        assertEquals(List.of("SxxExx"), suggestion.suggestedPatterns());
        assertTrue(suggestion.explanation().contains("S01E02"));
    }

    @Test
    void emptyContextReturnsAdvisoryWithoutInvokingRuntime() {
        BundledLocalAiPatternAssistant assistant = new BundledLocalAiPatternAssistant(() -> {
            throw new AssertionError("Runtime must not be invoked for empty context.");
        });

        AiPatternSuggestion suggestion = assistant.suggestPattern(new AiPatternSuggestionRequest(
                List.of(), ""));

        assertTrue(suggestion.suggestedPatterns().isEmpty());
        assertFalse(suggestion.explanation().isBlank());
    }

    @Test
    void runtimeNotReadyReturnsAdvisoryFallback() {
        BundledLocalAiPatternAssistant assistant = new BundledLocalAiPatternAssistant(Optional::empty);

        AiPatternSuggestion suggestion = assistant.suggestPattern(new AiPatternSuggestionRequest(
                List.of("Show.S01E02.mkv"), ""));

        assertTrue(suggestion.suggestedPatterns().isEmpty());
        assertTrue(suggestion.advisoryOnly());
        assertFalse(suggestion.explanation().isBlank());
    }

    @Test
    void malformedResponseFallsBackToAdvisoryWithoutPatterns() {
        BundledLocalAiPatternAssistant assistant = assistantPointingTo("not even close to JSON");

        AiPatternSuggestion suggestion = assistant.suggestPattern(new AiPatternSuggestionRequest(
                List.of("Show.S01E02.mkv"), ""));

        assertTrue(suggestion.suggestedPatterns().isEmpty());
        assertTrue(suggestion.advisoryOnly());
    }

    @Test
    void doesNotEchoFullPathInContextOnlyFilename() {
        BundledLocalAiPatternAssistant assistant = assistantPointingTo(
                "{\"patterns\":[\"SxxExx\"],\"explanation\":\"hint\"}");

        AiPatternSuggestion suggestion = assistant.suggestPattern(new AiPatternSuggestionRequest(
                List.of(), "C:\\Users\\private\\Videos\\Show.S01E01.mkv"));

        assertEquals(List.of("Show.S01E01.mkv"), suggestion.selectedItemContext());
        assertFalse(suggestion.explanation().contains("C:\\Users\\private"));
    }
}
