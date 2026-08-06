package com.episort.filesystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;

public final class WorkspaceBoundary {
    private final Path configuredWorkspaceRoot;
    private final Path workspaceRoot;

    public WorkspaceBoundary(Path workspaceRoot) throws IOException {
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        this.configuredWorkspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.workspaceRoot = configuredWorkspaceRoot.toRealPath();
    }

    public Path root() {
        return workspaceRoot;
    }

    public Optional<Path> resolveInside(Path candidate) throws IOException {
        Objects.requireNonNull(candidate, "candidate");
        if (traversesSymbolicLink(candidate)) {
            return Optional.empty();
        }
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
        if (traversesSymbolicLink(candidate)) {
            return Optional.empty();
        }
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

    /**
     * Detects links below the selected workspace root without rejecting a
     * workspace that the user deliberately selected through a linked path.
     * Every descendant component is checked, including a missing destination's
     * existing ancestors.
     */
    private boolean traversesSymbolicLink(Path candidate) {
        Path cursor = candidate.toAbsolutePath().normalize();
        while (cursor != null
                && !cursor.equals(configuredWorkspaceRoot)
                && !cursor.equals(workspaceRoot)) {
            if (isLinkLike(cursor)) {
                return true;
            }
            cursor = cursor.getParent();
        }
        return false;
    }

    private static boolean isLinkLike(Path path) {
        if (Files.isSymbolicLink(path)) {
            return true;
        }
        try {
            return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isOther();
        } catch (NoSuchFileException exception) {
            return false;
        } catch (IOException | SecurityException exception) {
            return true;
        }
    }
}
