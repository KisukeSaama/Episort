package com.episort.matching;

import java.util.List;
import java.util.Objects;

public record TmdbSeriesMetadata(String tmdbId, String officialName, List<TmdbEpisodeMetadata> episodes) {
    public TmdbSeriesMetadata {
        tmdbId = requireText(tmdbId, "tmdbId");
        officialName = requireText(officialName, "officialName");
        episodes = episodes == null ? List.of() : List.copyOf(episodes);
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
