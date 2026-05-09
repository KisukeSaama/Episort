package com.episort.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.workflow.AiWorkflowGate;
import org.junit.jupiter.api.Test;

class AiContextualHelpServiceTest {

    @Test
    void refusesWhenAiPrerequisitesAreUnavailableAndNeverInvokesAssistant() {
        AiWorkflowGate blockedGate = new AiWorkflowGate(new AiPrerequisiteService(
                new UnavailableLocalAiRuntimeProbe()));
        AiContextualAssistant tripWire = request -> {
            throw new AssertionError("Assistant must not be called when AI is unavailable.");
        };
        AiContextualHelpService service = new AiContextualHelpService(blockedGate, tripWire);

        AiContextualHelpResult result = service.help(new AiContextualRequest(
                new AiContextualSelection.File("Show.S01E01.mkv")));

        assertFalse(result.provided());
        assertTrue(result.explanation().isEmpty());
        assertEquals("AI_PREREQUISITES_UNAVAILABLE", result.refusalReason().orElseThrow().code());
    }

    @Test
    void providesAdvisoryExplanationWhenGateAllows() throws Exception {
        try (FakeLlamaServer fake = FakeLlamaServer.start()) {
            AiWorkflowGate openGate = new AiWorkflowGate(new AiPrerequisiteService(fake.probe()));
            AiContextualHelpService service = new AiContextualHelpService(
                    openGate,
                    new BundledLocalAiContextualAssistant(fake.patternAssistant()));

            AiContextualHelpResult result = service.help(new AiContextualRequest(
                    new AiContextualSelection.File("Show.S01E01.mkv")));

            assertTrue(result.provided());
            AiExplanation explanation = result.explanation().orElseThrow();
            assertTrue(explanation.advisoryOnly());
            assertFalse(explanation.validationAuthority());
            assertFalse(explanation.executionAuthority());
            assertTrue(result.refusalReason().isEmpty());
        }
    }

    @Test
    void constructorRejectsNullCollaborators() {
        AiContextualAssistant noop = request -> AiExplanation.advisory("x", java.util.Optional.empty());
        AiWorkflowGate gate = new AiWorkflowGate(new AiPrerequisiteService(new UnavailableLocalAiRuntimeProbe()));
        assertThrows(NullPointerException.class, () -> new AiContextualHelpService(null, noop));
        assertThrows(NullPointerException.class, () -> new AiContextualHelpService(gate, null));
    }
}
