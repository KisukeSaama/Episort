package com.episort.workflow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.config.JanusConfiguration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TmdbGatewayServiceTest {
    private final JanusConfiguration configuration = new JanusConfiguration("caller-key", Optional.empty());

    @Test
    void reportsAvailableWhenJanusTmdbRoutePasses() {
        TmdbGatewayService service = new TmdbGatewayService(
                configuration, ignored -> TmdbConnectionTestResult.passed());
        assertTrue(service.currentStatus().organizationAllowed());
    }

    @Test
    void reportsUnavailableWhenJanusTmdbRouteFails() {
        ApplicationError error = ApplicationError.recoverable(
                "TMDB_CONNECTION_FAILED", ErrorSeverity.BLOCKING, "Unavailable", "Janus failed");
        TmdbGatewayService service = new TmdbGatewayService(
                configuration, ignored -> TmdbConnectionTestResult.failure(error));
        assertFalse(service.currentStatus().organizationAllowed());
    }
}
