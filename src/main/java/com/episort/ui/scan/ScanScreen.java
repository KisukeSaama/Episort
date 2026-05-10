package com.episort.ui.scan;

import com.episort.ai.AiChatBackend;
import com.episort.ai.AiChatToolCall;
import com.episort.ai.AiFilePatternParse;
import com.episort.ai.AiGroupSuggestion;
import com.episort.ai.AiPatternRefinementResult;
import com.episort.ai.AiPatternSuggestion;
import com.episort.ai.AiPatternToken;
import com.episort.scanner.InventoryGroup;
import com.episort.scanner.InventoryGroupType;
import com.episort.scanner.InventoryScanResult;
import com.episort.scanner.InventorySummary;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
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
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.cell.TextFieldTableCell;
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
import javafx.util.StringConverter;
import javafx.util.converter.DefaultStringConverter;

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
    private final SortedList<ScanRow> sorted = new SortedList<>(filtered);
    private final HBox filterBar = new HBox(8);
    private final ToggleGroup filterGroup = new ToggleGroup();
    private final Map<ScanRowFilter, ToggleButton> filterButtons = new HashMap<>();
    private final TableColumn<ScanRow, Boolean> selectionColumn = new TableColumn<>();
    private final TableColumn<ScanRow, String> originalColumn = new TableColumn<>();
    private final TableColumn<ScanRow, String> arrowColumn = new TableColumn<>();
    private final TableColumn<ScanRow, String> proposedColumn = new TableColumn<>();
    private final TableColumn<ScanRow, String> patternColumn = new TableColumn<>();
    private final TableColumn<ScanRow, String> extensionColumn = new TableColumn<>();
    private final TableColumn<ScanRow, ScanMediaType> typeColumn = new TableColumn<>();
    private final TableColumn<ScanRow, String> orderColumn = new TableColumn<>();
    private final TableColumn<ScanRow, String> confidenceColumn = new TableColumn<>();
    private final TableColumn<ScanRow, ScanRowStatus> statusColumn = new TableColumn<>();

    private final RowDetailPanel detailPanel = new RowDetailPanel();
    private final Region detailRoot;
    private final AiChatPanel aiChatPanel = new AiChatPanel();
    private final Map<ScanRow, BatchTvdbMatch> rowToGroupMatch = new HashMap<>();
    private final Map<ScanRow, InventoryGroup> rowToGroup = new HashMap<>();
    private final Map<ScanRow, java.util.List<ScanRow>> groupRows = new HashMap<>();
    private final java.util.List<String> tvdbCandidatesForCurrentGroup = new java.util.ArrayList<>();
    private final CheckBox selectAllCheckbox = new CheckBox();
    private final SimpleObjectProperty<ScanRow> selectedRow = new SimpleObjectProperty<>(null);
    private final SimpleBooleanProperty syncingSelection = new SimpleBooleanProperty(false);
    private boolean loadedFolder;
    private boolean loading;
    private String searchQuery = "";
    private ScanRowFilter activeFilter = ScanRowFilter.ALL;

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
        detailPanel.setOnApplyCandidate(this::applyTvdbCandidate);
        detailPanel.setOnResetMatch(this::resetTvdbMatch);
        detailPanel.setOnApplyInputPattern(this::applyInputPatternToSelection);
        detailRoot = detailPanel.root();
        detailRoot.setMinWidth(320);
        detailRoot.setPrefWidth(380);
        detailRoot.setMaxWidth(420);
        aiChatPanel.setApplyHandler(this::applyToolCall);

        VBox tableStack = new VBox(12, filterBar, table, aiChatPanel.root());
        VBox.setVgrow(table, Priority.ALWAYS);
        HBox initialBody = new HBox(16, tableStack, detailRoot);
        HBox.setHgrow(tableStack, Priority.ALWAYS);
        HBox.setHgrow(table, Priority.ALWAYS);
        HBox.setHgrow(detailRoot, Priority.NEVER);
        VBox.setVgrow(detailRoot, Priority.ALWAYS);
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
        patternColumn.setText(UiText.scanColumnPattern(language));
        extensionColumn.setText(UiText.scanColumnExtension(language));
        typeColumn.setText(UiText.scanColumnType(language));
        orderColumn.setText(UiText.scanColumnOrder(language));
        confidenceColumn.setText(UiText.scanColumnConfidence(language));
        statusColumn.setText(UiText.scanColumnStatus(language));
        setFilterButtonText(ScanRowFilter.ALL, UiText.scanFilterAll(language));
        setFilterButtonText(ScanRowFilter.MOVIES, UiText.scanFilterMovies(language));
        setFilterButtonText(ScanRowFilter.SERIES, UiText.scanFilterSeries(language));
        setFilterButtonText(ScanRowFilter.UNKNOWN, UiText.scanFilterUnknown(language));

        table.setPlaceholder(buildEmptyState(
                "◫",
                UiText.scanEmptyTitle(language),
                UiText.scanEmptyHint(language)));

        detailPanel.applyLanguage(language);
        aiChatPanel.applyLanguage(language);
        table.refresh();
    }

    public void setAiChatBackend(AiChatBackend backend) {
        aiChatPanel.setBackend(backend);
    }

    public void refreshAiChatAvailability() {
        aiChatPanel.refreshAvailability();
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
        updateWorkflowActiveStep();
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
        int activeIndex = loading || loadedFolder ? 1 : 0;
        for (int index = 0; index < steps.size(); index++) {
            steps.get(index).setState(index, activeIndex, loading && index == activeIndex);
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
        searchQuery = normalized;
        updateFilterPredicate();
        updateSelectAllCheckbox();
    }

    public void apply(Optional<InventoryScanResult> result) {
        if (result.isEmpty()) {
            clear();
            return;
        }
        InventoryScanResult scan = result.orElseThrow();
        loading = false;
        loadedFolder = true;
        updateWorkflowActiveStep();
        rows.setAll(ScanRowFactory.from(scan));
        rebuildGroupIndex(scan);
        applyMetrics(scan.summary());
        selectedRow.set(null);
        detailPanel.clear();
        aiChatPanel.clear();
        aiChatPanel.refreshAvailability();
        table.getSelectionModel().clearSelection();
        updateSelectAllCheckbox();
    }

    private void rebuildGroupIndex(InventoryScanResult scan) {
        rowToGroup.clear();
        rowToGroupMatch.clear();
        groupRows.clear();
        Map<String, ScanRow> byPath = rows.stream()
                .collect(Collectors.toMap(
                        r -> r.sourcePath().toAbsolutePath().normalize().toString(),
                        r -> r,
                        (a, b) -> a));
        for (InventoryGroup group : scan.groups()) {
            BatchTvdbMatch direct = directMatch(group);
            java.util.List<ScanRow> members = new java.util.ArrayList<>();
            for (com.episort.scanner.InventoryItem item : group.items()) {
                ScanRow row = byPath.get(item.sourcePath().toAbsolutePath().normalize().toString());
                if (row != null) {
                    members.add(row);
                    rowToGroup.put(row, group);
                    rowToGroupMatch.put(row, direct);
                }
            }
            for (ScanRow row : members) {
                groupRows.put(row, members);
            }
        }
    }

    private static BatchTvdbMatch directMatch(InventoryGroup group) {
        String seed = group.seedName() == null || group.seedName().isBlank() ? "—" : group.seedName();
        return new BatchTvdbMatch(seed, group.type(), group.items().size(), group.tvdbIdentityFinal());
    }

    public void clear() {
        rows.clear();
        loadedFolder = false;
        loading = false;
        updateWorkflowActiveStep();
        rowToGroup.clear();
        rowToGroupMatch.clear();
        groupRows.clear();
        aiChatPanel.clear();
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

    /**
     * Applies the local-AI pattern-refinement output. The AI is the authority on
     * media-type classification: when it returns a confident verdict for a
     * group, every row in that group has its mediaType overridden. The pattern
     * label (if any) is attached to rows as an advisory note.
     */
    public void applyAiRefinement(AiPatternRefinementResult refinement) {
        if (refinement == null || !refinement.refined() || refinement.suggestions().isEmpty()) {
            return;
        }
        Map<String, AiGroupSuggestion> bySeed = new HashMap<>();
        for (AiGroupSuggestion s : refinement.suggestions()) {
            bySeed.put(s.seedName(), s);
        }
        for (ScanRow row : rows) {
            InventoryGroup group = rowToGroup.get(row);
            if (group == null) continue;
            AiGroupSuggestion suggestion = bySeed.get(group.seedName());
            if (suggestion == null) continue;
            AiPatternSuggestion ps = suggestion.suggestion();
            ps.classifiedType()
                    .filter(t -> ps.classificationConfidence().orElse(0.0) >= 0.6)
                    .ifPresent(type -> row.setMediaType(toScanMediaType(type)));
            if (!ps.suggestedPatterns().isEmpty()) {
                row.setNoteText(Optional.of("AI : " + String.join(", ", ps.suggestedPatterns())));
            }
            if (canAcceptAiParse(row)) {
                aiParseFor(row, ps)
                        .or(() -> ScanInputPatternParser.parse(row.originalFilename()))
                        .map(parse -> parse.withSource(ScanInputParseSource.AI))
                        .ifPresent(parse -> {
                            row.setInputParse(Optional.of(parse));
                            row.setInputPattern(Optional.of(parse.summary().isBlank() ? parse.label() : parse.summary()));
                            if (parse.confidence().isPresent()) {
                                row.setConfidence(parse.confidence());
                            }
                        });
            }
        }
        table.refresh();
    }

    private void setFilterButtonText(ScanRowFilter filter, String text) {
        ToggleButton button = filterButtons.get(filter);
        if (button != null) {
            button.setText(text);
        }
    }

    private static boolean canAcceptAiParse(ScanRow row) {
        if (row.inputParse().map(ScanInputParse::source).filter(ScanInputParseSource.USER::equals).isPresent()) {
            return false;
        }
        return row.inputParse().isEmpty() || row.confidence().isEmpty() || row.confidence().orElseThrow() < 0.6;
    }

    private static Optional<ScanInputParse> aiParseFor(ScanRow row, AiPatternSuggestion suggestion) {
        for (AiFilePatternParse parse : suggestion.fileParses()) {
            if (row.originalFilename().equals(parse.filename())) {
                java.util.List<ScanInputToken> tokens = parse.tokens().stream()
                        .map(ScanScreen::toScanToken)
                        .flatMap(Optional::stream)
                        .toList();
                if (tokens.isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(new ScanInputParse(
                        parse.pattern(),
                        tokens,
                        parse.normalizedOrder(),
                        parse.confidence(),
                        ScanInputParseSource.AI));
            }
        }
        return Optional.empty();
    }

    private static Optional<ScanInputToken> toScanToken(AiPatternToken token) {
        try {
            return Optional.of(new ScanInputToken(
                    ScanInputRole.valueOf(token.role().toUpperCase(Locale.ROOT)),
                    token.rawValue(),
                    token.normalizedValue(),
                    token.start(),
                    token.end()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static ScanMediaType toScanMediaType(InventoryGroupType type) {
        return switch (type) {
            case LIKELY_SERIES -> ScanMediaType.SERIES;
            case LIKELY_MOVIE -> ScanMediaType.MOVIE;
            case UNKNOWN -> ScanMediaType.UNKNOWN;
            case SIDECAR, UNSUPPORTED, IGNORED -> ScanMediaType.IGNORED;
        };
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

        Pane next;
        if (stacked) {
            detailRoot.setMaxWidth(Double.MAX_VALUE);
            VBox tableStack = new VBox(12, filterBar, table, aiChatPanel.root());
            VBox.setVgrow(table, Priority.ALWAYS);
            VBox stack = new VBox(16, tableStack, detailRoot);
            VBox.setVgrow(tableStack, Priority.ALWAYS);
            VBox.setVgrow(table, Priority.ALWAYS);
            VBox.setVgrow(detailRoot, Priority.ALWAYS);
            next = stack;
        } else {
            detailRoot.setMaxWidth(420);
            detailRoot.setPrefWidth(380);
            VBox tableStack = new VBox(12, filterBar, table, aiChatPanel.root());
            VBox.setVgrow(table, Priority.ALWAYS);
            HBox row = new HBox(16, tableStack, detailRoot);
            HBox.setHgrow(tableStack, Priority.ALWAYS);
            HBox.setHgrow(detailRoot, Priority.NEVER);
            VBox.setVgrow(detailRoot, Priority.ALWAYS);
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
        table.setEditable(true);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.getSelectionModel().getSelectedItems().addListener((ListChangeListener<ScanRow>) change -> {
            if (syncingSelection.get()) {
                return;
            }
            syncRowsFromTableSelection();
            updateAiChatTargetFromSelection();
        });
        table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            selectedRow.set(newValue);
            if (newValue == null) {
                detailPanel.clear();
            } else {
                detailPanel.show(newValue, rowToGroupMatch.get(newValue));
            }
            updateAiChatTargetFromSelection();
        });

        configureSelectionColumn();
        configureOriginalColumn();
        configureArrowColumn();
        configureProposedColumn();
        configurePatternColumn();
        configureExtensionColumn();
        configureTypeColumn();
        configureOrderColumn();
        configureConfidenceColumn();
        configureStatusColumn();
        configureFilterBar();

        table.getColumns().setAll(
                selectionColumn,
                originalColumn,
                arrowColumn,
                proposedColumn,
                patternColumn,
                extensionColumn,
                typeColumn,
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
        originalColumn.setComparator(ScanRowTableSupport.NATURAL_TEXT);
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
        proposedColumn.setCellFactory(column -> {
            TextFieldTableCell<ScanRow, String> cell = new TextFieldTableCell<>(new DefaultStringConverter());
            cell.getStyleClass().add("proposed-name-cell");
            return cell;
        });
        proposedColumn.setOnEditCommit(event -> {
            ScanRow row = event.getRowValue();
            String value = event.getNewValue() == null ? "" : event.getNewValue().trim();
            if (value.isBlank() || EMPTY.equals(value)) {
                row.setProposedFilename(Optional.empty());
            } else {
                row.setProposedFilename(Optional.of(value));
            }
            table.refresh();
            if (selectedRow.get() == row) {
                detailPanel.show(row, rowToGroupMatch.get(row));
            }
            updateAiChatTargetFromSelection(true);
        });
        proposedColumn.setComparator(ScanRowTableSupport.NATURAL_TEXT);
    }

    private void configurePatternColumn() {
        patternColumn.setMinWidth(160);
        patternColumn.setPrefWidth(300);
        patternColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().inputPattern().orElse(EMPTY)));
        patternColumn.setCellFactory(column -> new TextFieldTableCell<>(new DefaultStringConverter()) {
            @Override
            public void updateItem(String item, boolean empty) {
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
                    return;
                }
                getStyleClass().remove("cell-muted");
                ScanRow row = getTableRow() == null ? null : getTableRow().getItem();
                String tooltip = row == null ? item : patternTooltip(row);
                Tooltip t = new Tooltip(tooltip);
                t.setWrapText(true);
                t.setMaxWidth(520);
                setTooltip(t);
            }
        });
        patternColumn.setOnEditCommit(event -> applyInputPatternToSelection(event.getRowValue(), event.getNewValue()));
        patternColumn.setComparator(ScanRowTableSupport.NATURAL_TEXT);
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
        extensionColumn.setComparator(ScanRowTableSupport.NATURAL_TEXT);
    }

    private void configureTypeColumn() {
        typeColumn.setMinWidth(118);
        typeColumn.setPrefWidth(132);
        typeColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().mediaType()));
        typeColumn.setCellFactory(column -> new TableCell<>() {
            private final ComboBox<ScanMediaType> picker = new ComboBox<>();
            private boolean updating;

            {
                picker.getItems().setAll(ScanMediaType.SERIES, ScanMediaType.MOVIE);
                picker.setMaxWidth(Double.MAX_VALUE);
                picker.setConverter(mediaTypeConverter());
                picker.setOnAction(event -> {
                    if (updating) {
                        return;
                    }
                    ScanRow row = getTableRow() == null ? null : getTableRow().getItem();
                    ScanMediaType value = picker.getValue();
                    if (row != null && value != null && row.mediaType() != value) {
                        applyMediaTypeToSelection(row, value);
                    }
                });
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }

            @Override
            protected void updateItem(ScanMediaType item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                updating = true;
                try {
                    picker.setConverter(mediaTypeConverter());
                    picker.setValue(item == ScanMediaType.MOVIE ? ScanMediaType.MOVIE : ScanMediaType.SERIES);
                } finally {
                    updating = false;
                }
                setGraphic(picker);
            }
        });
        typeColumn.setComparator(ScanRowTableSupport.MEDIA_TYPE);
    }

    private void configureOrderColumn() {
        orderColumn.setMinWidth(92);
        orderColumn.setPrefWidth(112);
        orderColumn.setCellValueFactory(data -> new SimpleStringProperty(orderText(data.getValue().order().orElse(""))));
        orderColumn.setCellFactory(monoEllipsisCellFactory());
        orderColumn.setComparator(ScanRowTableSupport.NATURAL_TEXT);
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
        confidenceColumn.setComparator(ScanRowTableSupport.CONFIDENCE_PERCENT);
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
                ScanRow row = getTableRow() == null ? null : getTableRow().getItem();
                if (row != null && !row.statusReasons().isEmpty()) {
                    Tooltip tooltip = new Tooltip(String.join("\n", row.statusReasons()));
                    tooltip.setWrapText(true);
                    tooltip.setMaxWidth(420);
                    pill.setTooltip(tooltip);
                } else {
                    pill.setTooltip(null);
                }
                setGraphic(pill);
            }
        });
        statusColumn.setComparator(ScanRowTableSupport.STATUS);
    }

    private void configureFilterBar() {
        filterBar.getStyleClass().add("scan-filter-bar");
        filterBar.setAlignment(Pos.CENTER_LEFT);
        addFilterButton(ScanRowFilter.ALL);
        addFilterButton(ScanRowFilter.MOVIES);
        addFilterButton(ScanRowFilter.SERIES);
        addFilterButton(ScanRowFilter.UNKNOWN);
        filterGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                filterGroup.selectToggle(filterButtons.get(activeFilter));
                return;
            }
            activeFilter = (ScanRowFilter) newValue.getUserData();
            updateFilterPredicate();
            updateFilterButtonStyles();
        });
        filterGroup.selectToggle(filterButtons.get(ScanRowFilter.ALL));
    }

    private void addFilterButton(ScanRowFilter filter) {
        ToggleButton button = new ToggleButton();
        button.setUserData(filter);
        button.setToggleGroup(filterGroup);
        button.getStyleClass().add("filter-chip");
        filterButtons.put(filter, button);
        filterBar.getChildren().add(button);
    }

    private static String styleClassFor(ScanRowStatus status) {
        return switch (status) {
            case OK -> "ready";
            case REVIEW -> "warning";
            case AI, TVDB, TYPE, EXT, PATTERN, META, PATH, ERROR -> "warning";
            case CONFLICT -> "conflict";
            case DUPLICATE -> "conflict";
            case IGNORED -> "ignored";
        };
    }

    private String orderText(String order) {
        return switch (order) {
            case "TO_DEFINE" -> UiText.scanOrderToDefine(currentLanguage);
            case "UNAVAILABLE" -> UiText.scanOrderUnavailable(currentLanguage);
            default -> order == null || order.isBlank() ? EMPTY : order;
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

    private void updateFilterPredicate() {
        filtered.setPredicate(row -> matchesSearch(row) && ScanRowTableSupport.matchesFilter(row, activeFilter));
        updateSelectAllCheckbox();
    }

    private boolean matchesSearch(ScanRow row) {
        if (searchQuery.isBlank()) {
            return true;
        }
        return row.originalFilename().toLowerCase(Locale.ROOT).contains(searchQuery)
                || row.extension().toLowerCase(Locale.ROOT).contains(searchQuery);
    }

    private void updateFilterButtonStyles() {
        for (Map.Entry<ScanRowFilter, ToggleButton> entry : filterButtons.entrySet()) {
            ToggleButton button = entry.getValue();
            button.getStyleClass().remove("active");
            if (entry.getKey() == activeFilter && !button.getStyleClass().contains("active")) {
                button.getStyleClass().add("active");
            }
        }
    }

    private StringConverter<ScanMediaType> mediaTypeConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(ScanMediaType type) {
                if (type == null) {
                    return "";
                }
                return switch (type) {
                    case SERIES -> UiText.scanMediaTypeSeries(currentLanguage);
                    case MOVIE -> UiText.scanMediaTypeMovie(currentLanguage);
                    case UNKNOWN, IGNORED -> "";
                };
            }

            @Override
            public ScanMediaType fromString(String value) {
                if (UiText.scanMediaTypeMovie(currentLanguage).equals(value)) {
                    return ScanMediaType.MOVIE;
                }
                return ScanMediaType.SERIES;
            }
        };
    }

    private void applyMediaTypeToSelection(ScanRow anchor, ScanMediaType mediaType) {
        for (ScanRow row : rowsForBatchEdit(anchor)) {
            row.setMediaType(mediaType);
        }
        refreshAfterBatchEdit(anchor);
    }

    private void applyInputPatternToSelection(ScanRow anchor, String patternText) {
        if (anchor == null) {
            return;
        }
        String value = patternText == null ? "" : patternText.trim();
        for (ScanRow row : rowsForBatchEdit(anchor)) {
            applyManualInputPattern(row, value);
        }
        refreshAfterBatchEdit(anchor);
    }

    private java.util.List<ScanRow> rowsForBatchEdit(ScanRow anchor) {
        java.util.List<ScanRow> selected = selectedRowsForAiContext();
        if (selected.contains(anchor)) {
            return selected;
        }
        return java.util.List.of(anchor);
    }

    private void refreshAfterBatchEdit(ScanRow anchor) {
        table.refresh();
        if (selectedRow.get() != null) {
            detailPanel.show(selectedRow.get(), rowToGroupMatch.get(selectedRow.get()));
        } else if (anchor != null) {
            detailPanel.show(anchor, rowToGroupMatch.get(anchor));
        }
        updateAiChatTargetFromSelection(true);
    }

    private static void applyManualInputPattern(ScanRow row, String value) {
        if (value == null || value.isBlank() || EMPTY.equals(value)) {
            row.setInputPattern(Optional.empty());
            row.setInputParse(Optional.empty());
            return;
        }
        row.setInputPattern(Optional.of(value));
        ScanInputPatternParser.parse(row.originalFilename())
                .map(parse -> parse.withLabel(firstLine(value)).withSource(ScanInputParseSource.USER))
                .ifPresent(parse -> {
                    row.setInputParse(Optional.of(parse));
                    if (parse.confidence().isPresent()) {
                        row.setConfidence(parse.confidence());
                    }
                });
    }

    private static String firstLine(String value) {
        int newline = value.indexOf('\n');
        return newline >= 0 ? value.substring(0, newline).trim() : value.trim();
    }

    private static String patternTooltip(ScanRow row) {
        if (row.inputParse().isEmpty()) {
            return row.inputPattern().orElse(EMPTY);
        }
        ScanInputParse parse = row.inputParse().orElseThrow();
        return (parse.summary().isBlank() ? parse.label() : parse.summary())
                + "\n" + parse.positionsSummary()
                + "\nsource=" + parse.source();
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
                int visibleIndex = sorted.indexOf(row);
                if (visibleIndex >= 0) {
                    table.getSelectionModel().clearSelection(visibleIndex);
                }
                if (selectedRow.get() == row) {
                    ScanRow fallback = table.getSelectionModel().getSelectedItem();
                    selectedRow.set(fallback);
                    if (fallback == null) {
                        detailPanel.clear();
                    } else {
                        detailPanel.show(fallback, rowToGroupMatch.get(fallback));
                    }
                }
            }
        } finally {
            syncingSelection.set(false);
            updateSelectAllCheckbox();
            updateAiChatTargetFromSelection();
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
            updateAiChatTargetFromSelection();
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

    private String buildContextSummary(ScanRow row) {
        return buildSingleRowContext(row);
    }

    private String buildContextSummary(java.util.List<ScanRow> selectedRows) {
        if (selectedRows == null || selectedRows.isEmpty()) {
            return "";
        }
        if (selectedRows.size() == 1) {
            return buildSingleRowContext(selectedRows.get(0));
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Lot sélectionné : ").append(selectedRows.size()).append(" fichiers.\n");
        sb.append("Pattern d'entrée détecté : ")
                .append(commonInputPattern(selectedRows).orElse("mixte ou inconnu")).append('\n');
        sb.append("Pattern de sortie attendu : {series} - S{season}E{episode} - {title}.{extension}\n");
        sb.append("Utilise chaque ligne du lot, pas seulement la dernière.\n");
        sb.append("Fichiers du lot :\n");
        for (ScanRow selected : selectedRows) {
            sb.append("  - original=").append(selected.originalFilename())
                    .append(" | ext=").append(selected.extension().isBlank() ? EMPTY : selected.extension());
            selected.inputPattern().ifPresent(pattern -> sb.append(" | patternEntree=").append(pattern));
            selected.inputParse().ifPresent(parse -> sb.append(" | patternStructure=")
                    .append(parse.summary())
                    .append(" | positions=")
                    .append(parse.positionsSummary())
                    .append(" | source=")
                    .append(parse.source()));
            selected.inputParse().flatMap(ScanInputParse::normalizedOrder).ifPresent(order -> {
                sb.append(" | ordreEpisode=").append(order);
                sb.append(" | saison=").append(seasonFromOrder(order));
                sb.append(" | episode=").append(episodeFromOrder(order));
            });
            sb.append(" | titreDetecte=").append(inferredTitle(selected));
            selected.proposedFilename().ifPresent(proposed -> sb.append(" | propose=").append(proposed));
            selected.tvdbMatch().ifPresent(match -> sb.append(" | tvdb=").append(match));
            sb.append('\n');
        }
        if (tvdbCandidatesForCurrentGroup.isEmpty()) {
            sb.append("Candidats TVDB disponibles : aucun pour l'instant.\n");
        } else {
            sb.append("Candidats TVDB disponibles : ")
              .append(String.join(", ", tvdbCandidatesForCurrentGroup)).append('\n');
        }
        return sb.toString();
    }

    private String buildSingleRowContext(ScanRow row) {
        StringBuilder sb = new StringBuilder();
        sb.append("Fichier sélectionné : ").append(row.originalFilename()).append('\n');
        sb.append("Type détecté : ").append(RowDetailPanel.mediaTypeText(row.mediaType(), currentLanguage)).append('\n');
        row.inputPattern().ifPresent(pattern -> sb.append("Pattern d'entrée détecté : ").append(pattern).append('\n'));
        row.inputParse().ifPresent(parse -> {
            sb.append("Pattern d'entrée structuré : ").append(parse.summary()).append('\n');
            sb.append("Positions du pattern d'entrée : ").append(parse.positionsSummary()).append('\n');
            sb.append("Source du pattern d'entrée : ").append(parse.source()).append('\n');
        });
        sb.append("Titre détecté depuis le fichier : ").append(inferredTitle(row)).append('\n');
        row.proposedFilename().ifPresent(p -> sb.append("Nom proposé actuel : ").append(p).append('\n'));
        row.tvdbMatch().ifPresent(m -> sb.append("Correspondance TVDB actuelle : ").append(m).append('\n'));
        row.order().ifPresent(o -> sb.append("Ordre TVDB/configuration : ").append(orderText(o)).append('\n'));
        row.inputParse().flatMap(ScanInputParse::normalizedOrder)
                .ifPresent(o -> sb.append("Ordre episode detecte : ").append(o).append('\n'));
        BatchTvdbMatch match = rowToGroupMatch.get(row);
        if (match != null) {
            sb.append("Groupe : ").append(match.seedName())
              .append(" (").append(match.itemCount()).append(" éléments, ")
              .append(match.statusText(currentLanguage)).append(")\n");
        }
        java.util.List<ScanRow> peers = groupRows.get(row);
        if (peers != null && peers.size() > 1) {
            sb.append("Autres fichiers du groupe :\n");
            int count = 0;
            for (ScanRow peer : peers) {
                if (peer == row) continue;
                if (count++ >= 12) {
                    sb.append("  … (+").append(peers.size() - 13).append(" autres)\n");
                    break;
                }
                sb.append("  - ").append(peer.originalFilename()).append('\n');
            }
        }
        if (tvdbCandidatesForCurrentGroup.isEmpty()) {
            sb.append("Candidats TVDB disponibles : aucun pour l'instant.\n");
        } else {
            sb.append("Candidats TVDB disponibles : ")
              .append(String.join(", ", tvdbCandidatesForCurrentGroup)).append('\n');
        }
        return sb.toString();
    }

    private void updateAiChatTargetFromSelection() {
        updateAiChatTargetFromSelection(false);
    }

    private void updateAiChatTargetFromSelection(boolean preserveMessages) {
        java.util.List<ScanRow> selectedRows = selectedRowsForAiContext();
        if (selectedRows.isEmpty()) {
            aiChatPanel.setTarget(null, "");
            return;
        }
        ScanRow anchor = selectedRow.get();
        if (anchor == null || !selectedRows.contains(anchor)) {
            anchor = selectedRows.get(selectedRows.size() - 1);
        }
        String label = selectedRows.size() == 1
                ? anchor.originalFilename()
                : selectedRows.size() + " fichiers sélectionnés";
        aiChatPanel.setTarget(anchor, buildContextSummary(selectedRows), label, preserveMessages);
    }

    private java.util.List<ScanRow> selectedRowsForAiContext() {
        java.util.List<ScanRow> selectedRows = new java.util.ArrayList<>();
        for (ScanRow row : rows) {
            if (row.isSelected()) {
                selectedRows.add(row);
            }
        }
        return selectedRows;
    }

    private static Optional<String> commonInputPattern(java.util.List<ScanRow> selectedRows) {
        String common = null;
        for (ScanRow row : selectedRows) {
            if (row.inputPattern().isEmpty()) {
                return Optional.empty();
            }
            String value = row.inputPattern().orElseThrow();
            if (common == null) {
                common = value;
            } else if (!common.equals(value)) {
                return Optional.empty();
            }
        }
        return Optional.ofNullable(common);
    }

    private static String seasonFromOrder(String order) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("[Ss](\\d{1,2})[Ee](\\d{1,3})")
                .matcher(order == null ? "" : order);
        return matcher.find() ? matcher.group(1) : EMPTY;
    }

    private static String episodeFromOrder(String order) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("[Ss](\\d{1,2})[Ee](\\d{1,3})")
                .matcher(order == null ? "" : order);
        return matcher.find() ? matcher.group(2) : EMPTY;
    }

    private static String inferredTitle(ScanRow row) {
        String name = row.originalFilename();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("[Ss]\\d{1,2}[\\s._-]?[Ee]\\d{1,3}|(?<![A-Za-z0-9])\\d{1,2}[xX]\\d{1,3}(?![A-Za-z0-9])")
                .matcher(base);
        if (!matcher.find()) {
            return EMPTY;
        }
        String title = base.substring(matcher.end())
                .replaceAll("[._]+", " ")
                .replaceAll("\\s*-\\s*", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return title.isBlank() ? EMPTY : title;
    }

    private void applyTvdbCandidate(ScanRow row, String candidate) {
        row.setTvdbMatch(java.util.Optional.of(candidate));
        table.refresh();
        if (selectedRow.get() == row) {
            detailPanel.show(row, rowToGroupMatch.get(row));
        }
    }

    private void resetTvdbMatch(ScanRow row) {
        row.setTvdbMatch(java.util.Optional.empty());
        row.setOrder(java.util.Optional.empty());
        row.setProposedFilename(java.util.Optional.empty());
        row.setDestination(java.util.Optional.empty());
        table.refresh();
        if (selectedRow.get() == row) {
            detailPanel.show(row, rowToGroupMatch.get(row));
        }
    }

    private void applyToolCall(AiChatToolCall call, ScanRow target) {
        java.util.List<ScanRow> selectedRows = selectedRowsForAiContext();
        java.util.List<ScanRow> targetRows = selectedRows.isEmpty()
                ? groupRows.getOrDefault(target, java.util.List.of(target))
                : selectedRows;
        ScanRowToolbox.apply(call, target, targetRows);
        table.refresh();
        if (selectedRow.get() == target) {
            detailPanel.show(target, rowToGroupMatch.get(target));
        }
        updateAiChatTargetFromSelection(true);
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
                detailPanel.show(row, rowToGroupMatch.get(row));
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
                detailPanel.show(row, rowToGroupMatch.get(row));
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
        private final ProgressIndicator loader;
        private final Label label;

        WorkflowStep(int stepNumber, String text) {
            number = new Label(String.valueOf(stepNumber));
            number.getStyleClass().add("workflow-step-number");
            loader = new ProgressIndicator();
            loader.getStyleClass().add("workflow-step-loader");
            loader.setMaxSize(22, 22);
            loader.setVisible(false);
            loader.setManaged(false);
            label = new Label(text);
            label.getStyleClass().add("workflow-step-label");
            root = new HBox(8, number, loader, label);
            root.getStyleClass().add("workflow-step");
            root.setAlignment(Pos.CENTER_LEFT);
        }

        HBox root() {
            return root;
        }

        void setState(int index, int activeIndex, boolean loading) {
            root.getStyleClass().setAll("workflow-step");
            number.setVisible(!loading);
            number.setManaged(!loading);
            loader.setVisible(loading);
            loader.setManaged(loading);
            if (index == activeIndex) {
                root.getStyleClass().add("active");
                if (loading) {
                    root.getStyleClass().add("loading");
                }
            } else if (index < activeIndex) {
                root.getStyleClass().add("complete");
            } else {
                root.getStyleClass().add("pending");
            }
        }
    }
}
