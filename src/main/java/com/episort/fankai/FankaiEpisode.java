package com.episort.fankai;

/**
 * One episode of a kai. Fankai keeps counting episodes across seasons, so the
 * second season of a kai may start at episode 8: that is the numbering the Plex
 * Fankai agent expects and it is kept as is.
 */
public record FankaiEpisode(int id, int seasonNumber, int episodeNumber, String title) {
    public FankaiEpisode {
        if (seasonNumber < 0) throw new IllegalArgumentException("seasonNumber must be zero or greater");
        if (episodeNumber < 0) throw new IllegalArgumentException("episodeNumber must be zero or greater");
        title = title == null ? "" : title.trim();
    }
}
