package com.episort.ui.scan;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Generates preview names from lightweight scan-row metadata and a user/AI pattern. */
public final class ScanPatternFormatter {
    private static final Pattern SXXEXX = Pattern.compile("[Ss](\\d{1,2})[\\s._-]?[Ee](\\d{1,3})");
    private static final Pattern NXNN = Pattern.compile("(?<![A-Za-z0-9])(\\d{1,2})[xX](\\d{1,3})(?![A-Za-z0-9])");
    private static final Pattern ABSOLUTE = Pattern.compile("(?<!\\d)(\\d{2,4})(?!\\d)");
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)(19\\d{2}|20\\d{2})(?!\\d)");

    private ScanPatternFormatter() {
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
                .replace("{series}", series)
                .replace("{season}", metadata.season())
                .replace("{episode}", metadata.episode())
                .replace("{title}", metadata.title())
                .replace("{order}", row.order().orElse(metadata.seasonEpisode()));
        String extension = row.extension().isBlank() ? "" : "." + row.extension().toLowerCase(Locale.ROOT);
        return Optional.of(rendered + extension);
    }

    private static String formatMovie(ScanRow row, String titleOverride) {
        String baseName = row.originalFilename();
        int dot = baseName.lastIndexOf('.');
        String stem = dot > 0 ? baseName.substring(0, dot) : baseName;
        String extension = row.extension().isBlank() ? "" : "." + row.extension().toLowerCase(Locale.ROOT);

        // YEAR token (set by AI/user via setYear) overrides the regex scan of
        // the original filename — that is exactly what lets the chat assistant
        // correct a wrong year in the source name.
        Optional<String> yearOverride = row.inputParse()
                .flatMap(parse -> parse.tokenValue(ScanInputRole.YEAR))
                .filter(s -> s.matches("(19|20)\\d{2}"));
        String year = "";
        int titleEnd = stem.length();
        Matcher yearMatcher = YEAR.matcher(stem);
        while (yearMatcher.find()) {
            year = yearMatcher.group(1);
            titleEnd = yearMatcher.start();
        }
        if (yearOverride.isPresent()) {
            year = yearOverride.get();
        }

        String titleSource = titleOverride != null && !titleOverride.isBlank()
                ? titleOverride.trim()
                : row.inputParse()
                        .flatMap(parse -> parse.tokenValue(ScanInputRole.TITLE)
                                .or(() -> parse.tokenValue(ScanInputRole.SERIES)))
                        .filter(s -> hasLetterOrDigit(s))
                        .orElse(stem.substring(0, titleEnd));

        String title = cleanMovieTitle(titleSource);
        if (!hasLetterOrDigit(title)) {
            // Heuristic parser sometimes hands us garbage tokens (e.g. ")" when
            // a year/parenthesis got mis-tokenized). Fall back to the stem-up-
            // to-year before declaring the title untitled.
            String fallback = cleanMovieTitle(stem.substring(0, titleEnd));
            if (hasLetterOrDigit(fallback)) {
                title = fallback;
            } else {
                title = "Untitled";
            }
        }
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
            String baseName = stripExtension(row.originalFilename());
            String season = "??";
            String episode = "??";
            Matcher matcher = SXXEXX.matcher(baseName);
            if (!matcher.find()) {
                matcher = NXNN.matcher(baseName);
            }
            if (matcher.find(0)) {
                season = twoDigits(matcher.group(1));
                episode = twoDigits(matcher.group(2));
            } else if (row.order().isPresent()) {
                Matcher orderMatcher = SXXEXX.matcher(row.order().orElseThrow());
                if (orderMatcher.find()) {
                    season = twoDigits(orderMatcher.group(1));
                    episode = twoDigits(orderMatcher.group(2));
                }
            } else {
                Matcher absolute = ABSOLUTE.matcher(baseName);
                if (absolute.find()) {
                    season = "01";
                    episode = twoDigits(absolute.group(1));
                }
            }
            String series = cleanSegment(beforeEpisodeToken(baseName)).orElse("Unknown Series");
            String title = cleanSegment(afterEpisodeToken(baseName)).orElse("Untitled");
            return new Metadata(series, season, episode, title, "S" + season + "E" + episode);
        }

        private static String stripExtension(String filename) {
            int dot = filename.lastIndexOf('.');
            return dot > 0 ? filename.substring(0, dot) : filename;
        }

        private static String beforeEpisodeToken(String name) {
            Matcher matcher = SXXEXX.matcher(name);
            if (!matcher.find()) {
                matcher = NXNN.matcher(name);
            }
            return matcher.find(0) ? name.substring(0, matcher.start()) : name;
        }

        private static String afterEpisodeToken(String name) {
            Matcher matcher = SXXEXX.matcher(name);
            if (!matcher.find()) {
                matcher = NXNN.matcher(name);
            }
            return matcher.find(0) ? name.substring(matcher.end()) : "";
        }

        private static Optional<String> cleanSegment(String value) {
            String cleaned = value.replaceAll("[._]+", " ")
                    .replaceAll("\\s*-\\s*", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            return cleaned.isBlank() ? Optional.empty() : Optional.of(cleaned);
        }

        private static String twoDigits(String value) {
            try {
                return String.format(Locale.ROOT, "%02d", Integer.parseInt(value));
            } catch (NumberFormatException ex) {
                return "??";
            }
        }

        private static String seasonFromOrder(String order) {
            Matcher matcher = SXXEXX.matcher(order == null ? "" : order);
            return matcher.find() ? twoDigits(matcher.group(1)) : "??";
        }

        private static String episodeFromOrder(String order) {
            Matcher matcher = SXXEXX.matcher(order == null ? "" : order);
            return matcher.find() ? twoDigits(matcher.group(2)) : "??";
        }
    }
}
