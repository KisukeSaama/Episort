package com.episort.tvdb.dto;

/** Common fields returned by the TVDB series/{id} and movies/{id} endpoints. */
public final class TvdbEntityResponseDto {
    public Data data;

    public static final class Data {
        public Long id;
        public String name;
        public String image;
        public String year;
        public String firstAired;
        public String country;
        public String originalCountry;
    }
}
