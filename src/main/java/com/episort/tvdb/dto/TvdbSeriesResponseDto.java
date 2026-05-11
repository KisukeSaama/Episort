package com.episort.tvdb.dto;

import java.util.List;

public final class TvdbSeriesResponseDto {
    public Data data;

    public static final class Data {
        public String id;
        public String name;
        public String translationsName;
        public List<String> airsOrder;
        public List<Episode> episodes;
    }

    public static final class Episode {
        public String id;
        public Integer seasonNumber;
        public Integer number;
        public Integer absoluteNumber;
        public String name;
        public String translationsName;
    }
}
