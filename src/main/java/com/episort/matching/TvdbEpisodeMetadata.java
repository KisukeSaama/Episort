package com.episort.matching;

import java.util.Objects;
import java.util.Optional;

public record TvdbEpisodeMetadata(
        String tvdbId,
        int seasonNumber,
        int episodeNumber,
        Optional<Integer> absoluteNumber,
        String title,
        boolean special) {
    public TvdbEpisodeMetadata {
        if (seasonNumber < 0) {
            throw new IllegalArgumentException("seasonNumber must be zero or greater");
        }
        if (episodeNumber < 0) {
            throw new IllegalArgumentException("episodeNumber must be zero or greater");
        }
        tvdbId = requireText(tvdbId, "tvdbId");
        absoluteNumber = absoluteNumber == null ? Optional.empty() : absoluteNumber;
        title = title == null || title.isBlank() ? "Untitled episode" : title.trim();
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
