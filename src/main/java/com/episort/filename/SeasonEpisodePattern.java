package com.episort.filename;

import java.util.regex.Pattern;

/**
 * The shared {@code SxxExx} patterns, in the package that owns filename parsing.
 *
 * <p>These regexes previously existed as four private copies in
 * {@code matching} and {@code ui.scan}, and the copies did not agree: two
 * accepted a two-digit season and three-digit episode, two accepted three and
 * four. The same filename could therefore be recognised by the plan mapper and
 * missed by the matcher.
 *
 * <p>Both bounds are preserved here rather than silently unified — widening what
 * the matcher accepts is a product decision, not a refactor. Group 1 is the
 * season, group 2 the episode, in both patterns.
 */
public final class SeasonEpisodePattern {

    /**
     * The stricter form used when <em>matching</em> a file against TMDB
     * episodes: season up to two digits, episode up to three.
     */
    public static final Pattern STRICT =
            Pattern.compile("[Ss](\\d{1,2})[\\s._-]?[Ee](\\d{1,3})");

    /**
     * The permissive form used when <em>reading</em> an order the user may have
     * typed: season up to three digits, episode up to four.
     */
    public static final Pattern LENIENT =
            Pattern.compile("[Ss](\\d{1,3})[\\s._-]?[Ee](\\d{1,4})");

    private SeasonEpisodePattern() {
    }
}
