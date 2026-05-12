package com.episort.tvdb.dto;

import java.util.List;

public final class TvdbSearchResponseDto {
    public List<Item> data;

    public static final class Item {
        public String id;
        public String tvdb_id;
        public String type;
        public String recordType;
        public String name;
        public String translationsName;
        public String overview;
        public String year;
        public String country;
        public String network;
        public String status;
        public String image_url;
        public java.util.List<String> aliases;
        /** Per-language name map ({@code "eng": "Foo", "fra": "Toto"}). */
        public java.util.Map<String, String> translations;
        /** Per-language overview map (parallel structure to {@link #translations}). */
        public java.util.Map<String, String> overviews;
    }
}
