package com.episort.filename;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a media file name (plus the folders around it) into structured data.
 *
 * <p>The parser is deliberately conservative and layered:
 *
 * <ol>
 *   <li>release noise is masked first ({@link ReleaseTagLexer}) so no number
 *       inside {@code 1080p}, {@code x264}, {@code DDP5.1} or {@code [A1B2C3D4]}
 *       can ever be mistaken for a season, an episode or a year;</li>
 *   <li>episode markers are tried strongest-first, each with its own confidence,
 *       and a marker is only accepted on characters no tag already owns;</li>
 *   <li>competing markers are compared instead of silently ignored — a
 *       disagreement lowers confidence and raises
 *       {@link ParseWarning#CONFLICTING_MARKERS};</li>
 *   <li>anything that remains a guess (absolute numbering, season taken from the
 *       folder, assumed season 1, date-based episodes) is reported as a warning
 *       so the review screen can require a human decision.</li>
 * </ol>
 *
 * <p>Nothing here throws on hostile input: empty names, extension-only names,
 * numbers far out of range and unterminated brackets all resolve to a low
 * confidence result carrying {@link ParseWarning#UNUSABLE_NAME} or an equivalent
 * flag.
 */
public final class FilenameParser {
    private static final int MIN_PLAUSIBLE_YEAR = 1900;
    private static final int MAX_SEASON = 99;
    private static final int MAX_EPISODE = 9999;
    private static final int MAX_MULTI_EPISODE_GAP = 24;
    /** Sequels rarely go past single digits; beyond that a number is an episode. */
    private static final int MAX_SEQUEL_NUMBER = 9;
    private static final Set<String> MEDIA_EXTENSIONS = Set.of(
            "mkv", "mp4", "avi", "m4v", "mov", "wmv", "ts", "m2ts", "mpg", "mpeg", "flv", "webm");

    private static final Pattern EXTENSION = Pattern.compile("^[A-Za-z][A-Za-z0-9]{0,4}$");
    private static final Pattern SXXEXX = Pattern.compile(
            "(?i)(?<![a-z0-9])s(\\d{1,4})[\\s._-]{0,2}(?:e|ep|x)(\\d{1,4})(?!\\d)");
    private static final Pattern MULTI_EPISODE = Pattern.compile(
            "(?i)\\G(?:[\\s._-]{0,3}e(?:p)?(\\d{1,4})|[-+&](\\d{1,4}))(?!\\d)");
    private static final Pattern VERBOSE = Pattern.compile(
            "(?i)(?<![a-z0-9])(?:season|saison|staffel|temporada)[\\s._-]{0,2}(\\d{1,3})"
                    + "[\\s._-]{0,3}(?:episode|episodio|épisode|ep|e)[\\s._-]{0,2}(\\d{1,4})(?!\\d)");
    private static final Pattern NXNN = Pattern.compile("(?<![A-Za-z0-9])(\\d{1,3})[xX](\\d{1,4})(?!\\d)");
    private static final Pattern DATE_YMD = Pattern.compile(
            "(?<!\\d)(19\\d{2}|20\\d{2})[.\\-_ ](\\d{1,2})[.\\-_ ](\\d{1,2})(?!\\d)");
    private static final Pattern DATE_DMY = Pattern.compile(
            "(?<!\\d)(\\d{1,2})[.\\-_ ](\\d{1,2})[.\\-_ ](19\\d{2}|20\\d{2})(?!\\d)");
    private static final Pattern SEASON_ONLY = Pattern.compile(
            "(?i)(?<![a-z0-9])(?:s|season|saison|staffel|temporada)[\\s._-]{0,2}(\\d{1,3})(?!\\d)");
    private static final Pattern EPISODE_ONLY = Pattern.compile(
            "(?i)(?<![a-z0-9])(?:e|ep|eps|episode|episodio|épisode)[\\s._-]{0,2}(\\d{1,4})(?!\\d)");
    private static final Pattern PART = Pattern.compile(
            "(?i)(?<![a-z0-9])(?:part|pt|partie)[\\s._-]{0,2}(\\d{1,2})(?!\\d)");
    private static final Pattern BARE_NUMBER = Pattern.compile("(?<!\\d)(\\d{1,4})(?!\\d)");
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)(\\d{4})(?!\\d)");
    private static final Pattern BRACKETED_YEAR = Pattern.compile("[\\[(](\\d{4})[\\])]");
    /** What may sit between a sequel number and the year that follows it. */
    private static final Pattern SEQUEL_GAP = Pattern.compile("[\\s._\\-\\[(]*");
    private static final Pattern SPECIALS_MARKER = Pattern.compile(
            "(?i)(?<![a-z0-9])(?:specials?|oav|ova|hors[\\s._-]?serie)(?![a-z0-9])");

    private FilenameParser() {
    }

    public static ParsedFilename parse(String filename) {
        return parse(filename, FolderContext.none());
    }

    public static ParsedFilename parse(String filename, FolderContext folders) {
        FolderContext context = folders == null ? FolderContext.none() : folders;
        String name = filename == null ? "" : filename;
        String extension = extensionOf(name);
        String rawBase = extension.isEmpty() ? name : name.substring(0, name.length() - extension.length());
        String base = stripResidualMediaExtension(rawBase.replaceAll("\\p{Cntrl}", " "));

        ParsedFilename.Builder builder = ParsedFilename.builder(name, base, extension);
        if (base.isBlank() || name.startsWith(".")) {
            return builder.patternLabel("Unusable")
                    .kind(MediaKindHint.UNKNOWN)
                    .warn(ParseWarning.UNUSABLE_NAME)
                    .confidence(0.0)
                    .build();
        }

        List<ReleaseTag> tags = ReleaseTagLexer.scan(base);
        boolean[] mask = ReleaseTagLexer.mask(base.length(), tags);
        builder.tags(tags);
        applyTags(builder, tags);
        for (ReleaseTag tag : tags) {
            builder.span(new FilenameSpan(SpanRole.TAG, tag.raw(), tag.normalized(), tag.start(), tag.end()));
        }

        Optional<Marker> marker = detectMarker(base, mask, context);
        marker.ifPresent(value -> reportConflicts(base, mask, value, builder));

        Optional<int[]> year = resolveYear(base, mask, marker, builder);
        applyMarker(builder, base, marker, context);
        applyTitles(builder, base, mask, marker, year.orElse(null), context);
        applyKindAndConfidence(builder, marker, year.map(range -> range[0]).orElse(null));

        if (!extension.isEmpty()) {
            builder.span(new FilenameSpan(SpanRole.EXTENSION, extension.substring(1),
                    extension.substring(1).toLowerCase(Locale.ROOT), base.length() + 1, name.length()));
        }
        return builder.build();
    }

    // ---------------------------------------------------------------- markers

    private static Optional<Marker> detectMarker(String base, boolean[] mask, FolderContext context) {
        return firstOf(
                () -> detectSxxExx(base, mask),
                () -> detectVerbose(base, mask),
                () -> detectNxNN(base, mask),
                () -> detectAirDate(base, mask),
                () -> detectSeparateSeasonEpisode(base, mask),
                () -> detectEpisodeOnly(base, mask),
                () -> detectPart(base, mask),
                () -> detectAbsolute(base, mask, context));
    }

    @SafeVarargs
    private static Optional<Marker> firstOf(Supplier<Optional<Marker>>... detectors) {
        for (Supplier<Optional<Marker>> detector : detectors) {
            Optional<Marker> found = detector.get();
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private static Optional<Marker> detectSxxExx(String base, boolean[] mask) {
        Matcher matcher = freeMatch(SXXEXX, base, mask);
        if (matcher == null) {
            return Optional.empty();
        }
        List<Integer> episodes = new ArrayList<>();
        episodes.add(Integer.parseInt(matcher.group(2)));
        List<FilenameSpan> spans = new ArrayList<>();
        spans.add(span(SpanRole.SEASON, base, matcher.start(1), matcher.end(1)));
        spans.add(span(SpanRole.EPISODE, base, matcher.start(2), matcher.end(2)));
        int end = collectMultiEpisodes(base, mask, matcher.end(), episodes, spans);
        return Optional.of(new Marker("SxxExx", matcher.start(), end,
                Integer.parseInt(matcher.group(1)), episodes, null, null, 0.95, spans));
    }

    /**
     * Extends {@code S01E01} into {@code S01E01E02} / {@code S01E01-E02} /
     * {@code S01E01-02}. Continuations must be contiguous, ascending, close
     * together and untouched by release tags — otherwise a trailing
     * {@code -1080p} or {@code - 2019} would become a second episode.
     */
    private static int collectMultiEpisodes(
            String base, boolean[] mask, int from, List<Integer> episodes, List<FilenameSpan> spans) {
        Matcher matcher = MULTI_EPISODE.matcher(base);
        matcher.region(from, base.length());
        matcher.useAnchoringBounds(false);
        matcher.useTransparentBounds(true);
        int end = from;
        while (matcher.find()) {
            String raw = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            int group = matcher.group(1) != null ? 1 : 2;
            if (!isFree(mask, matcher.start(), matcher.end())) {
                break;
            }
            int episode = Integer.parseInt(raw);
            int previous = episodes.get(episodes.size() - 1);
            if (episode <= previous || episode - previous > MAX_MULTI_EPISODE_GAP || episode > MAX_EPISODE) {
                break;
            }
            episodes.add(episode);
            spans.add(span(SpanRole.EPISODE_END, base, matcher.start(group), matcher.end(group)));
            end = matcher.end();
        }
        return end;
    }

    private static Optional<Marker> detectVerbose(String base, boolean[] mask) {
        Matcher matcher = freeMatch(VERBOSE, base, mask);
        if (matcher == null) {
            return Optional.empty();
        }
        return Optional.of(new Marker("Season/Episode", matcher.start(), matcher.end(),
                Integer.parseInt(matcher.group(1)),
                List.of(Integer.parseInt(matcher.group(2))),
                null, null, 0.93,
                List.of(span(SpanRole.SEASON, base, matcher.start(1), matcher.end(1)),
                        span(SpanRole.EPISODE, base, matcher.start(2), matcher.end(2)))));
    }

    private static Optional<Marker> detectNxNN(String base, boolean[] mask) {
        Matcher matcher = freeMatch(NXNN, base, mask);
        if (matcher == null) {
            return Optional.empty();
        }
        return Optional.of(new Marker("NxNN", matcher.start(), matcher.end(),
                Integer.parseInt(matcher.group(1)),
                List.of(Integer.parseInt(matcher.group(2))),
                null, null, 0.9,
                List.of(span(SpanRole.SEASON, base, matcher.start(1), matcher.end(1)),
                        span(SpanRole.EPISODE, base, matcher.start(2), matcher.end(2)))));
    }

    private static Optional<Marker> detectAirDate(String base, boolean[] mask) {
        Matcher ymd = freeMatch(DATE_YMD, base, mask);
        if (ymd != null) {
            Optional<LocalDate> date = date(ymd.group(1), ymd.group(2), ymd.group(3));
            if (date.isPresent()) {
                return Optional.of(dateMarker(base, ymd, date.orElseThrow()));
            }
        }
        Matcher dmy = freeMatch(DATE_DMY, base, mask);
        if (dmy != null) {
            Optional<LocalDate> date = date(dmy.group(3), dmy.group(2), dmy.group(1));
            if (date.isPresent()) {
                return Optional.of(dateMarker(base, dmy, date.orElseThrow()));
            }
        }
        return Optional.empty();
    }

    private static Marker dateMarker(String base, Matcher matcher, LocalDate date) {
        return new Marker("Air date", matcher.start(), matcher.end(), null, List.of(), date, null, 0.88,
                List.of(span(SpanRole.AIR_DATE, base, matcher.start(), matcher.end())));
    }

    private static Optional<Marker> detectSeparateSeasonEpisode(String base, boolean[] mask) {
        Matcher season = freeMatch(SEASON_ONLY, base, mask);
        if (season == null) {
            return Optional.empty();
        }
        Matcher episode = EPISODE_ONLY.matcher(base);
        if (!episode.find(season.end())) {
            return Optional.empty();
        }
        do {
            if (isFree(mask, episode.start(), episode.end())) {
                return Optional.of(new Marker("Sxx + Exx", season.start(), episode.end(),
                        Integer.parseInt(season.group(1)),
                        List.of(Integer.parseInt(episode.group(1))),
                        null, null, 0.8,
                        List.of(span(SpanRole.SEASON, base, season.start(1), season.end(1)),
                                span(SpanRole.EPISODE, base, episode.start(1), episode.end(1)))));
            }
        } while (episode.find());
        return Optional.empty();
    }

    private static Optional<Marker> detectEpisodeOnly(String base, boolean[] mask) {
        Matcher matcher = freeMatch(EPISODE_ONLY, base, mask);
        if (matcher == null) {
            return Optional.empty();
        }
        return Optional.of(new Marker("Exx", matcher.start(), matcher.end(), null,
                List.of(Integer.parseInt(matcher.group(1))), null, null, 0.7,
                List.of(span(SpanRole.EPISODE, base, matcher.start(1), matcher.end(1)))));
    }

    /**
     * Bare numbering, the most dangerous case: {@code [Grp] Show - 12 [1080p]}
     * is an episode, {@code 1917 (2019)} is a title and {@code 2012} can be
     * both. Only numbers that survive tag masking, year plausibility and a
     * leading-position check are accepted, and always with a warning.
     */
    private static Optional<Marker> detectAbsolute(String base, boolean[] mask, FolderContext context) {
        Matcher matcher = BARE_NUMBER.matcher(base);
        Matcher chosen = null;
        while (matcher.find()) {
            if (!isFree(mask, matcher.start(), matcher.end())) {
                continue;
            }
            if (matcher.start() == 0) {
                // A name that opens on a number is a title far more often than
                // it is an episode ("24", "1917", "2012").
                continue;
            }
            String raw = matcher.group(1);
            int value = Integer.parseInt(raw);
            // Four-digit numbers in the 19xx/20xx range are years, never episode
            // numbers — including implausible ones such as "Blade Runner 2049".
            if (raw.length() == 4 && value >= MIN_PLAUSIBLE_YEAR) {
                continue;
            }
            if (value == 0) {
                continue;
            }
            // Keep scanning: the last usable number wins, which is where the
            // episode sits in "Show 2 - 05" and in "[Grp] Show - 12".
            chosen = matcherAt(BARE_NUMBER, base, matcher.start());
        }
        if (chosen == null) {
            return Optional.empty();
        }
        int value = Integer.parseInt(chosen.group(1));
        if (isSequelNumber(base, mask, chosen, value, context)) {
            return Optional.empty();
        }
        OptionalInt folderSeason = context.season();
        if (folderSeason.isPresent() && chosen.group(1).length() >= 3) {
            int season = folderSeason.getAsInt();
            int prefixDigits = String.valueOf(season).length();
            String raw = chosen.group(1);
            if (raw.length() > prefixDigits && Integer.parseInt(raw.substring(0, prefixDigits)) == season) {
                int episode = Integer.parseInt(raw.substring(prefixDigits));
                return Optional.of(new Marker("SEE (compact)", chosen.start(), chosen.end(), season,
                        List.of(episode), null, null, 0.6,
                        List.of(span(SpanRole.EPISODE, base, chosen.start(), chosen.end())),
                        List.of(ParseWarning.SEASON_FROM_FOLDER)));
            }
        }
        return Optional.of(new Marker("Absolute", chosen.start(), chosen.end(), null, List.of(value), null, value,
                0.5,
                List.of(span(SpanRole.ABSOLUTE, base, chosen.start(), chosen.end())),
                List.of(ParseWarning.AMBIGUOUS_ABSOLUTE_NUMBER)));
    }

    /**
     * A small number glued to the end of the title is an instalment number, not
     * an episode: "Le Flic de Hong Kong 2 (1985)" is a film whose title ends on
     * a 2. Reading it as episode 2 stripped the number from the title, so a film
     * and its sequel parsed to the same title, landed in one group, and the
     * review screen offered a single TVDB identity for both.
     *
     * <p>Two shapes qualify, both deliberately narrow so that absolute anime
     * numbering keeps working: the number immediately precedes the year
     * ("Le.Flic.de.Hong.Kong.2.1985"), or it is a lone digit attached to the
     * title by a plain space and nothing but release tags follows it
     * ("Le Flic de Hong Kong 2"). A separator before the number
     * ("Naruto Shippuden - 001"), zero padding, or text after it keeps the
     * absolute-episode reading, and a folder that states a season always wins.
     */
    private static boolean isSequelNumber(
            String base, boolean[] mask, Matcher chosen, int value, FolderContext context) {
        if (value > MAX_SEQUEL_NUMBER || context.carriesSeason()) {
            return false;
        }
        return precedesYear(base, mask, chosen.end())
                || closesTitleOnASingleDigit(base, mask, chosen.start(), chosen.end());
    }

    private static boolean precedesYear(String base, boolean[] mask, int numberEnd) {
        Matcher year = YEAR.matcher(base);
        while (year.find()) {
            if (year.start() < numberEnd || !isFree(mask, year.start(), year.end())) {
                continue;
            }
            if (!isPlausibleYear(Integer.parseInt(year.group(1)))) {
                continue;
            }
            if (SEQUEL_GAP.matcher(base.substring(numberEnd, year.start())).matches()) {
                return true;
            }
        }
        return false;
    }

    private static boolean closesTitleOnASingleDigit(String base, boolean[] mask, int start, int end) {
        if (end - start != 1 || start < 2 || base.charAt(start - 1) != ' ') {
            return false;
        }
        // " - 5" is an episode, "Kong 2" is a title: what precedes the space decides.
        if (!Character.isLetterOrDigit(base.charAt(start - 2))) {
            return false;
        }
        return SEQUEL_GAP.matcher(base.substring(end, firstTagAfter(mask, end, base.length()))).matches();
    }

    private static Optional<Marker> detectPart(String base, boolean[] mask) {
        Matcher matcher = freeMatch(PART, base, mask);
        if (matcher == null) {
            return Optional.empty();
        }
        return Optional.of(new Marker("Part", matcher.start(), matcher.end(), null,
                List.of(Integer.parseInt(matcher.group(1))), null, null, 0.4,
                List.of(span(SpanRole.EPISODE, base, matcher.start(1), matcher.end(1))),
                List.of(ParseWarning.MULTI_PART_FILE)));
    }

    /**
     * Looks for a second, differently-shaped marker that disagrees with the one
     * that was chosen. Silence here would be the worst outcome: the file would
     * look perfectly parsed while pointing at the wrong episode.
     */
    private static void reportConflicts(
            String base, boolean[] mask, Marker chosen, ParsedFilename.Builder builder) {
        if (chosen.season() == null || chosen.episodes().isEmpty()) {
            return;
        }
        List<Optional<Marker>> others = List.of(
                detectSxxExx(base, mask), detectVerbose(base, mask), detectNxNN(base, mask));
        for (Optional<Marker> other : others) {
            if (other.isEmpty()) {
                continue;
            }
            Marker candidate = other.orElseThrow();
            if (candidate.start() == chosen.start() || candidate.label().equals(chosen.label())) {
                continue;
            }
            boolean sameSeason = candidate.season() != null && candidate.season().equals(chosen.season());
            boolean sameEpisode = candidate.episodes().equals(chosen.episodes());
            if (!sameSeason || !sameEpisode) {
                builder.warn(ParseWarning.CONFLICTING_MARKERS);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ year

    /** Returns the selected year as {@code {value, start, end}}, if any. */
    private static Optional<int[]> resolveYear(
            String base, boolean[] mask, Optional<Marker> marker, ParsedFilename.Builder builder) {
        List<int[]> candidates = new ArrayList<>();
        Matcher matcher = YEAR.matcher(base);
        boolean implausibleSeen = false;
        while (matcher.find()) {
            if (!isFree(mask, matcher.start(), matcher.end()) || overlapsMarker(marker, matcher.start(), matcher.end())) {
                continue;
            }
            int value = Integer.parseInt(matcher.group(1));
            if (!isPlausibleYear(value)) {
                implausibleSeen = true;
                continue;
            }
            candidates.add(new int[] {value, matcher.start(), matcher.end()});
        }
        if (candidates.isEmpty()) {
            if (implausibleSeen) {
                builder.warn(ParseWarning.IMPLAUSIBLE_YEAR);
            }
            return Optional.empty();
        }
        if (candidates.size() > 1) {
            builder.warn(ParseWarning.AMBIGUOUS_YEAR);
        }
        int[] selected = selectYear(base, mask, marker, candidates);
        builder.year(selected[0]);
        builder.span(new FilenameSpan(SpanRole.YEAR, base.substring(selected[1], selected[2]),
                String.valueOf(selected[0]), selected[1], selected[2]));
        return Optional.of(selected);
    }

    private static int[] selectYear(String base, boolean[] mask, Optional<Marker> marker, List<int[]> candidates) {
        // A bracketed year is an explicit statement of intent and always wins.
        Matcher bracketed = BRACKETED_YEAR.matcher(base);
        int[] bracketedPick = null;
        while (bracketed.find()) {
            int value = Integer.parseInt(bracketed.group(1));
            if (isPlausibleYear(value) && isFree(mask, bracketed.start(), bracketed.end())) {
                bracketedPick = new int[] {value, bracketed.start(1), bracketed.end(1)};
            }
        }
        if (bracketedPick != null) {
            return bracketedPick;
        }
        if (marker.isPresent()) {
            int markerStart = marker.orElseThrow().start();
            for (int index = candidates.size() - 1; index >= 0; index--) {
                if (candidates.get(index)[2] <= markerStart) {
                    return candidates.get(index);
                }
            }
        }
        // Scene convention: "<title> <year>" — the trailing year is the real one.
        return candidates.get(candidates.size() - 1);
    }

    private static boolean isPlausibleYear(int value) {
        return value >= MIN_PLAUSIBLE_YEAR && value <= Year.now().getValue() + 2;
    }

    // ----------------------------------------------------------- application

    private static void applyMarker(
            ParsedFilename.Builder builder, String base, Optional<Marker> marker, FolderContext context) {
        if (marker.isEmpty()) {
            return;
        }
        Marker value = marker.orElseThrow();
        value.warnings().forEach(builder::warn);
        value.spans().forEach(builder::span);
        builder.patternLabel(value.label());

        if (value.date() != null) {
            builder.airDate(value.date()).warn(ParseWarning.DATE_BASED_EPISODE);
            return;
        }
        Integer season = value.season();
        if (season == null) {
            OptionalInt folderSeason = context.season();
            if (folderSeason.isPresent()) {
                season = folderSeason.getAsInt();
                builder.warn(ParseWarning.SEASON_FROM_FOLDER);
            } else if (!value.episodes().isEmpty()) {
                // Season 1 is the least-surprising assumption, but it stays an
                // assumption: the warning is what keeps it reviewable, and the
                // absolute number is preserved for TVDB absolute ordering.
                season = 1;
                builder.warn(ParseWarning.ASSUMED_SEASON);
            }
        }
        if (season != null) {
            if (season < 0 || (season > MAX_SEASON && !isPlausibleYear(season))) {
                builder.warn(ParseWarning.OUT_OF_RANGE_NUMBER);
            } else {
                builder.season(season);
                if (season == 0) {
                    builder.warn(ParseWarning.SPECIAL_EPISODE);
                }
            }
        }
        List<Integer> episodes = value.episodes().stream().filter(episode -> episode <= MAX_EPISODE).toList();
        if (episodes.size() != value.episodes().size()) {
            builder.warn(ParseWarning.OUT_OF_RANGE_NUMBER);
        }
        builder.episodes(episodes);
        if (episodes.size() > 1) {
            builder.warn(ParseWarning.MULTI_EPISODE);
        }
        if (value.absolute() != null) {
            builder.absoluteNumber(value.absolute());
        }
        if (SPECIALS_MARKER.matcher(base).find() || context.specials()) {
            builder.warn(ParseWarning.SPECIAL_EPISODE);
        }
    }

    private static void applyTitles(
            ParsedFilename.Builder builder,
            String base,
            boolean[] mask,
            Optional<Marker> marker,
            int[] year,
            FolderContext context) {
        int titleEnd = marker.map(Marker::start).orElse(base.length());
        if (marker.isEmpty() && year != null) {
            titleEnd = year[1];
        }
        // The year is metadata, not part of the title: "Show 2019 S01E02" is
        // the series "Show", not the series "Show 2019".
        boolean[] titleMask = mask;
        if (year != null && year[1] < titleEnd) {
            titleMask = mask.clone();
            for (int index = year[1]; index < Math.min(year[2], titleMask.length); index++) {
                titleMask[index] = true;
            }
        }
        String title = cleanRange(base, 0, titleEnd, titleMask);
        // A folder that states a season usually states the series too, and it
        // states it in full: files inside release folders are routinely
        // abbreviated ("sgi-hkyu01.mkv" inside "Haikyu.S01.1080p.BluRay-GRP").
        // It only gets to speak for the file when the two titles can be the
        // same show — a folder holding two series would otherwise rename one
        // of them after the other.
        if (marker.isPresent() && context.carriesSeason()) {
            Optional<String> folderTitle = context.seriesTitle();
            if (folderTitle.isPresent()) {
                if (context.agreesWith(title)) {
                    title = folderTitle.get();
                } else {
                    builder.warn(ParseWarning.FOLDER_TITLE_MISMATCH);
                }
            }
        }
        if (title.isBlank()) {
            title = context.seriesTitle().orElse("");
            if (title.isBlank()) {
                builder.warn(ParseWarning.MISSING_TITLE);
            }
        } else if (title.chars().noneMatch(Character::isLetter)) {
            builder.warn(ParseWarning.NUMERIC_TITLE);
        }
        if (!title.isBlank()) {
            builder.title(title);
            builder.span(new FilenameSpan(SpanRole.SERIES, base.substring(0, Math.min(titleEnd, base.length())),
                    title, 0, Math.min(titleEnd, base.length())));
        }
        marker.ifPresent(value -> applyEpisodeTitle(builder, base, mask, value));
    }

    private static void applyEpisodeTitle(
            ParsedFilename.Builder builder, String base, boolean[] mask, Marker marker) {
        int start = Math.min(marker.end(), base.length());
        int end = firstTagAfter(mask, start, base.length());
        String episodeTitle = cleanRange(base, start, end, mask);
        if (episodeTitle.isBlank() || episodeTitle.chars().noneMatch(Character::isLetter)) {
            builder.warn(ParseWarning.NO_EPISODE_TITLE);
            return;
        }
        builder.episodeTitle(episodeTitle);
        builder.span(new FilenameSpan(SpanRole.TITLE, base.substring(start, end), episodeTitle, start, end));
    }

    private static void applyKindAndConfidence(
            ParsedFilename.Builder builder, Optional<Marker> marker, Integer year) {
        ParsedFilename snapshot = builder.build();
        MediaKindHint kind;
        double confidence;
        if (snapshot.tags().stream().anyMatch(tag -> tag.kind() == TagKind.EXTRA)) {
            builder.warn(ParseWarning.EXTRA_MATERIAL);
            kind = MediaKindHint.EXTRA;
            confidence = 0.5;
        } else if (marker.isPresent() && (snapshot.hasEpisode() || snapshot.airDate().isPresent())) {
            kind = snapshot.season().isPresent() && snapshot.season().getAsInt() == 0
                    ? MediaKindHint.SPECIAL
                    : MediaKindHint.SERIES;
            confidence = marker.orElseThrow().confidence();
        } else if (year != null) {
            kind = MediaKindHint.MOVIE;
            confidence = 0.85;
            builder.patternLabel("Title.Year");
        } else {
            kind = MediaKindHint.UNKNOWN;
            confidence = snapshot.title().isPresent() ? 0.35 : 0.1;
            builder.patternLabel(snapshot.patternLabel().isBlank() ? "Title only" : snapshot.patternLabel());
        }
        if (snapshot.hasWarning(ParseWarning.SPECIAL_EPISODE) && kind == MediaKindHint.SERIES) {
            kind = MediaKindHint.SPECIAL;
        }
        confidence -= penalty(snapshot);
        builder.kind(kind).confidence(Math.max(0.05, confidence));
    }

    private static double penalty(ParsedFilename snapshot) {
        double penalty = 0.0;
        if (snapshot.hasWarning(ParseWarning.CONFLICTING_MARKERS)) {
            penalty += 0.25;
        }
        if (snapshot.hasWarning(ParseWarning.MISSING_TITLE)) {
            penalty += 0.15;
        }
        if (snapshot.hasWarning(ParseWarning.ASSUMED_SEASON)) {
            penalty += 0.10;
        }
        if (snapshot.hasWarning(ParseWarning.SEASON_FROM_FOLDER)) {
            penalty += 0.05;
        }
        if (snapshot.hasWarning(ParseWarning.MULTI_EPISODE)) {
            penalty += 0.05;
        }
        if (snapshot.hasWarning(ParseWarning.AMBIGUOUS_YEAR)) {
            penalty += 0.05;
        }
        if (snapshot.hasWarning(ParseWarning.OUT_OF_RANGE_NUMBER)) {
            penalty += 0.20;
        }
        return penalty;
    }

    private static void applyTags(ParsedFilename.Builder builder, List<ReleaseTag> tags) {
        for (ReleaseTag tag : tags) {
            switch (tag.kind()) {
                case RESOLUTION -> builder.quality(tag.raw());
                case SOURCE -> builder.source(tag.raw());
                case CODEC -> builder.codec(tag.raw());
                case AUDIO -> builder.audio(tag.raw());
                case LANGUAGE -> builder.language(tag.raw());
                case EDITION -> builder.edition(tag.raw());
                case GROUP -> builder.releaseGroup(stripDecorations(tag.raw()));
                case PART -> builder.partNumber(digits(tag.raw()));
                default -> {
                    // Checksums, websites, HDR flags and extras carry no field of their own.
                }
            }
        }
    }

    // --------------------------------------------------------------- helpers

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot <= 0 || dot == filename.length() - 1) {
            return "";
        }
        String candidate = filename.substring(dot + 1);
        return EXTENSION.matcher(candidate).matches() ? "." + candidate.toLowerCase(Locale.ROOT) : "";
    }

    /**
     * Removes a media extension left inside the base name by a download suffix:
     * {@code Show.S01E02.mkv.part} must not end up with "mkv" as its episode
     * title. The real extension ({@code .part}) is still reported as such.
     */
    private static String stripResidualMediaExtension(String base) {
        String current = base;
        for (int pass = 0; pass < 2; pass++) {
            int dot = current.lastIndexOf('.');
            if (dot <= 0) {
                return current;
            }
            String candidate = current.substring(dot + 1).toLowerCase(Locale.ROOT);
            if (!MEDIA_EXTENSIONS.contains(candidate)) {
                return current;
            }
            current = current.substring(0, dot);
        }
        return current;
    }

    private static Matcher freeMatch(Pattern pattern, String base, boolean[] mask) {
        Matcher matcher = pattern.matcher(base);
        while (matcher.find()) {
            if (isFree(mask, matcher.start(), matcher.end())) {
                return matcher;
            }
        }
        return null;
    }

    /** A detached matcher positioned on the match that starts at {@code from}. */
    private static Matcher matcherAt(Pattern pattern, String base, int from) {
        Matcher matcher = pattern.matcher(base);
        matcher.find(from);
        return matcher;
    }

    private static boolean isFree(boolean[] mask, int start, int end) {
        for (int index = start; index < Math.min(end, mask.length); index++) {
            if (mask[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean overlapsMarker(Optional<Marker> marker, int start, int end) {
        return marker.filter(value -> start < value.end() && end > value.start()).isPresent();
    }

    private static int firstTagAfter(boolean[] mask, int from, int fallback) {
        for (int index = Math.max(from, 0); index < mask.length; index++) {
            if (mask[index]) {
                return index;
            }
        }
        return fallback;
    }

    /**
     * Renders a range as human-readable text: masked characters disappear,
     * separators become spaces, empty bracket pairs and dangling punctuation are
     * dropped. Hyphens inside words survive ("Spider-Man"), hyphens used as
     * separators do not.
     */
    static String cleanRange(String base, int start, int end, boolean[] mask) {
        int safeStart = Math.max(0, Math.min(start, base.length()));
        int safeEnd = Math.max(safeStart, Math.min(end, base.length()));
        StringBuilder builder = new StringBuilder(safeEnd - safeStart);
        for (int index = safeStart; index < safeEnd; index++) {
            builder.append(index < mask.length && mask[index] ? ' ' : base.charAt(index));
        }
        return normalizeText(builder.toString());
    }

    static String normalizeText(String value) {
        String cleaned = value
                .replaceAll("[\\[({][^\\])}]*[\\])}]", " ")
                .replaceAll("[\\[\\](){}]", " ")
                .replaceAll("[._]+", " ")
                .replaceAll("\\s+-+\\s+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        cleaned = cleaned.replaceAll("^[-–—,;:]+", "").replaceAll("[-–—,;:]+$", "").trim();
        return cleaned;
    }

    private static String stripDecorations(String value) {
        return value.replaceAll("^[\\[({-]+", "").replaceAll("[\\])}]+$", "").trim();
    }

    private static Integer digits(String value) {
        Matcher matcher = Pattern.compile("(\\d{1,2})").matcher(value);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }

    private static Optional<LocalDate> date(String year, String month, String day) {
        try {
            LocalDate date = LocalDate.of(
                    Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
            return date.getYear() <= Year.now().getValue() + 1 ? Optional.of(date) : Optional.empty();
        } catch (DateTimeException | NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static FilenameSpan span(SpanRole role, String base, int start, int end) {
        String raw = base.substring(Math.max(0, start), Math.min(end, base.length()));
        return new FilenameSpan(role, raw, raw, start, end);
    }

    private record Marker(
            String label,
            int start,
            int end,
            Integer season,
            List<Integer> episodes,
            LocalDate date,
            Integer absolute,
            double confidence,
            List<FilenameSpan> spans,
            List<ParseWarning> warnings) {

        Marker(String label, int start, int end, Integer season, List<Integer> episodes,
               LocalDate date, Integer absolute, double confidence, List<FilenameSpan> spans) {
            this(label, start, end, season, episodes, date, absolute, confidence, spans, List.of());
        }
    }
}
