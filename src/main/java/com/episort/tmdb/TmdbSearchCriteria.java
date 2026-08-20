package com.episort.tmdb;

import java.util.Optional;

/**
 * User-supplied criteria for a TMDB identity search.
 *
 * <p>{@code mediaType} narrows the search to one index. Left empty, both are
 * queried, which is what a manual search wants; a caller that already knows it
 * is looking at a series spares itself the movie index, and the other way
 * around.
 */
public record TmdbSearchCriteria(
        String query, Optional<Integer> year, Optional<String> tmdbId, Optional<TmdbMediaType> mediaType) {
    public TmdbSearchCriteria {
        query = query == null ? "" : query.trim();
        year = year == null ? Optional.empty() : year;
        tmdbId = tmdbId == null
                ? Optional.empty()
                : tmdbId.map(String::trim).filter(value -> !value.isBlank());
        mediaType = mediaType == null ? Optional.empty() : mediaType;
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

    public TmdbSearchCriteria(String query, Optional<Integer> year, Optional<String> tmdbId) {
        this(query, year, tmdbId, Optional.empty());
    }

    public static TmdbSearchCriteria title(String query) {
        return new TmdbSearchCriteria(query, Optional.empty(), Optional.empty());
    }

    /** Same title search, restricted to the index the caller already knows it needs. */
    public static TmdbSearchCriteria title(String query, TmdbMediaType mediaType) {
        return new TmdbSearchCriteria(query, Optional.empty(), Optional.empty(), Optional.ofNullable(mediaType));
    }
}
