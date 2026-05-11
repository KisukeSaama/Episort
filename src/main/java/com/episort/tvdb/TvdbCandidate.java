package com.episort.tvdb;

import java.util.Objects;
import java.util.Optional;

public record TvdbCandidate(
        TvdbIdentity identity,
        Optional<String> overview,
        Optional<Integer> year,
        Optional<String> country,
        Optional<String> network,
        OptionalDoubleScore advisoryAiScore,
        int tvdbRank) {
    public TvdbCandidate {
        identity = Objects.requireNonNull(identity, "identity");
        overview = overview == null ? Optional.empty() : overview;
        year = year == null ? Optional.empty() : year;
        country = country == null ? Optional.empty() : country;
        network = network == null ? Optional.empty() : network;
        advisoryAiScore = advisoryAiScore == null ? OptionalDoubleScore.empty() : advisoryAiScore;
        if (tvdbRank < 0) {
            throw new IllegalArgumentException("tvdbRank must be zero or greater");
        }
    }

    public TvdbCandidate withAdvisoryAiScore(double score) {
        return new TvdbCandidate(identity, overview, year, country, network, OptionalDoubleScore.of(score), tvdbRank);
    }
}
