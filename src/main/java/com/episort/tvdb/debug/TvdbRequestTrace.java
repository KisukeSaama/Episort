package com.episort.tvdb.debug;

import java.time.Instant;

public record TvdbRequestTrace(
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

    public static TvdbRequestTrace cacheHit(Instant timestamp, String path) {
        return new TvdbRequestTrace(timestamp, "CACHE", path, 0, 0L, "(served from cache)", null, true);
    }
}
