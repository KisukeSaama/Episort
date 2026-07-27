package com.episort.tvdb;

import com.episort.config.TvdbCredentials;

public interface TvdbClient {
    TvdbSearchResult search(String query, TvdbCredentials credentials);

    TvdbSeriesDetails seriesDetails(TvdbIdentity identity, TvdbCredentials credentials);

    TvdbMovieDetails movieDetails(TvdbIdentity identity, TvdbCredentials credentials);
}
