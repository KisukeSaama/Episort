package com.episort.matching;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

public record MediaMatchProposal(
        Path sourcePath,
        MediaMatchType type,
        Optional<String> tmdbId,
        Optional<String> title,
        Optional<Integer> seasonNumber,
        Optional<Integer> episodeNumber,
        Optional<Integer> absoluteNumber,
        OptionalDouble confidence,
        String reason) {
    public MediaMatchProposal {
        sourcePath = Objects.requireNonNull(sourcePath, "sourcePath");
        type = Objects.requireNonNull(type, "type");
        tmdbId = tmdbId == null ? Optional.empty() : tmdbId;
        title = title == null ? Optional.empty() : title;
        seasonNumber = seasonNumber == null ? Optional.empty() : seasonNumber;
        episodeNumber = episodeNumber == null ? Optional.empty() : episodeNumber;
        absoluteNumber = absoluteNumber == null ? Optional.empty() : absoluteNumber;
        confidence = confidence == null ? OptionalDouble.empty() : confidence;
        reason = reason == null ? "" : reason;
    }

    public static MediaMatchProposal unmatched(Path sourcePath, String reason) {
        return new MediaMatchProposal(
                sourcePath,
                MediaMatchType.UNMATCHED,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                OptionalDouble.empty(),
                reason);
    }
}
