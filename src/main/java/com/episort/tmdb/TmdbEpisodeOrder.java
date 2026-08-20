package com.episort.tmdb;

import java.util.Arrays;
import java.util.Optional;

/** Episode group types exposed by TMDB for a series. */
public enum TmdbEpisodeOrder {
    AIRED(1),
    ABSOLUTE(2),
    DVD(3),
    DIGITAL(4),
    STORY_ARC(5),
    PRODUCTION(6),
    TV(7);

    private final int groupType;

    TmdbEpisodeOrder(int groupType) {
        this.groupType = groupType;
    }

    public int groupType() {
        return groupType;
    }

    public static Optional<TmdbEpisodeOrder> fromGroupType(int groupType) {
        return Arrays.stream(values()).filter(order -> order.groupType == groupType).findFirst();
    }
}
