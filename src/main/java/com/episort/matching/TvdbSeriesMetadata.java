package com.episort.matching;

import java.util.List;
import java.util.Objects;

public record TvdbSeriesMetadata(String tvdbId, String officialName, List<TvdbEpisodeMetadata> episodes) {
    public TvdbSeriesMetadata {
        tvdbId = requireText(tvdbId, "tvdbId");
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
