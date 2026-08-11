package com.episort.ui.scan;

import com.episort.filename.SeasonEpisodePattern;
import com.episort.filename.FilenameParser;
import com.episort.filename.ParsedFilename;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;

/** Generates preview names from lightweight scan-row metadata and a user/AI pattern. */
public final class ScanPatternFormatter {

    private ScanPatternFormatter() {
    }

    /**
     * Neutralizes the path separators a metadata value may legitimately contain
     * — TMDB episode titles like {@code Makoto/Truth} are common. Left in a name
     * they would be read as a directory boundary and the row would end up
     * showing only the last segment. Other Windows-forbidden characters are
     * handled downstream by {@code WindowsPathSafety} when the plan is built.
     */
    public static String sanitizeSegment(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[/\\\\]", " ").replaceAll("\\s+", " ").trim();
    }

    public static Optional<String> format(ScanRow row, String pattern) {
        return format(row, pattern, null);
    }

    public static Optional<String> format(ScanRow row, String pattern, String seriesOverride) {
        if (row == null || pattern == null || pattern.isBlank()) {
            return Optional.empty();
        }
        if (row.mediaType() == ScanMediaType.MOVIE) {
            return Optional.of(formatMovie(row, seriesOverride));
        }
        Metadata metadata = Metadata.from(row);
        String series = seriesOverride != null && !seriesOverride.isBlank()
                ? seriesOverride.trim()
                : metadata.series();
        String rendered = pattern
                .replace("{series}", sanitizeSegment(series))
                .replace("{season}", metadata.season())
                .replace("{episode}", metadata.episode())
                .replace("{title}", sanitizeSegment(metadata.title()))
                .replace("{order}", row.order().orElse(metadata.seasonEpisode()));
        String extension = row.extension().isBlank() ? "" : "." + row.extension().toLowerCase(Locale.ROOT);
        return Optional.of(rendered + extension);
    }

    private static String formatMovie(ScanRow row, String titleOverride) {
        String baseName = row.originalFilename();
        int dot = baseName.lastIndexOf('.');
        String stem = dot > 0 ? baseName.substring(0, dot) : baseName;
        String extension = row.extension().isBlank() ? "" : "." + row.extension().toLowerCase(Locale.ROOT);

        // A YEAR token set by the user overrides whatever the name says: that
        // is how a wrong year in the source name gets corrected.
        Optional<String> yearOverride = row.inputParse()
                .flatMap(parse -> parse.tokenValue(ScanInputRole.YEAR))
                .filter(s -> s.matches("(19|20)\\d{2}"));
        ParsedFilename parsed = FilenameParser.parse(row.originalFilename());
        String year = yearOverride.orElseGet(
                () -> parsed.year().isPresent() ? String.valueOf(parsed.year().getAsInt()) : "");

        String titleSource = titleOverride != null && !titleOverride.isBlank()
                ? titleOverride.trim()
                : row.inputParse()
                        .flatMap(parse -> parse.tokenValue(ScanInputRole.TITLE)
                                .or(() -> parse.tokenValue(ScanInputRole.SERIES)))
                        .filter(ScanPatternFormatter::hasLetterOrDigit)
                        .orElse(parsed.title().orElse(stem));

        String title = cleanMovieTitle(titleSource);
        if (!hasLetterOrDigit(title)) {
            // Tokens can still be garbage after a manual edit; fall back to the
            // parser's own title before declaring the movie untitled.
            String fallback = cleanMovieTitle(parsed.title().orElse(""));
            title = hasLetterOrDigit(fallback) ? fallback : "Untitled";
        }
        title = sanitizeSegment(title);
        return year.isBlank() ? title + extension : title + " (" + year + ")" + extension;
    }

    private static String cleanMovieTitle(String raw) {
        return raw
                .replaceAll("[._]+", " ")
                .replaceAll("[\\(\\[\\{].*?[\\)\\]\\}]", " ")
                .replaceAll("[\\(\\)\\[\\]\\{\\}]+", " ")
                .replaceAll("\\s*-\\s*", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean hasLetterOrDigit(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetterOrDigit(s.charAt(i))) return true;
        }
        return false;
    }

    private record Metadata(String series, String season, String episode, String title, String seasonEpisode) {
        static Metadata from(ScanRow row) {
            if (row.inputParse().isPresent()) {
                ScanInputParse parse = row.inputParse().orElseThrow();
                String order = parse.normalizedOrder().orElse(row.order().orElse("S??E??"));
                String season = parse.tokenValue(ScanInputRole.SEASON).orElse(seasonFromOrder(order));
                String episode = parse.tokenValue(ScanInputRole.EPISODE).orElse(episodeFromOrder(order));
                String series = parse.tokenValue(ScanInputRole.SERIES).orElse("Unknown Series");
                String title = parse.tokenValue(ScanInputRole.TITLE).orElse("Untitled");
                return new Metadata(series, season, episode, title, "S" + season + "E" + episode);
            }
            // No tokens on the row: read the name with the shared parser rather
            // than a second, weaker set of regexes.
            ParsedFilename parsed = FilenameParser.parse(row.originalFilename());
            String season = parsed.season().isPresent() ? twoDigits(String.valueOf(parsed.season().getAsInt())) : "??";
            String episode = parsed.firstEpisode().isPresent()
                    ? twoDigits(String.valueOf(parsed.firstEpisode().getAsInt()))
                    : "??";
            if ((season.equals("??") || episode.equals("??")) && row.order().isPresent()) {
                Matcher orderMatcher = SeasonEpisodePattern.STRICT.matcher(row.order().orElseThrow());
                if (orderMatcher.find()) {
                    season = twoDigits(orderMatcher.group(1));
                    episode = twoDigits(orderMatcher.group(2));
                }
            }
            String series = parsed.title().orElse("Unknown Series");
            String title = parsed.episodeTitle().orElse("Untitled");
            return new Metadata(series, season, episode, title, "S" + season + "E" + episode);
        }


        private static String twoDigits(String value) {
            try {
                return String.format(Locale.ROOT, "%02d", Integer.parseInt(value));
            } catch (NumberFormatException ex) {
                return "??";
            }
        }

        private static String seasonFromOrder(String order) {
            Matcher matcher = SeasonEpisodePattern.STRICT.matcher(order == null ? "" : order);
            return matcher.find() ? twoDigits(matcher.group(1)) : "??";
        }

        private static String episodeFromOrder(String order) {
            Matcher matcher = SeasonEpisodePattern.STRICT.matcher(order == null ? "" : order);
            return matcher.find() ? twoDigits(matcher.group(2)) : "??";
        }
    }
}
