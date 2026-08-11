package com.episort.tmdb.dto;

import java.util.List;

/** Shared response shape for TMDB's movie and TV search endpoints. */
public final class TmdbSearchResponseDto {
    public List<Result> results;

    public static final class Result {
        public Integer id;
        public String name;
        public String original_name;
        public String title;
        public String original_title;
        public String overview;
        public String poster_path;
        public String first_air_date;
        public String release_date;
        public List<String> origin_country;
        public Double popularity;
    }
}
