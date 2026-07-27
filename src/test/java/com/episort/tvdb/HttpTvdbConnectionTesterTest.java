package com.episort.tvdb;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.config.TvdbCredentials;
import com.episort.workflow.TvdbConnectionTestResult;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HttpTvdbConnectionTesterTest {
    @Test
    void requiresTokenInSuccessfulLoginResponse() {
        HttpTvdbConnectionTester tester = new HttpTvdbConnectionTester(
                HttpClient.newHttpClient(), URI.create("http://localhost/unused"));

        assertFalse(tester.successfulLoginResponse("{}"));
        assertFalse(tester.successfulLoginResponse(""));
        assertTrue(tester.successfulLoginResponse("{\"data\":{\"token\":\"abc\"}}"));
    }

    @Test
    void sendsLoginRequestWithApiKeyAndPin() throws Exception {
        CapturedRequest capturedRequest = new CapturedRequest();
        HttpServer server = startServer(capturedRequest, "{\"data\":{\"token\":\"abc\"}}", 200);
        try {
            HttpTvdbConnectionTester tester = new HttpTvdbConnectionTester(
                    HttpClient.newHttpClient(),
                    URI.create("http://localhost:" + server.getAddress().getPort() + "/v4/login"));

            TvdbConnectionTestResult result = tester.test(new TvdbCredentials("api-key", Optional.of("pin")));

            assertTrue(result.success());
            assertTrue(capturedRequest.body.contains("\"apikey\":\"api-key\""));
            assertTrue(capturedRequest.body.contains("\"pin\":\"pin\""));
            assertTrue(capturedRequest.contentType.contains("application/json"));
        } finally {
            server.stop(0);
        }
    }

    private HttpServer startServer(CapturedRequest capturedRequest, String responseBody, int statusCode) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v4/login", exchange -> {
            capturedRequest.contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            capturedRequest.body = new String(exchange.getRequestBody().readAllBytes());
            byte[] response = responseBody.getBytes();
            exchange.sendResponseHeaders(statusCode, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static final class CapturedRequest {
        private String body = "";
        private String contentType = "";
    }
}
