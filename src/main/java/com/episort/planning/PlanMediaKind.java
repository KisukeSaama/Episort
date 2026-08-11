package com.episort.planning;


/** What a planned item maps to in the destination layout. */
public enum PlanMediaKind {
    /** Regular episode: lands in a {@code Season XX} folder. */
    SERIES_EPISODE,
    /** Special, OVA, or any season-zero entry: lands in {@code Specials}. */
    SPECIAL,
    /** Movie: lands in its own {@code Title (Year)} folder. */
    MOVIE
}
