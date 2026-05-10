package com.episort.ai;

public enum AiBundledModel {
    QWEN3_1_7B("qwen3:1.7b", true);

    private final String identity;
    private final boolean requiresGpu;

    AiBundledModel(String identity, boolean requiresGpu) {
        this.identity = identity;
        this.requiresGpu = requiresGpu;
    }

    public String identity() {
        return identity;
    }

    public boolean requiresGpu() {
        return requiresGpu;
    }
}
