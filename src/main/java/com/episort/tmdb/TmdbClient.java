package com.episort.tmdb;

import com.episort.config.JanusConfiguration;

public interface TmdbClient {
    TmdbSearchResult search(String query, JanusConfiguration credentials);

    default TmdbSearchResult search(TmdbSearchCriteria criteria, JanusConfiguration credentials) {
        return search(criteria.query(), credentials);
    }

    TmdbSeriesDetails seriesDetails(TmdbIdentity identity, JanusConfiguration credentials);

    /** Loads the requested episode order; implementations may include aired data for remapping. */
    default TmdbSeriesDetails seriesDetails(
            TmdbIdentity identity,
            TmdbEpisodeOrder order,
            JanusConfiguration credentials) {
        return seriesDetails(identity, credentials);
    }

    TmdbMovieDetails movieDetails(TmdbIdentity identity, JanusConfiguration credentials);
}
