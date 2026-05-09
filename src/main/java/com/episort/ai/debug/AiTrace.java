package com.episort.ai.debug;

import java.time.Instant;

public record AiTrace(
        Instant timestamp,
        String source,
        String prompt,
        String response,
        long latencyMs,
        String error) {

    public boolean failed() {
        return error != null && !error.isBlank();
    }
}
