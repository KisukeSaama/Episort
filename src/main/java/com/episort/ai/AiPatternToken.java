package com.episort.ai;

import java.util.Objects;

public record AiPatternToken(
        String role,
        String rawValue,
        String normalizedValue,
        int start,
        int end) {
    public AiPatternToken {
        role = role == null ? "" : role;
        rawValue = rawValue == null ? "" : rawValue;
        normalizedValue = normalizedValue == null ? "" : normalizedValue;
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Token positions must be valid.");
        }
        Objects.requireNonNull(role, "role");
    }
}
