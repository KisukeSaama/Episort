package com.episort.tvdb.dto;

import java.util.List;

/**
 * Response shape for {@code GET /series/{id}/episodes/default/eng?page=0}. The
 * language-specific endpoint returns the series name and every episode title
 * in the requested language, with TVDB's automatic fallback to the original
 * when no translation exists. That removes our need to chase translations
 * separately and keeps one call per series.
 */
public final class TvdbSeriesResponseDto {
    public Data data;
    public Links links;

    public static final class Links {
        public String next;
    }

    public static final class Data {
        public Series series;
        public List<Episode> episodes;
    }

    public static final class Series {
        public String id;
        public String name;
        public List<String> airsOrder;
    }

    public static final class Episode {
        public String id;
        public Integer seasonNumber;
        public Integer number;
        public Integer absoluteNumber;
        public String name;
    }
}
