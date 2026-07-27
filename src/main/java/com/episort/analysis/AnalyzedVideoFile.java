package com.episort.analysis;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

public final class AnalyzedVideoFile {
    private final Path originalPath;
    private final String originalFileName;
    private String extension = "";
    private VideoMediaType mediaType = VideoMediaType.UNKNOWN;
    private String inputPattern;
    private String detectedTitle;
    private Integer seasonNumber;
    private Integer episodeNumber;
    private String episodeTitle;
    private Integer year;
    private String quality;
    private String source;
    private String codec;
    private String language;
    private String releaseGroup;
    private String proposedName;
    private String proposedDestinationPath;
    private TvdbOrder tvdbOrder = TvdbOrder.TO_DEFINE;
    private OptionalDouble confidence = OptionalDouble.empty();
    private AnalysisStatus status = AnalysisStatus.REVIEW;
    private boolean reviewRequired;
    private final List<String> statusReasons = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final EnumMap<AnalysisField, FieldSource> fieldSources = new EnumMap<>(AnalysisField.class);
    private final EnumMap<AnalysisField, Object> userOverrides = new EnumMap<>(AnalysisField.class);

    public AnalyzedVideoFile(Path originalPath, String originalFileName) {
        this.originalPath = originalPath;
        this.originalFileName = originalFileName == null ? "" : originalFileName;
    }

    public Path originalPath() { return originalPath; }
    public String originalFileName() { return originalFileName; }
    public String extension() { return extension; }
    public VideoMediaType mediaType() { return mediaType; }
    public Optional<String> inputPattern() { return optional(inputPattern); }
    public Optional<String> detectedTitle() { return optional(detectedTitle); }
    public Optional<Integer> seasonNumber() { return Optional.ofNullable(seasonNumber); }
    public Optional<Integer> episodeNumber() { return Optional.ofNullable(episodeNumber); }
    public Optional<String> episodeTitle() { return optional(episodeTitle); }
    public Optional<Integer> year() { return Optional.ofNullable(year); }
    public Optional<String> quality() { return optional(quality); }
    public Optional<String> source() { return optional(source); }
    public Optional<String> codec() { return optional(codec); }
    public Optional<String> language() { return optional(language); }
    public Optional<String> releaseGroup() { return optional(releaseGroup); }
    public Optional<String> proposedName() { return optional(proposedName); }
    public Optional<String> proposedDestinationPath() { return optional(proposedDestinationPath); }
    public TvdbOrder tvdbOrder() { return tvdbOrder; }
    public OptionalDouble confidence() { return confidence; }
    public AnalysisStatus status() { return status; }
    public List<String> statusReasons() { return List.copyOf(statusReasons); }
    public List<String> warnings() { return List.copyOf(warnings); }
    public Map<AnalysisField, FieldSource> fieldSources() { return Map.copyOf(fieldSources); }
    public Map<AnalysisField, Object> userOverrides() { return Map.copyOf(userOverrides); }

    public void set(AnalysisField field, Object value, FieldSource source) {
        FieldSource normalizedSource = source == null ? FieldSource.UNKNOWN : source;
        if (normalizedSource != FieldSource.USER && userOverrides.containsKey(field)) {
            return;
        }
        FieldSource currentSource = fieldSources.getOrDefault(field, FieldSource.UNKNOWN);
        if (priority(currentSource) > priority(normalizedSource)) {
            return;
        }
        switch (field) {
            case EXTENSION -> extension = string(value);
            case MEDIA_TYPE -> mediaType = value instanceof VideoMediaType type ? type : mediaType;
            case INPUT_PATTERN -> inputPattern = string(value);
            case DETECTED_TITLE -> detectedTitle = string(value);
            case SEASON_NUMBER -> seasonNumber = integer(value);
            case EPISODE_NUMBER -> episodeNumber = integer(value);
            case EPISODE_TITLE -> episodeTitle = string(value);
            case YEAR -> year = integer(value);
            case QUALITY -> quality = string(value);
            case SOURCE -> this.source = string(value);
            case CODEC -> codec = string(value);
            case LANGUAGE -> language = string(value);
            case RELEASE_GROUP -> releaseGroup = string(value);
            case PROPOSED_NAME -> proposedName = fileNameOnly(string(value));
            case PROPOSED_DESTINATION_PATH -> proposedDestinationPath = string(value);
            case TVDB_ORDER -> tvdbOrder = value instanceof TvdbOrder order ? order : tvdbOrder;
            case CONFIDENCE -> confidence = value instanceof Number number
                    ? OptionalDouble.of(number.doubleValue())
                    : OptionalDouble.empty();
        }
        fieldSources.put(field, normalizedSource);
        if (normalizedSource == FieldSource.USER) {
            userOverrides.put(field, value);
        }
    }

    public void addWarning(String warning) {
        if (warning != null && !warning.isBlank()) {
            warnings.add(warning);
        }
    }

    /**
     * Records a warning that a human has to look at before the file can be
     * organized. Kept separate from {@link #addWarning(String)} so purely
     * informative notes (a numeric title, a season read from the folder) do not
     * flood the review queue.
     */
    public void requireReview(String reason) {
        reviewRequired = true;
        addWarning(reason);
    }

    public boolean reviewRequired() {
        return reviewRequired;
    }

    public void setValidation(AnalysisStatus status, List<String> reasons) {
        this.status = status;
        statusReasons.clear();
        if (reasons != null) {
            statusReasons.addAll(reasons.stream().filter(reason -> reason != null && !reason.isBlank()).toList());
        }
    }

    private static Optional<String> optional(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static String fileNameOnly(String value) {
        if (value == null) {
            return null;
        }
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        return slash >= 0 ? value.substring(slash + 1) : value;
    }

    private static Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return Integer.parseInt(value.toString());
    }

    private static int priority(FieldSource source) {
        return switch (source) {
            case USER -> 4;
            case TVDB -> 3;
            case HEURISTIC -> 1;
            case UNKNOWN -> 0;
        };
    }
}
