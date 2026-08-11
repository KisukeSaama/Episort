package com.episort.workflow;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * What actually happened to one file (Story 7.3).
 *
 * @param error present only for failures; carries a stable code and whether a
 *              retry could plausibly succeed
 */
public record FileExecutionResult(
        Path sourcePath,
        Optional<Path> destinationPath,
        FileExecutionStatus status,
        Optional<ApplicationError> error) {

    public FileExecutionResult {
        Objects.requireNonNull(sourcePath, "sourcePath");
        Objects.requireNonNull(destinationPath, "destinationPath");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(error, "error");
    }

    public static FileExecutionResult succeeded(Path source, Path destination) {
        boolean sameFolder = Objects.equals(source.getParent(), destination.getParent());
        return new FileExecutionResult(
                source,
                Optional.of(destination),
                sameFolder ? FileExecutionStatus.RENAMED : FileExecutionStatus.MOVED,
                Optional.empty());
    }

    /** The file is gone, as the user asked while resolving its conflict. */
    public static FileExecutionResult deleted(Path source) {
        return new FileExecutionResult(
                source, Optional.empty(), FileExecutionStatus.DELETED, Optional.empty());
    }

    public static FileExecutionResult failed(Path source, Path destination, ApplicationError error) {
        return new FileExecutionResult(
                source, Optional.ofNullable(destination), FileExecutionStatus.FAILED, Optional.of(error));
    }

    public static FileExecutionResult skipped(Path source, Path destination) {
        return new FileExecutionResult(
                source, Optional.ofNullable(destination), FileExecutionStatus.SKIPPED, Optional.empty());
    }

    public static FileExecutionResult untouched(Path source) {
        return new FileExecutionResult(source, Optional.empty(), FileExecutionStatus.UNTOUCHED, Optional.empty());
    }

    /** True when the approved operation did what it said it would do. */
    public boolean successful() {
        return status == FileExecutionStatus.MOVED
                || status == FileExecutionStatus.RENAMED
                || status == FileExecutionStatus.DELETED;
    }
}
