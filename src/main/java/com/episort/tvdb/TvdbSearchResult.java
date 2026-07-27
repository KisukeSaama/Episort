package com.episort.tvdb;

import java.util.List;

public record TvdbSearchResult(List<TvdbCandidate> seriesCandidates, List<TvdbCandidate> movieCandidates) {
    public TvdbSearchResult {
        seriesCandidates = seriesCandidates == null ? List.of() : List.copyOf(seriesCandidates);
        movieCandidates = movieCandidates == null ? List.of() : List.copyOf(movieCandidates);
    }
}
