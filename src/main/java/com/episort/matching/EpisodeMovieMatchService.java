package com.episort.matching;

import com.episort.filename.SeasonEpisodePattern;
import com.episort.filename.FilenameParser;
import com.episort.filename.FolderContext;
import com.episort.scanner.InventoryItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic advisory matcher. It proposes at most one TVDB-backed match per
 * supported file and never creates, moves, renames, or validates anything.
 */
public final class EpisodeMovieMatchService {
    private static final Pattern NXNN = Pattern.compile("(?<![A-Za-z0-9])(\\d{1,2})[xX](\\d{1,3})(?![A-Za-z0-9])");
    private static final Pattern ABSOLUTE = Pattern.compile("(?<!\\d)(\\d{2,4})(?!\\d)");

    public List<MediaMatchProposal> proposeSeriesMatches(
            List<InventoryItem> items,
            TvdbSeriesMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        List<MediaMatchProposal> proposals = new ArrayList<>();
        Set<String> assignedEpisodeIds = new HashSet<>();
        for (InventoryItem item : safeItems(items)) {
            Optional<ParsedEpisodeKey> parsed = parseEpisodeKey(item.filename());
            if (parsed.isEmpty()) {
                proposals.add(MediaMatchProposal.unmatched(item.sourcePath(), "No deterministic episode marker found."));
                continue;
            }
            Optional<TvdbEpisodeMetadata> matched = findEpisode(metadata, parsed.orElseThrow(), assignedEpisodeIds);
            if (matched.isEmpty()) {
                proposals.add(MediaMatchProposal.unmatched(item.sourcePath(), "No matching TVDB episode found."));
                continue;
            }
            TvdbEpisodeMetadata episode = matched.orElseThrow();
            assignedEpisodeIds.add(episode.tvdbId());
            proposals.add(new MediaMatchProposal(
                    item.sourcePath(),
                    episode.special() ? MediaMatchType.SERIES_SPECIAL : MediaMatchType.SERIES_EPISODE,
                    Optional.of(episode.tvdbId()),
                    Optional.of(episode.title()),
                    Optional.of(episode.seasonNumber()),
                    Optional.of(episode.episodeNumber()),
                    episode.absoluteNumber(),
                    OptionalDouble.of(parsed.orElseThrow().absolute() ? 0.78 : 0.92),
                    parsed.orElseThrow().absolute() ? "Matched by absolute episode number." : "Matched by season/episode marker."));
        }
        return List.copyOf(proposals);
    }

    public List<MediaMatchProposal> proposeMovieMatches(
            List<InventoryItem> items,
            TvdbMovieMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        List<MediaMatchProposal> proposals = new ArrayList<>();
        boolean assigned = false;
        for (InventoryItem item : safeItems(items)) {
            if (assigned) {
                proposals.add(MediaMatchProposal.unmatched(item.sourcePath(), "Movie identity already assigned to another file."));
                continue;
            }
            if (!movieLooksCompatible(item.filename(), metadata)) {
                proposals.add(MediaMatchProposal.unmatched(item.sourcePath(), "Filename does not resemble selected TVDB movie."));
                continue;
            }
            assigned = true;
            proposals.add(new MediaMatchProposal(
                    item.sourcePath(),
                    MediaMatchType.MOVIE,
                    Optional.of(metadata.tvdbId()),
                    Optional.of(metadata.title()),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    OptionalDouble.of(0.86),
                    "Matched by selected TVDB movie identity."));
        }
        return List.copyOf(proposals);
    }

    private Optional<TvdbEpisodeMetadata> findEpisode(
            TvdbSeriesMetadata metadata,
            ParsedEpisodeKey key,
            Set<String> assignedEpisodeIds) {
        return metadata.episodes().stream()
                .filter(episode -> !assignedEpisodeIds.contains(episode.tvdbId()))
                .filter(episode -> matches(episode, key))
                .sorted(Comparator.comparing(TvdbEpisodeMetadata::special))
                .findFirst();
    }

    private boolean matches(TvdbEpisodeMetadata episode, ParsedEpisodeKey key) {
        if (key.absolute()) {
            return episode.absoluteNumber().filter(number -> number == key.episode()).isPresent();
        }
        return episode.seasonNumber() == key.season() && episode.episodeNumber() == key.episode();
    }

    private Optional<ParsedEpisodeKey> parseEpisodeKey(String filename) {
        if (looksLikeMultipleEpisodes(filename)) {
            return Optional.empty();
        }
        Optional<ParsedEpisodeKey> explicit = parse(filename, SeasonEpisodePattern.STRICT);
        if (explicit.isPresent()) {
            return explicit;
        }
        explicit = parse(filename, NXNN);
        if (explicit.isPresent()) {
            return explicit;
        }
        String base = stripExtension(filename);
        Matcher absolute = ABSOLUTE.matcher(base);
        if (absolute.find()) {
            return Optional.of(new ParsedEpisodeKey(1, intValue(absolute.group(1)), true));
        }
        return Optional.empty();
    }

    private static boolean looksLikeMultipleEpisodes(String filename) {
        return FilenameParser.parse(filename, FolderContext.none()).multiEpisode();
    }

    private Optional<ParsedEpisodeKey> parse(String filename, Pattern pattern) {
        Matcher matcher = pattern.matcher(stripExtension(filename));
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(new ParsedEpisodeKey(intValue(matcher.group(1)), intValue(matcher.group(2)), false));
    }

    private boolean movieLooksCompatible(String filename, TvdbMovieMetadata metadata) {
        String normalizedFilename = normalize(stripExtension(filename));
        String normalizedTitle = normalize(metadata.title());
        if (normalizedFilename.contains(normalizedTitle)) {
            return metadata.releaseYear()
                    .map(year -> normalizedFilename.contains(String.valueOf(year)) || normalizedTitle.length() >= 6)
                    .orElse(true);
        }
        return metadata.releaseYear().map(year -> normalizedFilename.contains(String.valueOf(year))).orElse(false);
    }

    private List<InventoryItem> safeItems(List<InventoryItem> items) {
        return items == null ? List.of() : List.copyOf(items);
    }

    private static String stripExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static int intValue(String value) {
        return Integer.parseInt(value);
    }

    private record ParsedEpisodeKey(int season, int episode, boolean absolute) {
    }
}
