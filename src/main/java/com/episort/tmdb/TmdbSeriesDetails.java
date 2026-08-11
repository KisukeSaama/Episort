package com.episort.tmdb;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record TmdbSeriesDetails(
        TmdbIdentity identity,
        Set<TmdbEpisodeOrder> supportedOrders,
        List<TmdbEpisode> airedEpisodes,
        List<TmdbEpisode> dvdEpisodes,
        List<TmdbEpisode> absoluteEpisodes) {
    public TmdbSeriesDetails {
        identity = Objects.requireNonNull(identity, "identity");
        if (identity.mediaType() != TmdbMediaType.SERIES) {
            throw new IllegalArgumentException("identity must be a series");
        }
        supportedOrders = supportedOrders == null ? Set.of() : Set.copyOf(supportedOrders);
        airedEpisodes = airedEpisodes == null ? List.of() : List.copyOf(airedEpisodes);
        dvdEpisodes = dvdEpisodes == null ? List.of() : List.copyOf(dvdEpisodes);
        absoluteEpisodes = absoluteEpisodes == null ? List.of() : List.copyOf(absoluteEpisodes);
    }

    public List<TmdbEpisode> episodesFor(TmdbEpisodeOrder order) {
        return switch (order == null ? TmdbEpisodeOrder.AIRED : order) {
            case AIRED -> airedEpisodes;
            case DVD -> dvdEpisodes;
            case ABSOLUTE -> absoluteEpisodes;
        };
    }
}
