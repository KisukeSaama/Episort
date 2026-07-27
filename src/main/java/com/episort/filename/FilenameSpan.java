package com.episort.filename;

import java.util.Objects;

/** A recognised range of the original file name, with its normalized value. */
public record FilenameSpan(SpanRole role, String raw, String normalized, int start, int end) {
    public FilenameSpan {
        Objects.requireNonNull(role, "role");
        raw = raw == null ? "" : raw;
        normalized = normalized == null ? "" : normalized;
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Span positions must be a valid [start, end) range.");
        }
    }
}
