package com.episort.tvdb;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.episort.config.TvdbCredentials;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("tvdb-live")
class TvdbLiveIntegrationTest {
    @Test
    void searchesRealTvdbForKnownSeriesWhenCredentialsAreProvided() throws Exception {
        assumeTrue(Boolean.getBoolean("runTvdbLive"), "Run with -DrunTvdbLive=true and TVDB_API_KEY set.");
        String apiKey = System.getenv("TVDB_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isBlank(), "TVDB_API_KEY is required.");
        TvdbCredentials credentials = new TvdbCredentials(apiKey, Optional.ofNullable(System.getenv("TVDB_PIN")));

        HttpTvdbClient client = new HttpTvdbClient();

        assertFalse(client.search("Sherlock", credentials).seriesCandidates().isEmpty());
        assertFalse(client.search("The Matrix", credentials).movieCandidates().isEmpty());
    }
}
