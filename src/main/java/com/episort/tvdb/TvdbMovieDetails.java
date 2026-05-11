package com.episort.tvdb;

import java.util.Objects;
import java.util.Optional;

public record TvdbMovieDetails(TvdbIdentity identity, Optional<Integer> releaseYear) {
    public TvdbMovieDetails {
        identity = Objects.requireNonNull(identity, "identity");
        if (identity.mediaType() != TvdbMediaType.MOVIE) {
            throw new IllegalArgumentException("identity must be a movie");
        }
        releaseYear = releaseYear == null ? Optional.empty() : releaseYear;
    }
}
