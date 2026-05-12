package com.episort.workflow;

import com.episort.matching.MediaMatchProposal;
import com.episort.tvdb.TvdbIdentity;
import com.episort.tvdb.TvdbCandidate;
import com.episort.tvdb.TvdbMovieDetails;
import com.episort.tvdb.TvdbSeriesDetails;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregated outcome of the post-scan batch TVDB resolution. One
 * {@link GroupMatch} per inventory group, keyed by seed name. Carries the
 * per-row {@link MediaMatchProposal}s so the UI can paint everything in one
 * pass.
 */
public record TvdbBatchMatchResult(Map<String, GroupMatch> matchesBySeed, List<String> errors) {

    public TvdbBatchMatchResult {
        matchesBySeed = matchesBySeed == null ? Map.of() : Map.copyOf(matchesBySeed);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static TvdbBatchMatchResult empty() {
        return new TvdbBatchMatchResult(Map.of(), List.of());
    }

    public boolean isEmpty() {
        return matchesBySeed.isEmpty();
    }

    public record GroupMatch(
            String seedName,
            TvdbIdentity identity,
            Optional<TvdbSeriesDetails> series,
            Optional<TvdbMovieDetails> movie,
            Optional<TvdbCandidate> candidate,
            Map<Path, MediaMatchProposal> proposalsByPath,
            double score,
            boolean automatic,
            Optional<String> alert) {
        public GroupMatch {
            Objects.requireNonNull(seedName, "seedName");
            Objects.requireNonNull(identity, "identity");
            series = series == null ? Optional.empty() : series;
            movie = movie == null ? Optional.empty() : movie;
            candidate = candidate == null ? Optional.empty() : candidate;
            proposalsByPath = proposalsByPath == null ? Map.of() : Map.copyOf(proposalsByPath);
            if (score < 0.0 || score > 1.0) {
                throw new IllegalArgumentException("score must be between 0 and 1");
            }
            alert = alert == null ? Optional.empty() : alert;
        }

        public GroupMatch(
                String seedName,
                TvdbIdentity identity,
                Optional<TvdbSeriesDetails> series,
                Optional<TvdbMovieDetails> movie,
                Map<Path, MediaMatchProposal> proposalsByPath,
                double score,
                boolean automatic,
                Optional<String> alert) {
            this(seedName, identity, series, movie, Optional.empty(), proposalsByPath, score, automatic, alert);
        }
    }
}
