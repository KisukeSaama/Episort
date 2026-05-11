package com.episort.matching;

import java.util.Objects;
import java.util.Optional;

public record TvdbMovieMetadata(String tvdbId, String title, Optional<Integer> releaseYear) {
    public TvdbMovieMetadata {
        tvdbId = requireText(tvdbId, "tvdbId");
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
