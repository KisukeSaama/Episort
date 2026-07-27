package com.episort.filename;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Everything {@link FilenameParser} could establish about one file name, with
 * the confidence it deserves and the reasons it might be wrong.
 *
 * <p>Absent is always represented as absent — never as a placeholder value.
 * A missing season is {@link OptionalInt#empty()}, never {@code 0} or
 * {@code 1}, so callers cannot silently mistake a guess for a fact.
 */
public record ParsedFilename(
        String originalName,
        String baseName,
        String extension,
        String patternLabel,
        MediaKindHint kind,
        Optional<String> title,
        OptionalInt year,
        OptionalInt season,
        List<Integer> episodes,
        OptionalInt absoluteNumber,
        Optional<LocalDate> airDate,
        Optional<String> episodeTitle,
        Optional<String> quality,
        Optional<String> source,
        Optional<String> codec,
        Optional<String> audio,
        Optional<String> language,
        Optional<String> edition,
        Optional<String> releaseGroup,
        OptionalInt partNumber,
        double confidence,
        List<ParseWarning> warnings,
        List<FilenameSpan> spans,
        List<ReleaseTag> tags) {

    public ParsedFilename {
        originalName = originalName == null ? "" : originalName;
        baseName = baseName == null ? "" : baseName;
        extension = extension == null ? "" : extension;
        patternLabel = patternLabel == null ? "" : patternLabel;
        Objects.requireNonNull(kind, "kind");
        episodes = episodes == null ? List.of() : List.copyOf(episodes);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        spans = spans == null ? List.of() : List.copyOf(spans);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public boolean hasEpisode() {
        return !episodes.isEmpty();
    }

    public OptionalInt firstEpisode() {
        return episodes.isEmpty() ? OptionalInt.empty() : OptionalInt.of(episodes.get(0));
    }

    public boolean multiEpisode() {
        return episodes.size() > 1;
    }

    public boolean extraMaterial() {
        return kind == MediaKindHint.EXTRA;
    }

    public boolean hasWarning(ParseWarning warning) {
        return warnings.contains(warning);
    }

    /**
     * {@code S01E02}, or {@code S01E02-E03} for a multi-episode file. Empty when
     * no season/episode pair could be established.
     */
    public Optional<String> normalizedOrder() {
        if (season.isEmpty() || episodes.isEmpty()) {
            return Optional.empty();
        }
        StringBuilder order = new StringBuilder(
                String.format(Locale.ROOT, "S%02dE%02d", season.getAsInt(), episodes.get(0)));
        for (int index = 1; index < episodes.size(); index++) {
            order.append(String.format(Locale.ROOT, "-E%02d", episodes.get(index)));
        }
        return Optional.of(order.toString());
    }

    public static Builder builder(String originalName, String baseName, String extension) {
        return new Builder(originalName, baseName, extension);
    }

    /** Mutable accumulator; {@link #build()} freezes it into the record. */
    public static final class Builder {
        private final String originalName;
        private final String baseName;
        private final String extension;
        private String patternLabel = "";
        private MediaKindHint kind = MediaKindHint.UNKNOWN;
        private String title;
        private Integer year;
        private Integer season;
        private final List<Integer> episodes = new ArrayList<>();
        private Integer absoluteNumber;
        private LocalDate airDate;
        private String episodeTitle;
        private String quality;
        private String source;
        private String codec;
        private String audio;
        private String language;
        private String edition;
        private String releaseGroup;
        private Integer partNumber;
        private double confidence;
        private final Set<ParseWarning> warnings = new LinkedHashSet<>();
        private final List<FilenameSpan> spans = new ArrayList<>();
        private List<ReleaseTag> tags = List.of();

        private Builder(String originalName, String baseName, String extension) {
            this.originalName = originalName;
            this.baseName = baseName;
            this.extension = extension;
        }

        public Builder patternLabel(String value) {
            patternLabel = value;
            return this;
        }

        public Builder kind(MediaKindHint value) {
            kind = value;
            return this;
        }

        public Builder title(String value) {
            title = value;
            return this;
        }

        public Builder year(Integer value) {
            year = value;
            return this;
        }

        public Builder season(Integer value) {
            season = value;
            return this;
        }

        public Builder episodes(List<Integer> values) {
            episodes.clear();
            if (values != null) {
                episodes.addAll(values);
            }
            return this;
        }

        public Builder absoluteNumber(Integer value) {
            absoluteNumber = value;
            return this;
        }

        public Builder airDate(LocalDate value) {
            airDate = value;
            return this;
        }

        public Builder episodeTitle(String value) {
            episodeTitle = value;
            return this;
        }

        public Builder quality(String value) {
            quality = value;
            return this;
        }

        public Builder source(String value) {
            source = value;
            return this;
        }

        public Builder codec(String value) {
            codec = value;
            return this;
        }

        public Builder audio(String value) {
            audio = value;
            return this;
        }

        public Builder language(String value) {
            language = value;
            return this;
        }

        public Builder edition(String value) {
            edition = value;
            return this;
        }

        public Builder releaseGroup(String value) {
            releaseGroup = value;
            return this;
        }

        public Builder partNumber(Integer value) {
            partNumber = value;
            return this;
        }

        public Builder confidence(double value) {
            confidence = value;
            return this;
        }

        public Builder warn(ParseWarning warning) {
            if (warning != null) {
                warnings.add(warning);
            }
            return this;
        }

        public Builder span(FilenameSpan span) {
            if (span != null) {
                spans.add(span);
            }
            return this;
        }

        public Builder tags(List<ReleaseTag> value) {
            tags = value == null ? List.of() : List.copyOf(value);
            return this;
        }

        public ParsedFilename build() {
            spans.sort((left, right) -> Integer.compare(left.start(), right.start()));
            return new ParsedFilename(
                    originalName,
                    baseName,
                    extension,
                    patternLabel,
                    kind,
                    optional(title),
                    optionalInt(year),
                    optionalInt(season),
                    List.copyOf(episodes),
                    optionalInt(absoluteNumber),
                    Optional.ofNullable(airDate),
                    optional(episodeTitle),
                    optional(quality),
                    optional(source),
                    optional(codec),
                    optional(audio),
                    optional(language),
                    optional(edition),
                    optional(releaseGroup),
                    optionalInt(partNumber),
                    Math.max(0.0, Math.min(1.0, confidence)),
                    List.copyOf(warnings),
                    List.copyOf(spans),
                    tags);
        }

        private static Optional<String> optional(String value) {
            return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
        }

        private static OptionalInt optionalInt(Integer value) {
            return value == null ? OptionalInt.empty() : OptionalInt.of(value);
        }
    }
}
