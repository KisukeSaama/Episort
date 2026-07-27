package com.episort.planning;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds Plex-compatible destination paths from a validated identity
 * (Stories 6.1 and 6.2).
 *
 * <pre>
 * Series Name/Season 02/Series Name - S02E05 - Episode Title.mkv
 * Series Name/Specials/Series Name - S00E03 - Special Title.mkv
 * Movie Name (1999).mkv
 * </pre>
 *
 * <p>The original extension is preserved exactly and every component goes
 * through {@link WindowsPathSafety}, so the result is always a legal Windows
 * path and always the same for the same inputs.
 */
public final class PlexDestinationNamer {
    static final String SPECIALS_FOLDER = "Specials";
    private static final int SPECIALS_SEASON = 0;

    /**
     * @throws IllegalArgumentException when the identity is incomplete; callers
     *         must check {@link PlanSourceItem#hasCompleteIdentity()} first and
     *         mark incomplete items as unassigned instead of planning them.
     */
    public Path destinationFor(Path workspaceRoot, PlanSourceItem item) {
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        Objects.requireNonNull(item, "item");
        if (!item.hasCompleteIdentity()) {
            throw new IllegalArgumentException("Incomplete identity for " + item.sourcePath());
        }
        return switch (item.kind()) {
            case SERIES_EPISODE -> seriesDestination(workspaceRoot, item);
            case SPECIAL -> specialDestination(workspaceRoot, item);
            case MOVIE -> movieDestination(workspaceRoot, item);
        };
    }

    private Path seriesDestination(Path workspaceRoot, PlanSourceItem item) {
        String series = item.seriesTitle().orElseThrow().trim();
        int season = item.seasonNumber().orElseThrow();
        int episode = item.episodeNumber().orElseThrow();
        return WindowsPathSafety.fitWithinMaxPath(
                workspaceRoot,
                List.of(series, seasonFolder(season)),
                episodeBaseName(series, season, episode, item.episodeTitle()),
                item.extension());
    }

    /**
     * Specials keep the series folder but land in {@code Specials} and are
     * numbered as season zero, which is what Plex expects for OVAs and extras.
     */
    private Path specialDestination(Path workspaceRoot, PlanSourceItem item) {
        String series = item.seriesTitle().orElseThrow().trim();
        int season = item.seasonNumber().orElse(SPECIALS_SEASON);
        int episode = item.episodeNumber().orElseThrow();
        return WindowsPathSafety.fitWithinMaxPath(
                workspaceRoot,
                List.of(series, SPECIALS_FOLDER),
                episodeBaseName(series, season, episode, item.episodeTitle()),
                item.extension());
    }

    /**
     * Movies land directly at the workspace root — no folder of their own. The
     * year stays in the file name, which is what keeps homonymous movies
     * distinguishable now that there is no folder to carry that identity.
     */
    private Path movieDestination(Path workspaceRoot, PlanSourceItem item) {
        return WindowsPathSafety.fitWithinMaxPath(
                workspaceRoot, List.of(), movieIdentity(item), item.extension());
    }

    static String movieIdentity(PlanSourceItem item) {
        String title = item.movieTitle().orElseThrow().trim();
        return item.movieYear().isPresent() ? title + " (" + item.movieYear().getAsInt() + ")" : title;
    }

    static String seasonFolder(int season) {
        return String.format(Locale.ROOT, "Season %02d", season);
    }

    static String episodeBaseName(String series, int season, int episode, Optional<String> episodeTitle) {
        String base = String.format(Locale.ROOT, "%s - S%02dE%02d", series, season, episode);
        return episodeTitle
                .map(String::trim)
                .filter(title -> !title.isEmpty())
                .map(title -> base + " - " + title)
                .orElse(base);
    }
}
