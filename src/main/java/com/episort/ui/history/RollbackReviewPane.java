package com.episort.ui.history;

import com.episort.persistence.RollbackMove;
import com.episort.ui.AppLanguage;
import com.episort.ui.HorizontalScrollTable;
import com.episort.ui.RoundedClip;
import com.episort.ui.UiText;
import com.episort.ui.WorkflowPhase;
import com.episort.ui.WorkflowStepper;
import com.episort.workflow.LastPlanRollbackService;
import com.episort.workflow.RollbackProgress;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Full-frame review, execution, and recap for the persisted inverse plan. */
final class RollbackReviewPane {
    private final AppLanguage language;
    private final UUID runId;
    private final List<RollbackMove> moves;
    private final LastPlanRollbackService service;
    private final Runnable onClose;
    private final IntConsumer onSuccess;
    private final Consumer<String> onFailure;
    private final Consumer<Boolean> onExecutingChanged;
    private final VBox root = new VBox(12);
    private final Label title = new Label();
    private final Button backButton = new Button();
    private final Button footerButton = new Button();
    private final WorkflowStepper stepper;
    private final VBox header;
    private boolean executing;

    RollbackReviewPane(
            AppLanguage language,
            UUID runId,
            List<RollbackMove> moves,
            LastPlanRollbackService service,
            Runnable onClose,
            IntConsumer onSuccess,
            Consumer<String> onFailure,
            Consumer<Boolean> onExecutingChanged) {
        this.language = language;
        this.runId = runId;
        this.moves = List.copyOf(moves);
        this.service = service;
        this.onClose = onClose;
        this.onSuccess = onSuccess;
        this.onFailure = onFailure;
        this.onExecutingChanged = onExecutingChanged;

        title.getStyleClass().add("tmdb-dialog-title");
        backButton.getStyleClass().addAll("header-action", "back-action");
        backButton.setOnAction(event -> close());
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox headerRow = new HBox(10, backButton, title, headerSpacer);
        headerRow.getStyleClass().add("tmdb-dialog-header");
        headerRow.setAlignment(Pos.CENTER_LEFT);

        stepper = new WorkflowStepper(language);
        VBox stepperBox = new VBox(stepper.root());
        stepperBox.getStyleClass().add("workflow-progress");
        header = new VBox(12, headerRow, stepperBox);

        root.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        showReview();
    }

    Region root() {
        return root;
    }

    boolean executing() {
        return executing;
    }

    private void showReview() {
        title.setText(UiText.rollbackReviewTitle(language));
        backButton.setText(UiText.rollbackBack(language));
        backButton.setDisable(false);
        stepper.setPhase(WorkflowPhase.PLAN_REVIEW, false);

        Label subtitle = new Label(UiText.historyRollbackConfirmation(language));
        subtitle.getStyleClass().add("tmdb-dialog-message");
        subtitle.setWrapText(true);

        FlowPane summary = new FlowPane(12, 6);
        summary.getChildren().add(metric(UiText.rollbackMetricFiles(language), moves.size()));

        Label bannerText = new Label(UiText.rollbackReady(language));
        bannerText.getStyleClass().add("banner-text");
        HBox banner = new HBox(8, bannerText);
        banner.getStyleClass().addAll("banner", "banner-info");

        TableView<RollbackMove> table = buildTable();

        Label notice = new Label(UiText.rollbackNotice(language));
        notice.getStyleClass().add("tmdb-dialog-message");
        notice.setWrapText(true);

        Button cancel = new Button(UiText.rollbackBack(language));
        cancel.getStyleClass().add("ghost");
        cancel.setOnAction(event -> close());
        Button restore = new Button(UiText.historyRollbackConfirmButton(language));
        restore.getStyleClass().add("primary");
        restore.setOnAction(event -> startExecution());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(8, spacer, cancel, restore);
        footer.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().setAll(header, subtitle, summary, banner, table, notice, footer);
        VBox.setVgrow(table, Priority.ALWAYS);
    }

    private TableView<RollbackMove> buildTable() {
        TableView<RollbackMove> table = new TableView<>(FXCollections.observableArrayList(moves));
        table.getStyleClass().addAll("preview-table", "plan-table");
        // Without this the empty manifest falls back to JavaFX's own English
        // "No content in table" in the middle of a French screen.
        table.setPlaceholder(new Label(UiText.rollbackEmpty(language)));
        RoundedClip.install(table, 14);

        TableColumn<RollbackMove, String> source =
                new TableColumn<>(UiText.historyRollbackColumnCurrent(language));
        source.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().currentPath().toString()));
        source.setCellFactory(column -> pathCell());
        source.setPrefWidth(430);
        source.setMinWidth(280);

        TableColumn<RollbackMove, String> destination =
                new TableColumn<>(UiText.historyRollbackColumnOriginal(language));
        destination.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().originalPath().toString()));
        destination.setCellFactory(column -> pathCell());
        destination.setPrefWidth(430);
        destination.setMinWidth(280);

        TableColumn<RollbackMove, String> status = new TableColumn<>(UiText.planColumnStatus(language));
        status.setCellValueFactory(data -> new SimpleStringProperty(UiText.historyRollbackConfirmButton(language)));
        status.setCellFactory(column -> statusCell());
        status.setPrefWidth(160);

        table.getColumns().setAll(List.of(source, destination, status));
        HorizontalScrollTable.install(table, List.of(source, destination));
        return table;
    }

    private void startExecution() {
        executing = true;
        onExecutingChanged.accept(true);
        title.setText(UiText.rollbackExecutionTitle(language));
        stepper.setPhase(WorkflowPhase.APPLY, true);
        backButton.setDisable(true);

        Label section = new Label(UiText.rollbackProgressLabel(language));
        section.getStyleClass().add("section-heading");
        Label count = new Label("0");
        count.getStyleClass().add("exec-count");
        Label total = new Label("/ " + moves.size());
        total.getStyleClass().add("exec-count-total");
        Label percent = new Label("0 %");
        percent.getStyleClass().add("exec-percent");
        Region counterSpacer = new Region();
        HBox.setHgrow(counterSpacer, Priority.ALWAYS);
        HBox counter = new HBox(8, count, total, counterSpacer, percent);
        counter.setAlignment(Pos.BASELINE_LEFT);

        ProgressBar bar = new ProgressBar(0);
        bar.getStyleClass().add("episort-progress");
        bar.setMaxWidth(Double.MAX_VALUE);
        Label source = new Label(UiText.EMPTY);
        Label destination = new Label(UiText.EMPTY);
        VBox flight = new VBox(10,
                pathField(UiText.historyRollbackColumnCurrent(language), source),
                pathField(UiText.historyRollbackColumnOriginal(language), destination));
        flight.getStyleClass().add("exec-flight");
        VBox readout = new VBox(14, section, counter, bar, flight);
        readout.getStyleClass().add("exec-readout");

        footerButton.setText(UiText.execRecapClose(language));
        footerButton.getStyleClass().setAll("button", "primary");
        footerButton.setDisable(true);
        footerButton.setOnAction(event -> close());
        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        HBox footer = new HBox(8, footerSpacer, footerButton);

        root.getChildren().setAll(header, readout, footer);
        VBox.setVgrow(readout, Priority.ALWAYS);

        AtomicInteger completedMoves = new AtomicInteger();
        Platform.runLater(() -> CompletableFuture
                .supplyAsync(() -> runRollback(progress -> {
                    completedMoves.set(progress.completed());
                    Platform.runLater(() -> {
                    count.setText(String.valueOf(progress.completed()));
                    total.setText("/ " + progress.total());
                    bar.setProgress(progress.fraction());
                    percent.setText(Math.round(progress.fraction() * 100) + " %");
                    setPath(source, progress.currentPath().toString());
                    setPath(destination, progress.originalPath().toString());
                    });
                }))
                .whenComplete((restored, throwable) -> Platform.runLater(() -> {
                    executing = false;
                    onExecutingChanged.accept(false);
                    stepper.setPhase(WorkflowPhase.APPLY, false);
                    if (throwable != null) {
                        String reason = failureReason(throwable);
                        onFailure.accept(reason);
                        showRecap(false, completedMoves.get(), reason);
                    } else {
                        onSuccess.accept(restored);
                        showRecap(true, restored, "");
                    }
                })));
    }

    private int runRollback(Consumer<RollbackProgress> progress) {
        try {
            return service.rollback(runId, progress);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void showRecap(boolean success, int restored, String reason) {
        Label recapHeading = new Label(UiText.rollbackRecapLabel(language));
        recapHeading.getStyleClass().add("section-heading");
        Label outcome = new Label(success
                ? UiText.rollbackCompletedCount(language, restored)
                : UiText.historyRollbackFailed(language).replace("{0}", reason));
        outcome.getStyleClass().addAll("exec-outcome", success ? "good" : "error");
        outcome.setWrapText(true);

        Label section = new Label(UiText.rollbackRecapDisk(language));
        section.getStyleClass().add("section-heading");
        Label value = new Label(String.valueOf(restored));
        value.getStyleClass().add("exec-stat-value");
        Label caption = new Label(UiText.rollbackMetricRestored(language));
        caption.getStyleClass().add("exec-stat-label");
        VBox stat = new VBox(2, value, caption);
        stat.getStyleClass().add("exec-stat");
        VBox recap = new VBox(14, recapHeading, outcome, section, stat);
        recap.getStyleClass().add("exec-recap");

        footerButton.setDisable(false);
        root.getChildren().setAll(header, recap, footerButtonHost());
        VBox.setVgrow(recap, Priority.ALWAYS);
        footerButton.requestFocus();
    }

    private HBox footerButtonHost() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(8, spacer, footerButton);
        footer.setAlignment(Pos.CENTER_RIGHT);
        return footer;
    }

    private void close() {
        if (!executing) {
            onClose.run();
        }
    }

    private static VBox pathField(String caption, Label value) {
        Label label = new Label(caption);
        label.getStyleClass().add("exec-label");
        value.getStyleClass().add("exec-path");
        return new VBox(4, label, value);
    }

    private static void setPath(Label label, String value) {
        label.setText(value);
        label.setTooltip(new Tooltip(value));
    }

    private static TableCell<RollbackMove, String> pathCell() {
        return new TableCell<>() {
            {
                setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("cell-mono");
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item);
                    setTooltip(new Tooltip(item));
                    getStyleClass().add("cell-mono");
                }
            }
        };
    }

    private static TableCell<RollbackMove, String> statusCell() {
        return new TableCell<>() {
            private final Label pill = new Label();

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    pill.getStyleClass().setAll("row-status", "replace");
                    pill.setText(item);
                    setGraphic(pill);
                }
            }
        };
    }

    private static Label metric(String label, int value) {
        Label chip = new Label(label + ": " + value);
        chip.getStyleClass().add("tmdb-match-meta");
        return chip;
    }

    private static String failureReason(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return String.valueOf(current.getMessage());
    }
}
