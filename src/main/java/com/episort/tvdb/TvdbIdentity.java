package com.episort.tvdb;

import java.util.Objects;

public record TvdbIdentity(String id, TvdbMediaType mediaType, String displayName) {
    public TvdbIdentity {
        id = requireText(id, "id");
        mediaType = Objects.requireNonNull(mediaType, "mediaType");
        displayName = requireText(displayName, "displayName");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return trimmed;
    }
}
