package com.episort.analysis;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public record AiVideoAnalysisResult(
        Optional<VideoMediaType> mediaType,
        Optional<String> inputPattern,
        Optional<String> detectedTitle,
        Optional<Integer> seasonNumber,
        Optional<Integer> episodeNumber,
        Optional<String> episodeTitle,
        Optional<Integer> year,
        Optional<String> quality,
        Optional<String> source,
        Optional<String> codec,
        Optional<String> language,
        Optional<String> releaseGroup,
        OptionalDouble confidence,
        List<String> warnings,
        List<String> missingFields,
        boolean valid) {
    public AiVideoAnalysisResult {
        mediaType = mediaType == null ? Optional.empty() : mediaType;
        inputPattern = inputPattern == null ? Optional.empty() : inputPattern;
        detectedTitle = detectedTitle == null ? Optional.empty() : detectedTitle;
        seasonNumber = seasonNumber == null ? Optional.empty() : seasonNumber;
        episodeNumber = episodeNumber == null ? Optional.empty() : episodeNumber;
        episodeTitle = episodeTitle == null ? Optional.empty() : episodeTitle;
        year = year == null ? Optional.empty() : year;
        quality = quality == null ? Optional.empty() : quality;
        source = source == null ? Optional.empty() : source;
        codec = codec == null ? Optional.empty() : codec;
        language = language == null ? Optional.empty() : language;
        releaseGroup = releaseGroup == null ? Optional.empty() : releaseGroup;
        confidence = confidence == null ? OptionalDouble.empty() : confidence;
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
    }
}
