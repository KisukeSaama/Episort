package com.episort.persistence;

import com.episort.filesystem.MediaFileFingerprint;
import java.nio.file.Path;
import java.util.Objects;

/** One successful move and the exact inverse needed to undo it. */
public record RollbackMove(
        Path originalPath,
        Path currentPath,
        MediaFileFingerprint fingerprint) {
    public RollbackMove {
        Objects.requireNonNull(originalPath, "originalPath");
        Objects.requireNonNull(currentPath, "currentPath");
        Objects.requireNonNull(fingerprint, "fingerprint");
    }
}
