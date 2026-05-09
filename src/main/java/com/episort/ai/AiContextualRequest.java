package com.episort.ai;

import java.util.Objects;

public record AiContextualRequest(AiContextualSelection selection) {
    public AiContextualRequest {
        Objects.requireNonNull(selection, "selection");
    }
}
