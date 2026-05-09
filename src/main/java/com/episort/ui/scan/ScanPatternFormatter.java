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

    private ScanPatternFormatter() {
    }

    public static Optional<String> format(ScanRow row, String pattern) {
        if (row == null || pattern == null || pattern.isBlank()) {
            return Optional.empty();
        }
        Metadata metadata = Metadata.from(row);
        String rendered = pattern
                .replace("{series}", metadata.series())
                .replace("{season}", metadata.season())
                .replace("{episode}", metadata.episode())
                .replace("{title}", metadata.title())
                .replace("{order}", row.order().orElse(metadata.seasonEpisode()));
        String extension = row.extension().isBlank() ? "" : "." + row.extension().toLowerCase(Locale.ROOT);
        return Optional.of(rendered + extension);
    }

    private record Metadata(String series, String season, String episode, String title, String seasonEpisode) {
        static Metadata from(ScanRow row) {
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
    }
}
