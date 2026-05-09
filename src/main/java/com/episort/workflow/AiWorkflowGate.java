package com.episort.workflow;

import com.episort.ai.AiPrerequisiteCheckResult;
import com.episort.ai.AiPrerequisiteService;

public final class AiWorkflowGate {
    private final AiPrerequisiteService prerequisiteService;

    public AiWorkflowGate(AiPrerequisiteService prerequisiteService) {
        this.prerequisiteService = prerequisiteService;
    }

    public AiWorkflowGateResult requireAiAvailable() {
        AiPrerequisiteCheckResult result = prerequisiteService.check();
        if (result.aiWorkflowsAvailable()) {
            return AiWorkflowGateResult.passed();
        }
        return AiWorkflowGateResult.blocked(result.error().orElseThrow());
    }

    public boolean nonAiWorkflowAvailable() {
        return prerequisiteService.check().nonAiWorkflowsAvailable();
    }
}
