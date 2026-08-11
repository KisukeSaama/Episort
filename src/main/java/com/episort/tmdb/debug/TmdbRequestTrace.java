package com.episort.tmdb.debug;

import java.time.Instant;

public record TmdbRequestTrace(
        Instant timestamp,
        String method,
        String path,
        int statusCode,
        long latencyMs,
        String responsePreview,
        String error,
        boolean cacheHit) {

    public boolean failed() {
        return (error != null && !error.isBlank())
                || (statusCode != 0 && (statusCode < 200 || statusCode >= 300));
    }

    public static TmdbRequestTrace cacheHit(Instant timestamp, String path) {
        return new TmdbRequestTrace(timestamp, "CACHE", path, 0, 0L, "(served from cache)", null, true);
    }
}
