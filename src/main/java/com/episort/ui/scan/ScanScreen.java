package com.episort.ui.scan;

import com.episort.scanner.InventoryScanResult;
import com.episort.scanner.InventorySummary;
import com.episort.ui.AppLanguage;
import com.episort.ui.RoundedClip;
import com.episort.ui.UiText;
import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public final class ScanScreen {
    private static final String EMPTY = "—";

    private final VBox root;
    private final Label heading;
    private final HBox workflowSteps;
    private final Label workflowHelp;
    private final java.util.List<WorkflowStep> steps = new java.util.ArrayList<>();

    private final FlowPane metricGrid;
    private final MetricCard cardTotal = new MetricCard();
    private final MetricCard cardSeries = new MetricCard();
    private final MetricCard cardMovies = new MetricCard();
    private final MetricCard cardUnknown = new MetricCard();
    private final MetricCard cardIgnored = new MetricCard();
    private final MetricCard cardToProcess = new MetricCard();
    private final MetricCard cardConflicts = new MetricCard();
    private final MetricCard cardWarnings = new MetricCard();

    private final TableView<ScanRow> table = new TableView<>();
    private final ObservableList<ScanRow> rows = FXCollections.observableArrayList();
    private final FilteredList<ScanRow> filtered = new FilteredList<>(rows, row -> true);
    private final TableColumn<ScanRow, Boolean> selectionColumn = new TableColumn<>();
    private final TableColumn<ScanRow, String> originalColumn = new TableColumn<>();
    private final TableColumn<ScanRow, String> arrowColumn = new TableColumn<>();
    private final TableColumn<ScanRow, String> proposedColumn = new TableColumn<>();
    private final TableColumn<ScanRow, String> extensionColumn = new TableColumn<>();
    private final TableColumn<ScanRow, ScanMediaType> typeColumn = new TableColumn<>();
    private final TableColumn<ScanRow, String> tvdbColumn = new TableColumn<>();
    private final TableColumn<ScanRow, String> orderColumn = new TableColumn<>();
    private final TableColumn<ScanRow, String> confidenceColumn = new TableColumn<>();
    private final TableColumn<ScanRow, ScanRowStatus> statusColumn = new TableColumn<>();

    private final RowDetailPanel detailPanel = new RowDetailPanel();
    private final VBox batchTvdbSection;
    private final Label batchTvdbHeading;
    private final ListView<BatchTvdbMatch> batchTvdbList = new ListView<>();
    private final ComboBox<String> batchCandidateChoice = new ComboBox<>();
    private final ComboBox<String> batchOrderChoice = new ComboBox<>();
    private final Button batchApplyButton = new Button();
    private final Button batchResetButton = new Button();
    private final CheckBox selectAllCheckbox = new CheckBox();
    private final SimpleObjectProperty<ScanRow> selectedRow = new SimpleObjectProperty<>(null);
    private final SimpleBooleanProperty syncingSelection = new SimpleBooleanProperty(false);
    private boolean loadedFolder;

    private Pane currentBody;
    private boolean stackedLayout = false;
    private AppLanguage currentLanguage = AppLanguage.FRENCH;

    public ScanScreen() {
        heading = new Label();
        heading.getStyleClass().addAll("section-heading", "section-heading-accent");

        workflowSteps = new HBox(0);
        workflowSteps.getStyleClass().add("workflow-steps");
        workflowSteps.setAlignment(Pos.CENTER_LEFT);

        workflowHelp = new Label();
        workflowHelp.getStyleClass().add("workflow-help");
        workflowHelp.setWrapText(true);

        VBox workflow = new VBox(8, workflowSteps, workflowHelp);
        workflow.getStyleClass().add("workflow-progress");

        metricGrid = new FlowPane();
        metricGrid.getStyleClass().add("metric-grid");
        metricGrid.setHgap(12);
        metricGrid.setVgap(12);
        metricGrid.getChildren().addAll(
                cardTotal.root(),
                cardSeries.root(),
                cardMovies.root(),
                cardUnknown.root(),
                cardIgnored.root(),
                cardToProcess.root(),
                cardConflicts.root(),
                cardWarnings.root());

        configureTable();
        batchTvdbHeading = new Label();
        batchTvdbHeading.getStyleClass().addAll("section-heading", "section-heading-accent");
        configureBatchTvdbSection();
        batchTvdbSection = new VBox(10, batchTvdbHeading, batchTvdbList, buildBatchTvdbControls());
        batchTvdbSection.getStyleClass().add("batch-tvdb-section");

        VBox tableStack = new VBox(12, table, batchTvdbSection);
        VBox.setVgrow(table, Priority.ALWAYS);
        HBox initialBody = new HBox(16, tableStack, detailPanel.root());
        HBox.setHgrow(tableStack, Priority.ALWAYS);
        HBox.setHgrow(table, Priority.ALWAYS);
        currentBody = initialBody;

        root = new VBox(14, heading, workflow, metricGrid, currentBody);
        root.getStyleClass().add("screen-root");
        VBox.setVgrow(currentBody, Priority.ALWAYS);

        applyLanguage(AppLanguage.FRENCH);
        clear();
    }

    public Region root() {
        return root;
    }

    public void applyLanguage(AppLanguage language) {
        currentLanguage = language;
        heading.setText(UiText.scanHeading(language));
        rebuildWorkflowSteps(language);
        workflowHelp.setText(UiText.scanSafetyBanner(language));

        cardTotal.setTitle(UiText.scanMetricTotal(language));
        cardSeries.setTitle(UiText.scanMetricSeries(language));
        cardMovies.setTitle(UiText.scanMetricMovies(language));
        cardUnknown.setTitle(UiText.scanMetricUnknown(language));
        cardIgnored.setTitle(UiText.scanMetricIgnored(language));
        cardToProcess.setTitle(UiText.scanMetricToProcess(language));
        cardConflicts.setTitle(UiText.scanMetricConflicts(language));
        cardWarnings.setTitle(UiText.scanMetricWarnings(language));

        selectionColumn.setText(UiText.scanColumnSelection(language));
        originalColumn.setText(UiText.scanColumnOriginal(language));
        arrowColumn.setText("→");
        proposedColumn.setText(UiText.scanColumnProposed(language));
        extensionColumn.setText(UiText.scanColumnExtension(language));
        typeColumn.setText(UiText.scanColumnType(language));
        tvdbColumn.setText(UiText.scanColumnTvdb(language));
        orderColumn.setText(UiText.scanColumnOrder(language));
        confidenceColumn.setText(UiText.scanColumnConfidence(language));
        statusColumn.setText(UiText.scanColumnStatus(language));
        batchTvdbHeading.setText(UiText.scanBatchTvdbHeading(language));
        batchCandidateChoice.setPromptText(UiText.scanBatchTvdbCandidatePlaceholder(language));
        batchOrderChoice.setPromptText(UiText.scanBatchTvdbOrderPlaceholder(language));
        batchOrderChoice.getItems().setAll(
                UiText.scanBatchTvdbOrderAired(language),
                UiText.scanBatchTvdbOrderDvd(language),
                UiText.scanBatchTvdbOrderAbsolute(language));
        batchApplyButton.setText(UiText.scanBatchTvdbApply(language));
        batchResetButton.setText(UiText.scanBatchTvdbReset(language));

        table.setPlaceholder(buildEmptyState(
                "◫",
                UiText.scanEmptyTitle(language),
                UiText.scanEmptyHint(language)));

        detailPanel.applyLanguage(language);
        table.refresh();
    }

    private void rebuildWorkflowSteps(AppLanguage language) {
        workflowSteps.getChildren().clear();
        steps.clear();
        String[] labels = UiText.scanWorkflowSteps(language);
        for (int index = 0; index < labels.length; index++) {
            WorkflowStep step = new WorkflowStep(index + 1, labels[index]);
            steps.add(step);
            workflowSteps.getChildren().add(step.root());
            if (index < labels.length - 1) {
                Region connector = new Region();
                connector.getStyleClass().add("workflow-step-connector");
                HBox.setHgrow(connector, Priority.ALWAYS);
                workflowSteps.getChildren().add(connector);
            }
        }
        updateWorkflowActiveStep();
    }

    private void updateWorkflowActiveStep() {
        int activeIndex = loadedFolder ? 1 : 0;
        for (int index = 0; index < steps.size(); index++) {
            steps.get(index).setState(index, activeIndex);
        }
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

    public void setSearchFilter(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            filtered.setPredicate(row -> true);
            updateSelectAllCheckbox();
            return;
        }
        filtered.setPredicate(row ->
                row.originalFilename().toLowerCase(Locale.ROOT).contains(normalized)
                        || row.extension().toLowerCase(Locale.ROOT).contains(normalized));
        updateSelectAllCheckbox();
    }

    public void apply(Optional<InventoryScanResult> result) {
        if (result.isEmpty()) {
            clear();
            return;
        }
        InventoryScanResult scan = result.orElseThrow();
        loadedFolder = true;
        updateWorkflowActiveStep();
        rows.setAll(ScanRowFactory.from(scan));
        batchTvdbList.getItems().setAll(BatchTvdbMatch.from(scan));
        applyMetrics(scan.summary());
        selectedRow.set(null);
        detailPanel.clear();
        table.getSelectionModel().clearSelection();
        updateSelectAllCheckbox();
    }

    public void clear() {
        rows.clear();
        loadedFolder = false;
        updateWorkflowActiveStep();
        batchTvdbList.getItems().clear();
        cardTotal.setValue(EMPTY);
        cardSeries.setValue(EMPTY);
        cardMovies.setValue(EMPTY);
        cardUnknown.setValue(EMPTY);
        cardIgnored.setValue(EMPTY);
        cardToProcess.setValue(EMPTY);
        cardConflicts.setValue(EMPTY);
        cardWarnings.setValue(EMPTY);
        selectedRow.set(null);
        detailPanel.clear();
        table.getSelectionModel().clearSelection();
        updateSelectAllCheckbox();
    }

    public boolean hasRows() {
        return !rows.isEmpty();
    }

    public boolean hasLoadedFolder() {
        return loadedFolder;
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
            VBox tableStack = new VBox(12, table, batchTvdbSection);
            VBox.setVgrow(table, Priority.ALWAYS);
            VBox stack = new VBox(16, tableStack, detailRoot);
            VBox.setVgrow(tableStack, Priority.ALWAYS);
            VBox.setVgrow(table, Priority.ALWAYS);
            next = stack;
        } else {
            detailRoot.setMaxWidth(420);
            VBox tableStack = new VBox(12, table, batchTvdbSection);
            VBox.setVgrow(table, Priority.ALWAYS);
            HBox row = new HBox(16, tableStack, detailRoot);
            HBox.setHgrow(tableStack, Priority.ALWAYS);
            next = row;
        }
        replaceCurrentBody(next);
    }

    private void replaceCurrentBody(Pane next) {
        int index = root.getChildren().indexOf(currentBody);
        if (index < 0) {
            return;
        }
        root.getChildren().set(index, next);
        currentBody = next;
        VBox.setVgrow(currentBody, Priority.ALWAYS);
    }

    private void applyMetrics(InventorySummary summary) {
        int total = summary.supportedVideoCount()
                + summary.sidecarCount()
                + summary.unsupportedCount()
                + summary.ignoredCount();
        int ignored = summary.sidecarCount() + summary.unsupportedCount() + summary.ignoredCount();

        cardTotal.setValue(String.valueOf(total));
        cardSeries.setValue(String.valueOf(summary.likelySeriesGroupCount()));
        cardMovies.setValue(String.valueOf(summary.likelyMovieGroupCount()));
        cardUnknown.setValue(String.valueOf(summary.unknownItemCount()));
        cardIgnored.setValue(String.valueOf(ignored));
        cardToProcess.setValue(String.valueOf(summary.supportedVideoCount()));
        cardConflicts.setValue("0");
        cardWarnings.setValue(String.valueOf(summary.unknownItemCount()));
    }

    private void configureTable() {
        table.getStyleClass().add("preview-table");
        RoundedClip.install(table, 14);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
        table.setItems(filtered);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.getSelectionModel().getSelectedItems().addListener((ListChangeListener<ScanRow>) change -> {
            if (syncingSelection.get()) {
                return;
            }
            syncRowsFromTableSelection();
        });
        table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            selectedRow.set(newValue);
            if (newValue == null) {
                detailPanel.clear();
            } else {
                detailPanel.show(newValue);
            }
        });

        configureSelectionColumn();
        configureOriginalColumn();
        configureArrowColumn();
        configureProposedColumn();
        configureExtensionColumn();
        configureTypeColumn();
        configureTvdbColumn();
        configureOrderColumn();
        configureConfidenceColumn();
        configureStatusColumn();

        table.getColumns().setAll(
                selectionColumn,
                originalColumn,
                arrowColumn,
                proposedColumn,
                extensionColumn,
                typeColumn,
                tvdbColumn,
                orderColumn,
                confidenceColumn,
                statusColumn);

        table.setRowFactory(view -> {
            TableRow<ScanRow> row = new TableRow<>();
            row.contextMenuProperty().bind(javafx.beans.binding.Bindings.createObjectBinding(
                    () -> row.isEmpty() ? null : buildContextMenu(row.getItem()),
                    row.itemProperty()));
            return row;
        });
    }

    private void configureSelectionColumn() {
        selectionColumn.setMinWidth(46);
        selectionColumn.setPrefWidth(46);
        selectionColumn.setMaxWidth(60);
        selectionColumn.setSortable(false);
        selectionColumn.setReorderable(false);
        selectionColumn.getStyleClass().add("selection-column");
        selectAllCheckbox.getStyleClass().add("row-checkbox");
        selectAllCheckbox.setOnAction(event -> setAllVisibleSelected(selectAllCheckbox.isSelected()));
        StackPane selectAllHost = new StackPane(selectAllCheckbox);
        selectAllHost.getStyleClass().add("selection-header");
        selectionColumn.setGraphic(selectAllHost);
        selectionColumn.setCellValueFactory(data -> data.getValue().selectedProperty().asObject());
        selectionColumn.setCellFactory(column -> new TableCell<>() {
            private final CheckBox checkBox = new CheckBox();
            private ScanRow boundRow;

            {
                checkBox.getStyleClass().add("row-checkbox");
                checkBox.setMouseTransparent(true);
                checkBox.setOnAction(event -> {
                    if (boundRow == null) {
                        return;
                    }
                    setRowSelected(boundRow, checkBox.isSelected());
                });
                setAlignment(Pos.CENTER);
                getStyleClass().add("selection-cell");
                addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                    if (!isEmpty() && event.getButton() == MouseButton.PRIMARY) {
                        event.consume();
                    }
                });
                setOnMouseClicked(event -> {
                    if (boundRow == null || event.getButton() != MouseButton.PRIMARY) {
                        return;
                    }
                    setRowSelected(boundRow, !boundRow.isSelected());
                    event.consume();
                });
            }

            @Override
            protected void updateItem(Boolean value, boolean empty) {
                super.updateItem(value, empty);
                ScanRow row = empty ? null : (getTableRow() == null ? null : getTableRow().getItem());
                boundRow = row;
                if (row == null) {
                    setGraphic(null);
                    return;
                }
                checkBox.setSelected(row.isSelected());
                setGraphic(checkBox);
            }
        });
    }

    private void configureOriginalColumn() {
        originalColumn.setMinWidth(280);
        originalColumn.setPrefWidth(430);
        originalColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().originalFilename()));
        originalColumn.setCellFactory(monoEllipsisCellFactory());
    }

    private void configureArrowColumn() {
        arrowColumn.setMinWidth(34);
        arrowColumn.setPrefWidth(38);
        arrowColumn.setMaxWidth(44);
        arrowColumn.setSortable(false);
        arrowColumn.setReorderable(false);
        arrowColumn.setCellValueFactory(data -> new SimpleStringProperty("→"));
        arrowColumn.setCellFactory(column -> new TableCell<>() {
            {
                getStyleClass().add("before-after-arrow-cell");
                setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
            }
        });
    }

    private void configureProposedColumn() {
        proposedColumn.setMinWidth(280);
        proposedColumn.setPrefWidth(430);
        proposedColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().proposedFilename().orElse(EMPTY)));
        proposedColumn.setCellFactory(proposedNameCellFactory());
    }

    private void configureExtensionColumn() {
        extensionColumn.setMinWidth(60);
        extensionColumn.setPrefWidth(72);
        extensionColumn.setMaxWidth(96);
        extensionColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().extension().isEmpty() ? EMPTY : data.getValue().extension()));
        extensionColumn.setCellFactory(column -> new TableCell<>() {
            private final Label badge = new Label();

            {
                badge.getStyleClass().add("extension-badge");
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                badge.setText(item);
                badge.getStyleClass().setAll("extension-badge", extensionStyle(item));
                setGraphic(badge);
            }
        });
    }

    private void configureTypeColumn() {
        typeColumn.setMinWidth(80);
        typeColumn.setPrefWidth(100);
        typeColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().mediaType()));
        typeColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(ScanMediaType item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(RowDetailPanel.mediaTypeText(item, currentLanguage));
            }
        });
    }

    private void configureTvdbColumn() {
        tvdbColumn.setMinWidth(140);
        tvdbColumn.setPrefWidth(180);
        tvdbColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().tvdbMatch().orElse(EMPTY)));
        tvdbColumn.setCellFactory(monoEllipsisCellFactory());
    }

    private void configureOrderColumn() {
        orderColumn.setMinWidth(92);
        orderColumn.setPrefWidth(112);
        orderColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().order().orElse(EMPTY)));
        orderColumn.setCellFactory(monoEllipsisCellFactory());
    }

    private static String extensionStyle(String extension) {
        return switch (extension.toUpperCase(Locale.ROOT)) {
            case "MKV" -> "mkv";
            case "MP4" -> "mp4";
            case "AVI" -> "avi";
            case "SRT" -> "srt";
            default -> "fallback";
        };
    }

    private void configureConfidenceColumn() {
        confidenceColumn.setMinWidth(70);
        confidenceColumn.setPrefWidth(80);
        confidenceColumn.setCellValueFactory(data -> new SimpleStringProperty(formatConfidence(data.getValue().confidence())));
        confidenceColumn.setCellFactory(monoEllipsisCellFactory());
    }

    private void configureStatusColumn() {
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
            protected void updateItem(ScanRowStatus item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                pill.getStyleClass().setAll("row-status", styleClassFor(item));
                pill.setText(RowDetailPanel.statusText(item, currentLanguage));
                setGraphic(pill);
            }
        });
    }

    private static String styleClassFor(ScanRowStatus status) {
        return switch (status) {
            case PREVIEW -> "preview";
            case READY -> "ready";
            case WARNING -> "warning";
            case CONFLICT -> "conflict";
            case IGNORED -> "ignored";
        };
    }

    private static javafx.util.Callback<TableColumn<ScanRow, String>, TableCell<ScanRow, String>> monoEllipsisCellFactory() {
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
                if (EMPTY.equals(item)) {
                    if (!getStyleClass().contains("cell-muted")) {
                        getStyleClass().add("cell-muted");
                    }
                    setTooltip(null);
                } else {
                    getStyleClass().remove("cell-muted");
                    Tooltip tooltip = new Tooltip(item);
                    setTooltip(tooltip);
                }
            }
        };
    }

    private static javafx.util.Callback<TableColumn<ScanRow, String>, TableCell<ScanRow, String>> proposedNameCellFactory() {
        return column -> {
            TableCell<ScanRow, String> cell = monoEllipsisCellFactory().call(column);
            cell.getStyleClass().add("proposed-name-cell");
            return cell;
        };
    }

    private static String formatConfidence(OptionalDouble confidence) {
        if (confidence.isEmpty()) {
            return EMPTY;
        }
        return String.format("%.0f%%", confidence.orElseThrow() * 100.0);
    }

    private void setRowSelected(ScanRow row, boolean selected) {
        syncingSelection.set(true);
        try {
            row.setSelected(selected);
            if (selected) {
                if (!table.getSelectionModel().getSelectedItems().contains(row)) {
                    table.getSelectionModel().select(row);
                }
            } else {
                int visibleIndex = filtered.indexOf(row);
                if (visibleIndex >= 0) {
                    table.getSelectionModel().clearSelection(visibleIndex);
                }
                if (selectedRow.get() == row) {
                    ScanRow fallback = table.getSelectionModel().getSelectedItem();
                    selectedRow.set(fallback);
                    if (fallback == null) {
                        detailPanel.clear();
                    } else {
                        detailPanel.show(fallback);
                    }
                }
            }
        } finally {
            syncingSelection.set(false);
            updateSelectAllCheckbox();
            table.refresh();
        }
    }

    private void setAllVisibleSelected(boolean selected) {
        syncingSelection.set(true);
        try {
            table.getSelectionModel().clearSelection();
            for (ScanRow row : filtered) {
                row.setSelected(selected);
                if (selected) {
                    table.getSelectionModel().select(row);
                }
            }
            ScanRow current = table.getSelectionModel().getSelectedItem();
            selectedRow.set(current);
            if (current == null) {
                detailPanel.clear();
            } else {
                detailPanel.show(current);
            }
        } finally {
            syncingSelection.set(false);
            updateSelectAllCheckbox();
            table.refresh();
        }
    }

    private void syncRowsFromTableSelection() {
        for (ScanRow row : rows) {
            row.setSelected(table.getSelectionModel().getSelectedItems().contains(row));
        }
        updateSelectAllCheckbox();
        table.refresh();
    }

    private void updateSelectAllCheckbox() {
        boolean hasVisibleRows = !filtered.isEmpty();
        boolean allVisibleSelected = hasVisibleRows && filtered.stream().allMatch(ScanRow::isSelected);
        selectAllCheckbox.setDisable(!hasVisibleRows);
        selectAllCheckbox.setSelected(allVisibleSelected);
    }

    private ContextMenu buildContextMenu(ScanRow row) {
        ContextMenu menu = new ContextMenu();
        MenuItem ignore = new MenuItem(UiText.scanContextIgnore(currentLanguage));
        ignore.setOnAction(event -> {
            row.setStatus(ScanRowStatus.IGNORED);
            table.refresh();
            if (selectedRow.get() == row) {
                detailPanel.show(row);
            }
        });

        MenuItem reset = new MenuItem(UiText.scanContextResetMatch(currentLanguage));
        reset.setDisable(row.tvdbMatch().isEmpty());
        reset.setOnAction(event -> {
            row.setTvdbMatch(Optional.empty());
            row.setOrder(Optional.empty());
            row.setProposedFilename(Optional.empty());
            row.setDestination(Optional.empty());
            table.refresh();
            if (selectedRow.get() == row) {
                detailPanel.show(row);
            }
        });

        MenuItem copyPath = new MenuItem(UiText.scanContextCopyPath(currentLanguage));
        copyPath.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(row.sourcePath().toAbsolutePath().normalize().toString());
            Clipboard.getSystemClipboard().setContent(content);
        });

        MenuItem openFolder = new MenuItem(UiText.scanContextOpenFolder(currentLanguage));
        Path parent = row.sourcePath().getParent();
        openFolder.setDisable(parent == null || !Desktop.isDesktopSupported());
        openFolder.setOnAction(event -> {
            if (parent == null) {
                return;
            }
            try {
                Desktop.getDesktop().open(parent.toFile());
            } catch (IOException | UnsupportedOperationException ignored) {
                // Best-effort; if the desktop integration is unavailable, do nothing.
            }
        });

        menu.getItems().setAll(ignore, reset, copyPath, openFolder);
        return menu;
    }

    private void configureBatchTvdbSection() {
        batchTvdbList.getStyleClass().add("batch-tvdb-list");
        batchTvdbList.setPrefHeight(122);
        batchTvdbList.setPlaceholder(new Label(EMPTY));
        batchTvdbList.setCellFactory(view -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(BatchTvdbMatch item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label title = new Label(item.seedName());
                title.getStyleClass().add("batch-tvdb-title");
                Label meta = new Label("%s • %d • %s".formatted(
                        item.typeText(currentLanguage),
                        item.itemCount(),
                        item.statusText(currentLanguage)));
                meta.getStyleClass().add("batch-tvdb-meta");
                VBox box = new VBox(2, title, meta);
                setGraphic(box);
            }
        });
        batchCandidateChoice.setDisable(true);
        batchOrderChoice.setDisable(true);
        batchApplyButton.getStyleClass().add("primary");
        batchApplyButton.setDisable(true);
        batchResetButton.getStyleClass().add("ghost");
        batchResetButton.setDisable(true);
    }

    private HBox buildBatchTvdbControls() {
        HBox controls = new HBox(8, batchCandidateChoice, batchOrderChoice, batchApplyButton, batchResetButton);
        controls.getStyleClass().add("batch-tvdb-controls");
        controls.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(batchCandidateChoice, Priority.ALWAYS);
        return controls;
    }

    private static final class MetricCard {
        private final Label title = new Label();
        private final Label value = new Label(EMPTY);
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

    private static final class WorkflowStep {
        private final HBox root;
        private final Label number;
        private final Label label;

        WorkflowStep(int stepNumber, String text) {
            number = new Label(String.valueOf(stepNumber));
            number.getStyleClass().add("workflow-step-number");
            label = new Label(text);
            label.getStyleClass().add("workflow-step-label");
            root = new HBox(8, number, label);
            root.getStyleClass().add("workflow-step");
            root.setAlignment(Pos.CENTER_LEFT);
        }

        HBox root() {
            return root;
        }

        void setState(int index, int activeIndex) {
            root.getStyleClass().setAll("workflow-step");
            if (index == activeIndex) {
                root.getStyleClass().add("active");
            } else if (index < activeIndex) {
                root.getStyleClass().add("complete");
            } else {
                root.getStyleClass().add("pending");
            }
        }
    }
}
