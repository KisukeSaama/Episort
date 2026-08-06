package com.episort.tvdb;

import com.episort.config.TvdbCredentials;
import com.episort.workflow.ApplicationError;
import com.episort.workflow.ErrorSeverity;
import com.episort.workflow.TvdbConnectionTestResult;
import com.episort.workflow.TvdbConnectionTester;
import com.episort.tvdb.guard.TvdbRequestScheduler;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class HttpTvdbConnectionTester implements TvdbConnectionTester {
    private static final URI LOGIN_ENDPOINT = URI.create("https://api4.thetvdb.com/v4/login");

    private final HttpClient httpClient;
    private final URI loginEndpoint;
    private final TvdbRequestScheduler requestScheduler;

    public HttpTvdbConnectionTester() {
        this(new TvdbRequestScheduler());
    }

    public HttpTvdbConnectionTester(TvdbRequestScheduler requestScheduler) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                LOGIN_ENDPOINT, requestScheduler);
    }

    HttpTvdbConnectionTester(HttpClient httpClient, URI loginEndpoint) {
        this(httpClient, loginEndpoint, TvdbRequestScheduler.unthrottled());
    }

    HttpTvdbConnectionTester(
            HttpClient httpClient,
            URI loginEndpoint,
            TvdbRequestScheduler requestScheduler) {
        this.httpClient = httpClient;
        this.loginEndpoint = loginEndpoint;
        this.requestScheduler = requestScheduler;
    }

    @Override
    public TvdbConnectionTestResult test(TvdbCredentials credentials) {
        HttpRequest request = HttpRequest.newBuilder(loginEndpoint)
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(loginBody(credentials)))
                .build();

        try {
            HttpResponse<String> response = requestScheduler.execute(
                    () -> httpClient.send(request, HttpResponse.BodyHandlers.ofString()));
            if (response.statusCode() >= 200 && response.statusCode() < 300 && successfulLoginResponse(response.body())) {
                return TvdbConnectionTestResult.passed();
            }
            return TvdbConnectionTestResult.failure(tvdbFailure("TVDB authentication failed."));
        } catch (IOException exception) {
            return TvdbConnectionTestResult.failure(tvdbFailure("TVDB is unavailable. Check your network and credentials."));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return TvdbConnectionTestResult.failure(tvdbFailure("TVDB connection test was interrupted."));
        } catch (Exception exception) {
            return TvdbConnectionTestResult.failure(tvdbFailure("TVDB is unavailable. Check your network and credentials."));
        }
    }

    private String loginBody(TvdbCredentials credentials) {
        StringBuilder builder = new StringBuilder("{\"apikey\":\"")
                .append(escapeJson(credentials.apiKey()))
                .append("\"");
        credentials.subscriberPin()
                .ifPresent(pin -> builder.append(",\"pin\":\"").append(escapeJson(pin)).append("\""));
        return builder.append("}").toString();
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    boolean successfulLoginResponse(String responseBody) {
        return responseBody != null && responseBody.matches("(?s).*\"token\"\\s*:\\s*\"[^\"]+\".*");
    }

    private ApplicationError tvdbFailure(String message) {
        return ApplicationError.recoverable(
                "TVDB_CONNECTION_FAILED",
                ErrorSeverity.BLOCKING,
                message,
                "TVDB connection test failed. Credentials and response body are not included in diagnostics.");
    }
}
