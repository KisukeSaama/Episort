package com.episort.tmdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.config.JanusConfiguration;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HttpTmdbClientTest {
    @Test
    void searchesTvAndMoviesThroughJanusHeaders() throws Exception {
        FakeTmdbServer server = FakeTmdbServer.start();
        server.enqueue("/search/tv", 200, """
                {"results":[{"id":101,"name":"The Office","original_name":"The Office US",
                "first_air_date":"2005-03-24","origin_country":["US"],"poster_path":"/poster.jpg",
                "overview":"A workplace comedy."}]}
                """);
        server.enqueue("/search/movie", 200, """
                {"results":[{"id":202,"title":"The Office","release_date":"1995-09-15"}]}
                """);
        try {
            HttpTmdbClient client = new HttpTmdbClient(HttpClient.newHttpClient(), server.baseUri());

            TmdbSearchResult result = client.search("The Office", tokenCredentials());

            assertEquals(1, result.seriesCandidates().size());
            assertEquals(1, result.movieCandidates().size());
            TmdbCandidate series = result.seriesCandidates().getFirst();
            assertEquals(Optional.of(2005), series.year());
            assertEquals(Optional.of("US"), series.country());
            assertEquals(Optional.of("https://image.tmdb.org/t/p/w342/poster.jpg"), series.posterUrl());
            assertEquals(List.of("The Office US"), series.alternateTitles());
            assertTrue(server.authorizationHeaders.isEmpty());
            assertEquals(List.of("read-token", "read-token"), server.janusApiKeys);
            assertEquals(2, server.janusApplicationIds.size());
            assertFalse(server.rawQueries.stream().anyMatch(query -> query.contains("api_key")));
        } finally {
            server.stop();
        }
    }

    @Test
    void appliesYearFiltersWithoutLeakingUpstreamAuthentication() throws Exception {
        FakeTmdbServer server = FakeTmdbServer.start();
        server.enqueue("/search/tv", 200, "{\"results\":[]}");
        server.enqueue("/search/movie", 200, "{\"results\":[]}");
        try {
            HttpTmdbClient client = new HttpTmdbClient(HttpClient.newHttpClient(), server.baseUri());

            client.search(new TmdbSearchCriteria("Dune", Optional.of(2021), Optional.empty()),
                    new JanusConfiguration("api-key", Optional.empty()));

            assertTrue(server.rawQueries.get(0).contains("first_air_date_year=2021"));
            assertTrue(server.rawQueries.get(1).contains("primary_release_year=2021"));
            assertTrue(server.rawQueries.stream().noneMatch(query -> query.contains("api_key")));
            assertTrue(server.authorizationHeaders.isEmpty());
            assertEquals(List.of("api-key", "api-key"), server.janusApiKeys);
        } finally {
            server.stop();
        }
    }

    @Test
    void looksUpBothMediaTypesDirectlyByTmdbId() throws Exception {
        FakeTmdbServer server = FakeTmdbServer.start();
        server.enqueue("/tv/101", 200, """
                {"id":101,"name":"Show","original_name":"Original Show","first_air_date":"2010-01-01",
                "networks":[{"name":"HBO"}],"origin_country":["US"]}
                """);
        server.enqueue("/movie/101", 404, "{}");
        try {
            HttpTmdbClient client = new HttpTmdbClient(HttpClient.newHttpClient(), server.baseUri());

            TmdbSearchResult result = client.search(
                    new TmdbSearchCriteria("", Optional.empty(), Optional.of("101")), tokenCredentials());

            assertEquals("101", result.seriesCandidates().getFirst().identity().id());
            assertEquals(Optional.of("HBO"), result.seriesCandidates().getFirst().network());
            assertTrue(result.movieCandidates().isEmpty());
        } finally {
            server.stop();
        }
    }

    @Test
    void loadsAllAiredSeasonsAndDetectsAlternativeOrders() throws Exception {
        FakeTmdbServer server = FakeTmdbServer.start();
        server.enqueue("/tv/101", 200, """
                {"id":101,"name":"Show","seasons":[
                  {"season_number":0,"episode_count":1},{"season_number":1,"episode_count":2}]}
                """);
        server.enqueue("/tv/101/episode_groups", 200,
                "{\"results\":[{\"id\":\"absolute\",\"type\":2},{\"id\":\"dvd\",\"type\":3}]}");
        server.enqueue("/tv/101/season/0", 200,
                "{\"season_number\":0,\"episodes\":[{\"id\":9,\"season_number\":0,\"episode_number\":1,\"name\":\"Special\"}]}");
        server.enqueue("/tv/101/season/1", 200, """
                {"season_number":1,"episodes":[
                  {"id":11,"season_number":1,"episode_number":1,"name":"Pilot"},
                  {"id":12,"season_number":1,"episode_number":2,"name":"Second"}]}
                """);
        try {
            HttpTmdbClient client = new HttpTmdbClient(HttpClient.newHttpClient(), server.baseUri());

            TmdbSeriesDetails details = client.seriesDetails(
                    new TmdbIdentity("101", TmdbMediaType.SERIES, "Local"), tokenCredentials());

            assertEquals("Show", details.identity().displayName());
            assertEquals(3, details.airedEpisodes().size());
            assertTrue(details.airedEpisodes().getFirst().special());
            assertTrue(details.supportedOrders().containsAll(
                    List.of(TmdbEpisodeOrder.AIRED, TmdbEpisodeOrder.DVD, TmdbEpisodeOrder.ABSOLUTE)));
        } finally {
            server.stop();
        }
    }

    @Test
    void mapsDvdAndAbsoluteEpisodeGroupsUsingStableTmdbEpisodeIds() throws Exception {
        FakeTmdbServer server = FakeTmdbServer.start();
        enqueueSeriesAndGroups(server);
        server.enqueue("/tv/episode_group/dvd", 200, """
                {"id":"dvd","type":3,"groups":[{"order":0,"episodes":[
                  {"id":11,"order":0,"season_number":1,"episode_number":1,"name":"Pilot"},
                  {"id":12,"order":1,"season_number":1,"episode_number":2,"name":"Second"}]}]}
                """);
        enqueueSeriesAndGroups(server);
        server.enqueue("/tv/episode_group/absolute", 200, """
                {"id":"absolute","type":2,"groups":[{"order":0,"episodes":[
                  {"id":11,"order":0,"season_number":1,"episode_number":1,"name":"Pilot"},
                  {"id":12,"order":1,"season_number":1,"episode_number":2,"name":"Second"}]}]}
                """);
        try {
            HttpTmdbClient client = new HttpTmdbClient(HttpClient.newHttpClient(), server.baseUri());
            TmdbIdentity identity = new TmdbIdentity("101", TmdbMediaType.SERIES, "Show");

            TmdbSeriesDetails dvd = client.seriesDetails(identity, TmdbEpisodeOrder.DVD, tokenCredentials());
            TmdbSeriesDetails absolute = client.seriesDetails(identity, TmdbEpisodeOrder.ABSOLUTE, tokenCredentials());

            assertEquals("11", dvd.dvdEpisodes().getFirst().id());
            assertEquals(1, dvd.dvdEpisodes().getFirst().seasonNumber());
            assertEquals(2, dvd.dvdEpisodes().get(1).episodeNumber());
            assertEquals("11", absolute.absoluteEpisodes().getFirst().id());
            assertEquals(Optional.of(2), absolute.absoluteEpisodes().get(1).absoluteNumber());
            assertEquals(2, absolute.absoluteEpisodes().get(1).episodeNumber());
        } finally {
            server.stop();
        }
    }

    @Test
    void mapsMovieDetailsWithEnglishTitleAndReleaseYear() throws Exception {
        FakeTmdbServer server = FakeTmdbServer.start();
        server.enqueue("/movie/202", 200,
                "{\"id\":202,\"title\":\"English Movie\",\"original_title\":\"Film\",\"release_date\":\"1995-02-03\"}");
        try {
            HttpTmdbClient client = new HttpTmdbClient(HttpClient.newHttpClient(), server.baseUri());

            TmdbMovieDetails details = client.movieDetails(
                    new TmdbIdentity("202", TmdbMediaType.MOVIE, "Local"), tokenCredentials());

            assertEquals("English Movie", details.identity().displayName());
            assertEquals(Optional.of(1995), details.releaseYear());
        } finally {
            server.stop();
        }
    }

    @Test
    void authenticationErrorsAreRecoverableAndDoNotExposeSecrets() throws Exception {
        FakeTmdbServer server = FakeTmdbServer.start();
        server.enqueue("/search/tv", 401, "{\"status_message\":\"Invalid secret-token\"}");
        try {
            HttpTmdbClient client = new HttpTmdbClient(HttpClient.newHttpClient(), server.baseUri());

            TmdbException exception = assertThrows(
                    TmdbException.class, () -> client.search("Show", JanusConfiguration.callerKey("secret-token")));

            assertTrue(exception.error().recoverable());
            assertFalse(exception.error().details().contains("secret-token"));
        } finally {
            server.stop();
        }
    }

    private static void enqueueSeriesAndGroups(FakeTmdbServer server) {
        server.enqueue("/tv/101", 200, "{\"id\":101,\"name\":\"Show\",\"seasons\":[]}");
        server.enqueue("/tv/101/episode_groups", 200,
                "{\"results\":[{\"id\":\"dvd\",\"type\":3,\"order\":0},{\"id\":\"absolute\",\"type\":2,\"order\":0}]}");
    }

    private static JanusConfiguration tokenCredentials() {
        return JanusConfiguration.callerKey("read-token");
    }

    private static final class FakeTmdbServer {
        private final HttpServer server;
        private final List<Response> responses = new ArrayList<>();
        private final List<String> paths = new ArrayList<>();
        private final List<String> rawQueries = new ArrayList<>();
        private final List<String> authorizationHeaders = new ArrayList<>();
        private final List<String> janusApiKeys = new ArrayList<>();
        private final List<String> janusApplicationIds = new ArrayList<>();

        private FakeTmdbServer(HttpServer server) {
            this.server = server;
        }

        static FakeTmdbServer start() throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            FakeTmdbServer fake = new FakeTmdbServer(server);
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                fake.paths.add(path);
                String rawQuery = exchange.getRequestURI().getRawQuery();
                if (rawQuery != null) fake.rawQueries.add(rawQuery);
                String authorization = exchange.getRequestHeaders().getFirst("Authorization");
                if (authorization != null) fake.authorizationHeaders.add(authorization);
                fake.janusApiKeys.add(exchange.getRequestHeaders().getFirst("X-Janus-Api-Key"));
                fake.janusApplicationIds.add(exchange.getRequestHeaders().getFirst("X-Janus-Application-Id"));
                Response response = fake.responses.stream()
                        .filter(candidate -> candidate.path().equals(path))
                        .findFirst()
                        .orElse(new Response(path, 404, "{}"));
                fake.responses.remove(response);
                byte[] body = response.body().getBytes();
                exchange.sendResponseHeaders(response.status(), body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return fake;
        }

        URI baseUri() {
            return URI.create("http://localhost:" + server.getAddress().getPort() + "/");
        }

        void enqueue(String path, int status, String body) {
            responses.add(new Response(path, status, body));
        }

        void stop() {
            server.stop(0);
        }
    }

    private record Response(String path, int status, String body) {
    }
}
