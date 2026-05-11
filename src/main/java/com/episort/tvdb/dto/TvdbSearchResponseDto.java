package com.episort.tvdb.dto;

import java.util.List;

public final class TvdbSearchResponseDto {
    public Data data;

    public static final class Data {
        public List<Item> items;
    }

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
        public java.util.List<TvdbAliasDto> aliases;
    }
}
