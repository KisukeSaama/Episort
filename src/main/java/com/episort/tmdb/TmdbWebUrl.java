package com.episort.tmdb;

import java.util.Objects;
import java.util.Optional;

/** Builds safe, canonical public TMDB pages for resolved media identities. */
public final class TmdbWebUrl {
    private static final String BASE_URL = "https://www.themoviedb.org/";

    private TmdbWebUrl() {
    }

    public static Optional<String> forIdentity(TmdbIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        String id = identity.id();
        if (id == null || !id.matches("[0-9]+")) return Optional.empty();
        String path = identity.mediaType() == TmdbMediaType.SERIES ? "tv/" : "movie/";
        return Optional.of(BASE_URL + path + id);
    }
}
