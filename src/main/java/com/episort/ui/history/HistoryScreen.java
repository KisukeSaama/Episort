package com.episort.ui.history;

import com.episort.persistence.RunEvent;
import com.episort.persistence.RunEventStatus;
import com.episort.persistence.RunEventStore;
import com.episort.persistence.RunEventType;
import com.episort.persistence.RollbackMove;
import com.episort.ui.AppLanguage;
import com.episort.ui.AppShell;
import com.episort.ui.RoundedClip;
import com.episort.ui.TableSearchBox;
import com.episort.ui.UiText;
import com.episort.workflow.LastPlanRollbackService;
import java.io.IOException;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public final class HistoryScreen {
    private static final double MIN_USABLE_TABLE_HEIGHT = 240;
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final RunEventStore store;
    private final LastPlanRollbackService rollbackService;
    private final Runnable onRollbackCompleted;
    private final Consumer<Boolean> onRollbackExecutingChanged;
    private final ScrollPane root;
    private final VBox content;
    private final Label heading;
    private final Label bannerText;
    private final HBox banner;

    private final FlowPane metricGrid;
    private final MetricCard cardTotal = new MetricCard();
    private final MetricCard cardSuccess = new MetricCard();
    private final MetricCard cardWarning = new MetricCard();
    private final MetricCard cardConflict = new MetricCard();
    private final MetricCard cardFailed = new MetricCard();

    private final FlowPane filterRow = new FlowPane(8, 8);
    private final TableSearchBox searchBox = new TableSearchBox(this::setSearchFilter);
    private final ToggleGroup filterGroup = new ToggleGroup();
    private final Map<HistoryFilter, ToggleButton> filterButtons = new EnumMap<>(HistoryFilter.class);
    private final Button clearHistoryButton = new Button();
    private final Button rollbackButton = new Button();

    private final TableView<RunEvent> table = new TableView<>();
    private final ObservableList<RunEvent> events = FXCollections.observableArrayList();
    private final FilteredList<RunEvent> filtered = new FilteredList<>(events, event -> true);
    private final TableColumn<RunEvent, String> timestampColumn = new TableColumn<>();
    private final TableColumn<RunEvent, RunEventType> eventColumn = new TableColumn<>();
    private final TableColumn<RunEvent, String> sourceColumn = new TableColumn<>();
    private final TableColumn<RunEvent, String> tvdbColumn = new TableColumn<>();
    private final TableColumn<RunEvent, String> destinationColumn = new TableColumn<>();
    private final TableColumn<RunEvent, String> proposedColumn = new TableColumn<>();
    private final TableColumn<RunEvent, RunEventStatus> statusColumn = new TableColumn<>();
    private final TableColumn<RunEvent, String> resultColumn = new TableColumn<>();

    private final HistoryDetailPanel detailPanel = new HistoryDetailPanel();
    private final SimpleObjectProperty<RunEvent> selectedEvent = new SimpleObjectProperty<>();
    private Pane currentBody;
    private boolean stackedLayout = false;
    private AppLanguage currentLanguage = AppLanguage.FRENCH;
    private RollbackFeedback rollbackFeedback = RollbackFeedback.NONE;
    private String rollbackFailureReason = "";
    private RollbackReviewPane rollbackReviewPane;
    private HistoryFilter activeFilter = HistoryFilter.ALL;
    private String searchFilter = "";

    public HistoryScreen(RunEventStore store) {
        this(store, null, () -> {}, running -> {});
    }

    public HistoryScreen(
            RunEventStore store,
            LastPlanRollbackService rollbackService,
            Runnable onRollbackCompleted) {
        this(store, rollbackService, onRollbackCompleted, running -> {});
    }

    public HistoryScreen(
            RunEventStore store,
            LastPlanRollbackService rollbackService,
            Runnable onRollbackCompleted,
            Consumer<Boolean> onRollbackExecutingChanged) {
        this.store = Objects.requireNonNull(store, "store");
        this.rollbackService = rollbackService;
        this.onRollbackCompleted = Objects.requireNonNull(onRollbackCompleted, "onRollbackCompleted");
        this.onRollbackExecutingChanged = Objects.requireNonNull(
                onRollbackExecutingChanged, "onRollbackExecutingChanged");

        heading = new Label();
        heading.getStyleClass().addAll("section-heading", "section-heading-accent");

        Label bannerIcon = new Label("•");
        bannerIcon.getStyleClass().add("banner-icon");

        bannerText = new Label();
        bannerText.getStyleClass().add("banner-text");
        bannerText.setWrapText(true);
        HBox.setHgrow(bannerText, Priority.ALWAYS);

        banner = new HBox(10, bannerIcon, bannerText);
        banner.getStyleClass().addAll("banner", "banner-info");
        banner.setAlignment(Pos.CENTER_LEFT);

        metricGrid = new FlowPane();
        metricGrid.getStyleClass().add("metric-grid");
        metricGrid.setHgap(12);
        metricGrid.setVgap(12);
        metricGrid.getChildren().addAll(
                cardTotal.root(),
                cardSuccess.root(),
                cardWarning.root(),
                cardConflict.root(),
                cardFailed.root());

        configureFilters();
        configureTable();

        HBox initialBody = new HBox(16, table, detailPanel.root());
        HBox.setHgrow(table, Priority.ALWAYS);
        currentBody = initialBody;

        content = new VBox(14, heading, banner, metricGrid, filterRow, currentBody);
        content.getStyleClass().add("screen-root");
        VBox.setVgrow(currentBody, Priority.ALWAYS);

        root = new ScrollPane(content);
        root.getStyleClass().add("content-scroll");
        root.setFitToWidth(true);
        root.setFitToHeight(true);
        root.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        applyLanguage(AppLanguage.FRENCH);
        refresh();
    }

    public Region root() {
        return root;
    }

    public boolean reviewingRollback() {
        return rollbackReviewPane != null;
    }

    public boolean rollbackExecuting() {
        return rollbackReviewPane != null && rollbackReviewPane.executing();
    }

    public void closeRollbackReview() {
        if (rollbackReviewPane != null && !rollbackReviewPane.executing()) {
            showHistoryContent();
        }
    }

    public void applyLanguage(AppLanguage language) {
        currentLanguage = language;
        searchBox.applyLanguage(UiText.historySearchPlaceholder(language), UiText.a11yClearSearch(language));
        heading.setText(UiText.historyHeading(language));
        applyBannerState();

        cardTotal.setTitle(UiText.historyMetricTotal(language));
        cardSuccess.setTitle(UiText.historyMetricSuccess(language));
        cardWarning.setTitle(UiText.historyMetricWarning(language));
        cardConflict.setTitle(UiText.historyMetricConflict(language));
        cardFailed.setTitle(UiText.historyMetricFailed(language));

        filterButtons.get(HistoryFilter.ALL).setText(UiText.historyFilterAll(language));
        filterButtons.get(HistoryFilter.SUCCESSFUL).setText(UiText.historyFilterSuccessful(language));
        filterButtons.get(HistoryFilter.WARNINGS).setText(UiText.historyFilterWarnings(language));
        filterButtons.get(HistoryFilter.CONFLICTS).setText(UiText.historyFilterConflicts(language));
        filterButtons.get(HistoryFilter.FAILED).setText(UiText.historyFilterFailed(language));
        filterButtons.get(HistoryFilter.IGNORED).setText(UiText.historyFilterIgnored(language));
        clearHistoryButton.setText(UiText.historyClearButton(language));
        rollbackButton.setText(UiText.historyRollbackButton(language));
        rollbackButton.setTooltip(new Tooltip(UiText.historyRollbackUnavailable(language)));

        timestampColumn.setText(UiText.historyColumnTimestamp(language));
        eventColumn.setText(UiText.historyColumnEventType(language));
        sourceColumn.setText(UiText.historyColumnSource(language));
        tvdbColumn.setText(UiText.scanColumnTvdb(language));
        destinationColumn.setText(UiText.scanColumnDestination(language));
        proposedColumn.setText(UiText.scanColumnProposed(language));
        statusColumn.setText(UiText.historyColumnStatus(language));
        resultColumn.setText(UiText.historyColumnResult(language));

        table.setPlaceholder(buildEmptyState(
                "↺",
                UiText.historyEmptyTitle(language),
                UiText.historyEmptyHint(language)));
        detailPanel.applyLanguage(language);
        table.refresh();
        updateRollbackAvailability();
    }

    private static VBox buildEmptyState(String iconText, String title, String hint) {
        Label icon = new Label(iconText);
        icon.getStyleClass().add("table-empty-icon");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("table-empty-title");
        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().add("table-empty-hint");
        hintLabel.setWrapText(true);
        hintLabel.setMaxWidth(360);
        VBox box = new VBox(10, icon, titleLabel, hintLabel);
        box.getStyleClass().add("table-empty-state");
        box.setAlignment(Pos.CENTER);
        return box;
    }

    public void refresh() {
        List<RunEvent> loaded = new ArrayList<>(store.readAll());
        loaded.sort(Comparator.comparing(RunEvent::occurredAt).reversed());
        events.setAll(loaded);
        applyMetrics();
        selectedEvent.set(null);
        detailPanel.clear();
        table.getSelectionModel().clearSelection();
        updateRollbackAvailability();
    }

    public void setStackedLayout(boolean stacked) {
        if (this.stackedLayout == stacked) {
            return;
        }
        this.stackedLayout = stacked;

        if (currentBody instanceof HBox hbox) {
            hbox.getChildren().clear();
        } else if (currentBody instanceof VBox vbox) {
            vbox.getChildren().clear();
        }

        Region detailRoot = detailPanel.root();
        Pane next;
        if (stacked) {
            detailRoot.setMaxWidth(Double.MAX_VALUE);
            VBox stack = new VBox(16, table, detailRoot);
            VBox.setVgrow(table, Priority.ALWAYS);
            next = stack;
        } else {
            detailRoot.setMaxWidth(420);
            HBox row = new HBox(16, table, detailRoot);
            HBox.setHgrow(table, Priority.ALWAYS);
            next = row;
        }
        int index = content.getChildren().indexOf(currentBody);
        if (index >= 0) {
            content.getChildren().set(index, next);
        }
        currentBody = next;
        VBox.setVgrow(currentBody, Priority.ALWAYS);
    }

    private void configureFilters() {
        filterRow.setAlignment(Pos.CENTER_LEFT);
        filterRow.getChildren().add(searchBox.root());
        for (HistoryFilter filter : HistoryFilter.values()) {
            ToggleButton button = new ToggleButton();
            button.getStyleClass().setAll("filter-chip");
            button.setToggleGroup(filterGroup);
            button.setOnAction(event -> {
                if (!button.isSelected()) {
                    button.setSelected(true);
                    return;
                }
                applyFilter(filter);
            });
            filterButtons.put(filter, button);
            filterRow.getChildren().add(button);
        }
        clearHistoryButton.getStyleClass().add("ghost");
        clearHistoryButton.setOnAction(event -> confirmAndClearHistory());
        rollbackButton.getStyleClass().add("primary");
        rollbackButton.setDisable(true);
        rollbackButton.setOnAction(event -> confirmAndRollback());
        HBox actions = new HBox(8, rollbackButton, clearHistoryButton);
        actions.setAlignment(Pos.CENTER_LEFT);
        filterRow.getChildren().add(actions);
        filterButtons.get(HistoryFilter.ALL).setSelected(true);
        applyFilter(HistoryFilter.ALL);
    }

    private void confirmAndClearHistory() {
        Stage dialog = new Stage(StageStyle.TRANSPARENT);
        dialog.setTitle(UiText.historyClearButton(currentLanguage));
        dialog.initModality(Modality.WINDOW_MODAL);
        if (root.getScene() != null && root.getScene().getWindow() != null) {
            dialog.initOwner(root.getScene().getWindow());
        }
        dialog.getIcons().add(AppShell.logoImage());

        Label title = new Label(UiText.historyClearButton(currentLanguage));
        title.getStyleClass().add("custom-dialog-title");
        Label message = new Label(UiText.historyClearConfirmation(currentLanguage));
        message.getStyleClass().add("custom-dialog-message");
        message.setWrapText(true);

        Button cancel = new Button(UiText.cancelButton(currentLanguage));
        cancel.getStyleClass().add("ghost");
        cancel.setCancelButton(true);
        cancel.setOnAction(event -> dialog.close());

        Button clear = new Button(UiText.historyClearConfirmButton(currentLanguage));
        clear.getStyleClass().add("primary");
        clear.setDefaultButton(true);
        clear.setOnAction(event -> {
            clearHistory();
            dialog.close();
        });

        HBox actions = new HBox(10, cancel, clear);
        actions.getStyleClass().add("custom-dialog-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(12, title, message, actions);
        content.getStyleClass().add("custom-dialog");
        Scene scene = new Scene(content);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        scene.getStylesheets().add(Objects.requireNonNull(
                        HistoryScreen.class.getResource("/styles/app.css"),
                        "Missing stylesheet /styles/app.css")
                .toExternalForm());
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private void clearHistory() {
        store.clear();
        events.clear();
        applyMetrics();
        selectedEvent.set(null);
        detailPanel.clear();
        table.getSelectionModel().clearSelection();
        updateRollbackAvailability();
    }

    private void updateRollbackAvailability() {
        Optional<UUID> runId = selectedExecution().flatMap(HistoryScreen::runId);
        boolean available = rollbackService != null
                && runId.flatMap(rollbackService::availablePlan).isPresent();
        rollbackButton.setDisable(!available);
    }

    private Optional<RunEvent> selectedExecution() {
        return Optional.ofNullable(selectedEvent.get())
                .filter(event -> event.type() == RunEventType.EXECUTION_COMPLETED
                        || event.type() == RunEventType.EXECUTION_FAILED);
    }

    private static Optional<UUID> runId(RunEvent event) {
        try {
            return Optional.of(UUID.fromString(event.metrics().getOrDefault("runId", "")));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private void confirmAndRollback() {
        Optional<RunEvent> execution = selectedExecution();
        Optional<UUID> runId = execution.flatMap(HistoryScreen::runId);
        if (rollbackService == null || runId.isEmpty()) {
            return;
        }
        List<RollbackMove> moves;
        try {
            moves = rollbackService.validate(runId.orElseThrow());
        } catch (IOException exception) {
            recordRollbackFailure(execution.orElseThrow(), exception.getMessage());
            return;
        }
        showRollbackReview(execution.orElseThrow(), runId.orElseThrow(), moves);
    }

    private void showRollbackReview(RunEvent execution, UUID runId, List<RollbackMove> moves) {
        rollbackReviewPane = new RollbackReviewPane(
                currentLanguage,
                runId,
                moves,
                rollbackService,
                this::showHistoryContent,
                restored -> recordRollbackSuccess(execution, runId, restored),
                reason -> recordRollbackFailure(execution, reason),
                onRollbackExecutingChanged);
        Region review = rollbackReviewPane.root();
        content.getChildren().setAll(review);
        VBox.setVgrow(review, Priority.ALWAYS);
    }

    private void showHistoryContent() {
        rollbackReviewPane = null;
        content.getChildren().setAll(heading, banner, metricGrid, filterRow, currentBody);
        VBox.setVgrow(currentBody, Priority.ALWAYS);
        refresh();
    }

    private void recordRollbackSuccess(RunEvent execution, UUID runId, int restored) {
        store.append(RunEvent.of(
                RunEventType.ROLLBACK_COMPLETED,
                RunEventStatus.SUCCESS,
                execution.workspace(),
                Optional.empty(),
                UiText.historyRollbackCompleted(currentLanguage),
                Map.of("restored", String.valueOf(restored), "runId", runId.toString())));
        rollbackFeedback = RollbackFeedback.SUCCESS;
        rollbackFailureReason = "";
        applyBannerState();
        onRollbackCompleted.run();
    }

    private void recordRollbackFailure(RunEvent execution, String reason) {
        rollbackFeedback = RollbackFeedback.FAILURE;
        rollbackFailureReason = String.valueOf(reason);
        store.append(RunEvent.of(
                RunEventType.ROLLBACK_FAILED,
                RunEventStatus.FAILED,
                execution.workspace(),
                Optional.empty(),
                UiText.historyRollbackFailed(currentLanguage).replace("{0}", String.valueOf(reason)),
                Map.of()));
        refresh();
        applyBannerState();
    }

    private void applyBannerState() {
        banner.getStyleClass().removeAll("banner-info", "banner-good", "banner-error");
        switch (rollbackFeedback) {
            case SUCCESS -> {
                banner.getStyleClass().add("banner-good");
                bannerText.setText(UiText.historyRollbackCompleted(currentLanguage));
            }
            case FAILURE -> {
                banner.getStyleClass().add("banner-error");
                bannerText.setText(UiText.historyRollbackFailed(currentLanguage)
                        .replace("{0}", rollbackFailureReason));
            }
            case NONE -> {
                banner.getStyleClass().add("banner-info");
                bannerText.setText(UiText.historyBanner(currentLanguage));
            }
        }
    }

    private enum RollbackFeedback {
        NONE,
        SUCCESS,
        FAILURE
    }

    private void applyFilter(HistoryFilter filter) {
        activeFilter = filter;
        applyCombinedFilter();
    }

    public void setSearchFilter(String query) {
        searchFilter = normalizeSearchText(query);
        applyCombinedFilter();
    }

    private void applyCombinedFilter() {
        filtered.setPredicate(event -> activeFilter.matches(event) && matchesSearch(event, searchFilter));
    }

    private boolean matchesSearch(RunEvent event, String query) {
        if (query.isEmpty()) {
            return true;
        }
        return searchableText(event).contains(query);
    }

    private String searchableText(RunEvent event) {
        StringBuilder text = new StringBuilder();
        appendSearchText(text, TIMESTAMP_FORMAT.format(event.occurredAt()));
        appendSearchText(text, event.subjectPath().map(Path::toString).orElse(""));
        appendSearchText(text, eventTypeText(event.type(), currentLanguage));
        appendSearchText(text, event.type().name());
        appendSearchText(text, metric(event, "tvdbMatch", "tvdb_match"));
        appendSearchText(text, metric(event, "destination", "targetPath", "target_path"));
        appendSearchText(text, metric(event, "proposedFilename", "proposed_filename", "newName", "new_name"));
        appendSearchText(text, statusText(event.status(), currentLanguage));
        appendSearchText(text, event.status().name());
        appendSearchText(text, resultText(event));
        appendSearchText(text, event.summary());
        event.metrics().forEach((key, value) -> {
            appendSearchText(text, key);
            appendSearchText(text, value);
        });
        return normalizeSearchText(text.toString());
    }

    private static void appendSearchText(StringBuilder text, String value) {
        if (value != null && !value.isBlank() && !UiText.EMPTY.equals(value)) {
            text.append(' ').append(value);
        }
    }

    private static String normalizeSearchText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String decomposed = Normalizer.normalize(text.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "");
    }

    private void configureTable() {
        table.getStyleClass().add("preview-table");
        table.setMinHeight(MIN_USABLE_TABLE_HEIGHT);
        RoundedClip.install(table, 14);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
        table.setItems(filtered);
        table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            selectedEvent.set(newValue);
            if (newValue == null) {
                detailPanel.clear();
            } else {
                detailPanel.show(newValue, currentLanguage);
            }
            updateRollbackAvailability();
        });

        timestampColumn.setMinWidth(140);
        timestampColumn.setPrefWidth(160);
        timestampColumn.setCellValueFactory(data ->
                new SimpleStringProperty(TIMESTAMP_FORMAT.format(data.getValue().occurredAt())));
        timestampColumn.setCellFactory(monoCell());

        eventColumn.setMinWidth(140);
        eventColumn.setPrefWidth(160);
        eventColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().type()));
        eventColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(RunEventType item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(eventTypeText(item, currentLanguage));
            }
        });

        sourceColumn.setMinWidth(180);
        sourceColumn.setPrefWidth(240);
        sourceColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().subjectPath().map(Path::toString).orElse(UiText.EMPTY)));
        sourceColumn.setCellFactory(monoCell());

        tvdbColumn.setMinWidth(120);
        tvdbColumn.setPrefWidth(150);
        tvdbColumn.setCellValueFactory(data -> new SimpleStringProperty(metric(data.getValue(), "tvdbMatch", "tvdb_match")));
        tvdbColumn.setCellFactory(proseCell());

        destinationColumn.setMinWidth(160);
        destinationColumn.setPrefWidth(220);
        destinationColumn.setCellValueFactory(data -> new SimpleStringProperty(
                metric(data.getValue(), "destination", "targetPath", "target_path")));
        destinationColumn.setCellFactory(monoCell());

        proposedColumn.setMinWidth(170);
        proposedColumn.setPrefWidth(230);
        proposedColumn.setCellValueFactory(data -> new SimpleStringProperty(
                metric(data.getValue(), "proposedFilename", "proposed_filename", "newName", "new_name")));
        proposedColumn.setCellFactory(monoCell());

        statusColumn.setMinWidth(96);
        statusColumn.setPrefWidth(112);
        statusColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().status()));
        statusColumn.setCellFactory(column -> new TableCell<>() {
            private final Label pill = new Label();

            {
                pill.getStyleClass().add("row-status");
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }

            @Override
            protected void updateItem(RunEventStatus item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                pill.getStyleClass().setAll("row-status", styleClassFor(item));
                pill.setText(statusText(item, currentLanguage));
                setGraphic(pill);
            }
        });

        resultColumn.setMinWidth(120);
        resultColumn.setPrefWidth(150);
        resultColumn.setCellValueFactory(data -> new SimpleStringProperty(resultText(data.getValue())));
        resultColumn.setCellFactory(proseCell());

        table.getColumns().setAll(
                timestampColumn,
                sourceColumn,
                eventColumn,
                tvdbColumn,
                destinationColumn,
                proposedColumn,
                statusColumn,
                resultColumn);
    }

    private static String metric(RunEvent event, String... keys) {
        for (String key : keys) {
            String value = event.metrics().get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return UiText.EMPTY;
    }

    private static String resultText(RunEvent event) {
        String confidence = metric(event, "confidence", "matchConfidence", "match_confidence");
        if (!UiText.EMPTY.equals(confidence)) {
            return confidence;
        }
        return event.summary().isBlank() ? UiText.EMPTY : event.summary();
    }

    private void applyMetrics() {
        int total = events.size();
        long success = events.stream().filter(event -> event.status() == RunEventStatus.SUCCESS).count();
        long warning = events.stream().filter(event -> event.status() == RunEventStatus.WARNING).count();
        long conflict = events.stream().filter(event -> event.status() == RunEventStatus.CONFLICT).count();
        long failed = events.stream().filter(event -> event.status() == RunEventStatus.FAILED).count();
        cardTotal.setValue(String.valueOf(total));
        cardSuccess.setValue(String.valueOf(success));
        cardWarning.setValue(String.valueOf(warning));
        cardConflict.setValue(String.valueOf(conflict));
        cardFailed.setValue(String.valueOf(failed));
    }

    static String eventTypeText(RunEventType type, AppLanguage language) {
        return switch (type) {
            case SCAN_COMPLETED -> UiText.historyEventScanCompleted(language);
            case SCAN_FAILED -> UiText.historyEventScanFailed(language);
            case EXECUTION_COMPLETED -> UiText.historyEventExecutionCompleted(language);
            case EXECUTION_FAILED -> UiText.historyEventExecutionFailed(language);
            case ROLLBACK_COMPLETED -> UiText.historyEventRollbackCompleted(language);
            case ROLLBACK_FAILED -> UiText.historyEventRollbackFailed(language);
        };
    }

    static String statusText(RunEventStatus status, AppLanguage language) {
        return switch (status) {
            case SUCCESS -> UiText.scanRowStatusReady(language);
            case WARNING -> UiText.scanRowStatusWarning(language);
            case CONFLICT -> UiText.scanRowStatusConflict(language);
            case FAILED -> UiText.historyEventScanFailed(language);
            case IGNORED -> UiText.scanRowStatusIgnored(language);
        };
    }

    static String styleClassFor(RunEventStatus status) {
        return switch (status) {
            case SUCCESS -> "ready";
            case WARNING -> "warning";
            case CONFLICT -> "conflict";
            case FAILED -> "conflict";
            case IGNORED -> "ignored";
        };
    }

    private static javafx.util.Callback<TableColumn<RunEvent, String>, TableCell<RunEvent, String>> monoCell() {
        return column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                    getStyleClass().remove("cell-mono");
                    getStyleClass().remove("cell-muted");
                    return;
                }
                setText(item);
                if (!getStyleClass().contains("cell-mono")) {
                    getStyleClass().add("cell-mono");
                }
                if (UiText.EMPTY.equals(item)) {
                    if (!getStyleClass().contains("cell-muted")) {
                        getStyleClass().add("cell-muted");
                    }
                    setTooltip(null);
                } else {
                    getStyleClass().remove("cell-muted");
                    setTooltip(new Tooltip(item));
                }
            }
        };
    }

    private static javafx.util.Callback<TableColumn<RunEvent, String>, TableCell<RunEvent, String>> proseCell() {
        return column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                    return;
                }
                setText(item);
                if (!UiText.EMPTY.equals(item)) {
                    setTooltip(new Tooltip(item));
                }
            }
        };
    }

    private static final class MetricCard {
        private final Label title = new Label();
        private final Label value = new Label(UiText.EMPTY);
        private final VBox root;

        MetricCard() {
            title.getStyleClass().add("card-title");
            value.getStyleClass().add("card-value-mono");
            root = new VBox(4, title, value);
            root.getStyleClass().add("card");
        }

        VBox root() {
            return root;
        }

        void setTitle(String text) {
            title.setText(text);
        }

        void setValue(String text) {
            value.setText(text);
        }
    }
}
