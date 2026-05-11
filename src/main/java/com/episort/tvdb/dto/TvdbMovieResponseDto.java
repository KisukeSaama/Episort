package com.episort.tvdb.dto;

public final class TvdbMovieResponseDto {
    public Data data;

    public static final class Data {
        public String id;
        public String name;
        public String translationsName;
        public String year;
        public String releaseDate;
    }
}
