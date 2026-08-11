package com.episort.tmdb;

import java.util.Objects;

public record TmdbIdentity(String id, TmdbMediaType mediaType, String displayName) {
    public TmdbIdentity {
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
