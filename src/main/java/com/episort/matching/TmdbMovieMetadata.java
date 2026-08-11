package com.episort.matching;

import java.util.Objects;
import java.util.Optional;

public record TmdbMovieMetadata(String tmdbId, String title, Optional<Integer> releaseYear) {
    public TmdbMovieMetadata {
        tmdbId = requireText(tmdbId, "tmdbId");
        title = requireText(title, "title");
        releaseYear = releaseYear == null ? Optional.empty() : releaseYear;
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
