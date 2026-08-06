package com.episort.workflow;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The post-execution recap (Story 7.5).
 *
 * <p>Holds per-file outcomes and paths only. TVDB credentials, tokens, and PINs
 * never enter this object, and failure details go through
 * {@link ApplicationError#safeMessage()} before display.
 *
 * @param journalLocation where the diagnostic journal was written, so the user
 *                        can find it after a failure
 * @param deletedSourceFolders the source folders removed once every file they
 *                             held had moved out, deepest first
 * @param renamedSourceFolders parent containers of non-empty source folders
 *                             renamed with `[TRI]` after successful operations
 */
public record ExecutionReport(
        UUID runId,
        Path workspaceRoot,
        List<FileExecutionResult> results,
        boolean aborted,
        Optional<Path> journalLocation,
        List<Path> deletedSourceFolders,
        List<FolderRenameResult> renamedSourceFolders) {

    public ExecutionReport {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        Objects.requireNonNull(journalLocation, "journalLocation");
        results = List.copyOf(results);
        deletedSourceFolders = List.copyOf(deletedSourceFolders);
        renamedSourceFolders = List.copyOf(renamedSourceFolders);
    }

    public ExecutionReport(
            UUID runId,
            Path workspaceRoot,
            List<FileExecutionResult> results,
            boolean aborted,
            Optional<Path> journalLocation) {
        this(runId, workspaceRoot, results, aborted, journalLocation, List.of(), List.of());
    }

    public ExecutionReport(
            UUID runId,
            Path workspaceRoot,
            List<FileExecutionResult> results,
            boolean aborted,
            Optional<Path> journalLocation,
            List<Path> deletedSourceFolders) {
        this(runId, workspaceRoot, results, aborted, journalLocation, deletedSourceFolders, List.of());
    }

    public List<FileExecutionResult> succeeded() {
        return results.stream().filter(FileExecutionResult::successful).toList();
    }

    public List<FileExecutionResult> failed() {
        return withStatus(FileExecutionStatus.FAILED);
    }

    public List<FileExecutionResult> skipped() {
        return withStatus(FileExecutionStatus.SKIPPED);
    }

    public List<FileExecutionResult> untouched() {
        return withStatus(FileExecutionStatus.UNTOUCHED);
    }

    public List<FileExecutionResult> moved() {
        return withStatus(FileExecutionStatus.MOVED);
    }

    public List<FileExecutionResult> renamed() {
        return withStatus(FileExecutionStatus.RENAMED);
    }

    public List<FileExecutionResult> deleted() {
        return withStatus(FileExecutionStatus.DELETED);
    }

    /** True only when every approved file was moved or renamed. */
    public boolean completeSuccess() {
        return !aborted
                && !results.isEmpty()
                && results.stream().allMatch(FileExecutionResult::successful);
    }

    /** True when some work landed but some did not: never reported as success. */
    public boolean partialSuccess() {
        return !completeSuccess() && results.stream().anyMatch(FileExecutionResult::successful);
    }

    private List<FileExecutionResult> withStatus(FileExecutionStatus status) {
        return results.stream().filter(result -> result.status() == status).toList();
    }
}
