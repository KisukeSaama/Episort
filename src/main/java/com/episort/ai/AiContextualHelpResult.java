package com.episort.ai;

import com.episort.workflow.ApplicationError;
import java.util.Optional;

public record AiContextualHelpResult(
        boolean provided,
        Optional<AiExplanation> explanation,
        Optional<ApplicationError> refusalReason) {
    public AiContextualHelpResult {
        explanation = explanation == null ? Optional.empty() : explanation;
        refusalReason = refusalReason == null ? Optional.empty() : refusalReason;
    }

    public static AiContextualHelpResult provided(AiExplanation explanation) {
        return new AiContextualHelpResult(true, Optional.of(explanation), Optional.empty());
    }

    public static AiContextualHelpResult refused(ApplicationError reason) {
        return new AiContextualHelpResult(false, Optional.empty(), Optional.ofNullable(reason));
    }
}
