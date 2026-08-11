package com.episort.tmdb;

import java.util.Objects;
import java.util.Optional;

public record TmdbMovieDetails(TmdbIdentity identity, Optional<Integer> releaseYear) {
    public TmdbMovieDetails {
        identity = Objects.requireNonNull(identity, "identity");
        if (identity.mediaType() != TmdbMediaType.MOVIE) {
            throw new IllegalArgumentException("identity must be a movie");
        }
        releaseYear = releaseYear == null ? Optional.empty() : releaseYear;
    }
}
