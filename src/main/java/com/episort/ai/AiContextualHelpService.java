package com.episort.ai;

import com.episort.workflow.AiWorkflowGate;
import com.episort.workflow.AiWorkflowGateResult;
import java.util.Objects;

/**
 * Workflow-layer entry point for contextual AI help. Gates the request through
 * {@link AiWorkflowGate} so the assistant is never invoked when local AI
 * prerequisites are unavailable. Consumable by the future Epic 5 review surface
 * without coupling to JavaFX.
 */
public final class AiContextualHelpService {
    private final AiWorkflowGate gate;
    private final AiContextualAssistant assistant;

    public AiContextualHelpService(AiWorkflowGate gate, AiContextualAssistant assistant) {
        this.gate = Objects.requireNonNull(gate, "gate");
        this.assistant = Objects.requireNonNull(assistant, "assistant");
    }

    public AiContextualHelpResult help(AiContextualRequest request) {
        Objects.requireNonNull(request, "request");
        AiWorkflowGateResult gateResult = gate.requireAiAvailable();
        if (!gateResult.allowed()) {
            return AiContextualHelpResult.refused(gateResult.error().orElse(null));
        }
        return AiContextualHelpResult.provided(assistant.explain(request));
    }
}
