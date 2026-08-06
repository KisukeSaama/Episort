package com.episort.tvdb;

import java.util.Optional;

/** User-supplied criteria for a TVDB identity search. */
public record TvdbSearchCriteria(String query, Optional<Integer> year, Optional<String> tvdbId) {
    public TvdbSearchCriteria {
        query = query == null ? "" : query.trim();
        year = year == null ? Optional.empty() : year;
        tvdbId = tvdbId == null
                ? Optional.empty()
                : tvdbId.map(String::trim).filter(value -> !value.isBlank());
        if (query.isBlank() && tvdbId.isEmpty()) {
            throw new IllegalArgumentException("A title or TVDB ID is required");
        }
        year.ifPresent(value -> {
            if (value < 1000 || value > 9999) {
                throw new IllegalArgumentException("year must contain four digits");
            }
        });
        tvdbId.ifPresent(value -> {
            if (!value.matches("\\d+")) {
                throw new IllegalArgumentException("TVDB ID must contain digits only");
            }
        });
    }

    public static TvdbSearchCriteria title(String query) {
        return new TvdbSearchCriteria(query, Optional.empty(), Optional.empty());
    }
}
