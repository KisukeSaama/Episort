package com.episort.workflow;

import java.util.Optional;

public record AiWorkflowGateResult(boolean allowed, Optional<ApplicationError> error) {
    public AiWorkflowGateResult {
        error = error == null ? Optional.empty() : error;
    }

    public static AiWorkflowGateResult passed() {
        return new AiWorkflowGateResult(true, Optional.empty());
    }

    public static AiWorkflowGateResult blocked(ApplicationError error) {
        return new AiWorkflowGateResult(false, Optional.of(error));
    }
}
