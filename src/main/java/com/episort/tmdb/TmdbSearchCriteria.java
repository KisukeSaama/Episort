package com.episort.tmdb;

import java.util.Optional;

/** User-supplied criteria for a TMDB identity search. */
public record TmdbSearchCriteria(String query, Optional<Integer> year, Optional<String> tmdbId) {
    public TmdbSearchCriteria {
        query = query == null ? "" : query.trim();
        year = year == null ? Optional.empty() : year;
        tmdbId = tmdbId == null
                ? Optional.empty()
                : tmdbId.map(String::trim).filter(value -> !value.isBlank());
        if (query.isBlank() && tmdbId.isEmpty()) {
            throw new IllegalArgumentException("A title or TMDB ID is required");
        }
        year.ifPresent(value -> {
            if (value < 1000 || value > 9999) {
                throw new IllegalArgumentException("year must contain four digits");
            }
        });
        tmdbId.ifPresent(value -> {
            if (!value.matches("\\d+")) {
                throw new IllegalArgumentException("TMDB ID must contain digits only");
            }
        });
    }

    public static TmdbSearchCriteria title(String query) {
        return new TmdbSearchCriteria(query, Optional.empty(), Optional.empty());
    }
}
