package com.episort.filesystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WorkspaceBoundary {
    private final Path workspaceRoot;

    public WorkspaceBoundary(Path workspaceRoot) {
        this.workspaceRoot = realPath(workspaceRoot);
    }

    public boolean contains(Path candidate) {
        try {
            Path realCandidate = candidate.toRealPath();
            return realCandidate.startsWith(workspaceRoot);
        } catch (IOException exception) {
            return false;
        }
    }

    private Path realPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException exception) {
            return path.toAbsolutePath().normalize();
        }
    }
}
