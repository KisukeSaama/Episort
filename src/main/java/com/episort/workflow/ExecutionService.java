package com.episort.workflow;

import com.episort.filesystem.MediaFileMover;
import com.episort.filesystem.WorkspaceBoundary;
import com.episort.persistence.ExecutionJournal;
import com.episort.persistence.ExecutionRunState;
import com.episort.planning.ApprovedPlan;
import com.episort.planning.PlannedOperation;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Executes an approved plan (Stories 7.2 to 7.5).
 *
 * <p>Contract: only files listed in the {@link ApprovedPlan} are touched, every
 * path is revalidated against the workspace boundary immediately before the
 * mutation, and a destination is overwritten only when the approved operation
 * carries the user's explicit replacement decision. The same holds for the one
 * operation that removes a file instead of moving it: it exists only where the
 * user chose to delete a duplicate in front of the exact plan. A per-file failure
 * is recorded and never inflates into a successful run.
 */
public final class ExecutionService {
    private static final String SORTING_PREFIX = "[TRI]";
    public static final String ERROR_DESTINATION_OCCUPIED = "EXECUTION_DESTINATION_OCCUPIED";
    public static final String ERROR_SOURCE_MISSING = "EXECUTION_SOURCE_MISSING";
    public static final String ERROR_FILE_LOCKED = "EXECUTION_FILE_LOCKED";
    public static final String ERROR_OUTSIDE_WORKSPACE = "EXECUTION_OUTSIDE_WORKSPACE";
    public static final String ERROR_IO = "EXECUTION_IO_ERROR";

    private final ExecutionJournal journal;

    public ExecutionService(ExecutionJournal journal) {
        this.journal = Objects.requireNonNull(journal, "journal");
    }

    public ExecutionReport execute(ApprovedPlan plan) throws IOException {
        return execute(plan, ExecutionFailureHandler.alwaysAbort(), ExecutionProgressListener.noop());
    }

    public ExecutionReport execute(
            ApprovedPlan plan,
            ExecutionFailureHandler failureHandler,
            ExecutionProgressListener progressListener) throws IOException {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(failureHandler, "failureHandler");
        Objects.requireNonNull(progressListener, "progressListener");

        WorkspaceBoundary boundary = new WorkspaceBoundary(plan.workspaceRoot());
        MediaFileMover mover = new MediaFileMover(boundary);

        int total = plan.size();
        journal.started(plan.id(), plan.workspaceRoot(), total);
        progressListener.onProgress(new ExecutionProgress(0, total, Optional.empty(), Optional.empty()));

        List<FileExecutionResult> results = new ArrayList<>(total);
        boolean aborted = false;
        int processed = 0;

        for (PlannedOperation operation : plan.operations()) {
            Path source = operation.sourcePath();
            // A deletion has no destination: it is the one approved operation that
            // makes a file go away instead of landing it somewhere.
            Path destination = operation.destinationPath().orElse(null);
            if (aborted) {
                results.add(FileExecutionResult.skipped(source, destination));
                continue;
            }
            progressListener.onProgress(new ExecutionProgress(
                    processed, total, Optional.of(source), operation.destinationPath()));

            FileExecutionResult result = null;
            int attempt = 0;
            while (result == null) {
                attempt++;
                try {
                    if (operation.deletesFile()) {
                        mover.deleteFile(source);
                        result = FileExecutionResult.deleted(source);
                    } else {
                        mover.move(source, Objects.requireNonNull(destination, "destination"),
                                operation.replaceExisting());
                        // The duplicate the user chose to retire goes only once the
                        // move has landed: never delete a copy before the file that
                        // replaces it is safely in place.
                        supersededDeletion(mover, operation).ifPresent(results::add);
                        result = FileExecutionResult.succeeded(source, destination);
                    }
                } catch (IOException exception) {
                    ApplicationError error = classify(exception);
                    ExecutionFailureDecision decision = failureHandler.onFailure(operation, error, attempt);
                    switch (decision) {
                        case RETRY -> {
                            // Loop again; the handler is responsible for bounding retries.
                        }
                        case CONTINUE -> result = FileExecutionResult.failed(source, destination, error);
                        case ABORT -> {
                            result = FileExecutionResult.failed(source, destination, error);
                            aborted = true;
                        }
                    }
                }
            }
            results.add(result);
            processed++;
            journal.progressed(plan.id(), plan.workspaceRoot(), total, processed);
            progressListener.onProgress(new ExecutionProgress(
                    processed, total, Optional.of(source), operation.destinationPath()));
        }

        SourceFolderCleanup cleanup = aborted
                ? SourceFolderCleanup.empty()
                : deleteEmptiedSourceFolders(plan, boundary, mover, results);

        ExecutionReport report = new ExecutionReport(
                plan.id(), plan.workspaceRoot(), results, aborted, Optional.of(journal.location()),
                cleanup.deleted(), cleanup.renamed());
        journal.finished(
                plan.id(), plan.workspaceRoot(), total, report.succeeded().size(), finalState(report));
        return report;
    }

    /**
     * Retires the duplicate copy an approved move supersedes.
     *
     * <p>A failure here is reported rather than swallowed: the move itself
     * succeeded, but the library is still holding two copies of the same media,
     * which is exactly what the user asked to stop.
     *
     * @return the extra result to record, empty when the operation retires nothing
     */
    private static Optional<FileExecutionResult> supersededDeletion(
            MediaFileMover mover, PlannedOperation operation) {
        Optional<Path> duplicate = operation.supersedes();
        if (duplicate.isEmpty()) {
            return Optional.empty();
        }
        Path target = duplicate.orElseThrow();
        try {
            mover.deleteFile(target);
            return Optional.of(FileExecutionResult.deleted(target));
        } catch (NoSuchFileException exception) {
            // Already gone: the outcome the deletion was after.
            return Optional.empty();
        } catch (IOException exception) {
            return Optional.of(FileExecutionResult.failed(target, null, classify(exception)));
        }
    }

    /**
     * Removes each source folder the run emptied out, deepest first, then walks
     * back up removing parents that are now empty.
     *
     * <p>A folder is only removed when every planned file it held actually moved
     * out. Anything the run still needs — a destination it wrote, a folder it
     * created, a source whose move failed or was skipped — makes that folder and
     * all of its ancestors off-limits, so a destination nested under a source
     * folder can never be destroyed by its own cleanup. The workspace root is
     * never a candidate.
     *
     * <p>Cleanup runs after the last move and never fails the run. An empty
     * source folder is removed; when it remains non-empty, its parent container
     * is renamed with {@code [TRI]}. A folder that cannot safely do either is
     * left in place.
     */
    private static SourceFolderCleanup deleteEmptiedSourceFolders(
            ApprovedPlan plan,
            WorkspaceBoundary boundary,
            MediaFileMover mover,
            List<FileExecutionResult> results) throws IOException {
        Path root = boundary.root();

        List<Path> preserved = new ArrayList<>();
        for (PlannedOperation operation : plan.operations()) {
            Optional<Path> destination = operation.destinationPath();
            if (destination.isPresent()) {
                canonicalize(boundary, destination.get()).ifPresent(preserved::add);
            }
        }
        for (Path folder : plan.foldersToCreate()) {
            canonicalize(boundary, folder).ifPresent(preserved::add);
        }
        for (FileExecutionResult result : results) {
            if (!result.successful()) {
                canonicalize(boundary, result.sourcePath()).ifPresent(preserved::add);
            }
        }

        // Deepest first, so a nested source folder goes before its parent.
        List<Path> candidates = new ArrayList<>();
        for (FileExecutionResult result : results) {
            if (!result.successful()) {
                continue;
            }
            Path parent = result.sourcePath().getParent();
            if (parent == null) {
                continue;
            }
            canonicalize(boundary, parent)
                    .filter(folder -> !candidates.contains(folder))
                    .ifPresent(candidates::add);
        }
        candidates.sort(Comparator.comparingInt(Path::getNameCount).reversed());

        List<Path> deleted = new ArrayList<>();
        List<FolderRenameResult> renamed = new ArrayList<>();
        for (Path candidate : candidates) {
            if (!isDeletable(candidate, root, preserved)) {
                continue;
            }
            try {
                if (mover.deleteFolderIfEmpty(candidate)) {
                    deleted.add(candidate);
                    deleteEmptiedAncestors(candidate, root, preserved, mover, deleted);
                } else if (Files.exists(candidate)) {
                    renameParentForSorting(candidate, root, preserved, mover).ifPresent(renamed::add);
                }
            } catch (IOException exception) {
                System.getLogger(ExecutionService.class.getName()).log(
                        System.Logger.Level.WARNING,
                        "Source folder could not be cleaned up after the move: " + candidate, exception);
            }
        }
        return new SourceFolderCleanup(deleted, renamed);
    }

    private static Optional<FolderRenameResult> renameParentForSorting(
            Path sourceFolder,
            Path root,
            List<Path> preserved,
            MediaFileMover mover)
            throws IOException {
        Path folder = sourceFolder.getParent();
        if (folder == null || !isDeletable(folder, root, preserved)) {
            return Optional.empty();
        }
        Path name = folder.getFileName();
        if (name == null || name.toString().startsWith(SORTING_PREFIX)) {
            return Optional.empty();
        }
        Path destination = folder.resolveSibling(SORTING_PREFIX + name);
        mover.renameFolder(folder, destination);
        return Optional.of(new FolderRenameResult(folder, destination));
    }

    private record SourceFolderCleanup(List<Path> deleted, List<FolderRenameResult> renamed) {
        private SourceFolderCleanup {
            deleted = List.copyOf(deleted);
            renamed = List.copyOf(renamed);
        }

        private static SourceFolderCleanup empty() {
            return new SourceFolderCleanup(List.of(), List.of());
        }
    }

    private static void deleteEmptiedAncestors(
            Path start, Path root, List<Path> preserved, MediaFileMover mover, List<Path> deleted)
            throws IOException {
        Path parent = start.getParent();
        while (parent != null && isDeletable(parent, root, preserved) && mover.deleteFolderIfEmpty(parent)) {
            deleted.add(parent);
            parent = parent.getParent();
        }
    }

    /** A folder may go only if nothing the run still needs lives under it. */
    private static boolean isDeletable(Path folder, Path root, List<Path> preserved) {
        if (folder.equals(root) || !folder.startsWith(root)) {
            return false;
        }
        return preserved.stream().noneMatch(path -> path.startsWith(folder));
    }

    private static Optional<Path> canonicalize(WorkspaceBoundary boundary, Path path) throws IOException {
        return boundary.resolvePlannedInside(path);
    }

    private static ExecutionRunState finalState(ExecutionReport report) {
        if (report.aborted()) {
            return ExecutionRunState.ABORTED;
        }
        return report.completeSuccess() ? ExecutionRunState.COMPLETED : ExecutionRunState.COMPLETED_WITH_FAILURES;
    }

    /**
     * Maps filesystem failures onto stable, typed errors. Only genuinely
     * transient problems are marked recoverable, so the UI does not offer a
     * retry that cannot possibly work.
     */
    static ApplicationError classify(IOException exception) {
        String detail = String.valueOf(exception.getMessage());
        if (exception instanceof FileAlreadyExistsException) {
            return new ApplicationError(ERROR_DESTINATION_OCCUPIED, ErrorSeverity.BLOCKING,
                    "The destination is already occupied; the file was left in place.", false, detail);
        }
        if (exception instanceof NoSuchFileException) {
            return new ApplicationError(ERROR_SOURCE_MISSING, ErrorSeverity.BLOCKING,
                    "The source file is no longer available.", false, detail);
        }
        if (exception instanceof AccessDeniedException) {
            return ApplicationError.recoverable(ERROR_FILE_LOCKED, ErrorSeverity.WARNING,
                    "The file is locked or access was denied; it can be retried.", detail);
        }
        if (detail.contains("outside the configured workspace")) {
            return new ApplicationError(ERROR_OUTSIDE_WORKSPACE, ErrorSeverity.BLOCKING,
                    "The path no longer resolves inside the configured workspace.", false, detail);
        }
        if (exception instanceof FileSystemException) {
            return ApplicationError.recoverable(ERROR_FILE_LOCKED, ErrorSeverity.WARNING,
                    "The file is temporarily unavailable; it can be retried.", detail);
        }
        return ApplicationError.recoverable(ERROR_IO, ErrorSeverity.WARNING,
                "The file operation failed; it can be retried.", detail);
    }
}
