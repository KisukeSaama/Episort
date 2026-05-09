package com.episort.ai;

public enum AiBundledModel {
    EPISORT_PATTERN_ASSISTANT_V1("episort-pattern-assistant-v1");

    private final String identity;

    AiBundledModel(String identity) {
        this.identity = identity;
    }

    public String identity() {
        return identity;
    }
}
