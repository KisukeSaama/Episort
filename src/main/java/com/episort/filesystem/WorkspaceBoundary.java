package com.episort.filesystem;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public final class WorkspaceBoundary {
    private final Path workspaceRoot;

    public WorkspaceBoundary(Path workspaceRoot) throws IOException {
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        this.workspaceRoot = workspaceRoot.toRealPath();
    }

    public Optional<Path> resolveInside(Path candidate) throws IOException {
        Objects.requireNonNull(candidate, "candidate");
        Path realCandidate = candidate.toRealPath();
        return realCandidate.startsWith(workspaceRoot) ? Optional.of(realCandidate) : Optional.empty();
    }

    public boolean contains(Path candidate) throws IOException {
        return resolveInside(candidate).isPresent();
    }
}
