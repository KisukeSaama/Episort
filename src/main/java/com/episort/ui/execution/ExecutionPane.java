package com.episort.ui.execution;

import com.episort.planning.ApprovedPlan;
import com.episort.planning.OperationPlan;
import com.episort.planning.PlannedOperation;
import com.episort.ui.AppLanguage;
import com.episort.ui.SmoothScroll;
import com.episort.ui.UiText;
import com.episort.workflow.ApplicationError;
import com.episort.workflow.ExecutionFailureDecision;
import com.episort.workflow.ExecutionRecap;
import com.episort.workflow.ExecutionReport;
import com.episort.workflow.ExecutionService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javafx.application.Platform;
import com.episort.ui.FxUpdateCoalescer;
import com.episort.workflow.ExecutionProgress;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Runs an approved plan and reports what happened (Stories 7.3, 7.4, 7.5).
 *
 * <p>A detached body rather than a window of its own: the plan review swaps it in
 * once the user validates, so approving and running stay a single sitting.
 *
 * <p>Two phases in one pane. While the run is in flight the pane is a readout of
 * one thing — the operation being carried out right now, named by its two paths,
 * under the count of what is done out of what was approved. Source and destination
 * are both written because a move is a pair: a progress bar over a lone filename
 * says work is happening but not what it is doing to the library.
 *
 * <p>Per-file failures pause the run and ask the user to retry, continue, or stop;
 * whatever they answer, the file stays listed under the progress for the rest of
 * the run. A failure the user waved through is still a failure, and it must not
 * disappear with the banner that announced it.
 *
 * <p>The recap then splits what changed on disk from what the run left alone.
 * Twelve counters of equal weight is a table of numbers, not an answer to "what
 * happened to my files"; the five that mean the disk is different now come first,
 * and the seven that mean nothing moved sit under their own heading.
 */
public final class ExecutionPane {
    private final AppLanguage language;
    private final OperationPlan plan;
    private final ApprovedPlan approvedPlan;
    private final ExecutionService executionService;

    private final Label countDone = new Label("0");
    private final Label countTotal = new Label();
    private final Label percent = new Label();
    private final ProgressBar progressBar = new ProgressBar(0);
    private final FxUpdateCoalescer<ExecutionProgress> progressUpdates =
            new FxUpdateCoalescer<>(Platform::runLater, this::applyProgress);
    private final Label sourcePath = new Label(UiText.EMPTY);
    private final Label destinationPath = new Label(UiText.EMPTY);
    private final VBox readout;
    private final VBox failurePanel = new VBox(8);
    private final Label failureMessage = new Label();
    private final Button retryButton = new Button();
    private final Button abortButton = new Button();
    private final VBox failureLog = new VBox(4);
    private final VBox recapPanel = new VBox(14);
    private final ScrollPane recapScroll;
    private final VBox root;

    private Optional<ExecutionRecap> recap = Optional.empty();

    public ExecutionPane(
            AppLanguage language,
            OperationPlan plan,
            ApprovedPlan approvedPlan,
            ExecutionService executionService) {
        this.language = language;
        this.plan = plan;
        this.approvedPlan = approvedPlan;
        this.executionService = executionService;

        readout = buildReadout();
        buildFailurePanel();

        failureLog.getStyleClass().add("exec-failure-log");
        setVisible(failureLog, false);

        recapPanel.getStyleClass().add("exec-recap");
        recapScroll = new ScrollPane(recapPanel);
        recapScroll.setFitToWidth(true);
        recapScroll.getStyleClass().add("content-scroll");
        SmoothScroll.install(recapScroll);
        setVisible(recapScroll, false);

        root = new VBox(18, readout, failurePanel, failureLog, recapScroll);
        VBox.setVgrow(recapScroll, Priority.ALWAYS);
    }

    public VBox root() {
        return root;
    }

    /** @return the recap once the run finished, empty while it is still running */
    public Optional<ExecutionRecap> recap() {
        return recap;
    }

    /**
     * Starts the run on a background thread. {@code onFinished} fires on the FX
     * thread once the recap is on screen, or empty when the run died outright.
     */
    public void start(Consumer<Optional<ExecutionRecap>> onFinished) {
        countTotal.setText("/ " + approvedPlan.size());
        percent.setText(percentText(0));
        CompletableFuture
                .supplyAsync(this::runExecution)
                .whenComplete((report, throwable) -> Platform.runLater(() -> {
                    if (throwable != null) {
                        showFatalFailure(throwable);
                        onFinished.accept(Optional.empty());
                        return;
                    }
                    showRecap(new ExecutionRecap(plan, report));
                    onFinished.accept(recap);
                }));
    }

    /* ---- Progress ------------------------------------------------------ */

    /**
     * The live half of the pane: how far the run is, and the exact pair of paths
     * the current operation is about to write.
     */
    private VBox buildReadout() {
        Label heading = new Label(UiText.execProgressLabel(language));
        heading.getStyleClass().add("section-heading");

        countDone.getStyleClass().add("exec-count");
        countTotal.getStyleClass().add("exec-count-total");
        countTotal.setText("/ " + approvedPlan.size());
        percent.getStyleClass().add("exec-percent");
        percent.setText(percentText(0));

        Region countSpacer = new Region();
        HBox.setHgrow(countSpacer, Priority.ALWAYS);
        HBox counter = new HBox(8, countDone, countTotal, countSpacer, percent);
        counter.setAlignment(Pos.BASELINE_LEFT);

        progressBar.getStyleClass().add("episort-progress");
        progressBar.setMaxWidth(Double.MAX_VALUE);

        VBox flight = new VBox(10,
                pathField(UiText.execFieldSource(language), sourcePath),
                pathField(UiText.execFieldDestination(language), destinationPath));
        flight.getStyleClass().add("exec-flight");

        VBox block = new VBox(14, heading, counter, progressBar, flight);
        block.getStyleClass().add("exec-readout");
        return block;
    }

    private static VBox pathField(String caption, Label value) {
        Label label = new Label(caption);
        label.getStyleClass().add("exec-label");
        value.getStyleClass().add("exec-path");
        return new VBox(4, label, value);
    }

    private ExecutionReport runExecution() {
        try {
            return executionService.execute(approvedPlan, this::askUser, progressUpdates::submit);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void applyProgress(ExecutionProgress progress) {
        progressBar.setProgress(progress.fraction());
        countDone.setText(String.valueOf(progress.completed()));
        countTotal.setText("/ " + progress.total());
        percent.setText(percentText(progress.fraction()));
        setPath(sourcePath, progress.currentSource());
        setPath(destinationPath, progress.currentDestination());
    }

    /**
     * Paths are long and the pane is not: the cell shows what fits and the tooltip
     * keeps the whole thing, the same bargain the tables strike.
     */
    private static void setPath(Label label, Optional<Path> path) {
        String text = path.map(Path::toString).orElse(UiText.EMPTY);
        label.setText(text);
        label.getStyleClass().remove("exec-path-empty");
        if (path.isEmpty()) {
            label.getStyleClass().add("exec-path-empty");
            label.setTooltip(null);
            return;
        }
        label.setTooltip(new Tooltip(text));
    }

    private static String percentText(double fraction) {
        return Math.round(Math.max(0, Math.min(1, fraction)) * 100) + " %";
    }

    /* ---- Failures ------------------------------------------------------ */

    private void buildFailurePanel() {
        Label failureTitle = new Label(UiText.execFailureTitle(language));
        failureTitle.getStyleClass().add("banner-icon");
        failureMessage.getStyleClass().add("banner-text");
        failureMessage.setWrapText(true);

        retryButton.setText(UiText.execFailureRetry(language));
        retryButton.getStyleClass().add("primary");
        abortButton.setText(UiText.execFailureAbort(language));
        abortButton.getStyleClass().addAll("ghost", "danger");

        HBox failureActions = new HBox(8, retryButton, abortButton);
        failureActions.setAlignment(Pos.CENTER_LEFT);

        failurePanel.getChildren().setAll(failureTitle, failureMessage, failureActions);
        failurePanel.getStyleClass().addAll("banner", "banner-warn");
        setVisible(failurePanel, false);
    }

    /**
     * Blocks the execution thread until the user chooses. Retry stays available
     * only for errors that could plausibly succeed on a second attempt.
     */
    private ExecutionFailureDecision askUser(
            PlannedOperation operation, ApplicationError error, int attempt) {
        AtomicReference<ExecutionFailureDecision> decision = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            failureMessage.setText(operation.sourcePath() + "\n" + error.safeMessage());
            retryButton.setDisable(!error.recoverable());
            retryButton.setOnAction(event -> resolve(decision, latch, operation, error, ExecutionFailureDecision.RETRY));
            abortButton.setOnAction(event -> resolve(decision, latch, operation, error, ExecutionFailureDecision.ABORT));
            setVisible(failurePanel, true);
        });
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ExecutionFailureDecision.ABORT;
        }
        return decision.get();
    }

    private void resolve(
            AtomicReference<ExecutionFailureDecision> holder,
            CountDownLatch latch,
            PlannedOperation operation,
            ApplicationError error,
            ExecutionFailureDecision decision) {
        holder.set(decision);
        setVisible(failurePanel, false);
        // A retry is not yet a failure: the file may still land. Anything else
        // ends the file's story, so it is written down where it stays readable
        // for the rest of the run.
        if (decision != ExecutionFailureDecision.RETRY) {
            logFailure(operation, error);
        }
        latch.countDown();
    }

    private void logFailure(PlannedOperation operation, ApplicationError error) {
        if (failureLog.getChildren().isEmpty()) {
            Label heading = new Label(UiText.execFailureLog(language));
            heading.getStyleClass().add("section-heading");
            failureLog.getChildren().add(heading);
        }
        Label line = new Label(error.code() + "  " + operation.sourcePath());
        line.getStyleClass().add("exec-failed-line");
        line.setTooltip(new Tooltip(operation.sourcePath() + "\n" + error.safeMessage()));
        failureLog.getChildren().add(line);
        setVisible(failureLog, true);
    }

    private void showFatalFailure(Throwable throwable) {
        setVisible(failurePanel, true);
        retryButton.setDisable(true);
        abortButton.setDisable(true);
        Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
        failureMessage.setText(String.valueOf(cause.getMessage()));
    }

    /* ---- Recap --------------------------------------------------------- */

    private void showRecap(ExecutionRecap executionRecap) {
        this.recap = Optional.of(executionRecap);
        progressBar.setProgress(1);
        setVisible(failurePanel, false);
        // The live readout has nothing left to say: the run is over and the two
        // paths on screen belong to the last file, not to anything in flight.
        setVisible(readout, false);

        Label title = new Label(UiText.execRecapTitle(language));
        title.getStyleClass().add("section-heading");

        Label outcome = new Label(outcomeText(executionRecap));
        outcome.getStyleClass().setAll("exec-outcome", outcomeVariant(executionRecap));
        outcome.setWrapText(true);

        recapPanel.getChildren().setAll(
                title,
                outcome,
                subHeading(UiText.execRecapSectionDisk(language)),
                stats(
                        stat(UiText.execRecapMoved(language), executionRecap.moved().size(), false),
                        stat(UiText.execRecapRenamed(language), executionRecap.renamed().size(), false),
                        stat(UiText.execRecapDeleted(language), executionRecap.deleted().size(), false),
                        stat(UiText.execRecapFoldersDeleted(language),
                                executionRecap.deletedSourceFolders().size(), false),
                        stat(UiText.execRecapFoldersTagged(language),
                                executionRecap.renamedSourceFolders().size(), false)),
                subHeading(UiText.execRecapSectionUntouched(language)),
                stats(
                        stat(UiText.execRecapFailed(language), executionRecap.failed().size(), true),
                        stat(UiText.execRecapSkipped(language), executionRecap.skipped().size(), false),
                        stat(UiText.execRecapUntouched(language), executionRecap.untouched().size(), false),
                        stat(UiText.execRecapIgnored(language), executionRecap.ignoredFiles().size(), false),
                        stat(UiText.execRecapUnsupported(language),
                                executionRecap.unsupportedFiles().size(), false),
                        stat(UiText.execRecapDuplicates(language),
                                executionRecap.duplicateExcludedFiles().size(), false),
                        stat(UiText.execRecapUnassigned(language),
                                executionRecap.unassignedFiles().size(), false)));

        List<String> hints = executionRecap.recoveryHints();
        if (!hints.isEmpty()) {
            recapPanel.getChildren().add(subHeading(UiText.execRecapNextActions(language)));
            for (String hint : hints) {
                Label hintLabel = new Label(hint);
                hintLabel.getStyleClass().add("exec-hint");
                hintLabel.setWrapText(true);
                recapPanel.getChildren().add(hintLabel);
            }
        }

        executionRecap.diagnosticLocation().ifPresent(location -> {
            Label label = new Label(UiText.execRecapDiagnostics(language));
            label.getStyleClass().add("exec-label");
            Label value = new Label(location.toString());
            value.getStyleClass().add("exec-path");
            value.setTooltip(new Tooltip(location.toString()));
            VBox block = new VBox(4, label, value);
            recapPanel.getChildren().add(block);
        });

        setVisible(recapScroll, true);
    }

    private static Label subHeading(String text) {
        Label heading = new Label(text);
        heading.getStyleClass().add("section-heading");
        return heading;
    }

    private static FlowPane stats(Region... children) {
        FlowPane pane = new FlowPane(24, 14, children);
        pane.getStyleClass().add("exec-stats");
        return pane;
    }

    /**
     * One counter: the number first, its name under it. {@code alarming} marks the
     * counters that mean something went wrong, so a non-zero one is read as a
     * problem and a zero one stays as quiet as its neighbours.
     */
    private static VBox stat(String label, int value, boolean alarming) {
        Label number = new Label(String.valueOf(value));
        number.getStyleClass().add("exec-stat-value");
        if (value == 0) {
            number.getStyleClass().add("zero");
        } else if (alarming) {
            number.getStyleClass().add("alarming");
        }
        Label name = new Label(label);
        name.getStyleClass().add("exec-stat-label");
        VBox block = new VBox(2, number, name);
        block.getStyleClass().add("exec-stat");
        return block;
    }

    private String outcomeText(ExecutionRecap executionRecap) {
        if (executionRecap.aborted()) {
            return UiText.execRecapAborted(language);
        }
        if (executionRecap.completeSuccess()) {
            return UiText.execRecapComplete(language);
        }
        return executionRecap.partialSuccess()
                ? UiText.execRecapPartial(language)
                : UiText.execRecapNone(language);
    }

    /** Colour follows the outcome, and partial success never wears the good one. */
    private static String outcomeVariant(ExecutionRecap executionRecap) {
        if (executionRecap.completeSuccess() && !executionRecap.aborted()) {
            return "good";
        }
        return executionRecap.aborted() || !executionRecap.failed().isEmpty() ? "error" : "warn";
    }

    private static void setVisible(Region region, boolean visible) {
        region.setVisible(visible);
        region.setManaged(visible);
    }
}
