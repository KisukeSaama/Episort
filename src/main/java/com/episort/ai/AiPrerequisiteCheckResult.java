package com.episort.ai;

import com.episort.workflow.ApplicationError;
import java.util.List;
import java.util.Optional;

public record AiPrerequisiteCheckResult(
        boolean aiWorkflowsAvailable,
        boolean nonAiWorkflowsAvailable,
        List<AiPrerequisite> missingPrerequisites,
        Optional<ApplicationError> error,
        AiBundledModel model) {
    public AiPrerequisiteCheckResult {
        missingPrerequisites = List.copyOf(missingPrerequisites);
        error = error == null ? Optional.empty() : error;
    }
}
