package com.episort.fankai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.config.JanusConfiguration;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpFankaiClientTest {
    private static final String SERIES = """
            [{"id":12,"title":"Naruto Shippuden Yabai","title_for_plex":"Naruto Shippuden Yabaï",
              "show_title":"Naruto Shippuden Yabai","original_title":"NARUTO -ナルト- 疾風伝",
              "ids":{"imdb":null,"tmdb":"31910","tvdb":null},
              "images":{"poster":"https://metadata.fankai.fr/series/12/image/poster"},
              "plot":"Deux ans et demi plus tard.","year":2007,"country":"Japon"},
             {"id":11,"title":"Dragon Ball Z Recut","ids":{"tmdb":null},"premiered":"2012-03-01"},
             {"title":"no id"}]
            """;

    private FakeFankaiServer server;
    private JanusConfiguration janus;
    private HttpFankaiClient client;

    @BeforeEach
    void start() throws IOException {
        server = FakeFankaiServer.start();
        janus = new JanusConfiguration(server.baseUri(), "episort-app", "caller-key");
        client = new HttpFankaiClient(HttpClient.newHttpClient());
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void readsTheCatalogWithAlternateTitlesAndTmdbLink() {
        server.respond("/series", 200, SERIES);

        List<FankaiSeries> series = client.series(janus);

        assertEquals(2, series.size());
        FankaiSeries yabai = series.getFirst();
        assertEquals(12, yabai.id());
        assertEquals("Naruto Shippuden Yabai", yabai.title());
        // The original title names the source show, which TMDB already offers:
        // as an alias it would tie the kai with that show on a plain folder name.
        assertEquals(List.of("Naruto Shippuden Yabaï"), yabai.alternateTitles());
        assertEquals(Optional.of("31910"), yabai.tmdbId());
        assertEquals(Optional.of("https://metadata.fankai.fr/series/12/image/poster"), yabai.posterUrl());
        assertEquals(Optional.of(2007), yabai.year());
        assertEquals(Optional.of("Japon"), yabai.country());
        FankaiSeries recut = series.get(1);
        assertEquals(Optional.empty(), recut.tmdbId());
        assertEquals(Optional.of(2012), recut.year());
    }

    @Test
    void callsTheCatalogThroughTheJanusGatewayWithTheCallerHeaders() {
        server.respond("/series", 200, SERIES);

        client.series(janus);

        assertEquals(1, server.hits("/series"));
        assertEquals("episort-app", server.lastHeader("X-Janus-Application-Id"));
        assertEquals("caller-key", server.lastHeader("X-Janus-Api-Key"));
    }

    @Test
    void reportsAGatewayRefusalWithItsCorrelationId() {
        server.respond("/series", 403, "{}");

        FankaiException failure = assertThrows(FankaiException.class, () -> client.series(janus));

        assertTrue(failure.getMessage().contains("HTTP 403"), failure.getMessage());
        assertTrue(failure.getMessage().contains("corr-1"), failure.getMessage());
    }

    @Test
    void loadsEpisodesOfEverySeasonInOrder() {
        server.respond("/series/12/seasons", 200, """
                [{"id":22,"season_number":2},{"id":203,"season_number":1},{"id":7,"season_number":0}]
                """);
        server.respond("/seasons/203/episodes", 200, """
                [{"id":2,"episode_number":2,"title":"Le réceptacle"},{"id":1,"episode_number":1,"title":"Le retour au pays"}]
                """);
        server.respond("/seasons/22/episodes", 200, """
                {"episodes":[{"id":8,"episode_number":8,"title":"Rituel"}]}
                """);
        server.respond("/seasons/7/episodes", 200, "[{\"id\":90,\"episode_number\":1,\"title\":\"Film\"}]");

        List<FankaiEpisode> episodes = client.episodes(12, janus);

        assertEquals(List.of(
                new FankaiEpisode(90, 0, 1, "Film"),
                new FankaiEpisode(1, 1, 1, "Le retour au pays"),
                new FankaiEpisode(2, 1, 2, "Le réceptacle"),
                new FankaiEpisode(8, 2, 8, "Rituel")), episodes);
        client.episodes(12, janus);
        assertEquals(2, server.hits("/series/12/seasons"));
    }

    @Test
    void reportsUnreadableAnswers() {
        server.respond("/series", 200, "not json {");

        assertThrows(FankaiException.class, () -> client.series(janus));
    }

    /** Stands in for Janus: the catalog answers under the gateway slug only. */
    private static final class FakeFankaiServer {
        private static final String GATEWAY = "/gateway/fankai-metadata/";
        private final HttpServer server;
        private final Map<String, Response> responses = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
        private final Map<String, String> lastHeaders = new ConcurrentHashMap<>();

        private FakeFankaiServer(HttpServer server) {
            this.server = server;
        }

        static FakeFankaiServer start() throws IOException {
            HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            FakeFankaiServer fake = new FakeFankaiServer(http);
            http.createContext(GATEWAY, exchange -> {
                String path = exchange.getRequestURI().getPath().substring(GATEWAY.length() - 1);
                for (String header : List.of("X-Janus-Application-Id", "X-Janus-Api-Key")) {
                    fake.lastHeaders.put(header, String.valueOf(exchange.getRequestHeaders().getFirst(header)));
                }
                fake.hits.computeIfAbsent(path, key -> new AtomicInteger()).incrementAndGet();
                Response response = fake.responses.getOrDefault(path, new Response(404, "{}"));
                byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.getResponseHeaders().add("X-Janus-Correlation-Id", "corr-1");
                exchange.sendResponseHeaders(response.status(), body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            http.start();
            return fake;
        }

        void respond(String path, int status, String body) {
            responses.put(path, new Response(status, body));
        }

        int hits(String path) {
            AtomicInteger count = hits.get(path);
            return count == null ? 0 : count.get();
        }

        String lastHeader(String name) {
            return lastHeaders.get(name);
        }

        URI baseUri() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        }

        void stop() {
            server.stop(0);
        }

        private record Response(int status, String body) {
        }
    }
}
