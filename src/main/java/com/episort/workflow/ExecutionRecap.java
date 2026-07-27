package com.episort.workflow;

import com.episort.planning.OperationPlan;
import com.episort.planning.PlanExclusionReason;
import com.episort.planning.PlannedOperation;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The complete post-execution picture (Story 7.5): what moved, what failed, and
 * everything the run deliberately left alone.
 *
 * <p>Combining the plan with the report is what makes "untouched" visible — the
 * executor only ever sees approved moves, so on its own it cannot report the
 * files it never had.
 */
public record ExecutionRecap(OperationPlan plan, ExecutionReport report) {

    public ExecutionRecap {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(report, "report");
    }

    public List<FileExecutionResult> moved() {
        return report.moved();
    }

    public List<FileExecutionResult> renamed() {
        return report.renamed();
    }

    /** Files the run removed, on decisions the user took in front of the plan. */
    public List<FileExecutionResult> deleted() {
        return report.deleted();
    }

    public List<FileExecutionResult> failed() {
        return report.failed();
    }

    public List<FileExecutionResult> skipped() {
        return report.skipped();
    }

    /** Files the run never opened: excluded by the plan or already in place. */
    public List<FileExecutionResult> untouched() {
        List<FileExecutionResult> untouched = new ArrayList<>(report.untouched());
        for (PlannedOperation operation : plan.excludedOperations()) {
            untouched.add(FileExecutionResult.untouched(operation.sourcePath()));
        }
        for (PlannedOperation operation : plan.alreadyInPlaceOperations()) {
            untouched.add(FileExecutionResult.untouched(operation.sourcePath()));
        }
        return List.copyOf(untouched);
    }

    /** Source folders removed once everything they held had moved out. */
    public List<Path> deletedSourceFolders() {
        return report.deletedSourceFolders();
    }

    public List<Path> ignoredFiles() {
        return excludedFor(PlanExclusionReason.IGNORED);
    }

    public List<Path> unsupportedFiles() {
        return excludedFor(PlanExclusionReason.UNSUPPORTED);
    }

    public List<Path> duplicateExcludedFiles() {
        return excludedFor(PlanExclusionReason.DUPLICATE);
    }

    public List<Path> unassignedFiles() {
        return excludedFor(PlanExclusionReason.UNASSIGNED);
    }

    public boolean completeSuccess() {
        return report.completeSuccess();
    }

    public boolean partialSuccess() {
        return report.partialSuccess();
    }

    public boolean aborted() {
        return report.aborted();
    }

    /** Where to look when something went wrong. */
    public Optional<Path> diagnosticLocation() {
        return report.journalLocation();
    }

    /**
     * Actionable next step for each failure, derived from the typed error rather
     * than from raw exception text.
     */
    public List<String> recoveryHints() {
        return report.failed().stream()
                .map(FileExecutionResult::error)
                .flatMap(Optional::stream)
                .map(error -> error.recoverable()
                        ? error.code() + ": retry once the file is available again"
                        : error.code() + ": resolve the conflict, then regenerate and revalidate the plan")
                .distinct()
                .toList();
    }

    private List<Path> excludedFor(PlanExclusionReason reason) {
        return plan.excludedOperations().stream()
                .filter(operation -> operation.exclusionReason() == reason)
                .map(PlannedOperation::sourcePath)
                .toList();
    }
}
