package com.episort.ui.execution;

import com.episort.planning.ApprovedPlan;
import com.episort.planning.ConflictResolution;
import com.episort.planning.OperationPlan;
import com.episort.planning.PlanConflict;
import com.episort.planning.PlanConflictResolver;
import com.episort.planning.PlanConflictType;
import com.episort.planning.PlannedOperation;
import com.episort.ui.AppLanguage;
import com.episort.ui.HorizontalScrollTable;
import com.episort.ui.UiText;
import com.episort.ui.WorkflowPhase;
import com.episort.ui.WorkflowStepper;
import com.episort.workflow.ExecutionRecap;
import com.episort.workflow.ExecutionService;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Exact operation-plan review, conflict resolution, and execution — all inside
 * the main window (Stories 6.4, 6.5, 7.x).
 *
 * <p>A detached body rather than a window of its own: the shell swaps it into the
 * content area, so the user never loses sight of the app while reviewing. Nothing
 * pops up on top of anything.
 *
 * <p>The blocking conflicts are decided on the plan rows themselves: a conflicting
 * row carries its own decision cell, so the user sees the conflict, its dates, and
 * what they chose about it in the very list they are about to validate. Rows are
 * selectable exactly like the scan table — tick the ones concerned, or the header
 * box for all of them, then apply one decision to the lot; the decision still
 * lands per row and stays visible and editable there. Changing a ticked row's own
 * decision cell carries it to the rest of the selection too, so the batch buttons
 * are a shortcut rather than the only way to decide in bulk. Rows start on
 * replace wherever a replacement can settle the conflict.
 *
 * <p>Nothing touches the disk before the single explicit validate click. Applying
 * decisions only rebuilds the plan; losing a destination never destroys a file —
 * the losing row simply leaves the plan and its file stays where it is. Removing
 * the extra copy is a decision of its own, taken row by row, shown as its own
 * status in the plan and counted on the line above the validate button: no file
 * ever goes away without having been listed as going away.
 */
public final class PlanReviewPane {
    private final AppLanguage language;
    private final Function<OperationPlan, Optional<ApprovedPlan>> approveOnValidate;
    private final ExecutionService executionService;
    private final Consumer<Optional<ExecutionRecap>> onClosed;
    private final Consumer<Boolean> onExecutingChanged;
    private final PlanConflictResolver resolver = new PlanConflictResolver();
    private final DateTimeFormatter dateFormat;

    private final Label title = new Label();
    private final Label subtitle = new Label();
    private final Button validateButton = new Button();
    private final Button closeButton = new Button();
    private final Button backButton = new Button();
    private final WorkflowStepper stepper;
    private final Label banner = new Label();
    private final HBox bannerBox;
    private final FlowPane summary = new FlowPane();
    private final FlowPane conflictToolbar;
    private final Label selectionCount = new Label();
    private final CheckBox selectAllCheckbox = new CheckBox();
    private final Button mostRecentButton = new Button();
    private final Button oldestButton = new Button();
    private final Button replaceButton = new Button();
    private final Button keepButton = new Button();
    private final Button deleteButton = new Button();
    private final TableView<PlanRow> table = new TableView<>();
    private final TableColumn<PlanRow, Boolean> selectionColumn = new TableColumn<>();
    private final TableColumn<PlanRow, String> datesColumn;
    private final TableColumn<PlanRow, PlanRow> decisionColumn;
    private final Label notice = new Label();
    private final VBox body;
    private final VBox header;
    private final HBox footer;

    /** Replaced in place when the user resolves the conflicts, never re-planned from disk. */
    private OperationPlan plan;
    /** The most-recent-wins answer for each source, recomputed with the rows. */
    private Map<Path, ConflictResolution> mostRecentDecisions = Map.of();
    private Map<Path, ConflictResolution> oldestDecisions = Map.of();
    private boolean validated;
    private boolean executing;
    private int selectionAnchor = -1;
    private Optional<ExecutionRecap> recap = Optional.empty();

    public PlanReviewPane(
            AppLanguage language,
            OperationPlan plan,
            Function<OperationPlan, Optional<ApprovedPlan>> approveOnValidate,
            ExecutionService executionService,
            Consumer<Optional<ExecutionRecap>> onClosed,
            Consumer<Boolean> onExecutingChanged) {
        this.language = language;
        this.plan = plan;
        this.approveOnValidate = approveOnValidate;
        this.executionService = executionService;
        this.onClosed = onClosed == null ? result -> { } : onClosed;
        this.onExecutingChanged = onExecutingChanged == null ? running -> { } : onExecutingChanged;
        this.dateFormat = DateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(Locale.forLanguageTag(language.languageTag()))
                .withZone(ZoneId.systemDefault());

        title.setText(UiText.planDialogTitle(language));
        title.getStyleClass().add("tmdb-dialog-title");

        // The way back, where a way back is looked for: leading the header, before
        // the plan itself. The footer keeps the same action under the table, so a
        // long list never has to be scrolled back up to leave it.
        backButton.setText(UiText.planBack(language));
        backButton.getStyleClass().addAll("header-action", "ghost");
        backButton.setOnAction(event -> close());

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox headerRow = new HBox(10, backButton, title, headerSpacer);
        headerRow.getStyleClass().add("tmdb-dialog-header");
        headerRow.setAlignment(Pos.CENTER_LEFT);

        // The same strip the scan screen shows, carried on where it left off: the
        // plan is step 4 of the run the user is already in, not a place of its own.
        stepper = new WorkflowStepper(language);
        stepper.setPhase(WorkflowPhase.PLAN_REVIEW, false);
        VBox stepperBox = new VBox(stepper.root());
        stepperBox.getStyleClass().add("workflow-progress");

        header = new VBox(12, headerRow, stepperBox);

        subtitle.setText(UiText.planDialogSubtitle(language));
        subtitle.getStyleClass().add("tmdb-dialog-message");
        subtitle.setWrapText(true);

        datesColumn = new TableColumn<>(UiText.conflictColumnDates(language));
        decisionColumn = new TableColumn<>(UiText.conflictColumnDecision(language));
        buildTable();
        conflictToolbar = buildConflictToolbar();

        banner.getStyleClass().add("banner-text");
        banner.setWrapText(true);
        Label bannerIcon = new Label("•");
        bannerIcon.getStyleClass().add("banner-icon");
        bannerBox = new HBox(8, bannerIcon, banner);
        bannerBox.getStyleClass().add("banner");
        HBox.setHgrow(banner, Priority.ALWAYS);

        notice.setText(UiText.planNotice(language));
        notice.getStyleClass().add("tmdb-dialog-message");
        notice.setWrapText(true);

        validateButton.getStyleClass().add("primary");
        // One primary button, two jobs: while conflicts remain it applies the
        // decisions taken on the rows, and only once they are gone does it become
        // the single click that starts the run.
        validateButton.setOnAction(event -> {
            if (this.plan.hasBlockingConflicts()) {
                applyDecisions();
            } else {
                startExecution();
            }
        });
        closeButton.setText(UiText.planBack(language));
        closeButton.getStyleClass().add("ghost");
        closeButton.setOnAction(event -> close());

        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        footer = new HBox(8, footerSpacer, closeButton, validateButton);
        footer.setAlignment(Pos.CENTER_RIGHT);

        summary.setHgap(12);
        summary.setVgap(6);
        body = new VBox(12, header, subtitle, summary, conflictToolbar, bannerBox, table, notice, footer);
        // Styled as a screen, not as a floating dialog: it belongs to the content
        // area now, and a window-looking card in there would only imitate the
        // second window this replaced.
        body.getStyleClass().add("screen-root");
        body.setPadding(new Insets(26, 30, 26, 30));
        VBox.setVgrow(table, Priority.ALWAYS);

        refreshPlanView();
    }

    public Region root() {
        return body;
    }

    /** @return true when the user validated the exact plan, whatever the run did next */
    public boolean validated() {
        return validated;
    }

    /** @return true while the run is touching the disk: leaving must stay refused */
    public boolean executing() {
        return executing;
    }

    /**
     * Leaves the review. Refused outright while a run is in flight — abandoning a
     * half-done move behind the user's back is never an option.
     */
    public void close() {
        if (executing) {
            return;
        }
        onClosed.accept(recap);
    }

    /**
     * Redraws everything the plan drives — rows, counters, banner, the conflict
     * toolbar and columns, and the meaning of the primary button — after the plan
     * is replaced by a resolved one.
     */
    private void refreshPlanView() {
        boolean blocked = plan.hasBlockingConflicts();

        mostRecentDecisions = resolver.mostRecentWins(plan);
        oldestDecisions = resolver.oldestWins(plan);
        List<PlanRow> rows = new ArrayList<>(plan.operations().size());
        for (PlannedOperation operation : plan.operations()) {
            rows.add(new PlanRow(operation));
        }
        table.getItems().setAll(rows);
        selectionAnchor = -1;

        summary.getChildren().setAll(
                metric(UiText.planSummaryToMove(language), plan.executableOperations().size()),
                metric(UiText.planSummaryToDelete(language), plan.deletions().size()),
                metric(UiText.planSummaryInPlace(language), plan.alreadyInPlaceOperations().size()),
                metric(UiText.planSummaryExcluded(language), plan.excludedOperations().size()),
                metric(UiText.planSummaryConflicts(language), plan.conflicts().size()),
                metric(UiText.planSummaryFoldersToCreate(language), plan.foldersToCreate().size()),
                metric(UiText.planSummaryFoldersReused(language), plan.reusedFolders().size()));

        // The conflict machinery only shows up when there is something to decide.
        setVisible(conflictToolbar, blocked);
        selectionColumn.setVisible(blocked);
        datesColumn.setVisible(blocked);
        decisionColumn.setVisible(blocked);

        subtitle.setText(blocked
                ? UiText.conflictDialogSubtitle(language)
                : UiText.planDialogSubtitle(language));
        banner.setText(PlanReviewText.banner(plan.isEmpty(), plan.mutatingOperations().isEmpty(), blocked, language));
        bannerBox.getStyleClass().removeAll("banner-warn", "banner-info");
        bannerBox.getStyleClass().add(blocked ? "banner-warn" : "banner-info");

        // A plan that removes files says so on its last line, right above the
        // button that starts it — a count of deletions is not a detail.
        int deletions = plan.deletions().size();
        notice.getStyleClass().remove("notice-danger");
        String baseNotice = (deletions == 0
                ? UiText.planNotice(language)
                : UiText.planNotice(language) + " " + UiText.planNoticeDelete(language, deletions));
        notice.setText(plan.executableOperations().isEmpty()
                ? baseNotice
                : baseNotice + " " + UiText.planNoticeSortingFolders(language));
        if (deletions > 0) {
            notice.getStyleClass().add("notice-danger");
        }

        validateButton.setText(blocked
                ? UiText.conflictApply(language)
                : UiText.planValidate(language));
        validateButton.setDisable(!blocked && plan.mutatingOperations().isEmpty());
        updateSelectionState();
    }

    /**
     * Rebuilds the plan from the decisions on the rows. If they produce a new
     * conflict — two files the user both let win the same destination — the review
     * stays on the remaining conflicts instead of pretending the plan is ready.
     */
    private void applyDecisions() {
        OperationPlan resolved = resolver.resolve(plan, decisions());
        boolean stillBlocked = resolved.hasBlockingConflicts();
        plan = resolved;
        refreshPlanView();
        if (stillBlocked) {
            banner.setText(UiText.conflictStillBlocked(language));
        }
    }

    /** The decisions to hand the resolver: one per conflicting row, as shown. */
    private Map<Path, ConflictResolution> decisions() {
        Map<Path, ConflictResolution> decisions = new LinkedHashMap<>();
        for (PlanRow row : table.getItems()) {
            if (row.conflicting()) {
                decisions.put(row.operation.sourcePath(), row.choice.get());
            }
        }
        return decisions;
    }

    /**
     * Swaps the review body for the live execution view, in place. The approval
     * callback closes the exact-plan gate and locks the plan; if it declines, a
     * gate reopened underneath us and the review stays on screen.
     */
    private void startExecution() {
        Optional<ApprovedPlan> approved = approveOnValidate.apply(plan);
        if (approved.isEmpty()) {
            return;
        }
        validated = true;
        executing = true;
        onExecutingChanged.accept(true);

        ExecutionPane executionPane = new ExecutionPane(
                language, plan, approved.orElseThrow(), executionService);

        title.setText(UiText.execDialogTitle(language));
        // Last step of the same strip, and no way back from a run in flight: the
        // trail keeps reading true instead of offering a return that is refused.
        stepper.setPhase(WorkflowPhase.APPLY, true);
        backButton.setDisable(true);
        closeButton.setText(UiText.execRecapClose(language));
        closeButton.getStyleClass().remove("ghost");
        closeButton.getStyleClass().add("primary");
        closeButton.setDisable(true);
        footer.getChildren().remove(validateButton);

        VBox executionRoot = executionPane.root();
        body.getChildren().setAll(header, executionRoot, footer);
        VBox.setVgrow(executionRoot, Priority.ALWAYS);

        executionPane.start(result -> {
            recap = result;
            executing = false;
            stepper.setPhase(WorkflowPhase.APPLY, false);
            onExecutingChanged.accept(false);
            closeButton.setDisable(false);
            closeButton.requestFocus();
        });
    }

    /* ---- Conflict batch actions ---------------------------------------- */

    /**
     * The batch actions: each one writes the same per-row decision it would have
     * taken by hand, on the ticked rows only, and leaves everything else alone.
     */
    private FlowPane buildConflictToolbar() {
        selectionCount.getStyleClass().add("tmdb-dialog-message");

        mostRecentButton.setText(UiText.conflictBatchMostRecent(language));
        mostRecentButton.getStyleClass().add("ghost");
        mostRecentButton.setOnAction(event -> applyToSelection(
                row -> mostRecentDecisions.getOrDefault(row.operation.sourcePath(), ConflictResolution.SKIP)));

        // Its mirror: same answers, opposite direction. A user who does not trust
        // the fresher file needs to say so in one gesture, not row by row.
        oldestButton.setText(UiText.conflictBatchOldest(language));
        oldestButton.getStyleClass().add("ghost");
        oldestButton.setOnAction(event -> applyToSelection(
                row -> oldestDecisions.getOrDefault(row.operation.sourcePath(), ConflictResolution.SKIP)));

        replaceButton.setText(UiText.conflictBatchReplace(language));
        replaceButton.getStyleClass().add("ghost");
        replaceButton.setOnAction(event -> applyToSelection(row -> ConflictResolution.REPLACE));

        keepButton.setText(UiText.conflictBatchKeep(language));
        keepButton.getStyleClass().add("ghost");
        keepButton.setOnAction(event -> applyToSelection(row -> ConflictResolution.SKIP));

        // Styled apart from its neighbours: it is the only batch action that ends
        // with files gone, and it must not be clicked by muscle memory.
        deleteButton.setText(UiText.conflictBatchDelete(language));
        deleteButton.getStyleClass().addAll("ghost", "danger");
        deleteButton.setOnAction(event -> applyToSelection(row -> ConflictResolution.DELETE_SOURCE));

        FlowPane toolbar = new FlowPane(
                8, 6, selectionCount, mostRecentButton, oldestButton, replaceButton, keepButton, deleteButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        return toolbar;
    }

    /**
     * Writes a decision on every ticked conflicting row that can accept it. A row
     * that cannot be replaced — or whose file must not be deleted — is left alone
     * by that action rather than silently given a decision it does not support.
     */
    private void applyToSelection(Function<PlanRow, ConflictResolution> decision) {
        for (PlanRow row : selectedRows()) {
            ConflictResolution value = decision.apply(row);
            if (!accepts(row, value)) {
                continue;
            }
            row.choice.set(value);
        }
        table.refresh();
    }

    /** Which decisions a row can actually carry, given the conflict behind it. */
    private static boolean accepts(PlanRow row, ConflictResolution value) {
        return switch (value) {
            case REPLACE -> row.type().resolvableByReplacement();
            case DELETE_SOURCE -> row.type().deletableSource();
            case SKIP -> true;
        };
    }

    /**
     * A decision taken in the table counts for the whole selection: when the
     * edited row is ticked, every other ticked row follows it, exactly as if the
     * matching batch button had been clicked. Editing an unticked row stays a
     * one-row change — the selection is what says "and these too".
     */
    private void onDecisionChosen(PlanRow row, ConflictResolution value) {
        row.choice.set(value);
        if (!row.selected.get()) {
            return;
        }
        for (PlanRow other : selectedRows()) {
            if (other == row || !accepts(other, value)) {
                continue;
            }
            other.choice.set(value);
        }
        // Deferred: the combo is still closing its popup on this very event, and
        // rebuilding its cell underneath it would swallow the click.
        Platform.runLater(table::refresh);
    }

    private List<PlanRow> selectedRows() {
        return table.getItems().stream().filter(row -> row.conflicting() && row.selected.get()).toList();
    }

    private List<PlanRow> conflictingRows() {
        return table.getItems().stream().filter(PlanRow::conflicting).toList();
    }

    private void setAllSelected(boolean selected) {
        selectionAnchor = -1;
        for (PlanRow row : conflictingRows()) {
            row.selected.set(selected);
        }
        updateSelectionState();
        table.refresh();
    }

    /**
     * A plain click ticks one row; shift-click extends from the last one ticked,
     * the same gesture the scan table answers to. Only conflicting rows have
     * anything to decide, so only they can be ticked.
     */
    private void onSelectionCellClick(PlanRow row, MouseEvent event) {
        int index = table.getItems().indexOf(row);
        if (index < 0 || !row.conflicting()) {
            return;
        }
        if (event.isShiftDown() && selectionAnchor >= 0) {
            int start = Math.min(selectionAnchor, index);
            int end = Math.max(selectionAnchor, index);
            for (int cursor = start; cursor <= end; cursor++) {
                PlanRow candidate = table.getItems().get(cursor);
                if (candidate.conflicting()) {
                    candidate.selected.set(true);
                }
            }
        } else {
            row.selected.set(!row.selected.get());
            selectionAnchor = index;
        }
        updateSelectionState();
        table.refresh();
    }

    private void updateSelectionState() {
        List<PlanRow> conflicting = conflictingRows();
        List<PlanRow> selected = selectedRows();
        int count = selected.size();
        selectionCount.setText(UiText.conflictSelectionCount(language, count, conflicting.size()));
        selectAllCheckbox.setDisable(conflicting.isEmpty());
        selectAllCheckbox.setSelected(!conflicting.isEmpty() && count == conflicting.size());

        mostRecentButton.setDisable(count == 0);
        oldestButton.setDisable(count == 0);
        keepButton.setDisable(count == 0);
        // Replacing and deleting are only offered where they can actually settle
        // the conflict; a path problem is never a reason to destroy a file.
        replaceButton.setDisable(selected.stream().noneMatch(row -> row.type().resolvableByReplacement()));
        deleteButton.setDisable(selected.stream().noneMatch(row -> row.type().deletableSource()));
    }

    /* ---- Table --------------------------------------------------------- */

    private void buildTable() {
        table.getStyleClass().addAll("preview-table", "plan-table");
        table.setEditable(true);
        table.setPlaceholder(new Label(UiText.planEmpty(language)));
        table.setRowFactory(view -> planTableRow());

        selectionColumn.setMinWidth(46);
        selectionColumn.setPrefWidth(46);
        selectionColumn.setMaxWidth(60);
        selectionColumn.setSortable(false);
        selectionColumn.setReorderable(false);
        selectionColumn.getStyleClass().add("selection-column");
        selectAllCheckbox.getStyleClass().add("row-checkbox");
        selectAllCheckbox.setOnAction(event -> setAllSelected(selectAllCheckbox.isSelected()));
        StackPane selectAllHost = new StackPane(selectAllCheckbox);
        selectAllHost.getStyleClass().add("selection-header");
        selectionColumn.setGraphic(selectAllHost);
        selectionColumn.setCellValueFactory(data -> data.getValue().selected);
        selectionColumn.setCellFactory(column -> selectionCell());

        TableColumn<PlanRow, String> source = new TableColumn<>(UiText.planColumnSource(language));
        source.setCellValueFactory(data -> new SimpleStringProperty(
                insideWorkspace(data.getValue().operation.sourcePath())));
        source.setCellFactory(column -> monoCell());
        source.setPrefWidth(340);

        TableColumn<PlanRow, String> destination = new TableColumn<>(UiText.planColumnDestination(language));
        destination.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().operation.destinationPath()
                        .map(this::insideWorkspace)
                        .orElse(UiText.EMPTY)));
        destination.setCellFactory(column -> monoCell());
        destination.setPrefWidth(360);

        TableColumn<PlanRow, PlanRow> status = new TableColumn<>(UiText.planColumnStatus(language));
        status.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        status.setCellFactory(column -> statusCell());
        status.setPrefWidth(180);

        datesColumn.setCellValueFactory(data -> new SimpleStringProperty(datesText(data.getValue())));
        datesColumn.setPrefWidth(220);

        decisionColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        decisionColumn.setCellFactory(column -> decisionCell());
        decisionColumn.setPrefWidth(210);
        decisionColumn.setSortable(false);

        table.getColumns().setAll(List.of(
                selectionColumn, source, destination, status, datesColumn, decisionColumn));

        // Same deal as the scan table: paths keep their readable widths and the
        // table scrolls horizontally when the pane is too narrow, instead of
        // squeezing the two path columns into unreadable stubs.
        HorizontalScrollTable.install(table, List.of(source, destination));
    }

    /**
     * The whole row lights up while it is ticked, not just its little box: batch
     * actions are aimed at a set of rows, and a set you cannot see at a glance is
     * a set you cannot trust before hitting apply. The row follows its own tick
     * mark, so the highlight is right even when a batch action or the header box
     * changes many rows at once.
     */
    private TableRow<PlanRow> planTableRow() {
        return new TableRow<>() {
            private final javafx.beans.value.ChangeListener<Boolean> watcher =
                    (observable, previous, current) -> markPicked(Boolean.TRUE.equals(current));
            private PlanRow bound;

            @Override
            protected void updateItem(PlanRow item, boolean empty) {
                super.updateItem(item, empty);
                if (bound != null) {
                    // Rows are recycled as the table scrolls: the old item's tick
                    // must stop driving a row that no longer shows it.
                    bound.selected.removeListener(watcher);
                    bound = null;
                }
                if (empty || item == null) {
                    markPicked(false);
                    return;
                }
                bound = item;
                bound.selected.addListener(watcher);
                markPicked(item.selected.get());
            }

            private void markPicked(boolean picked) {
                getStyleClass().remove("picked-row");
                if (picked) {
                    getStyleClass().add("picked-row");
                }
            }
        };
    }

    private TableCell<PlanRow, Boolean> selectionCell() {
        return new TableCell<>() {
            private final CheckBox checkBox = new CheckBox();
            private PlanRow boundRow;

            {
                checkBox.getStyleClass().add("row-checkbox");
                checkBox.setMouseTransparent(true);
                setAlignment(Pos.CENTER);
                getStyleClass().add("selection-cell");
                addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                    if (!isEmpty() && boundRow != null && event.getButton() == MouseButton.PRIMARY) {
                        onSelectionCellClick(boundRow, event);
                        event.consume();
                    }
                });
            }

            @Override
            protected void updateItem(Boolean value, boolean empty) {
                super.updateItem(value, empty);
                boundRow = empty || getTableRow() == null ? null : getTableRow().getItem();
                // A row with nothing to decide gets no box at all, rather than one
                // that looks clickable and does nothing.
                if (boundRow == null || !boundRow.conflicting()) {
                    setGraphic(null);
                    return;
                }
                checkBox.setSelected(boundRow.selected.get());
                setGraphic(checkBox);
            }
        };
    }

    /**
     * The two dates side by side — what "most recent" is about to decide, so the
     * batch action is never a leap of faith.
     */
    private String datesText(PlanRow row) {
        if (!row.conflicting()) {
            return UiText.EMPTY;
        }
        String sourceDate = format(PlanConflictResolver.lastModified(row.operation.sourcePath()));
        String destinationDate = row.operation.destinationPath()
                .map(PlanConflictResolver::lastModified)
                .map(this::format)
                .orElse(UiText.EMPTY);
        return UiText.conflictDatesLabel(language, sourceDate, destinationDate);
    }

    private String format(Optional<Instant> instant) {
        return instant.map(dateFormat::format).orElse(UiText.EMPTY);
    }

    private TableCell<PlanRow, PlanRow> decisionCell() {
        return new TableCell<>() {
            private final ComboBox<ConflictResolution> combo = new ComboBox<>();
            /** True while the cell is being rebound, so redisplay is not read as a user edit. */
            private boolean binding;

            {
                combo.setCellFactory(list -> optionCell());
                combo.setButtonCell(optionCell());
                combo.setMaxWidth(Double.MAX_VALUE);
                combo.valueProperty().addListener((observable, oldValue, newValue) -> {
                    PlanRow row = getItem();
                    if (!binding && row != null && newValue != null) {
                        onDecisionChosen(row, newValue);
                    }
                });
            }

            private ListCell<ConflictResolution> optionCell() {
                return new ListCell<>() {
                    @Override
                    protected void updateItem(ConflictResolution item, boolean empty) {
                        super.updateItem(item, empty);
                        setText(empty || item == null
                                ? null
                                : PlanReviewText.decisionOption(item, currentType(), language));
                    }
                };
            }

            private PlanConflictType currentType() {
                PlanRow row = getItem();
                return row == null || !row.conflicting()
                        ? PlanConflictType.DESTINATION_FILE_EXISTS
                        : row.type();
            }

            @Override
            protected void updateItem(PlanRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || !item.conflicting()) {
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }
                binding = true;
                combo.getItems().setAll(offeredFor(item.type()));
                combo.setValue(item.choice.get());
                binding = false;
                combo.setDisable(combo.getItems().size() < 2);
                setGraphic(combo);
                setTooltip(new Tooltip(item.operation.conflict().orElseThrow().detail()));
            }
        };
    }

    /**
     * The answers a conflict can actually carry. An answer that would leave the
     * disk exactly as it is — replacing a file that is already at the name it
     * would be moved to — is not offered at all: a choice that does nothing is
     * how a user ends up applying a plan three times and still seeing duplicates.
     */
    private static List<ConflictResolution> offeredFor(PlanConflictType type) {
        List<ConflictResolution> offered = new ArrayList<>(3);
        if (type.resolvableByReplacement()) {
            offered.add(ConflictResolution.REPLACE);
        }
        // Ignoring sits between them, so the destructive answer is never the
        // neighbour of the one the row starts on.
        offered.add(ConflictResolution.SKIP);
        if (type.deletableSource()) {
            offered.add(ConflictResolution.DELETE_SOURCE);
        }
        return List.copyOf(offered);
    }

    private TableCell<PlanRow, PlanRow> statusCell() {
        return new TableCell<>() {
            private final Label pill = new Label();

            @Override
            protected void updateItem(PlanRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }
                PlannedOperation operation = item.operation;
                // A replacement is an overwrite the user approved: it must not
                // read like an ordinary move in the list they are validating.
                pill.getStyleClass().setAll(
                        "row-status", operation.replaceExisting() ? "replace" : PlanReviewText.pillVariant(operation.status()));
                pill.setText(operation.replaceExisting()
                        ? UiText.planStatusReplace(language)
                        : PlanReviewText.status(operation.status(), language));
                setGraphic(pill);
                setTooltip(new Tooltip(PlanReviewText.statusDetail(operation, language)));
            }
        };
    }

    /**
     * The part of a path that tells the rows apart: what sits under the
     * workspace. Every row of a plan shares the workspace prefix, so showing it
     * costs width and says nothing.
     */
    private String insideWorkspace(Path path) {
        Path root = plan.workspaceRoot().toAbsolutePath().normalize();
        Path absolute = path.toAbsolutePath().normalize();
        return absolute.startsWith(root) ? root.relativize(absolute).toString() : absolute.toString();
    }

    private static TableCell<PlanRow, String> monoCell() {
        return new TableCell<>() {
            {
                // Paths are told apart by their tail, never their head: when the
                // column is too narrow it is the folder that goes, not the file
                // name the user is deciding about.
                setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("cell-mono", "cell-muted");
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                    return;
                }
                setText(item);
                getStyleClass().add(UiText.EMPTY.equals(item) ? "cell-muted" : "cell-mono");
                setTooltip(new Tooltip(item));
            }
        };
    }

    private static Label metric(String label, int value) {
        Label chip = new Label(label + ": " + value);
        chip.getStyleClass().add("tmdb-match-meta");
        return chip;
    }

    private static void setVisible(Region region, boolean visible) {
        region.setVisible(visible);
        region.setManaged(visible);
    }

    /**
     * One planned operation on screen: plus, when it is blocking, the decision the
     * user takes about it and whether a batch action reaches it.
     */
    private static final class PlanRow {
        private final PlannedOperation operation;
        private final SimpleBooleanProperty selected = new SimpleBooleanProperty(false);
        private final SimpleObjectProperty<ConflictResolution> choice;

        private PlanRow(PlannedOperation operation) {
            this.operation = operation;
            this.choice = new SimpleObjectProperty<>(defaultDecision(operation));
        }

        /**
         * Every row starts on the answer that settles its conflict, because that
         * is what the user came here for: the freshly sorted file wins its
         * destination. On a duplicate, winning is the wrong default — the copy
         * carrying the conflict is the one with the poorer name, so keeping it
         * would retire the better-named copy. There, settling means letting the
         * extra copy go. Conflicts nothing can fix keep the only answer they can
         * carry.
         */
        private static ConflictResolution defaultDecision(PlannedOperation operation) {
            if (!operation.blocking()) {
                return ConflictResolution.SKIP;
            }
            PlanConflictType type = operation.conflict()
                    .map(PlanConflict::type)
                    .orElse(null);
            if (type == PlanConflictType.DUPLICATE_MEDIA) {
                return ConflictResolution.DELETE_SOURCE;
            }
            return type != null && type.resolvableByReplacement()
                    ? ConflictResolution.REPLACE
                    : ConflictResolution.SKIP;
        }

        private boolean conflicting() {
            return operation.blocking();
        }

        private PlanConflictType type() {
            return operation.conflict().orElseThrow().type();
        }
    }
}
