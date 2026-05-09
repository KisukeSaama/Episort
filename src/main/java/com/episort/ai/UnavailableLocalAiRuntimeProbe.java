package com.episort.ai;

public final class UnavailableLocalAiRuntimeProbe implements AiRuntimeProbe {
    @Override
    public AiRuntimeStatus probe() {
        return AiRuntimeStatus.unavailable(
                new AiHardwareSignals(false, 0),
                false,
                "No local AI runtime adapter has been configured.");
    }
}
