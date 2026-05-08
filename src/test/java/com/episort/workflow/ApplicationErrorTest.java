package com.episort.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ApplicationErrorTest {
    @Test
    void recoverableErrorKeepsUiSafeMessageSeparateFromDetails() {
        ApplicationError error = ApplicationError.recoverable(
                "TVDB_AUTH_MISSING",
                ErrorSeverity.BLOCKING,
                "TVDB configuration is required before organization can start.",
                "Bearer token abc.def.ghi failed during startup");

        assertEquals("TVDB_AUTH_MISSING", error.code());
        assertEquals(ErrorSeverity.BLOCKING, error.severity());
        assertTrue(error.recoverable());
        assertEquals("TVDB configuration is required before organization can start.", error.message());
        assertFalse(error.safeMessage().contains("abc.def.ghi"));
    }

    @Test
    void safeMessageRedactsSecretsAccidentallyPlacedInMessage() {
        ApplicationError error = ApplicationError.recoverable(
                "TVDB_AUTH_FAILED",
                ErrorSeverity.BLOCKING,
                "TVDB failed with apiKey=abc123 Authorization: Bearer ey.secret.token",
                "Adapter error");

        assertFalse(error.safeMessage().contains("abc123"));
        assertFalse(error.safeMessage().contains("ey.secret.token"));
        assertTrue(error.safeMessage().contains("[REDACTED]"));
    }
}
