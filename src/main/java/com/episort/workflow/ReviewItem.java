package com.episort.workflow;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

public record ReviewItem(
        Path sourcePath,
        Optional<String> proposedIdentity,
        ReviewMatchState matchState,
        OptionalDouble confidence,
        boolean ignored,
        boolean unsupported,
        boolean blockingConflict) {
    public ReviewItem {
        Objects.requireNonNull(sourcePath, "sourcePath");
        Objects.requireNonNull(proposedIdentity, "proposedIdentity");
        Objects.requireNonNull(matchState, "matchState");
        Objects.requireNonNull(confidence, "confidence");
    }
}
