package com.episort.tmdb.dto;

import java.util.List;

/** Episode-order alternatives declared for a TMDB TV series. */
public final class TmdbEpisodeGroupsDto {
    public List<GroupSummary> results;

    public static final class GroupSummary {
        public String id;
        public String name;
        public Integer type;
        public Integer order;
        public Integer group_count;
        public Integer episode_count;
    }
}
