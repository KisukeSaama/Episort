package com.episort.workflow;

import com.episort.config.JanusConfiguration;
import java.util.Objects;

/** Checks the Janus-backed TMDB route; Janus owns upstream credentials and resilience. */
public final class TmdbGatewayService {
    private final JanusConfiguration configuration;
    private final TmdbConnectionTester connectionTester;

    public TmdbGatewayService(JanusConfiguration configuration, TmdbConnectionTester connectionTester) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.connectionTester = Objects.requireNonNull(connectionTester, "connectionTester");
    }

    public TmdbGatewayStatus currentStatus() {
        TmdbConnectionTestResult result = connectionTester.test(configuration);
        return result.success()
                ? TmdbGatewayStatus.passed()
                : TmdbGatewayStatus.failure(result.error().orElseThrow());
    }

    public JanusConfiguration configuration() {
        return configuration;
    }
}
