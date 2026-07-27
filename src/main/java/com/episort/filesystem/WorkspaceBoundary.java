package com.episort.filesystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;

public final class WorkspaceBoundary {
    private final Path workspaceRoot;

    public WorkspaceBoundary(Path workspaceRoot) throws IOException {
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        this.workspaceRoot = workspaceRoot.toRealPath();
    }

    public Path root() {
        return workspaceRoot;
    }

    public Optional<Path> resolveInside(Path candidate) throws IOException {
        Objects.requireNonNull(candidate, "candidate");
        Path realCandidate = candidate.toRealPath();
        return realCandidate.startsWith(workspaceRoot) ? Optional.of(realCandidate) : Optional.empty();
    }

    public boolean contains(Path candidate) throws IOException {
        return resolveInside(candidate).isPresent();
    }

    /**
     * Boundary check for a path that does not exist yet — a planned destination
     * folder or file.
     *
     * <p>The deepest existing ancestor is resolved to its real path before the
     * remaining segments are re-attached, so a symlink or junction planted
     * inside the workspace cannot smuggle a destination outside of it.
     *
     * @return the canonical planned path when it stays inside the workspace
     */
    public Optional<Path> resolvePlannedInside(Path candidate) throws IOException {
        Objects.requireNonNull(candidate, "candidate");
        Path normalized = candidate.toAbsolutePath().normalize();
        Deque<Path> missingSegments = new ArrayDeque<>();
        Path existing = normalized;
        while (existing != null && !Files.exists(existing)) {
            Path name = existing.getFileName();
            if (name == null) {
                return Optional.empty();
            }
            missingSegments.push(name);
            existing = existing.getParent();
        }
        if (existing == null) {
            return Optional.empty();
        }
        Path resolved = existing.toRealPath();
        for (Path segment : missingSegments) {
            resolved = resolved.resolve(segment);
        }
        resolved = resolved.normalize();
        return resolved.startsWith(workspaceRoot) ? Optional.of(resolved) : Optional.empty();
    }

    /** True when a not-yet-created path would stay inside the workspace. */
    public boolean containsPlanned(Path candidate) throws IOException {
        return resolvePlannedInside(candidate).isPresent();
    }
}
