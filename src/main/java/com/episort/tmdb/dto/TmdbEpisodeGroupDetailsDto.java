package com.episort.tmdb.dto;

import java.util.List;

/** Ordered groups and episodes returned by a TMDB episode-group lookup. */
public final class TmdbEpisodeGroupDetailsDto {
    public String id;
    public String name;
    public Integer type;
    public List<Group> groups;

    public static final class Group {
        public String id;
        public String name;
        public Integer order;
        public List<Episode> episodes;
    }

    public static final class Episode {
        public Integer id;
        public String name;
        public Integer order;
        public Integer season_number;
        public Integer episode_number;
    }
}
