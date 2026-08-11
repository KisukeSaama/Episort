package com.episort.filename;


/**
 * Every way a file name can be parsed and still not be trustworthy.
 *
 * <p>These are deliberately explicit rather than folded into a single
 * confidence number: the review screen has to tell the user <em>why</em> a row
 * needs attention, and "confidence 0.5" says nothing actionable.
 */
public enum ParseWarning {
    /** Two different episode markers disagree (e.g. {@code S01E02} and {@code 1x05}). */
    CONFLICTING_MARKERS,
    /** The episode number comes from a bare number with no season marker. */
    AMBIGUOUS_ABSOLUTE_NUMBER,
    /** The season had to be taken from the parent folder, not from the file name. */
    SEASON_FROM_FOLDER,
    /** No season information at all; season 1 was assumed. */
    ASSUMED_SEASON,
    /** The name carries several episodes (multi-episode file). */
    MULTI_EPISODE,
    /** Season 0 / specials folder: not a regular numbered episode. */
    SPECIAL_EPISODE,
    /** A date was found instead of a season/episode pair (daily show). */
    DATE_BASED_EPISODE,
    /** Several plausible years were found. */
    AMBIGUOUS_YEAR,
    /** The year looks implausible and was discarded. */
    IMPLAUSIBLE_YEAR,
    /** The name matches sample/trailer/extras material. */
    EXTRA_MATERIAL,
    /** The file is one part of a multi-part release (CD1/CD2, part1/part2). */
    MULTI_PART_FILE,
    /** No usable title could be isolated. */
    MISSING_TITLE,
    /**
     * The file name states a series the parent folder contradicts. The file
     * name was kept: the folder holds more than one series.
     */
    FOLDER_TITLE_MISMATCH,
    /** The title is made of digits only, which is legitimate but easy to confuse. */
    NUMERIC_TITLE,
    /** Nothing but noise tags surrounded the marker. */
    NO_EPISODE_TITLE,
    /** The name is unusable (empty, extension only, control characters). */
    UNUSABLE_NAME,
    /** Season or episode numbers are outside any plausible range. */
    OUT_OF_RANGE_NUMBER
}
