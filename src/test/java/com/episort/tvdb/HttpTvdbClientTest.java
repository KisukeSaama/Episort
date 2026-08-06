package com.episort.tvdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.config.TvdbCredentials;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HttpTvdbClientTest {
    @Test
    void searchesSeriesAndMoviesSeparatelyWithIdentityInfo() throws Exception {
        FakeTvdbServer server = FakeTvdbServer.start();
        server.enqueue("/login", 200, "{\"data\":{\"token\":\"token-1\"}}");
        server.enqueue("/search", 200, """
                {"data":[
                  {"tvdb_id":"101","type":"series","name":"The Office","year":"2005","country":"USA","network":"NBC","image_url":"https://img.example/poster.jpg"},
                  {"tvdb_id":"202","type":"movie","name":"The Office","year":"1995","country":"US"}
                ]}
                """);
        try {
            HttpTvdbClient client = new HttpTvdbClient(HttpClient.newHttpClient(), server.baseUri());

            TvdbSearchResult result = client.search("The Office", credentials());

            assertEquals(1, result.seriesCandidates().size());
            assertEquals(1, result.movieCandidates().size());
            assertEquals(TvdbMediaType.SERIES, result.seriesCandidates().get(0).identity().mediaType());
            assertEquals(Optional.of(2005), result.seriesCandidates().get(0).year());
            assertEquals(Optional.of("https://img.example/poster.jpg"), result.seriesCandidates().get(0).posterUrl());
            assertEquals("Bearer token-1", server.authorizationHeaders.get(0));
        } finally {
            server.stop();
        }
    }

    @Test
    void addsYearToTextSearch() throws Exception {
        FakeTvdbServer server = FakeTvdbServer.start();
        server.enqueue("/login", 200, "{\"data\":{\"token\":\"token-1\"}}");
        server.enqueue("/search", 200, "{\"data\":[]}");
        try {
            HttpTvdbClient client = new HttpTvdbClient(HttpClient.newHttpClient(), server.baseUri());

            client.search(new TvdbSearchCriteria("The Office", Optional.of(2005), Optional.empty()), credentials());

            assertEquals("query=The+Office&year=2005", server.rawQueries.getFirst());
        } finally {
            server.stop();
        }
    }

    @Test
    void looksUpSeriesDirectlyByTvdbIdWithoutATitle() throws Exception {
        FakeTvdbServer server = FakeTvdbServer.start();
        server.enqueue("/login", 200, "{\"data\":{\"token\":\"token-1\"}}");
        server.enqueue("/series/73244", 200,
                "{\"data\":{\"id\":73244,\"name\":\"The Office\",\"year\":\"2005\",\"image\":\"/poster.jpg\"}}");
        server.enqueue("/series/73244/translations/eng", 404, "{}");
        server.enqueue("/series/73244/translations/fra", 404, "{}");
        server.enqueue("/movies/73244", 404, "{}");
        try {
            HttpTvdbClient client = new HttpTvdbClient(HttpClient.newHttpClient(), server.baseUri());

            TvdbSearchResult result = client.search(
                    new TvdbSearchCriteria("", Optional.empty(), Optional.of("73244")), credentials());

            assertEquals(1, result.seriesCandidates().size());
            assertEquals("73244", result.seriesCandidates().getFirst().identity().id());
            assertEquals(Optional.of(2005), result.seriesCandidates().getFirst().year());
            assertTrue(result.movieCandidates().isEmpty());
        } finally {
            server.stop();
        }
    }

    @Test
    void usesSearchPayloadWithoutTranslationFanOutAndNormalizesRelativePosters() throws Exception {
        FakeTvdbServer server = FakeTvdbServer.start();
        server.enqueue("/login", 200, "{\"data\":{\"token\":\"token-1\"}}");
        server.enqueue("/search", 200, """
                {"data":[
                  {"tvdb_id":"101","type":"series","name":"Show","image_url":"/banners/poster one.jpg","overview":"Default"}
                ]}
                """);
        try {
            HttpTvdbClient client = new HttpTvdbClient(HttpClient.newHttpClient(), server.baseUri());

            TvdbCandidate candidate = client.search("Show", credentials()).seriesCandidates().getFirst();

            assertEquals(Optional.of("Default"), candidate.overview());
            assertTrue(candidate.englishOverview().isEmpty());
            assertTrue(candidate.frenchOverview().isEmpty());
            assertEquals(Optional.of("https://artworks.thetvdb.com/banners/poster%20one.jpg"), candidate.posterUrl());
            assertEquals(0, server.requestCount("/series/101/translations/eng"));
            assertEquals(0, server.requestCount("/series/101/translations/fra"));
        } finally {
            server.stop();
        }
    }

    @Test
    void retriesTransientFailuresBeforeReturningCandidates() throws Exception {
        FakeTvdbServer server = FakeTvdbServer.start();
        server.enqueue("/login", 200, "{\"data\":{\"token\":\"token-1\"}}");
        server.enqueue("/search", 503, "{}");
        server.enqueue("/search", 200, "{\"data\":[{\"tvdb_id\":\"101\",\"recordType\":\"series\",\"name\":\"Show\"}]}");
        try {
            HttpTvdbClient client = new HttpTvdbClient(HttpClient.newHttpClient(), server.baseUri());

            TvdbSearchResult result = client.search("Show", credentials());

            assertEquals(1, result.seriesCandidates().size());
            assertEquals(2, server.requestCount("/search"));
        } finally {
            server.stop();
        }
    }

    @Test
    void reacquiresTokenAfterUnauthorizedResponse() throws Exception {
        FakeTvdbServer server = FakeTvdbServer.start();
        server.enqueue("/login", 200, "{\"data\":{\"token\":\"expired\"}}");
        server.enqueue("/search", 401, "{}");
        server.enqueue("/login", 200, "{\"data\":{\"token\":\"fresh\"}}");
        server.enqueue("/search", 200, "{\"data\":[{\"tvdb_id\":\"101\",\"type\":\"series\",\"name\":\"Show\"}]}");
        try {
            HttpTvdbClient client = new HttpTvdbClient(HttpClient.newHttpClient(), server.baseUri());

            TvdbSearchResult result = client.search("Show", credentials());

            assertEquals(1, result.seriesCandidates().size());
            assertEquals(List.of("Bearer expired", "Bearer fresh"),
                    server.authorizationHeaders.subList(0, 2));
        } finally {
            server.stop();
        }
    }

    @Test
    void mapsSeriesDetailsWithFallbackTitleOrdersAndSpecials() throws Exception {
        FakeTvdbServer server = FakeTvdbServer.start();
        server.enqueue("/login", 200, "{\"data\":{\"token\":\"token-1\"}}");
        server.enqueue("/series/101/episodes/default/eng", 200, """
                {"data":{
                  "series":{
                    "id":"101",
                    "name":"Fallback Show",
                    "airsOrder":["aired","dvd","absolute"]
                  },
                  "episodes":[
                    {"id":"e1","seasonNumber":1,"number":1,"absoluteNumber":1,"name":"Pilot"},
                    {"id":"s1","seasonNumber":0,"number":2,"name":"Special"}
                  ]
                }}
                """);
        server.enqueue("/series/101/translations/eng", 200,
                "{\"data\":{\"language\":\"eng\",\"name\":\"Fallback Show\"}}");
        try {
            HttpTvdbClient client = new HttpTvdbClient(HttpClient.newHttpClient(), server.baseUri());

            TvdbSeriesDetails details = client.seriesDetails(
                    new TvdbIdentity("101", TvdbMediaType.SERIES, "Local Show"), credentials());

            assertEquals("Local Show", details.identity().displayName());
            assertTrue(details.supportedOrders().contains(TvdbEpisodeOrder.AIRED));
            assertTrue(details.supportedOrders().contains(TvdbEpisodeOrder.DVD));
            assertTrue(details.supportedOrders().contains(TvdbEpisodeOrder.ABSOLUTE));
            assertTrue(details.airedEpisodes().get(1).special());
        } finally {
            server.stop();
        }
    }

    @Test
    void mapsSeriesDetailsForDvdAndAbsoluteOrders() throws Exception {
        FakeTvdbServer server = FakeTvdbServer.start();
        server.enqueue("/login", 200, "{\"data\":{\"token\":\"token-1\"}}");
        server.enqueue("/series/101/episodes/default/eng", 200, """
                {"data":{"series":{"id":"101","name":"Show","airsOrder":["aired","dvd","absolute"]},
                "episodes":[{"id":"e29","seasonNumber":2,"number":1,"absoluteNumber":29,"name":"Aired 29"}]}}
                """);
        server.enqueue("/series/101/translations/eng", 200,
                "{\"data\":{\"language\":\"eng\",\"name\":\"Show\"}}");
        server.enqueue("/series/101/episodes/dvd/eng", 200, """
                {"data":{"series":{"id":"101","name":"Show"},
                "episodes":[{"id":"e29","seasonNumber":1,"number":29,"absoluteNumber":29,"name":"DVD 29"}]}}
                """);
        server.enqueue("/series/101/episodes/absolute/eng", 200, """
                {"data":{"series":{"id":"101","name":"Show"},
                "episodes":[{"id":"e29","seasonNumber":1,"number":29,"absoluteNumber":29,"name":"Absolute 29"}]}}
                """);
        try {
            HttpTvdbClient client = new HttpTvdbClient(HttpClient.newHttpClient(), server.baseUri());

            TvdbIdentity identity = new TvdbIdentity("101", TvdbMediaType.SERIES, "Show");
            TvdbSeriesDetails dvdDetails = client.seriesDetails(identity, TvdbEpisodeOrder.DVD, credentials());
            TvdbSeriesDetails absoluteDetails = client.seriesDetails(identity, TvdbEpisodeOrder.ABSOLUTE, credentials());

            assertTrue(dvdDetails.airedEpisodes().isEmpty());
            assertEquals(29, dvdDetails.dvdEpisodes().getFirst().episodeNumber());
            assertTrue(dvdDetails.absoluteEpisodes().isEmpty());
            assertEquals(29, absoluteDetails.absoluteEpisodes().getFirst().absoluteNumber().orElseThrow());
            assertTrue(absoluteDetails.dvdEpisodes().isEmpty());
            assertEquals(1, server.requestCount("/series/101/episodes/dvd/eng"));
            assertEquals(1, server.requestCount("/series/101/episodes/absolute/eng"));
        } finally {
            server.stop();
        }
    }

    @Test
    void followsEpisodePaginationUntilTvdbStopsProvidingNextLink() throws Exception {
        FakeTvdbServer server = FakeTvdbServer.start();
        server.enqueue("/login", 200, "{\"data\":{\"token\":\"token-1\"}}");
        server.enqueue("/series/101/episodes/default/eng", 200, """
                {"data":{"series":{"id":"101","name":"Show"},
                "episodes":[{"id":"e1","seasonNumber":1,"number":1,"name":"One"}]},
                "links":{"next":"/v4/series/101/episodes/default/eng?page=1"}}
                """);
        server.enqueue("/series/101/episodes/default/eng", 200, """
                {"data":{"series":{"id":"101","name":"Show"},
                "episodes":[{"id":"e2","seasonNumber":1,"number":2,"name":"Two"}]},
                "links":{"next":null}}
                """);
        try {
            HttpTvdbClient client = new HttpTvdbClient(HttpClient.newHttpClient(), server.baseUri());

            TvdbSeriesDetails details = client.seriesDetails(
                    new TvdbIdentity("101", TvdbMediaType.SERIES, "Show"), credentials());

            assertEquals(2, details.airedEpisodes().size());
            assertEquals(2, server.requestCount("/series/101/episodes/default/eng"));
            assertEquals(List.of("page=0", "page=1"), server.rawQueries.subList(0, 2));
        } finally {
            server.stop();
        }
    }

    @Test
    void mapsMovieDetailsWithFallbackTitleAndReleaseYear() throws Exception {
        FakeTvdbServer server = FakeTvdbServer.start();
        server.enqueue("/login", 200, "{\"data\":{\"token\":\"token-1\"}}");
        server.enqueue("/movies/202/extended", 200,
                "{\"data\":{\"id\":\"202\",\"name\":\"Fallback Movie\",\"releaseDate\":\"1995-02-03\"}}");
        try {
            HttpTvdbClient client = new HttpTvdbClient(HttpClient.newHttpClient(), server.baseUri());

            TvdbMovieDetails details = client.movieDetails(
                    new TvdbIdentity("202", TvdbMediaType.MOVIE, "Local Movie"), credentials());

            assertEquals("Fallback Movie", details.identity().displayName());
            assertEquals(Optional.of(1995), details.releaseYear());
        } finally {
            server.stop();
        }
    }

    @Test
    void authenticationErrorsAreRecoverableAndDoNotExposeSecrets() throws Exception {
        FakeTvdbServer server = FakeTvdbServer.start();
        server.enqueue("/login", 401, "{\"apikey\":\"secret-key\",\"token\":\"secret-token\"}");
        try {
            HttpTvdbClient client = new HttpTvdbClient(HttpClient.newHttpClient(), server.baseUri());

            TvdbException exception = assertThrows(TvdbException.class, () -> client.search("Show", credentials()));

            assertTrue(exception.error().recoverable());
            assertFalse(exception.error().details().contains("secret-key"));
            assertFalse(exception.error().details().contains("secret-token"));
        } finally {
            server.stop();
        }
    }

    private static TvdbCredentials credentials() {
        return new TvdbCredentials("api-key", Optional.of("pin"));
    }

    private static final class FakeTvdbServer {
        private final HttpServer server;
        private final List<Response> responses = new ArrayList<>();
        private final List<String> paths = new ArrayList<>();
        private final List<String> rawQueries = new ArrayList<>();
        private final List<String> authorizationHeaders = new ArrayList<>();

        private FakeTvdbServer(HttpServer server) {
            this.server = server;
        }

        static FakeTvdbServer start() throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            FakeTvdbServer fake = new FakeTvdbServer(server);
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                fake.paths.add(path);
                String rawQuery = exchange.getRequestURI().getRawQuery();
                if (rawQuery != null) {
                    fake.rawQueries.add(rawQuery);
                }
                String authorization = exchange.getRequestHeaders().getFirst("Authorization");
                if (authorization != null) {
                    fake.authorizationHeaders.add(authorization);
                }
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

        int requestCount(String path) {
            return (int) paths.stream().filter(path::equals).count();
        }

        void stop() {
            server.stop(0);
        }
    }

    private record Response(String path, int status, String body) {
    }
}
