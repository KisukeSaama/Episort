package com.episort.analysis;

import java.util.Objects;

public final class AiResultMapper {
    public void apply(AiVideoAnalysisResult result, AnalyzedVideoFile file) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(file, "file");
        if (!result.valid()) {
            file.addWarning("AI result is invalid.");
            return;
        }
        result.mediaType().ifPresent(value -> file.set(AnalysisField.MEDIA_TYPE, value, FieldSource.AI));
        result.inputPattern().ifPresent(value -> file.set(AnalysisField.INPUT_PATTERN, value, FieldSource.AI));
        result.detectedTitle().ifPresent(value -> file.set(AnalysisField.DETECTED_TITLE, value, FieldSource.AI));
        result.seasonNumber().ifPresent(value -> file.set(AnalysisField.SEASON_NUMBER, value, FieldSource.AI));
        result.episodeNumber().ifPresent(value -> file.set(AnalysisField.EPISODE_NUMBER, value, FieldSource.AI));
        result.episodeTitle().ifPresent(value -> file.set(AnalysisField.EPISODE_TITLE, value, FieldSource.AI));
        result.year().ifPresent(value -> file.set(AnalysisField.YEAR, value, FieldSource.AI));
        result.quality().ifPresent(value -> file.set(AnalysisField.QUALITY, value, FieldSource.AI));
        result.source().ifPresent(value -> file.set(AnalysisField.SOURCE, value, FieldSource.AI));
        result.codec().ifPresent(value -> file.set(AnalysisField.CODEC, value, FieldSource.AI));
        result.language().ifPresent(value -> file.set(AnalysisField.LANGUAGE, value, FieldSource.AI));
        result.releaseGroup().ifPresent(value -> file.set(AnalysisField.RELEASE_GROUP, value, FieldSource.AI));
        if (result.confidence().isPresent()) {
            file.set(AnalysisField.CONFIDENCE, result.confidence().orElseThrow(), FieldSource.AI);
        }
        result.warnings().forEach(file::addWarning);
        result.missingFields().forEach(field -> file.addWarning("AI missing field: " + field));
    }
}
