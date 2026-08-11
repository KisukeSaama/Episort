package com.episort.workflow;

import com.episort.logging.SecretRedactor;

public record ApplicationError(
        String code,
        ErrorSeverity severity,
        String message,
        boolean recoverable,
        String details) {
    public static ApplicationError recoverable(
            String code, ErrorSeverity severity, String message, String details) {
        return new ApplicationError(code, severity, message, true, details);
    }

    public String safeMessage() {
        return new SecretRedactor().redact(message);
    }
}
