package com.episort.workflow;

import java.nio.file.Path;
import java.util.Objects;

/** A non-empty source folder marked for manual sorting after execution. */
public record FolderRenameResult(Path sourcePath, Path destinationPath) {
    public FolderRenameResult {
        Objects.requireNonNull(sourcePath, "sourcePath");
        Objects.requireNonNull(destinationPath, "destinationPath");
    }
}
