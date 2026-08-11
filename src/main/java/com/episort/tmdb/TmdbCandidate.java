package com.episort.tmdb;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record TmdbCandidate(
        TmdbIdentity identity,
        Optional<String> posterUrl,
        Optional<String> overview,
        Optional<String> englishOverview,
        Optional<String> frenchOverview,
        Optional<Integer> year,
        Optional<String> country,
        Optional<String> network,
        OptionalDoubleScore advisoryAiScore,
        int tmdbRank,
        /** Alternate titles (aliases + per-language translations) reported by TMDB. */
        List<String> alternateTitles) {
    public TmdbCandidate {
        identity = Objects.requireNonNull(identity, "identity");
        posterUrl = posterUrl == null ? Optional.empty() : posterUrl;
        overview = overview == null ? Optional.empty() : overview;
        englishOverview = englishOverview == null ? Optional.empty() : englishOverview;
        frenchOverview = frenchOverview == null ? Optional.empty() : frenchOverview;
        year = year == null ? Optional.empty() : year;
        country = country == null ? Optional.empty() : country;
        network = network == null ? Optional.empty() : network;
        advisoryAiScore = advisoryAiScore == null ? OptionalDoubleScore.empty() : advisoryAiScore;
        alternateTitles = alternateTitles == null
                ? List.of()
                : alternateTitles.stream()
                        .filter(title -> title != null && !title.isBlank())
                        .distinct()
                        .toList();
        if (tmdbRank < 0) {
            throw new IllegalArgumentException("tmdbRank must be zero or greater");
        }
    }

    public TmdbCandidate(
            TmdbIdentity identity,
            Optional<String> posterUrl,
            Optional<String> overview,
            Optional<String> englishOverview,
            Optional<String> frenchOverview,
            Optional<Integer> year,
            Optional<String> country,
            Optional<String> network,
            OptionalDoubleScore advisoryAiScore,
            int tmdbRank) {
        this(identity, posterUrl, overview, englishOverview, frenchOverview,
                year, country, network, advisoryAiScore, tmdbRank, List.of());
    }

    public TmdbCandidate(
            TmdbIdentity identity,
            Optional<String> posterUrl,
            Optional<String> overview,
            Optional<Integer> year,
            Optional<String> country,
            Optional<String> network,
            OptionalDoubleScore advisoryAiScore,
            int tmdbRank) {
        this(identity, posterUrl, overview, Optional.empty(), Optional.empty(), year, country, network, advisoryAiScore, tmdbRank);
    }

    public TmdbCandidate withAdvisoryAiScore(double score) {
        return new TmdbCandidate(
                identity, posterUrl, overview, englishOverview, frenchOverview,
                year, country, network, OptionalDoubleScore.of(score), tmdbRank, alternateTitles);
    }
}
