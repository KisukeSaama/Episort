package com.episort.planning;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * One reviewed file handed to the planner, carrying the TMDB-backed identity the
 * user validated.
 *
 * @param sourcePath      absolute source file, inside the workspace
 * @param extension       original extension, preserved exactly (leading dot optional)
 * @param kind            destination layout to apply
 * @param seriesTitle     official English series name, required for series and specials
 * @param seasonNumber    season number, required for regular episodes
 * @param episodeNumber   episode number, required for series and specials
 * @param episodeTitle    English episode title, optional
 * @param movieTitle      English movie name, required for movies
 * @param movieYear       release year, optional but recommended for homonyms
 * @param exclusionReason {@code NONE} for executable items, otherwise why it is skipped
 */
public record PlanSourceItem(
        Path sourcePath,
        String extension,
        PlanMediaKind kind,
        Optional<String> seriesTitle,
        OptionalInt seasonNumber,
        OptionalInt episodeNumber,
        Optional<String> episodeTitle,
        Optional<String> movieTitle,
        OptionalInt movieYear,
        PlanExclusionReason exclusionReason) {

    public PlanSourceItem {
        Objects.requireNonNull(sourcePath, "sourcePath");
        Objects.requireNonNull(extension, "extension");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(seriesTitle, "seriesTitle");
        Objects.requireNonNull(seasonNumber, "seasonNumber");
        Objects.requireNonNull(episodeNumber, "episodeNumber");
        Objects.requireNonNull(episodeTitle, "episodeTitle");
        Objects.requireNonNull(movieTitle, "movieTitle");
        Objects.requireNonNull(movieYear, "movieYear");
        Objects.requireNonNull(exclusionReason, "exclusionReason");
    }

    public boolean excluded() {
        return exclusionReason != PlanExclusionReason.NONE;
    }

    /**
     * True when the identity carries everything the destination layout needs.
     * Missing identity is not an error — it simply makes the item unassigned.
     */
    public boolean hasCompleteIdentity() {
        return switch (kind) {
            case SERIES_EPISODE -> hasText(seriesTitle) && seasonNumber.isPresent() && episodeNumber.isPresent();
            case SPECIAL -> hasText(seriesTitle) && episodeNumber.isPresent();
            case MOVIE -> hasText(movieTitle);
        };
    }

    private static boolean hasText(Optional<String> value) {
        return value.filter(text -> !text.isBlank()).isPresent();
    }

    public static Builder forSource(Path sourcePath, String extension, PlanMediaKind kind) {
        return new Builder(sourcePath, extension, kind);
    }

    /** Keeps call sites readable: most items only set three or four fields. */
    public static final class Builder {
        private final Path sourcePath;
        private final String extension;
        private final PlanMediaKind kind;
        private Optional<String> seriesTitle = Optional.empty();
        private OptionalInt seasonNumber = OptionalInt.empty();
        private OptionalInt episodeNumber = OptionalInt.empty();
        private Optional<String> episodeTitle = Optional.empty();
        private Optional<String> movieTitle = Optional.empty();
        private OptionalInt movieYear = OptionalInt.empty();
        private PlanExclusionReason exclusionReason = PlanExclusionReason.NONE;

        private Builder(Path sourcePath, String extension, PlanMediaKind kind) {
            this.sourcePath = sourcePath;
            this.extension = extension;
            this.kind = kind;
        }

        public Builder series(String title, int season, int episode) {
            this.seriesTitle = Optional.ofNullable(title);
            this.seasonNumber = OptionalInt.of(season);
            this.episodeNumber = OptionalInt.of(episode);
            return this;
        }

        public Builder special(String title, int episode) {
            this.seriesTitle = Optional.ofNullable(title);
            this.episodeNumber = OptionalInt.of(episode);
            return this;
        }

        public Builder seriesTitle(Optional<String> title) {
            this.seriesTitle = title;
            return this;
        }

        public Builder seasonNumber(OptionalInt season) {
            this.seasonNumber = season;
            return this;
        }

        public Builder episodeNumber(OptionalInt episode) {
            this.episodeNumber = episode;
            return this;
        }

        public Builder episodeTitle(String title) {
            this.episodeTitle = Optional.ofNullable(title);
            return this;
        }

        public Builder episodeTitle(Optional<String> title) {
            this.episodeTitle = title;
            return this;
        }

        public Builder movie(String title, Integer year) {
            this.movieTitle = Optional.ofNullable(title);
            this.movieYear = year == null ? OptionalInt.empty() : OptionalInt.of(year);
            return this;
        }

        public Builder movieTitle(Optional<String> title) {
            this.movieTitle = title;
            return this;
        }

        public Builder movieYear(OptionalInt year) {
            this.movieYear = year;
            return this;
        }

        public Builder excluded(PlanExclusionReason reason) {
            this.exclusionReason = reason;
            return this;
        }

        public PlanSourceItem build() {
            return new PlanSourceItem(
                    sourcePath,
                    extension,
                    kind,
                    seriesTitle,
                    seasonNumber,
                    episodeNumber,
                    episodeTitle,
                    movieTitle,
                    movieYear,
                    exclusionReason);
        }
    }
}
