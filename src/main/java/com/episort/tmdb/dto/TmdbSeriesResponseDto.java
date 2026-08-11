package com.episort.tmdb.dto;

import java.util.List;

/** DTOs used by TMDB TV-series and TV-season detail endpoints. */
public final class TmdbSeriesResponseDto {
    public Integer id;
    public String name;
    public String original_name;
    public String overview;
    public String poster_path;
    public String first_air_date;
    public List<String> origin_country;
    public List<Network> networks;
    public List<SeasonSummary> seasons;

    public static final class Network {
        public String name;
    }

    public static final class SeasonSummary {
        public Integer id;
        public Integer season_number;
        public Integer episode_count;
        public String name;
    }

    public static final class SeasonDetails {
        public Integer id;
        public Integer season_number;
        public String name;
        public List<Episode> episodes;
    }

    public static final class Episode {
        public Integer id;
        public Integer season_number;
        public Integer episode_number;
        public String name;
    }
}
