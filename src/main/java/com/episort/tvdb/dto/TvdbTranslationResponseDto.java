package com.episort.tvdb.dto;


/**
 * Response shape for {@code GET /series/{id}/translations/{lang}}. Returns the
 * translated name and overview for the requested language when available.
 */
public final class TvdbTranslationResponseDto {
    public Data data;

    public static final class Data {
        public String language;
        public String name;
        public String overview;
    }
}
