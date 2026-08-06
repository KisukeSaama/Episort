package com.episort.tvdb;

import com.episort.config.TvdbCredentials;

public interface TvdbClient {
    TvdbSearchResult search(String query, TvdbCredentials credentials);

    default TvdbSearchResult search(TvdbSearchCriteria criteria, TvdbCredentials credentials) {
        return search(criteria.query(), credentials);
    }

    TvdbSeriesDetails seriesDetails(TvdbIdentity identity, TvdbCredentials credentials);

    /** Loads the requested episode order; implementations may include aired data for remapping. */
    default TvdbSeriesDetails seriesDetails(
            TvdbIdentity identity,
            TvdbEpisodeOrder order,
            TvdbCredentials credentials) {
        return seriesDetails(identity, credentials);
    }

    TvdbMovieDetails movieDetails(TvdbIdentity identity, TvdbCredentials credentials);
}
