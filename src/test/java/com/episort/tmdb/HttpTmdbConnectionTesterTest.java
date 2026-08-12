package com.episort.tmdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.config.JanusConfiguration;
import com.episort.workflow.TmdbConnectionTestResult;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;

class HttpTmdbConnectionTesterTest {
    @Test
    void requiresSuccessfulValidationResponse() {
        HttpTmdbConnectionTester tester = new HttpTmdbConnectionTester(HttpClient.newHttpClient());
        assertFalse(tester.successfulValidationResponse("{}"));
        assertTrue(tester.successfulValidationResponse("{\"success\":true}"));
    }

    @Test
    void validatesThroughJanusHeadersWithoutUpstreamAuthorization() throws Exception {
        Captured captured = new Captured();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/gateway/tmdb-v3/authentication", exchange -> {
            captured.applicationId = exchange.getRequestHeaders().getFirst("X-Janus-Application-Id");
            captured.apiKey = exchange.getRequestHeaders().getFirst("X-Janus-Api-Key");
            captured.authorization = exchange.getRequestHeaders().getFirst("Authorization");
            byte[] body = "{\"success\":true}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            JanusConfiguration configuration = new JanusConfiguration(
                    URI.create("http://localhost:" + server.getAddress().getPort()), "episort-id", "caller-key");
            TmdbConnectionTestResult result = new HttpTmdbConnectionTester(HttpClient.newHttpClient()).test(configuration);
            assertTrue(result.success());
            assertEquals("episort-id", captured.applicationId);
            assertEquals("caller-key", captured.apiKey);
            assertNull(captured.authorization);
        } finally {
            server.stop(0);
        }
    }

    private static final class Captured {
        String applicationId;
        String apiKey;
        String authorization;
    }
}
