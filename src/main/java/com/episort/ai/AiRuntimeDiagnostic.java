package com.episort.ai;

public record AiRuntimeDiagnostic(
        String modelIdentity,
        boolean runtimeAvailable,
        String runtimeName,
        String details) {
    public AiRuntimeDiagnostic {
        modelIdentity = modelIdentity == null ? "" : modelIdentity;
        runtimeName = runtimeName == null ? "" : runtimeName;
        details = details == null ? "" : details;
    }
}
