package com.episort.tmdb;

import com.episort.config.JanusConfiguration;
import com.episort.workflow.ApplicationError;
import com.episort.workflow.ErrorSeverity;
import com.episort.workflow.TmdbConnectionTestResult;
import com.episort.workflow.TmdbConnectionTester;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class HttpTmdbConnectionTester implements TmdbConnectionTester {
    private final HttpClient httpClient;

    public HttpTmdbConnectionTester() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    HttpTmdbConnectionTester(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public TmdbConnectionTestResult test(JanusConfiguration configuration) {
        HttpRequest request = HttpRequest.newBuilder(configuration.tmdbBaseUri().resolve("authentication"))
                .timeout(Duration.ofSeconds(40))
                .header("Accept", "application/json")
                .header("X-Janus-Application-Id", configuration.applicationId())
                .header("X-Janus-Api-Key", configuration.apiKey())
                .GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300
                    && successfulValidationResponse(response.body())) {
                return TmdbConnectionTestResult.passed();
            }
            return TmdbConnectionTestResult.failure(failure("TMDB through Janus is unavailable."));
        } catch (IOException exception) {
            return TmdbConnectionTestResult.failure(failure("Janus is unavailable. Check your network."));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return TmdbConnectionTestResult.failure(failure("The Janus connection test was interrupted."));
        }
    }

    boolean successfulValidationResponse(String body) {
        return body != null && body.matches("(?s).*\"success\"\\s*:\\s*true.*");
    }

    private static ApplicationError failure(String message) {
        return ApplicationError.recoverable("TMDB_CONNECTION_FAILED", ErrorSeverity.BLOCKING, message,
                "Janus TMDB route check failed. Response body and caller key omitted.");
    }
}
