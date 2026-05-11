package com.episort.tvdb;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record TvdbSeriesDetails(
        TvdbIdentity identity,
        Set<TvdbEpisodeOrder> supportedOrders,
        List<TvdbEpisode> airedEpisodes,
        List<TvdbEpisode> dvdEpisodes,
        List<TvdbEpisode> absoluteEpisodes) {
    public TvdbSeriesDetails {
        identity = Objects.requireNonNull(identity, "identity");
        if (identity.mediaType() != TvdbMediaType.SERIES) {
            throw new IllegalArgumentException("identity must be a series");
        }
        supportedOrders = supportedOrders == null ? Set.of() : Set.copyOf(supportedOrders);
        airedEpisodes = airedEpisodes == null ? List.of() : List.copyOf(airedEpisodes);
        dvdEpisodes = dvdEpisodes == null ? List.of() : List.copyOf(dvdEpisodes);
        absoluteEpisodes = absoluteEpisodes == null ? List.of() : List.copyOf(absoluteEpisodes);
    }
}
