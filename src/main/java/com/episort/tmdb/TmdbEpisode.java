package com.episort.tmdb;

import java.util.Objects;
import java.util.Optional;

public record TmdbEpisode(
        String id,
        int seasonNumber,
        int episodeNumber,
        Optional<Integer> absoluteNumber,
        String title,
        boolean special) {
    public TmdbEpisode {
        id = requireText(id, "id");
        title = title == null || title.isBlank() ? "Untitled episode" : title.trim();
        absoluteNumber = absoluteNumber == null ? Optional.empty() : absoluteNumber;
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
