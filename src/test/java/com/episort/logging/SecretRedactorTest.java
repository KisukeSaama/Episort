package com.episort.logging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SecretRedactorTest {
    @Test
    void redactsKnownCredentialShapes() {
        SecretRedactor redactor = new SecretRedactor();

        String redacted = redactor.redact(
                "apiKey=abc123 readAccessToken=read-secret Authorization: Bearer ey.secret.token token=raw-token password=hidden");

        assertFalse(redacted.contains("abc123"));
        assertFalse(redacted.contains("read-secret"));
        assertFalse(redacted.contains("ey.secret.token"));
        assertFalse(redacted.contains("raw-token"));
        assertFalse(redacted.contains("hidden"));
        assertTrue(redacted.contains("[REDACTED]"));
    }

    @Test
    void redactsJsonCredentialShapes() {
        SecretRedactor redactor = new SecretRedactor();

        String redacted = redactor.redact(
                "{\"apiKey\":\"abc123\",\"readAccessToken\":\"read-secret\",\"token\":\"raw-token\",\"authorization\":\"Bearer ey.secret.token\"}");

        assertFalse(redacted.contains("abc123"));
        assertFalse(redacted.contains("read-secret"));
        assertFalse(redacted.contains("raw-token"));
        assertFalse(redacted.contains("ey.secret.token"));
        assertTrue(redacted.contains("[REDACTED]"));
    }

    @Test
    void handlesNullMessages() {
        assertNull(new SecretRedactor().redact(null));
    }
}
