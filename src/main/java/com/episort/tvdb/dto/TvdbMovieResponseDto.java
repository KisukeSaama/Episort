package com.episort.tvdb.dto;

import java.util.List;

public final class TvdbMovieResponseDto {
    public Data data;

    public static final class Data {
        public String id;
        public String name;
        public String translationsName;
        public String year;
        public String releaseDate;
        public Translations translations;
    }

    public static final class Translations {
        public List<NameTranslation> nameTranslations;
    }

    public static final class NameTranslation {
        public String language;
        public String name;
        public Boolean isPrimary;
    }
}
