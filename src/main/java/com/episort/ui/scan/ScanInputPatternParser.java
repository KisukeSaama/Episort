package com.episort.ui.scan;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure filename parser for source-side episode patterns. */
public final class ScanInputPatternParser {
    private static final Pattern SXXEXX = Pattern.compile("[Ss](\\d{1,2})[\\s._-]?[Ee](\\d{1,3})");
    private static final Pattern NXNN = Pattern.compile("(?<![A-Za-z0-9])(\\d{1,2})[xX](\\d{1,3})(?![A-Za-z0-9])");
    private static final Pattern ABSOLUTE = Pattern.compile("(?<!\\d)(\\d{2,4})(?!\\d)");

    private ScanInputPatternParser() {
    }

    public static Optional<ScanInputParse> parse(String filename) {
        if (filename == null || filename.isBlank()) {
            return Optional.empty();
        }
        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        Optional<ScanInputToken> extension = dot > 0 && dot < filename.length() - 1
                ? Optional.of(new ScanInputToken(ScanInputRole.EXTENSION,
                        filename.substring(dot + 1), filename.substring(dot + 1).toLowerCase(Locale.ROOT),
                        dot + 1, filename.length()))
                : Optional.empty();
        Optional<ScanInputParse> explicit = parseMarker(filename, base, extension, SXXEXX, "SxxExx");
        if (explicit.isPresent()) {
            return explicit;
        }
        explicit = parseMarker(filename, base, extension, NXNN, "NxNN");
        if (explicit.isPresent()) {
            return explicit;
        }
        Matcher absolute = ABSOLUTE.matcher(base);
        if (absolute.find()) {
            return Optional.of(build(filename, base, extension, absolute, "absolute", "01", absolute.group(1)));
        }
        return Optional.empty();
    }

    private static Optional<ScanInputParse> parseMarker(
            String filename,
            String base,
            Optional<ScanInputToken> extension,
            Pattern marker,
            String label) {
        Matcher matcher = marker.matcher(base);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(build(filename, base, extension, matcher, label, matcher.group(1), matcher.group(2)));
    }

    private static ScanInputParse build(
            String filename,
            String base,
            Optional<ScanInputToken> extension,
            Matcher marker,
            String label,
            String seasonRaw,
            String episodeRaw) {
        List<ScanInputToken> tokens = new ArrayList<>();
        cleanSegment(base.substring(0, marker.start())).ifPresent(value ->
                tokens.add(new ScanInputToken(ScanInputRole.SERIES,
                        base.substring(0, marker.start()), value, 0, marker.start())));
        tokens.add(new ScanInputToken(ScanInputRole.SEASON, seasonRaw, twoDigits(seasonRaw), marker.start(1), marker.end(1)));
        int episodeGroup = marker.groupCount() >= 2 ? 2 : 1;
        tokens.add(new ScanInputToken(ScanInputRole.EPISODE, episodeRaw, twoDigits(episodeRaw),
                marker.start(episodeGroup), marker.end(episodeGroup)));
        cleanSegment(base.substring(marker.end())).ifPresent(value ->
                tokens.add(new ScanInputToken(ScanInputRole.TITLE,
                        base.substring(marker.end()), value, marker.end(), base.length())));
        extension.ifPresent(tokens::add);
        String order = "S" + twoDigits(seasonRaw) + "E" + twoDigits(episodeRaw);
        return new ScanInputParse(label, tokens, Optional.of(order), OptionalDouble.of(0.9), ScanInputParseSource.HEURISTIC);
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
