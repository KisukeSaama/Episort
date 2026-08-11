package com.episort.tmdb;

import java.util.List;

public record TmdbSearchResult(List<TmdbCandidate> seriesCandidates, List<TmdbCandidate> movieCandidates) {
    public TmdbSearchResult {
        seriesCandidates = seriesCandidates == null ? List.of() : List.copyOf(seriesCandidates);
        movieCandidates = movieCandidates == null ? List.of() : List.copyOf(movieCandidates);
    }
}
