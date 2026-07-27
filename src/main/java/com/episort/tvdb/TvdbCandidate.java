package com.episort.tvdb;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record TvdbCandidate(
        TvdbIdentity identity,
        Optional<String> posterUrl,
        Optional<String> overview,
        Optional<String> englishOverview,
        Optional<String> frenchOverview,
        Optional<Integer> year,
        Optional<String> country,
        Optional<String> network,
        OptionalDoubleScore advisoryAiScore,
        int tvdbRank,
        /** Alternate titles (aliases + per-language translations) reported by TVDB. */
        List<String> alternateTitles) {
    public TvdbCandidate {
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
        if (tvdbRank < 0) {
            throw new IllegalArgumentException("tvdbRank must be zero or greater");
        }
    }

    public TvdbCandidate(
            TvdbIdentity identity,
            Optional<String> posterUrl,
            Optional<String> overview,
            Optional<String> englishOverview,
            Optional<String> frenchOverview,
            Optional<Integer> year,
            Optional<String> country,
            Optional<String> network,
            OptionalDoubleScore advisoryAiScore,
            int tvdbRank) {
        this(identity, posterUrl, overview, englishOverview, frenchOverview,
                year, country, network, advisoryAiScore, tvdbRank, List.of());
    }

    public TvdbCandidate(
            TvdbIdentity identity,
            Optional<String> posterUrl,
            Optional<String> overview,
            Optional<Integer> year,
            Optional<String> country,
            Optional<String> network,
            OptionalDoubleScore advisoryAiScore,
            int tvdbRank) {
        this(identity, posterUrl, overview, Optional.empty(), Optional.empty(), year, country, network, advisoryAiScore, tvdbRank);
    }

    public TvdbCandidate withAdvisoryAiScore(double score) {
        return new TvdbCandidate(
                identity, posterUrl, overview, englishOverview, frenchOverview,
                year, country, network, OptionalDoubleScore.of(score), tvdbRank, alternateTitles);
    }
}
