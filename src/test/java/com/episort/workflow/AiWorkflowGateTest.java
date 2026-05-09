package com.episort.workflow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.ai.AiHardwareSignals;
import com.episort.ai.AiPrerequisiteService;
import com.episort.ai.AiRuntimeStatus;
import org.junit.jupiter.api.Test;

class AiWorkflowGateTest {
    @Test
    void aiDependentWorkflowIsBlockedWhenLocalPrerequisitesAreUnavailable() {
        AiPrerequisiteService prerequisiteService = new AiPrerequisiteService(() -> AiRuntimeStatus.unavailable(
                new AiHardwareSignals(false, 0),
                false,
                "runtime unavailable"));
        AiWorkflowGate gate = new AiWorkflowGate(prerequisiteService);

        AiWorkflowGateResult result = gate.requireAiAvailable();

        assertFalse(result.allowed());
        assertTrue(result.error().orElseThrow().recoverable());
    }

    @Test
    void nonAiWorkflowRemainsAllowedWhenLocalAiIsUnavailable() {
        AiPrerequisiteService prerequisiteService = new AiPrerequisiteService(() -> AiRuntimeStatus.unavailable(
                new AiHardwareSignals(false, 0),
                false,
                "runtime unavailable"));
        AiWorkflowGate gate = new AiWorkflowGate(prerequisiteService);

        assertTrue(gate.nonAiWorkflowAvailable());
    }
}
