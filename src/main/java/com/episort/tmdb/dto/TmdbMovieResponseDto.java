package com.episort.tmdb.dto;

import java.util.List;

/** Response from TMDB's movie details endpoint. */
public final class TmdbMovieResponseDto {
    public Integer id;
    public String title;
    public String original_title;
    public String overview;
    public String poster_path;
    public String release_date;
    public List<ProductionCountry> production_countries;

    public static final class ProductionCountry {
        public String iso_3166_1;
    }
}
