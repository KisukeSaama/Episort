package com.episort.ai;

import com.episort.workflow.ApplicationError;
import com.episort.workflow.ErrorSeverity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AiPrerequisiteService {
    private final AiRuntimeProbe runtimeProbe;

    public AiPrerequisiteService(AiRuntimeProbe runtimeProbe) {
        this.runtimeProbe = runtimeProbe;
    }

    public AiPrerequisiteCheckResult check() {
        AiRuntimeStatus status = runtimeProbe.probe();
        List<AiPrerequisite> missing = missingPrerequisites(status);
        if (missing.isEmpty()) {
            return new AiPrerequisiteCheckResult(
                    true,
                    true,
                    List.of(),
                    Optional.empty(),
                    status.model().orElse(AiBundledModel.EPISORT_PATTERN_ASSISTANT_V1));
        }

        return new AiPrerequisiteCheckResult(
                false,
                true,
                missing,
                Optional.of(ApplicationError.recoverable(
                        "AI_PREREQUISITES_UNAVAILABLE",
                        ErrorSeverity.BLOCKING,
                        "Local AI is unavailable. Non-AI workflows remain available.",
                        safeDiagnostic(missing, status))),
                AiBundledModel.EPISORT_PATTERN_ASSISTANT_V1);
    }

    public AiRuntimeDiagnostic diagnostic() {
        AiRuntimeStatus status = runtimeProbe.probe();
        return new AiRuntimeDiagnostic(
                AiBundledModel.EPISORT_PATTERN_ASSISTANT_V1.identity(),
                status.runtimeAvailable(),
                status.runtimeName(),
                safeDiagnostic(missingPrerequisites(status), status));
    }

    private List<AiPrerequisite> missingPrerequisites(AiRuntimeStatus status) {
        List<AiPrerequisite> missing = new ArrayList<>();
        if (!status.runtimeAvailable()) {
            missing.add(AiPrerequisite.RUNTIME);
        }
        if (!status.hardwareSignals().gpuAvailable()) {
            missing.add(AiPrerequisite.GPU);
        }
        if (!status.hardwareSignals().minimumVramAvailable()) {
            missing.add(AiPrerequisite.VRAM);
        }
        if (status.model().isEmpty()) {
            missing.add(AiPrerequisite.MODEL);
        }
        return missing;
    }

    private String safeDiagnostic(List<AiPrerequisite> missing, AiRuntimeStatus status) {
        return "Missing local AI prerequisites: " + missing
                + "; runtimeAvailable=" + status.runtimeAvailable()
                + "; gpuAvailable=" + status.hardwareSignals().gpuAvailable()
                + "; vramMegabytes=" + status.hardwareSignals().vramMegabytes()
                + "; bundledModel=" + AiBundledModel.EPISORT_PATTERN_ASSISTANT_V1;
    }
}
