package com.episort.filename;

import java.util.Objects;

/**
 * A recognised release tag and the exact half-open range {@code [start, end)}
 * it occupies in the file base name.
 *
 * <p>Positions matter as much as values: everything downstream (title
 * extraction, episode-marker detection, absolute-number detection) works by
 * excluding these ranges, so a tag that is recognised but mis-positioned would
 * be worse than one that is not recognised at all.
 */
public record ReleaseTag(TagKind kind, String raw, String normalized, int start, int end) {
    public ReleaseTag {
        Objects.requireNonNull(kind, "kind");
        raw = raw == null ? "" : raw;
        normalized = normalized == null ? "" : normalized;
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Tag positions must be a valid [start, end) range.");
        }
    }
}
