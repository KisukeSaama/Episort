package com.episort.ai;

import java.util.Optional;

public record AiRuntimeStatus(
        boolean runtimeAvailable,
        AiHardwareSignals hardwareSignals,
        Optional<AiBundledModel> model,
        String runtimeName,
        String diagnostic) {
    public AiRuntimeStatus {
        if (hardwareSignals == null) {
            throw new IllegalArgumentException("hardwareSignals is required");
        }
        model = model == null ? Optional.empty() : model;
        runtimeName = runtimeName == null ? "" : runtimeName;
        diagnostic = diagnostic == null ? "" : diagnostic;
    }

    public static AiRuntimeStatus available(
            AiHardwareSignals hardwareSignals,
            AiBundledModel model,
            String runtimeName) {
        return new AiRuntimeStatus(true, hardwareSignals, Optional.of(model), runtimeName, "");
    }

    public static AiRuntimeStatus unavailable(
            AiHardwareSignals hardwareSignals,
            boolean modelAvailable,
            String diagnostic) {
        return new AiRuntimeStatus(
                false,
                hardwareSignals,
                modelAvailable ? Optional.of(AiBundledModel.QWEN3_8B) : Optional.empty(),
                "",
                diagnostic);
    }
}
