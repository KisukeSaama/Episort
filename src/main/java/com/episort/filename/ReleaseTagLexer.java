package com.episort.filename;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the release "noise" in a file base name — resolution, source, codec,
 * audio, language, edition, checksum, website, release group, part markers and
 * extras — and reports where each one sits.
 *
 * <p>This exists because every classic parsing failure of this application came
 * from noise being read as data: {@code 720p} becoming episode 720,
 * {@code 2160p} becoming the year 2160, {@code x264} becoming episode 264, and
 * {@code 1080p.BluRay.x264-GRP} becoming the episode title. Masking the noise
 * <em>first</em> and only then looking for numbers removes that whole class of
 * bugs instead of patching it case by case.
 *
 * <p>Rules are ordered from most specific to least specific and applied
 * non-overlapping: the first rule that claims a range owns it. Anything that
 * could plausibly be part of a real title (a bare {@code HD}, a bare {@code VO},
 * a two-letter language code) is deliberately <em>not</em> a rule — a missed tag
 * costs a slightly noisier title, a false tag destroys a title.
 */
public final class ReleaseTagLexer {
    private static final List<Rule> RULES = List.of(
            // Checksums and site stamps first: they are unambiguous and often wrap other tokens.
            new Rule(TagKind.CHECKSUM, Pattern.compile("\\[[0-9A-Fa-f]{8}\\]")),
            new Rule(TagKind.CHECKSUM, Pattern.compile("(?i)\\{(?:tmdb|imdb|tvdb)-[a-z0-9]+\\}")),
            new Rule(TagKind.WEBSITE, Pattern.compile(
                    "(?i)(?:\\[|\\()?www\\.[a-z0-9-]+\\.[a-z]{2,6}(?:\\]|\\))?(?:\\s*-\\s*)?")),
            new Rule(TagKind.WEBSITE, Pattern.compile(
                    "(?i)\\[[a-z0-9-]+\\.(?:com|net|org|info|tv|me|cc|io|to|xyz)\\]")),

            new Rule(TagKind.RESOLUTION, Pattern.compile(
                    "(?i)(?<![a-z0-9])(?:4320|2160|1440|1080|720|576|480|360)[pi](?![a-z0-9])")),
            new Rule(TagKind.RESOLUTION, Pattern.compile(
                    "(?i)(?<![a-z0-9])(?:3840x2160|1920x1080|1280x720|720x576|4k|8k|uhd|fhd)(?![a-z0-9])")),

            new Rule(TagKind.SOURCE, Pattern.compile(
                    "(?i)(?<![a-z0-9])(?:blu-?ray|bd-?remux|bd-?rip|br-?rip|bdmv|remux|web-?dl|web-?rip"
                            + "|webhd|hd-?tv|pd-?tv|dvd-?rip|dvd-?scr|dvd-?[59r]|hd-?rip|hd-?dvd|vhs-?rip"
                            + "|tv-?rip|sat-?rip|dvb-?rip|screener|cam-?rip|telesync|telecine"
                            + "|amzn|dsnp|hmax|atvp|nf-?web|itunes|netflix)(?![a-z0-9])")),

            new Rule(TagKind.CODEC, Pattern.compile(
                    "(?i)(?<![a-z0-9])(?:[xh]\\.?26[456]|hevc|avc1?|xvid|divx|av1|vp9|vc-?1"
                            + "|mpeg-?[1245]|hi10p?|10-?bits?|8-?bits?)(?![a-z0-9])")),

            new Rule(TagKind.AUDIO, Pattern.compile(
                    "(?i)(?<![a-z0-9])(?:dts-?hd(?:-?ma)?|dts-?x|dts|true-?hd|atmos|e-?ac-?3|ac-?3"
                            + "|dd\\+?p?[257]\\.[01]|ddp?[257]\\.[01]|dd\\+|ddp|aac(?:2\\.0)?|flac|opus"
                            + "|mp3|lpcm|pcm|[257]\\.[01]ch|(?<=[\\s._-])[257]\\.[01](?=[\\s._-]|$))(?![a-z0-9])")),

            new Rule(TagKind.DYNAMIC_RANGE, Pattern.compile(
                    "(?i)(?<![a-z0-9])(?:hdr10\\+?|hdr|dolby[\\s._-]?vision|dovi|hlg|sdr|imax)(?![a-z0-9])")),

            new Rule(TagKind.EDITION, Pattern.compile(
                    "(?i)(?<![a-z0-9])(?:extended(?:[\\s._-]?cut)?|uncut|unrated|remastered|restored"
                            + "|director'?s?[\\s._-]?cut|final[\\s._-]?cut|theatrical(?:[\\s._-]?cut)?"
                            + "|special[\\s._-]?edition|collector'?s?[\\s._-]?edition|criterion"
                            + "|anniversary[\\s._-]?edition|proper|repack|rerip|internal|limited"
                            + "|integrale|integral)(?![a-z0-9])")),

            new Rule(TagKind.LANGUAGE, Pattern.compile(
                    "(?i)(?<![a-z0-9])(?:multi(?:lang|[\\s._-]?vf[a-z]?)?|vostfr|vost|subfrench|subforced"
                            + "|truefrench|french|vff|vfq|vfi|vf2|vfstfr|english|japanese|spanish|german"
                            + "|italian|korean|dual[\\s._-]?audio|dubbed|subbed)(?![a-z0-9])")),

            new Rule(TagKind.EXTRA, Pattern.compile(
                    "(?i)(?<![a-z0-9])(?:sample|trailer|teaser|featurette|extras?|bonus|making[\\s._-]?of"
                            + "|behind[\\s._-]?the[\\s._-]?scenes|deleted[\\s._-]?scenes?|interview"
                            + "|nc-?op\\d*|nc-?ed\\d*|preview|promo)(?![a-z0-9])")),

            new Rule(TagKind.PART, Pattern.compile(
                    "(?i)(?<![a-z0-9])(?:cd|disc|disk|dvd)[\\s._-]?([1-9]\\d?)(?![a-z0-9])")));

    /** {@code -GROUP} / {@code [GROUP]} suffix, resolved after the other tags. */
    private static final Pattern TRAILING_GROUP = Pattern.compile("-([A-Za-z][A-Za-z0-9_.@]{1,19})$");
    private static final Pattern BRACKETED_GROUP = Pattern.compile("^\\[([^\\]]{2,25})\\]");
    private static final Pattern TRAILING_BRACKET_GROUP = Pattern.compile("\\[([A-Za-z][A-Za-z0-9_.-]{1,19})\\]$");

    private ReleaseTagLexer() {
    }

    /**
     * Scans {@code base} (a file name without its extension) and returns the
     * recognised tags, ordered by position, never overlapping.
     */
    public static List<ReleaseTag> scan(String base) {
        if (base == null || base.isBlank()) {
            return List.of();
        }
        boolean[] claimed = new boolean[base.length()];
        List<ReleaseTag> tags = new ArrayList<>();
        for (Rule rule : RULES) {
            Matcher matcher = rule.pattern().matcher(base);
            while (matcher.find()) {
                claim(base, claimed, tags, rule.kind(), matcher.start(), matcher.end());
            }
        }
        scanGroups(base, claimed, tags);
        tags.sort((left, right) -> Integer.compare(left.start(), right.start()));
        return List.copyOf(tags);
    }

    /**
     * Marks every character covered by {@code tags} so callers can look for
     * numbers only in the parts of the name that still carry meaning.
     */
    public static boolean[] mask(int length, List<ReleaseTag> tags) {
        boolean[] mask = new boolean[Math.max(length, 0)];
        for (ReleaseTag tag : tags) {
            for (int index = tag.start(); index < Math.min(tag.end(), mask.length); index++) {
                mask[index] = true;
            }
        }
        return mask;
    }

    private static void scanGroups(String base, boolean[] claimed, List<ReleaseTag> tags) {
        // claim() is a no-op on ranges already taken by a checksum or site stamp.
        Matcher bracketed = BRACKETED_GROUP.matcher(base);
        if (bracketed.find() && looksLikeGroup(bracketed.group(1))) {
            claim(base, claimed, tags, TagKind.GROUP, bracketed.start(), bracketed.end());
        }
        Matcher trailingBracket = TRAILING_BRACKET_GROUP.matcher(base);
        if (trailingBracket.find() && looksLikeGroup(trailingBracket.group(1))) {
            claim(base, claimed, tags, TagKind.GROUP, trailingBracket.start(), trailingBracket.end());
        }
        Matcher trailing = TRAILING_GROUP.matcher(base);
        // A trailing "-Word" is only a release group when actual release noise
        // precedes it. Otherwise it is far more likely to be part of the title
        // ("Spider-Man", "Show - Title - Part") and must be left alone.
        if (trailing.find() && !tags.isEmpty() && hasNoiseBefore(tags, trailing.start())) {
            claim(base, claimed, tags, TagKind.GROUP, trailing.start(), trailing.end());
        }
    }

    private static boolean hasNoiseBefore(List<ReleaseTag> tags, int position) {
        return tags.stream()
                .anyMatch(tag -> tag.end() <= position && tag.kind() != TagKind.GROUP && tag.kind() != TagKind.PART);
    }

    private static boolean looksLikeGroup(String value) {
        return value.trim().matches("(?i)[a-z0-9][a-z0-9 _.@-]{1,24}");
    }

    private static void claim(
            String base, boolean[] claimed, List<ReleaseTag> tags, TagKind kind, int start, int end) {
        if (!isFree(claimed, start, end)) {
            return;
        }
        for (int index = start; index < end; index++) {
            claimed[index] = true;
        }
        String raw = base.substring(start, end);
        tags.add(new ReleaseTag(kind, raw, raw.trim().toLowerCase(Locale.ROOT), start, end));
    }

    private static boolean isFree(boolean[] claimed, int start, int end) {
        for (int index = start; index < end; index++) {
            if (claimed[index]) {
                return false;
            }
        }
        return true;
    }

    private record Rule(TagKind kind, Pattern pattern) {
    }
}
