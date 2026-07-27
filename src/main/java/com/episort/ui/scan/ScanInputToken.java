package com.episort.ui.scan;

import java.util.Objects;

public record ScanInputToken(
        ScanInputRole role,
        String rawValue,
        String normalizedValue,
        int start,
        int end) {
    public ScanInputToken {
        Objects.requireNonNull(role, "role");
        rawValue = rawValue == null ? "" : rawValue;
        normalizedValue = normalizedValue == null ? "" : normalizedValue;
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Token positions must be a valid [start, end) range.");
        }
    }
}
