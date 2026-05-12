package com.episort.ui.scan;

import com.episort.ai.AiChatBackend;
import com.episort.ai.AiChatToolCall;
import com.episort.ai.AiFilePatternParse;
import com.episort.ai.AiGroupSuggestion;
import com.episort.ai.AiPatternAssistant;
import com.episort.ai.AiPatternRefinementResult;
import com.episort.ai.AiPatternSuggestion;
import com.episort.ai.AiPatternSuggestionRequest;
import com.episort.ai.AiPatternToken;
import com.episort.config.TvdbCredentials;
import com.episort.matching.EpisodeMovieMatchService;
import com.episort.matching.MediaMatchProposal;
import com.episort.matching.MediaMatchType;
import com.episort.matching.TvdbEpisodeOrderMapper;
import com.episort.matching.TvdbEpisodeMetadata;
import com.episort.matching.TvdbMovieMetadata;
import com.episort.matching.TvdbSeriesMetadata;
import com.episort.scanner.InventoryGroup;
import com.episort.scanner.InventoryGroupType;
import com.episort.scanner.InventoryItem;
import com.episort.scanner.InventoryItemType;
import com.episort.scanner.InventoryScanResult;
import com.episort.scanner.InventorySummary;
import com.episort.tvdb.TvdbCandidate;
import com.episort.tvdb.TvdbClient;
import com.episort.tvdb.TvdbEpisode;
import com.episort.tvdb.TvdbEpisodeOrder;
import com.episort.tvdb.TvdbException;
import com.episort.tvdb.TvdbIdentity;
import com.episort.tvdb.TvdbMovieDetails;
import com.episort.tvdb.TvdbSearchResult;
import com.episort.tvdb.TvdbSeriesDetails;
import com.episort.workflow.TvdbBatchMatchResult;
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
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import javafx.scene.control.TextField;
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

    public enum WorkflowPhase {
        CHOOSE_FOLDER, AI_SCAN, TVDB_MATCHES, PLAN_REVIEW, APPLY
    }

    private final VBox root;
    private final Label heading;
    private final HBox workflowSteps;
    private final Label workflowHelp;
    private final java.util.List<WorkflowStep> steps = new java.util.ArrayList<>();
    private WorkflowPhase workflowPhase = WorkflowPhase.CHOOSE_FOLDER;
    private boolean workflowInProgress = false;

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
    private final ToggleGroup statusFilterGroup = new ToggleGroup();
    private final Map<ScanRowStatusFilter, ToggleButton> statusFilterButtons = new HashMap<>();
    private final Button resolveManualMatchesButton = new Button();
    private final TableColumn<ScanRow, Boolean> selectionColumn = new TableColumn<>();
    private final TableColumn<ScanRow, String> originalColumn = new TableColumn<>();
    private final TableColumn<ScanRow, String> arrowColumn = new TableColumn<>();
    private final TableColumn<ScanRow, String> proposedColumn = new TableColumn<>();
    private final TableColumn<ScanRow, String> seriesColumn = new TableColumn<>();
    private final TableColumn<ScanRow, String> seasonColumn = new TableColumn<>();
    private final TableColumn<ScanRow, String> episodeColumn = new TableColumn<>();
    private final TableColumn<ScanRow, String> titleColumn = new TableColumn<>();
    private final TableColumn<ScanRow, String> yearColumn = new TableColumn<>();
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
    private final Map<String, TvdbCandidate> tvdbCandidatesByLabel = new HashMap<>();
    private final EpisodeMovieMatchService matchService = new EpisodeMovieMatchService();
    private final TvdbEpisodeOrderMapper orderMapper = new TvdbEpisodeOrderMapper();
    private final CheckBox selectAllCheckbox = new CheckBox();
    private final SimpleObjectProperty<ScanRow> selectedRow = new SimpleObjectProperty<>(null);
    private final SimpleBooleanProperty syncingSelection = new SimpleBooleanProperty(false);
    private int selectionAnchorIndex = -1;
    private static final Pattern SXXEXX_PATTERN = Pattern.compile("[Ss](\\d{1,3})[\\s._-]?[Ee](\\d{1,4})");
    private boolean loadedFolder;
    private boolean loading;
    private boolean aiAssistanceEnabled = true;
    private Optional<java.nio.file.Path> workspaceRoot = Optional.empty();
    private String searchQuery = "";
    private ScanRowFilter activeFilter = ScanRowFilter.ALL;
    private ScanRowStatusFilter activeStatusFilter = ScanRowStatusFilter.ALL;

    private Pane currentBody;
    private boolean stackedLayout = false;
    private AppLanguage currentLanguage = AppLanguage.FRENCH;
    private AiPatternAssistant aiPatternAssistant;
    private TvdbClient tvdbClient;
    private Supplier<Optional<TvdbCredentials>> tvdbCredentialsSupplier = Optional::empty;

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
        detailPanel.setOnApplySelectedMatch(this::applySelectedTvdbMatchToSelection);
        detailPanel.setOnResetMatch(this::resetTvdbMatch);
        detailPanel.setOnApplyInputPattern(this::applyInputPatternToSelection);
        detailPanel.setOnSearchMatch(this::openTvdbManualMatchDialog);
        detailRoot = detailPanel.root();
        detailRoot.setMinWidth(320);
        detailRoot.setPrefWidth(380);
        detailRoot.setMaxWidth(420);
        aiChatPanel.setApplyHandler(this::applyToolCall);
        aiChatPanel.setProposeHandler(this::onProposeRename);

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
        seriesColumn.setText(UiText.scanColumnSeries(language));
        seasonColumn.setText(UiText.scanColumnSeason(language));
        episodeColumn.setText(UiText.scanColumnEpisode(language));
        titleColumn.setText(UiText.scanColumnTitle(language));
        yearColumn.setText(UiText.scanColumnYear(language));
        extensionColumn.setText(UiText.scanColumnExtension(language));
        typeColumn.setText(UiText.scanColumnType(language));
        orderColumn.setText(UiText.scanColumnOrder(language));
        confidenceColumn.setText(UiText.scanColumnConfidence(language));
        statusColumn.setText(UiText.scanColumnStatus(language));
        setFilterButtonText(ScanRowFilter.ALL, UiText.scanFilterAll(language));
        setFilterButtonText(ScanRowFilter.MOVIES, UiText.scanFilterMovies(language));
        setFilterButtonText(ScanRowFilter.SERIES, UiText.scanFilterSeries(language));
        setFilterButtonText(ScanRowFilter.UNKNOWN, UiText.scanFilterUnknown(language));
        setStatusFilterButtonText(ScanRowStatusFilter.ALL, UiText.scanStatusFilterAll(language));
        setStatusFilterButtonText(ScanRowStatusFilter.TO_PROCESS, UiText.scanStatusFilterToProcess(language));
        setStatusFilterButtonText(ScanRowStatusFilter.OK, UiText.scanStatusFilterOk(language));
        setStatusFilterButtonText(ScanRowStatusFilter.TVDB, UiText.scanStatusFilterTvdb(language));
        setStatusFilterButtonText(ScanRowStatusFilter.CONFLICTS, UiText.scanStatusFilterConflicts(language));
        setStatusFilterButtonText(ScanRowStatusFilter.IGNORED, UiText.scanStatusFilterIgnored(language));
        setStatusFilterButtonText(ScanRowStatusFilter.ALERTS, UiText.scanStatusFilterAlerts(language));
        resolveManualMatchesButton.setText(UiText.tvdbResolveNow(language));

        table.setPlaceholder(buildEmptyState(
                "◫",
                UiText.scanEmptyTitle(language),
                UiText.scanEmptyHint(language)));

        detailPanel.applyLanguage(language);
        aiChatPanel.applyLanguage(language);
        table.refresh();
        updateManualMatchNotice();
    }

    public void setAiChatBackend(AiChatBackend backend) {
        aiChatPanel.setBackend(backend);
    }

    public void setAiAssistanceEnabled(boolean enabled) {
        this.aiAssistanceEnabled = enabled;
        aiChatPanel.root().setVisible(enabled);
        aiChatPanel.root().setManaged(enabled);
        rebuildWorkflowSteps(currentLanguage);
    }

    public void setAiPatternAssistant(AiPatternAssistant assistant) {
        this.aiPatternAssistant = assistant;
    }

    public void setTvdbLookup(TvdbClient tvdbClient, Supplier<Optional<TvdbCredentials>> credentialsSupplier) {
        this.tvdbClient = tvdbClient;
        this.tvdbCredentialsSupplier = credentialsSupplier == null ? Optional::empty : credentialsSupplier;
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
        String[] labels = aiAssistanceEnabled
                ? UiText.scanWorkflowSteps(language)
                : UiText.scanWorkflowStepsNoAi(language);
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
        int activeIndex = phaseToIndex(workflowPhase);
        boolean inProgress = workflowInProgress || (loading && workflowPhase == WorkflowPhase.CHOOSE_FOLDER);
        for (int index = 0; index < steps.size(); index++) {
            steps.get(index).setState(index, activeIndex, inProgress && index == activeIndex);
        }
    }

    private int phaseToIndex(WorkflowPhase phase) {
        if (aiAssistanceEnabled) {
            return switch (phase) {
                case CHOOSE_FOLDER -> 0;
                case AI_SCAN -> 1;
                case TVDB_MATCHES -> 2;
                case PLAN_REVIEW -> 3;
                case APPLY -> 4;
            };
        }
        return switch (phase) {
            case CHOOSE_FOLDER -> 0;
            case AI_SCAN, TVDB_MATCHES -> 1;
            case PLAN_REVIEW -> 2;
            case APPLY -> 3;
        };
    }

    public void setWorkflowPhase(WorkflowPhase phase, boolean inProgress) {
        if (phase == null) return;
        this.workflowPhase = phase;
        this.workflowInProgress = inProgress;
        updateWorkflowActiveStep();
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

    public void setWorkspaceRoot(Optional<java.nio.file.Path> root) {
        this.workspaceRoot = root == null ? Optional.empty() : root;
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
        updateMetricsFromRows();
        selectedRow.set(null);
        detailPanel.clear();
        aiChatPanel.clear();
        aiChatPanel.refreshAvailability();
        table.getSelectionModel().clearSelection();
        updateSelectAllCheckbox();
        updateManualMatchNotice();
    }

    public void append(Optional<InventoryScanResult> result) {
        if (result == null || result.isEmpty()) {
            return;
        }
        InventoryScanResult scan = result.orElseThrow();
        loading = false;
        loadedFolder = true;
        rows.addAll(ScanRowFactory.from(scan));
        rebuildGroupIndex(new InventoryScanResult(
                rows.stream().map(ScanScreen::inventoryItemFor).toList(),
                scan.groups(),
                scan.summary()));
        updateMetricsFromRows();
        updateFilterPredicate();
        updateSelectAllCheckbox();
        updateManualMatchNotice();
        table.refresh();
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
        selectionAnchorIndex = -1;
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
        updateManualMatchNotice();
    }

    public boolean hasRows() {
        return !rows.isEmpty();
    }

    public boolean hasReadyActiveRows() {
        return rows.stream().anyMatch(row -> !row.isIgnored()
                && row.proposedFilename().filter(value -> !value.isBlank()).isPresent());
    }

    /**
     * Applies the local-AI pattern-refinement output. The AI is the authority on
     * media-type classification: when it returns a confident verdict for a
     * group, every row in that group has its mediaType overridden. The pattern
     * label (if any) is attached to rows as an advisory note.
     */
    /**
     * Applies the post-scan batch TVDB resolution: for every row whose group
     * was successfully matched, paints the identity, the per-row metadata
     * (S/E numbers, proposed filename, confidence), and the canonical "TVDB"
     * status. Rows without a group match keep their previous status so the
     * user can still kick off a manual search from the detail panel.
     */
    public void applyTvdbBatchResult(TvdbBatchMatchResult result) {
        applyTvdbBatchResultInternal(result, 0);
    }

    private void applyTvdbBatchResultInternal(TvdbBatchMatchResult result, int attempt) {
        if (result == null || result.isEmpty()) {
            return;
        }
        // The post-scan pipeline may enqueue this call BEFORE the viewmodel
        // apply runLater has populated rows (JavaFX can dispatch the outer
        // runLater of the double-runLater scheduler before the executor
        // thread finishes wiring the thenAccept continuation). Defer until
        // rows are present, with a small bound to avoid spinning forever.
        if (rows.isEmpty() && attempt < 20) {
            javafx.application.Platform.runLater(
                    () -> applyTvdbBatchResultInternal(result, attempt + 1));
            return;
        }
        int totalRows = rows.size();
        int rowsWithGroup = 0;
        int rowsApplied = 0;
        java.util.Set<String> seenSeeds = new java.util.HashSet<>();
        for (ScanRow row : rows) {
            InventoryGroup group = rowToGroup.get(row);
            if (group == null) continue;
            rowsWithGroup++;
            seenSeeds.add(group.seedName());
            TvdbBatchMatchResult.GroupMatch match = result.matchesBySeed().get(group.seedName());
            if (match == null) continue;
            applyBatchGroupMatchToRow(row, match);
            rowsApplied++;
        }
        publishApplyTrace(totalRows, rowsWithGroup, result.matchesBySeed().size(), rowsApplied,
                "seeds(rows)=" + seenSeeds + " seeds(matches)=" + result.matchesBySeed().keySet());
        table.refresh();
        updateManualMatchNotice();
        if (selectedRow.get() != null) {
            detailPanel.show(selectedRow.get(), rowToGroupMatch.get(selectedRow.get()));
        }
    }

    private static void publishApplyTrace(int totalRows, int rowsWithGroup, int matches, int rowsApplied, String detail) {
        try {
            String body = "totalRows=" + totalRows
                    + "  rowsWithGroup=" + rowsWithGroup
                    + "  matches=" + matches
                    + "  rowsApplied=" + rowsApplied
                    + "\n" + detail;
            com.episort.tvdb.debug.TvdbRequestBus.get().publish(new com.episort.tvdb.debug.TvdbRequestTrace(
                    java.time.Instant.now(), "APPLY", "scan-table", 0, 0L, body, null, false));
        } catch (RuntimeException ignored) {
            // ignore
        }
    }

    private void applyBatchGroupMatchToRow(ScanRow row, TvdbBatchMatchResult.GroupMatch match) {
        if (row.tvdbSelectedByUser()) {
            return;
        }
        String label = batchCandidateLabel(match);
        row.setTvdbMatch(Optional.of(label));
        row.setTvdbCandidate(Optional.of(match.candidate().orElseGet(() -> new TvdbCandidate(
                match.identity(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                com.episort.tvdb.OptionalDoubleScore.empty(),
                0))));
        row.setTvdbSelectedByUser(false);
        row.setStatus(match.automatic() ? ScanRowStatus.TVDB : ScanRowStatus.REVIEW);
        MediaMatchProposal proposal = match.proposalsByPath().get(row.sourcePath());
        String extension = row.extension().isBlank() ? "" : "." + row.extension().toLowerCase(Locale.ROOT);
        if (!match.automatic()) {
            row.setConfidence(OptionalDouble.of(match.score()));
            row.setNoteText(Optional.of(UiText.tvdbMatchSuggested(currentLanguage)));
            row.setAlertText(match.alert().or(() -> Optional.of(UiText.tvdbSuggestionNeedsValidation(currentLanguage))));
            return;
        }
        if (match.movie().isPresent()) {
            TvdbMovieDetails movie = match.movie().orElseThrow();
            String year = movie.releaseYear().map(value -> " (" + value + ")").orElse("");
            row.setMediaType(ScanMediaType.MOVIE);
            row.setProposedFilename(Optional.of(movie.identity().displayName() + year + extension));
            row.setOrder(Optional.of("N/A"));
            if (proposal != null && proposal.confidence().isPresent()) {
                row.setConfidence(proposal.confidence());
            }
            row.setInputParse(Optional.of(tvdbMovieParse(
                    movie.identity().displayName(),
                    movie.releaseYear().map(Object::toString).orElse(""))));
            row.setNoteText(Optional.of(UiText.tvdbAutomaticMatch(currentLanguage) + ": "
                    + movie.identity().displayName() + "."));
            if (proposal != null && proposal.type() == MediaMatchType.UNMATCHED) {
                row.setAlertText(Optional.of(proposal.reason()));
            } else {
                row.setAlertText(Optional.empty());
            }
            return;
        }
        if (match.series().isPresent()
                && proposal != null
                && proposal.seasonNumber().isPresent()
                && proposal.episodeNumber().isPresent()) {
            int season = proposal.seasonNumber().orElseThrow();
            int episode = proposal.episodeNumber().orElseThrow();
            row.setMediaType(ScanMediaType.SERIES);
            row.setOrder(Optional.of(String.format("S%02dE%02d", season, episode)));
            String episodeTitle = proposal.title().orElse("Untitled episode");
            String proposed = String.format("%s - S%02dE%02d - %s%s",
                    match.identity().displayName(), season, episode, episodeTitle, extension);
            row.setProposedFilename(Optional.of(proposed));
            row.setConfidence(proposal.confidence());
            row.setInputParse(Optional.of(tvdbEpisodeParse(
                    match.identity().displayName(),
                    String.format("%02d", season),
                    String.format("%02d", episode),
                    episodeTitle)));
            row.setAlertText(Optional.empty());
            row.setNoteText(Optional.of(UiText.tvdbAutomaticMatch(currentLanguage) + "."));
            return;
        }
        if (proposal != null && proposal.type() == MediaMatchType.UNMATCHED) {
            row.setAlertText(Optional.of(proposal.reason()));
            row.setNoteText(Optional.of("TVDB identity " + match.identity().displayName()
                    + " selected, but no episode match yet."));
        } else {
            row.setNoteText(Optional.of("TVDB identity " + match.identity().displayName() + " applied."));
        }
    }

    private static ScanInputParse tvdbMovieParse(String title, String year) {
        java.util.List<ScanInputToken> tokens = new java.util.ArrayList<>();
        if (title != null && !title.isBlank()) {
            tokens.add(new ScanInputToken(ScanInputRole.TITLE, title, title, 0, 0));
        }
        if (year != null && !year.isBlank()) {
            tokens.add(new ScanInputToken(ScanInputRole.YEAR, year, year, 0, 0));
        }
        return new ScanInputParse("TVDB", tokens, Optional.empty(),
                java.util.OptionalDouble.empty(), ScanInputParseSource.TVDB);
    }

    private static ScanInputParse tvdbEpisodeParse(String series, String season, String episode, String title) {
        java.util.List<ScanInputToken> tokens = new java.util.ArrayList<>();
        if (series != null && !series.isBlank()) {
            tokens.add(new ScanInputToken(ScanInputRole.SERIES, series, series, 0, 0));
        }
        if (season != null && !season.isBlank()) {
            tokens.add(new ScanInputToken(ScanInputRole.SEASON, season, season, 0, 0));
        }
        if (episode != null && !episode.isBlank()) {
            tokens.add(new ScanInputToken(ScanInputRole.EPISODE, episode, episode, 0, 0));
        }
        if (title != null && !title.isBlank()) {
            tokens.add(new ScanInputToken(ScanInputRole.TITLE, title, title, 0, 0));
        }
        String order = (season != null && !season.isBlank() && episode != null && !episode.isBlank())
                ? "S" + season + "E" + episode
                : null;
        return new ScanInputParse("TVDB", tokens,
                order == null ? Optional.empty() : Optional.of(order),
                java.util.OptionalDouble.empty(), ScanInputParseSource.TVDB);
    }

    private static String batchCandidateLabel(TvdbBatchMatchResult.GroupMatch match) {
        StringBuilder label = new StringBuilder(match.identity().displayName());
        match.movie().flatMap(TvdbMovieDetails::releaseYear)
                .ifPresent(year -> label.append(" (").append(year).append(")"));
        label.append(" [TVDB ").append(match.identity().id()).append("]");
        return label.toString();
    }

    public void applyAiRefinement(AiPatternRefinementResult refinement) {
        applyAiRefinementInternal(refinement, 0);
    }

    private void applyAiRefinementInternal(AiPatternRefinementResult refinement, int attempt) {
        if (refinement == null || !refinement.refined() || refinement.suggestions().isEmpty()) {
            return;
        }
        if (rows.isEmpty() && attempt < 20) {
            javafx.application.Platform.runLater(
                    () -> applyAiRefinementInternal(refinement, attempt + 1));
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
                Optional<ScanInputParse> aiParse = aiParseFor(row, ps)
                        .map(parse -> defendFolderDerivedSeries(parse, parentFolderChain(row.sourcePath())))
                        .map(parse -> parse.withSource(ScanInputParseSource.AI));
                aiParse.ifPresent(parse -> {
                    row.setInputParse(Optional.of(parse));
                    row.setInputPattern(Optional.of(parse.summary().isBlank() ? parse.label() : parse.summary()));
                    if (parse.confidence().isPresent()) {
                        row.setConfidence(parse.confidence());
                    }
                    parse.normalizedOrder().ifPresent(order -> row.setOrder(Optional.of(order)));
                });
            }
            // Always recompute the proposed name — even when the AI returned no
            // usable per-file tokens, we may have updated mediaType above and
            // the heuristic fallback can still produce a sensible name.
            recomputeProposedName(row);
        }
        table.refresh();
    }

    private void setFilterButtonText(ScanRowFilter filter, String text) {
        ToggleButton button = filterButtons.get(filter);
        if (button != null) {
            button.setText(text);
        }
    }

    private void setStatusFilterButtonText(ScanRowStatusFilter filter, String text) {
        ToggleButton button = statusFilterButtons.get(filter);
        if (button != null) {
            button.setText(text);
        }
    }

    private static void recomputeProposedName(ScanRow row) {
        String pattern = row.pattern()
                .filter(p -> !p.isBlank())
                .orElse("{series} - S{season}E{episode} - {title}");
        ScanPatternFormatter.format(row, pattern)
                .ifPresent(name -> row.setProposedFilename(Optional.of(name)));
    }

    private static boolean canAcceptAiParse(ScanRow row) {
        return row.inputParse().map(ScanInputParse::source).filter(ScanInputParseSource.USER::equals).isEmpty();
    }

    /**
     * Belt-and-suspenders: even with the strengthened per-file prompt, a small
     * model still sometimes returns a release-tag-shaped SERIES (e.g. "sgi hkyu",
     * "tfa") when a clean parent-folder title is staring it in the face. If the
     * AI's SERIES looks like junk and the parent folder yields a clean title, we
     * override here so the proposed name doesn't carry the release-tag garbage
     * into the user's preview.
     */
    private static ScanInputParse defendFolderDerivedSeries(ScanInputParse parse, java.util.List<String> parentChain) {
        Optional<String> cleanFolder = cleanedParentTitle(parentChain);
        if (cleanFolder.isEmpty()) {
            return parse;
        }
        java.util.List<ScanInputToken> tokens = new java.util.ArrayList<>(parse.tokens());
        Optional<ScanInputToken> currentSeries = tokens.stream()
                .filter(t -> t.role() == ScanInputRole.SERIES)
                .findFirst();
        boolean shouldOverride;
        if (currentSeries.isEmpty()) {
            shouldOverride = true;
        } else {
            String existing = currentSeries.get().normalizedValue();
            // No length comparison here: the bogus value (e.g. "sgi hkyu",
            // 8 chars) can easily be longer than the real cleaned title
            // (e.g. "Haikyu", 6 chars). If the value matches the release-tag
            // shape, trust the folder title regardless of relative length.
            shouldOverride = looksLikeReleaseTag(existing);
        }
        if (!shouldOverride) {
            return parse;
        }
        tokens.removeIf(t -> t.role() == ScanInputRole.SERIES);
        String clean = cleanFolder.get();
        tokens.add(0, new ScanInputToken(ScanInputRole.SERIES, clean, clean, 0, 0));
        return new ScanInputParse(parse.label(), tokens, parse.normalizedOrder(), parse.confidence(), parse.source());
    }

    private static boolean looksLikeReleaseTag(String value) {
        if (value == null || value.isBlank()) return true;
        String trimmed = value.trim();
        if (trimmed.length() > 12) return false;
        // Short tokens with optional dashes/digits/spaces — release-group
        // initials and shorthand patterns. Accept either case: the local
        // model sometimes title-cases its normalizedValue ("Sgi Hkyu") even
        // though the filename shorthand is lowercase ("sgi-hkyu"), and we
        // want to override either way when a clean folder title is available.
        return trimmed.matches("(?i)[a-z][a-z0-9 _.\\-]{1,11}");
    }

    private static Optional<String> cleanedParentTitle(java.util.List<String> parentChain) {
        if (parentChain == null || parentChain.isEmpty()) return Optional.empty();
        // Walk innermost → outermost. Skip season-only folders ("S01", "Season 2",
        // "Saison 03") so a "Haikyu/S01/file.mkv" layout still surfaces "Haikyu"
        // as the series name rather than a useless "S01".
        for (int i = parentChain.size() - 1; i >= 0; i--) {
            String name = parentChain.get(i);
            if (name == null || name.isBlank()) continue;
            if (isSeasonOnlyFolder(name)) continue;
            String cut = name
                    .split("(?i)[._\\-](?:s\\d{2}|season\\s*\\d+|saison\\s*\\d+|complete|multi|truefrench|french|vostfr|dual|1080p|720p|2160p|bluray|web-?dl|web|hdtv|x264|x265|h264|h265|hevc|10bit|ddp\\d|aac|flac|atmos)\\b.*$", 2)[0];
            String cleaned = cut
                    .replaceAll("[._]+", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (cleaned.isBlank() || cleaned.length() < 2) continue;
            if (isSeasonOnlyFolder(cleaned)) continue;
            return Optional.of(cleaned);
        }
        return Optional.empty();
    }

    private static boolean isSeasonOnlyFolder(String name) {
        if (name == null) return false;
        String trimmed = name.trim();
        return trimmed.matches("(?i)s\\d{1,2}|season\\s*\\d{1,2}|saison\\s*\\d{1,2}");
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
            ScanInputRole role = ScanInputRole.valueOf(token.role().toUpperCase(Locale.ROOT));
            // Drop tokens whose normalizedValue carries no letter/digit (e.g.
            // "(", ")", "-"): these come from a 1.7B model misaligning offsets
            // around bracketed segments and would otherwise pollute the token
            // columns and the proposed name. Numeric roles still need digits;
            // textual roles still need letters.
            String normalized = token.normalizedValue() == null ? "" : token.normalizedValue();
            boolean hasContent = false;
            for (int i = 0; i < normalized.length(); i++) {
                if (Character.isLetterOrDigit(normalized.charAt(i))) {
                    hasContent = true;
                    break;
                }
            }
            if (!hasContent) {
                return Optional.empty();
            }
            return Optional.of(new ScanInputToken(
                    role,
                    token.rawValue(),
                    token.normalizedValue(),
                    token.start(),
                    token.end()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static Optional<ScanMediaType> mediaTypeFromParse(ScanInputParse parse) {
        String pattern = parse.label() == null ? "" : parse.label().trim().toLowerCase(Locale.ROOT);
        boolean hasOrder = parse.normalizedOrder().filter(s -> !s.isBlank()).isPresent();
        if (hasOrder) return Optional.of(ScanMediaType.SERIES);
        return switch (pattern) {
            case "sxxexx", "nxnn", "absolute" -> Optional.of(ScanMediaType.SERIES);
            case "unknown", "" -> Optional.of(ScanMediaType.MOVIE);
            default -> Optional.empty();
        };
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
            if (selectedRow.get() != null) {
                detailPanel.setTvdbTargetCount(tvdbTargetRows(selectedRow.get()).size());
            }
            updateAiChatTargetFromSelection();
        });
        table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            selectedRow.set(newValue);
            if (newValue == null) {
                detailPanel.clear();
            } else {
                detailPanel.show(newValue, rowToGroupMatch.get(newValue));
                detailPanel.setTvdbTargetCount(tvdbTargetRows(newValue).size());
                loadTvdbCandidates(newValue);
            }
            updateAiChatTargetFromSelection();
        });

        configureSelectionColumn();
        configureOriginalColumn();
        configureArrowColumn();
        configureProposedColumn();
        configureRoleColumn(seriesColumn, ScanInputRole.SERIES, 200);
        configureRoleColumn(seasonColumn, ScanInputRole.SEASON, 70);
        configureRoleColumn(episodeColumn, ScanInputRole.EPISODE, 78);
        configureRoleColumn(titleColumn, ScanInputRole.TITLE, 200);
        configureRoleColumn(yearColumn, ScanInputRole.YEAR, 70);
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
                seriesColumn,
                seasonColumn,
                episodeColumn,
                titleColumn,
                yearColumn,
                extensionColumn,
                typeColumn,
                orderColumn,
                confidenceColumn,
                statusColumn);

        table.setRowFactory(view -> {
            TableRow<ScanRow> row = new TableRow<>();
            row.setOnContextMenuRequested(event -> {
                if (row.isEmpty()) {
                    return;
                }
                ScanRow item = row.getItem();
                if (!table.getSelectionModel().getSelectedItems().contains(item)) {
                    selectOnlyForContextMenu(item);
                }
                selectionAnchorIndex = sorted.indexOf(item);
                buildContextMenu(item).show(row, event.getScreenX(), event.getScreenY());
                event.consume();
            });
            row.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (row.isEmpty() || event.getButton() != MouseButton.PRIMARY) {
                    return;
                }
                ScanRow item = row.getItem();
                if (item == null || item.isIgnored()) {
                    event.consume();
                    return;
                }
                handleSelectionCellClick(item, event);
                event.consume();
            });
            row.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (row.isEmpty() || event.getButton() != MouseButton.SECONDARY) {
                    return;
                }
                ScanRow item = row.getItem();
                if (!table.getSelectionModel().getSelectedItems().contains(item)) {
                    selectOnlyForContextMenu(item);
                }
                selectionAnchorIndex = sorted.indexOf(item);
            });
            row.itemProperty().addListener((observable, oldItem, newItem) -> updateRowStyle(row, newItem));
            row.emptyProperty().addListener((observable, wasEmpty, isEmpty) -> updateRowStyle(row, row.getItem()));
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
                    handleSelectionCellClick(boundRow, event);
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
                checkBox.setDisable(row.isIgnored());
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
            CommitOnFocusLossStringCell<ScanRow> cell = new CommitOnFocusLossStringCell<>();
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

    private void configureRoleColumn(TableColumn<ScanRow, String> column, ScanInputRole role, double prefWidth) {
        column.setMinWidth(60);
        column.setPrefWidth(prefWidth);
        column.setCellValueFactory(data -> new SimpleStringProperty(roleValue(data.getValue(), role)));
        column.setCellFactory(c -> new CommitOnFocusLossStringCell<ScanRow>() {
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
                String display = item.isBlank() ? EMPTY : item;
                setText(display);
                if (!getStyleClass().contains("cell-mono")) {
                    getStyleClass().add("cell-mono");
                }
                if (EMPTY.equals(display)) {
                    if (!getStyleClass().contains("cell-muted")) {
                        getStyleClass().add("cell-muted");
                    }
                    setTooltip(null);
                } else {
                    getStyleClass().remove("cell-muted");
                    ScanRow row = getTableRow() == null ? null : getTableRow().getItem();
                    Tooltip t = new Tooltip(row == null ? display : patternTooltip(row));
                    t.setWrapText(true);
                    t.setMaxWidth(520);
                    setTooltip(t);
                }
            }
        });
        column.setOnEditCommit(event -> applyTokenEditToSelection(event.getRowValue(), role, event.getNewValue()));
        column.setComparator(ScanRowTableSupport.NATURAL_TEXT);
    }

    private static String roleValue(ScanRow row, ScanInputRole role) {
        boolean isMovie = row.mediaType() == ScanMediaType.MOVIE;
        if (isMovie && (role == ScanInputRole.SERIES
                || role == ScanInputRole.SEASON
                || role == ScanInputRole.EPISODE)) {
            return "";
        }
        // YEAR only applies to movies (series ordering is owned by TVDB downstream).
        if (!isMovie && role == ScanInputRole.YEAR) {
            return "";
        }
        Optional<String> direct = row.inputParse().flatMap(parse -> parse.tokenValue(role));
        if (direct.isPresent()) {
            return direct.get();
        }
        // For movies, derive Title and Year from the filename when the AI/heuristic
        // didn't tag them — the formatter already does this for the proposed name,
        // so the column should display the same value rather than stay blank.
        if (isMovie && role == ScanInputRole.TITLE) {
            return derivedMovieTitle(row);
        }
        if (isMovie && role == ScanInputRole.YEAR) {
            return derivedMovieYear(row);
        }
        return "";
    }

    private static String derivedMovieTitle(ScanRow row) {
        String name = row.originalFilename();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?<!\\d)(19\\d{2}|20\\d{2})(?!\\d)")
                .matcher(stem);
        int titleEnd = stem.length();
        while (matcher.find()) {
            titleEnd = matcher.start();
        }
        String raw = stem.substring(0, titleEnd);
        String cleaned = raw
                .replaceAll("[._]+", " ")
                .replaceAll("[\\(\\[\\{].*?[\\)\\]\\}]", " ")
                .replaceAll("[\\(\\)\\[\\]\\{\\}]+", " ")
                .replaceAll("\\s*-\\s*", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned;
    }

    private static String derivedMovieYear(ScanRow row) {
        String name = row.originalFilename();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?<!\\d)(19\\d{2}|20\\d{2})(?!\\d)")
                .matcher(name);
        String last = "";
        while (matcher.find()) {
            last = matcher.group(1);
        }
        return last;
    }

    private void applyTokenEditToSelection(ScanRow anchor, ScanInputRole role, String newValue) {
        if (anchor == null) {
            return;
        }
        String value = newValue == null ? "" : newValue.trim();
        if (EMPTY.equals(value)) {
            value = "";
        }
        for (ScanRow row : rowsForBatchEdit(anchor)) {
            applyTokenEdit(row, role, value);
        }
        refreshAfterBatchEdit(anchor);
    }

    private static void applyTokenEdit(ScanRow row, ScanInputRole role, String value) {
        ScanInputParse base = row.inputParse().orElseGet(() ->
                ScanInputPatternParser.parse(row.originalFilename())
                        .orElse(new ScanInputParse("", java.util.List.of(), Optional.empty(),
                                OptionalDouble.empty(), ScanInputParseSource.USER)));
        java.util.List<ScanInputToken> tokens = new java.util.ArrayList<>(base.tokens());
        boolean matched = false;
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).role() == role) {
                ScanInputToken old = tokens.get(i);
                if (value.isBlank()) {
                    tokens.remove(i);
                } else {
                    tokens.set(i, new ScanInputToken(role, value, value, old.start(), old.end()));
                }
                matched = true;
                break;
            }
        }
        if (!matched && !value.isBlank()) {
            tokens.add(new ScanInputToken(role, value, value, 0, 0));
        }
        Optional<String> normalizedOrder = (role == ScanInputRole.SEASON || role == ScanInputRole.EPISODE)
                ? recomputeOrder(tokens)
                : base.normalizedOrder();
        ScanInputParse updated = new ScanInputParse(
                base.label(), tokens, normalizedOrder, base.confidence(), ScanInputParseSource.USER);
        row.setInputParse(Optional.of(updated));
        String label = updated.summary();
        row.setInputPattern(label.isBlank() ? Optional.empty() : Optional.of(label));
        if (role == ScanInputRole.SEASON || role == ScanInputRole.EPISODE) {
            row.setOrder(normalizedOrder);
        }
        String pattern = row.pattern()
                .filter(p -> !p.isBlank())
                .orElse("{series} - S{season}E{episode} - {title}");
        ScanPatternFormatter.format(row, pattern)
                .ifPresent(name -> row.setProposedFilename(Optional.of(name)));
    }

    private static Optional<String> recomputeOrder(java.util.List<ScanInputToken> tokens) {
        String season = tokens.stream().filter(t -> t.role() == ScanInputRole.SEASON)
                .findFirst().map(ScanInputToken::normalizedValue).orElse("");
        String episode = tokens.stream().filter(t -> t.role() == ScanInputRole.EPISODE)
                .findFirst().map(ScanInputToken::normalizedValue).orElse("");
        if (season.isBlank() || episode.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(String.format("S%02dE%02d",
                    Integer.parseInt(season.trim()), Integer.parseInt(episode.trim())));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
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
        typeColumn.setMinWidth(150);
        typeColumn.setPrefWidth(170);
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
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox statusFilters = new HBox(8);
        statusFilters.getStyleClass().add("scan-status-filter-group");
        addStatusFilterButton(statusFilters, ScanRowStatusFilter.ALL);
        addStatusFilterButton(statusFilters, ScanRowStatusFilter.TO_PROCESS);
        addStatusFilterButton(statusFilters, ScanRowStatusFilter.OK);
        addStatusFilterButton(statusFilters, ScanRowStatusFilter.TVDB);
        addStatusFilterButton(statusFilters, ScanRowStatusFilter.CONFLICTS);
        addStatusFilterButton(statusFilters, ScanRowStatusFilter.IGNORED);
        addStatusFilterButton(statusFilters, ScanRowStatusFilter.ALERTS);
        statusFilterGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                statusFilterGroup.selectToggle(statusFilterButtons.get(activeStatusFilter));
                return;
            }
            activeStatusFilter = (ScanRowStatusFilter) newValue.getUserData();
            updateFilterPredicate();
            updateFilterButtonStyles();
        });
        statusFilterGroup.selectToggle(statusFilterButtons.get(ScanRowStatusFilter.ALL));
        resolveManualMatchesButton.getStyleClass().add("ghost");
        resolveManualMatchesButton.setVisible(false);
        resolveManualMatchesButton.setManaged(false);
        resolveManualMatchesButton.setOnAction(event -> firstManualMatchRow().ifPresent(row -> {
            table.getSelectionModel().select(row);
            table.scrollTo(row);
            selectedRow.set(row);
            detailPanel.show(row, rowToGroupMatch.get(row));
            openTvdbManualMatchDialog(row);
        }));
        filterBar.getChildren().addAll(spacer, statusFilters, resolveManualMatchesButton);
    }

    private void addFilterButton(ScanRowFilter filter) {
        ToggleButton button = new ToggleButton();
        button.setUserData(filter);
        button.setToggleGroup(filterGroup);
        button.getStyleClass().add("filter-chip");
        filterButtons.put(filter, button);
        filterBar.getChildren().add(button);
    }

    private void addStatusFilterButton(HBox host, ScanRowStatusFilter filter) {
        ToggleButton button = new ToggleButton();
        button.setUserData(filter);
        button.setToggleGroup(statusFilterGroup);
        button.getStyleClass().add("filter-chip");
        statusFilterButtons.put(filter, button);
        host.getChildren().add(button);
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
        filtered.setPredicate(row -> matchesSearch(row)
                && ScanRowTableSupport.matchesFilter(row, activeFilter)
                && ScanRowTableSupport.matchesStatusFilter(row, activeStatusFilter));
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
        for (Map.Entry<ScanRowStatusFilter, ToggleButton> entry : statusFilterButtons.entrySet()) {
            ToggleButton button = entry.getValue();
            button.getStyleClass().remove("active");
            if (entry.getKey() == activeStatusFilter && !button.getStyleClass().contains("active")) {
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
            recomputeProposedName(row);
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
        if (selectedRow.get() != null) {
            detailPanel.setTvdbTargetCount(tvdbTargetRows(selectedRow.get()).size());
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

    private void handleSelectionCellClick(ScanRow row, MouseEvent event) {
        if (row.isIgnored()) {
            return;
        }
        int clickedIndex = sorted.indexOf(row);
        if (clickedIndex < 0) {
            return;
        }
        if (event.isShiftDown() && selectionAnchorIndex >= 0) {
            selectVisibleRange(selectionAnchorIndex, clickedIndex);
            return;
        }
        if (event.isControlDown() || event.isMetaDown()) {
            setRowSelected(row, !row.isSelected());
            selectionAnchorIndex = clickedIndex;
            return;
        }
        selectOnlyForAction(row);
        selectionAnchorIndex = clickedIndex;
    }

    private void selectVisibleRange(int anchorIndex, int clickedIndex) {
        int start = Math.max(0, Math.min(anchorIndex, clickedIndex));
        int end = Math.min(sorted.size() - 1, Math.max(anchorIndex, clickedIndex));
        if (start > end) {
            return;
        }
        syncingSelection.set(true);
        try {
            for (int index = start; index <= end; index++) {
                ScanRow row = sorted.get(index);
                if (row.isIgnored()) {
                    row.setSelected(false);
                    continue;
                }
                row.setSelected(true);
                if (!table.getSelectionModel().getSelectedItems().contains(row)) {
                    table.getSelectionModel().select(row);
                }
            }
            ScanRow clicked = sorted.get(clickedIndex);
            selectedRow.set(clicked);
            detailPanel.show(clicked, rowToGroupMatch.get(clicked));
            detailPanel.setTvdbTargetCount(tvdbTargetRows(clicked).size());
        } finally {
            syncingSelection.set(false);
            updateSelectAllCheckbox();
            updateAiChatTargetFromSelection();
            table.refresh();
        }
    }

    private void setRowSelected(ScanRow row, boolean selected) {
        if (row == null || row.isIgnored()) {
            if (row != null) {
                row.setSelected(false);
            }
            updateSelectAllCheckbox();
            return;
        }
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

    private void selectOnlyForContextMenu(ScanRow row) {
        if (row.isIgnored()) {
            syncingSelection.set(true);
            try {
                table.getSelectionModel().clearSelection();
                selectedRow.set(row);
                detailPanel.show(row, rowToGroupMatch.get(row));
                detailPanel.setTvdbTargetCount(0);
            } finally {
                syncingSelection.set(false);
                updateSelectAllCheckbox();
                updateAiChatTargetFromSelection();
                table.refresh();
            }
            return;
        }
        selectOnlyForAction(row);
    }

    private void selectOnlyForAction(ScanRow row) {
        syncingSelection.set(true);
        try {
            for (ScanRow visible : sorted) {
                visible.setSelected(false);
            }
            table.getSelectionModel().clearSelection();
            row.setSelected(true);
            table.getSelectionModel().select(row);
            selectedRow.set(row);
            detailPanel.show(row, rowToGroupMatch.get(row));
            detailPanel.setTvdbTargetCount(tvdbTargetRows(row).size());
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
            selectionAnchorIndex = -1;
            for (ScanRow row : filtered) {
                boolean include = selected && !row.isIgnored();
                row.setSelected(include);
                if (include) {
                    table.getSelectionModel().select(row);
                }
            }
            ScanRow current = table.getSelectionModel().getSelectedItem();
            selectionAnchorIndex = current == null ? -1 : sorted.indexOf(current);
            selectedRow.set(current);
            if (current == null) {
                detailPanel.clear();
            } else {
                detailPanel.show(current, rowToGroupMatch.get(current));
                detailPanel.setTvdbTargetCount(tvdbTargetRows(current).size());
            }
        } finally {
            syncingSelection.set(false);
            updateSelectAllCheckbox();
            updateAiChatTargetFromSelection();
            table.refresh();
        }
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
            java.util.List<String> rowChain = parentFolderChain(selected.sourcePath());
            if (!rowChain.isEmpty()) {
                sb.append(" | dossiersParents=").append(String.join(" / ", rowChain));
            }
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
            sb.append('\n');
        }
        return sb.toString();
    }

    private String buildSingleRowContext(ScanRow row) {
        StringBuilder sb = new StringBuilder();
        sb.append("Fichier sélectionné : ").append(row.originalFilename()).append('\n');
        java.util.List<String> chain = parentFolderChain(row.sourcePath());
        if (!chain.isEmpty()) {
            sb.append("Dossiers parents (du plus externe au plus interne, workspace exclu) : ")
              .append(String.join(" / ", chain)).append('\n');
        }
        sb.append("Type détecté : ").append(RowDetailPanel.mediaTypeText(row.mediaType(), currentLanguage)).append('\n');
        row.inputPattern().ifPresent(pattern -> sb.append("Pattern d'entrée détecté : ").append(pattern).append('\n'));
        row.inputParse().ifPresent(parse -> {
            sb.append("Pattern d'entrée structuré : ").append(parse.summary()).append('\n');
            sb.append("Positions du pattern d'entrée : ").append(parse.positionsSummary()).append('\n');
            sb.append("Source du pattern d'entrée : ").append(parse.source()).append('\n');
        });
        sb.append("Titre détecté depuis le fichier : ").append(inferredTitle(row)).append('\n');
        row.proposedFilename().ifPresent(p -> sb.append("Nom proposé actuel : ").append(p).append('\n'));
        row.order().ifPresent(o -> sb.append("Ordre configuré : ").append(orderText(o)).append('\n'));
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
            if (row.isSelected() && !row.isIgnored()) {
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

    private java.util.List<String> parentFolderChain(java.nio.file.Path filePath) {
        if (filePath == null) {
            return java.util.List.of();
        }
        java.nio.file.Path parent = filePath.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            return java.util.List.of();
        }
        if (workspaceRoot.isPresent()) {
            java.nio.file.Path workspace = workspaceRoot.get().toAbsolutePath().normalize();
            if (parent.startsWith(workspace) && !parent.equals(workspace)) {
                java.nio.file.Path relative = workspace.relativize(parent);
                java.util.List<String> chain = new java.util.ArrayList<>(relative.getNameCount());
                for (java.nio.file.Path segment : relative) {
                    String name = segment.toString();
                    if (!name.isEmpty()) {
                        chain.add(name);
                    }
                }
                return java.util.List.copyOf(chain);
            }
        }
        java.nio.file.Path leaf = parent.getFileName();
        return leaf == null ? java.util.List.of() : java.util.List.of(leaf.toString());
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
        TvdbCandidate selectedCandidate = tvdbCandidatesByLabel.get(candidate);
        Optional<TvdbCredentials> credentials = tvdbCredentialsSupplier.get();
        if (selectedCandidate != null && credentials.isPresent()) {
            applyManualTvdbCandidate(row, selectedCandidate);
            return;
        }
        row.setTvdbMatch(java.util.Optional.of(candidate));
        row.setStatus(ScanRowStatus.TVDB);
        row.setNoteText(Optional.of("TVDB identity selected. Loading metadata..."));
        row.setTvdbCandidate(Optional.ofNullable(selectedCandidate));
        row.setTvdbSelectedByUser(selectedCandidate != null);
        table.refresh();
        updateManualMatchNotice();
        if (selectedRow.get() == row) {
            detailPanel.show(row, rowToGroupMatch.get(row));
        }
    }

    private void resetTvdbMatch(ScanRow row) {
        row.setTvdbMatch(java.util.Optional.empty());
        row.setTvdbCandidate(java.util.Optional.empty());
        row.setTvdbSelectedByUser(false);
        row.setOrder(java.util.Optional.empty());
        row.setProposedFilename(java.util.Optional.empty());
        row.setDestination(java.util.Optional.empty());
        row.setNoteText(Optional.empty());
        table.refresh();
        updateManualMatchNotice();
        if (selectedRow.get() == row) {
            detailPanel.show(row, rowToGroupMatch.get(row));
        }
    }

    private void openTvdbManualMatchDialog(ScanRow row) {
        if (row == null || tvdbClient == null) {
            return;
        }
        Optional<TvdbCredentials> credentials = tvdbCredentialsSupplier.get();
        if (credentials.isEmpty()) {
            row.setNoteText(Optional.of("TVDB credentials are not configured."));
            table.refresh();
            detailPanel.show(row, rowToGroupMatch.get(row));
            return;
        }
        java.util.List<TvdbCandidate> existing = new java.util.ArrayList<>(tvdbCandidatesByLabel.values());
        javafx.stage.Window owner = detailRoot.getScene() == null ? null : detailRoot.getScene().getWindow();
        TvdbManualMatchDialog dialog = new TvdbManualMatchDialog(
                owner,
                currentLanguage,
                tvdbClient,
                credentials.orElseThrow(),
                tvdbQuery(row),
                existing);
        dialog.showAndWait().ifPresent(candidate -> applyManualTvdbCandidate(row, candidate));
    }

    private void applyManualTvdbCandidate(ScanRow row, TvdbCandidate candidate) {
        applyManualTvdbCandidate(row, candidate, TvdbEpisodeOrder.AIRED);
    }

    private void applyManualTvdbCandidate(ScanRow row, TvdbCandidate candidate, TvdbEpisodeOrder order) {
        java.util.List<ScanRow> targets = tvdbTargetRows(row);
        if (targets.isEmpty()) {
            row.setNoteText(Optional.of(UiText.tvdbNoFileSelected(currentLanguage)));
            table.refresh();
            detailPanel.show(row, rowToGroupMatch.get(row));
            return;
        }
        String label = candidateLabel(candidate);
        tvdbCandidatesByLabel.put(label, candidate);
        for (ScanRow target : targets) {
            setManualTvdbCandidate(target, candidate, label);
            target.setNoteText(Optional.of("TVDB identity selected by user. Loading metadata..."));
        }
        loadSelectedTvdbMetadata(targets, candidate.identity(), tvdbCredentialsSupplier.get().orElseThrow(), order);
        table.refresh();
        updateManualMatchNotice();
        if (selectedRow.get() != null) {
            detailPanel.show(selectedRow.get(), rowToGroupMatch.get(selectedRow.get()));
            detailPanel.setTvdbTargetCount(tvdbTargetRows(selectedRow.get()).size());
        }
    }

    private void applySelectedTvdbMatchToSelection(ScanRow anchor, TvdbEpisodeOrder order) {
        if (anchor == null || anchor.tvdbCandidate().isEmpty()) {
            if (anchor != null) {
                anchor.setNoteText(Optional.of(UiText.tvdbNoResultSelected(currentLanguage)));
                table.refresh();
                detailPanel.show(anchor, rowToGroupMatch.get(anchor));
            }
            return;
        }
        Optional<TvdbCredentials> credentials = tvdbCredentialsSupplier.get();
        if (credentials.isEmpty()) {
            anchor.setNoteText(Optional.of("TVDB credentials are not configured."));
            table.refresh();
            detailPanel.show(anchor, rowToGroupMatch.get(anchor));
            return;
        }
        applyManualTvdbCandidate(anchor, anchor.tvdbCandidate().orElseThrow(), order);
    }

    private void setManualTvdbCandidate(ScanRow row, TvdbCandidate candidate, String label) {
        row.setTvdbMatch(Optional.of(label));
        row.setTvdbCandidate(Optional.of(candidate));
        row.setTvdbSelectedByUser(true);
        row.setStatus(ScanRowStatus.TVDB);
        row.setConfidence(OptionalDouble.of(1.0));
        row.setAlertText(Optional.empty());
    }

    private java.util.List<ScanRow> tvdbTargetRows(ScanRow anchor) {
        java.util.List<ScanRow> selected = selectedRowsForAiContext();
        if (!selected.isEmpty()) {
            return selected;
        }
        if (anchor != null && !anchor.isIgnored()) {
            return java.util.List.of(anchor);
        }
        return java.util.List.of();
    }

    private void loadTvdbCandidates(ScanRow row) {
        if (tvdbClient == null || row == null
                || row.mediaType() == ScanMediaType.UNKNOWN
                || row.mediaType() == ScanMediaType.IGNORED) {
            detailPanel.setTvdbCandidateOptions(java.util.List.of());
            return;
        }
        Optional<TvdbCredentials> credentials = tvdbCredentialsSupplier.get();
        if (credentials.isEmpty()) {
            detailPanel.setTvdbCandidateOptions(java.util.List.of());
            row.setNoteText(Optional.of("TVDB credentials are not configured."));
            table.refresh();
            return;
        }
        String query = tvdbQuery(row);
        if (query.isBlank()) {
            detailPanel.setTvdbCandidateOptions(java.util.List.of());
            return;
        }
        java.util.concurrent.CompletableFuture
                .supplyAsync(() -> tvdbClient.search(query, credentials.orElseThrow()))
                .thenAccept(result -> javafx.application.Platform.runLater(() -> applyTvdbCandidates(row, result)))
                .exceptionally(throwable -> {
                    javafx.application.Platform.runLater(() -> applyTvdbLookupFailure(row, throwable));
                    return null;
                });
    }

    private void applyTvdbCandidates(ScanRow row, TvdbSearchResult result) {
        if (selectedRow.get() != row) {
            return;
        }
        java.util.List<TvdbCandidate> candidates = row.mediaType() == ScanMediaType.MOVIE
                ? result.movieCandidates()
                : result.seriesCandidates();
        tvdbCandidatesByLabel.clear();
        java.util.List<String> labels = new java.util.ArrayList<>();
        for (TvdbCandidate candidate : candidates) {
            String label = candidateLabel(candidate);
            labels.add(label);
            tvdbCandidatesByLabel.put(label, candidate);
        }
        detailPanel.setTvdbCandidateOptions(labels);
        row.setNoteText(labels.isEmpty()
                ? Optional.of("No TVDB candidates found for " + tvdbQuery(row) + ".")
                : Optional.of("TVDB candidates loaded; select one to keep it for this session."));
        table.refresh();
        detailPanel.show(row, rowToGroupMatch.get(row));
    }

    private void applyTvdbLookupFailure(ScanRow row, Throwable throwable) {
        applyTvdbLookupFailure(java.util.List.of(row), throwable);
    }

    private void applyTvdbLookupFailure(java.util.List<ScanRow> targetRows, Throwable throwable) {
        ScanRow active = selectedRow.get();
        if (active != null && !targetRows.contains(active)) {
            return;
        }
        Throwable cause = throwable instanceof java.util.concurrent.CompletionException && throwable.getCause() != null
                ? throwable.getCause()
                : throwable;
        String message = cause instanceof TvdbException tvdbException
                ? tvdbException.error().safeMessage()
                : "TVDB lookup failed.";
        for (ScanRow row : targetRows) {
            row.setStatus(ScanRowStatus.ERROR);
            row.setAlertText(Optional.of(message));
        }
        table.refresh();
        if (active != null) {
            detailPanel.show(active, rowToGroupMatch.get(active));
            detailPanel.setTvdbTargetCount(tvdbTargetRows(active).size());
        }
    }

    private String tvdbQuery(ScanRow row) {
        InventoryGroup group = rowToGroup.get(row);
        if (group != null && group.seedName() != null && !group.seedName().isBlank()) {
            return group.seedName();
        }
        if (row.mediaType() == ScanMediaType.MOVIE) {
            return derivedMovieTitle(row);
        }
        return row.inputParse()
                .flatMap(parse -> parse.tokenValue(ScanInputRole.SERIES))
                .orElse(row.originalFilename());
    }

    private static String candidateLabel(TvdbCandidate candidate) {
        StringBuilder label = new StringBuilder();
        label.append(candidate.identity().displayName());
        candidate.year().ifPresent(year -> label.append(" (").append(year).append(")"));
        label.append(" [TVDB ").append(candidate.identity().id()).append("]");
        candidate.network().ifPresent(network -> label.append(" · ").append(network));
        candidate.country().ifPresent(country -> label.append(" · ").append(country));
        return label.toString();
    }

    private void loadSelectedTvdbMetadata(ScanRow row, TvdbIdentity identity, TvdbCredentials credentials) {
        loadSelectedTvdbMetadata(java.util.List.of(row), identity, credentials, TvdbEpisodeOrder.AIRED);
    }

    private void loadSelectedTvdbMetadata(java.util.List<ScanRow> targetRows, TvdbIdentity identity, TvdbCredentials credentials) {
        loadSelectedTvdbMetadata(targetRows, identity, credentials, TvdbEpisodeOrder.AIRED);
    }

    private void loadSelectedTvdbMetadata(
            java.util.List<ScanRow> targetRows,
            TvdbIdentity identity,
            TvdbCredentials credentials,
            TvdbEpisodeOrder order) {
        java.util.List<ScanRow> targets = java.util.List.copyOf(targetRows);
        java.util.concurrent.CompletableFuture
                .supplyAsync(() -> identity.mediaType() == com.episort.tvdb.TvdbMediaType.MOVIE
                        ? tvdbClient.movieDetails(identity, credentials)
                        : tvdbClient.seriesDetails(identity, credentials))
                .thenAccept(details -> javafx.application.Platform.runLater(() -> applySelectedTvdbMetadata(targets, details, order)))
                .exceptionally(throwable -> {
                    javafx.application.Platform.runLater(() -> applyTvdbLookupFailure(targets, throwable));
                    return null;
                });
    }

    private void applySelectedTvdbMetadata(ScanRow row, Object details) {
        applySelectedTvdbMetadata(java.util.List.of(row), details);
    }

    private void applySelectedTvdbMetadata(java.util.List<ScanRow> targetRows, Object details) {
        applySelectedTvdbMetadata(targetRows, details, TvdbEpisodeOrder.AIRED);
    }

    private void applySelectedTvdbMetadata(java.util.List<ScanRow> targetRows, Object details, TvdbEpisodeOrder order) {
        int updated = 0;
        for (ScanRow row : targetRows) {
            applySelectedTvdbMetadataToRow(row, details, order);
            updated++;
        }
        String message = UiText.tvdbFilesUpdated(currentLanguage, updated);
        for (ScanRow row : targetRows) {
            row.setNoteText(Optional.of(message));
        }
        table.refresh();
        updateManualMatchNotice();
        if (selectedRow.get() != null) {
            detailPanel.show(selectedRow.get(), rowToGroupMatch.get(selectedRow.get()));
            detailPanel.setTvdbTargetCount(tvdbTargetRows(selectedRow.get()).size());
        }
    }

    private void applySelectedTvdbMetadataToRow(ScanRow row, Object details) {
        applySelectedTvdbMetadataToRow(row, details, TvdbEpisodeOrder.AIRED);
    }

    private void applySelectedTvdbMetadataToRow(ScanRow row, Object details, TvdbEpisodeOrder order) {
        if (details instanceof TvdbMovieDetails movie) {
            applyMovieMetadata(row, movie);
        } else if (details instanceof TvdbSeriesDetails series) {
            applySeriesMetadata(row, series, order);
        }
    }

    private void applyMovieMetadata(ScanRow row, TvdbMovieDetails movie) {
        String extension = row.extension().isBlank() ? "" : "." + row.extension().toLowerCase(Locale.ROOT);
        String year = movie.releaseYear().map(value -> " (" + value + ")").orElse("");
        row.setMediaType(ScanMediaType.MOVIE);
        row.setProposedFilename(Optional.of(movie.identity().displayName() + year + extension));
        row.setOrder(Optional.of("N/A"));
        row.setNoteText(Optional.of("TVDB movie metadata applied from " + movie.identity().displayName() + "."));
        MediaMatchProposal proposal = matchService.proposeMovieMatches(
                java.util.List.of(inventoryItemFor(row)),
                new TvdbMovieMetadata(movie.identity().id(), movie.identity().displayName(), movie.releaseYear()))
                .getFirst();
        if (proposal.type() == MediaMatchType.UNMATCHED) {
            row.setAlertText(Optional.of(proposal.reason()));
        }
    }

    private void applySeriesMetadata(ScanRow row, TvdbSeriesDetails series) {
        applySeriesMetadata(row, series, TvdbEpisodeOrder.AIRED);
    }

    private void applySeriesMetadata(ScanRow row, TvdbSeriesDetails series, TvdbEpisodeOrder order) {
        if (!series.supportedOrders().contains(order)) {
            row.setAlertText(Optional.of("Requested TVDB order is not available for this series."));
            row.setNoteText(Optional.of("TVDB metadata loaded, but the selected order is unavailable."));
            return;
        }
        java.util.List<MediaMatchProposal> proposals = matchService.proposeSeriesMatches(
                java.util.List.of(inventoryItemFor(row)),
                new TvdbSeriesMetadata(
                        series.identity().id(),
                        series.identity().displayName(),
                        series.episodesFor(order).stream().map(ScanScreen::toMatchingEpisode).toList()));
        MediaMatchProposal proposal = proposals.isEmpty()
                ? MediaMatchProposal.unmatched(row.sourcePath(), "No TVDB episode proposal.")
                : proposals.getFirst();
        if (proposal.type() == MediaMatchType.UNMATCHED) {
            TvdbEpisodeOrderMapper.EpisodeOrderMappingResult mapped = remapAcrossTvdbOrders(row, series, order);
            proposal = mapped.proposal().orElse(proposal);
            mapped.error().ifPresent(error -> row.setAlertText(Optional.of(error)));
        }
        if (proposal.type() == MediaMatchType.UNMATCHED) {
            row.setAlertText(Optional.of(proposal.reason()));
            row.setNoteText(Optional.of("TVDB series metadata loaded, but this file did not match an episode."));
            return;
        }
        row.setMediaType(ScanMediaType.SERIES);
        row.setOrder(Optional.of(String.format("S%02dE%02d",
                proposal.seasonNumber().orElse(0),
                proposal.episodeNumber().orElse(0))));
        String extension = row.extension().isBlank() ? "" : "." + row.extension().toLowerCase(Locale.ROOT);
        String proposed = String.format("%s - S%02dE%02d - %s%s",
                series.identity().displayName(),
                proposal.seasonNumber().orElse(0),
                proposal.episodeNumber().orElse(0),
                proposal.title().orElse("Untitled episode"),
                extension);
        row.setProposedFilename(Optional.of(proposed));
        row.setConfidence(proposal.confidence());
        row.setAlertText(Optional.empty());
        row.setNoteText(Optional.of("TVDB episode metadata applied from " + series.identity().displayName() + "."));
    }

    private static TvdbEpisodeMetadata toMatchingEpisode(TvdbEpisode episode) {
        return new TvdbEpisodeMetadata(
                episode.id(),
                episode.seasonNumber(),
                episode.episodeNumber(),
                episode.absoluteNumber(),
                episode.title(),
                episode.special());
    }

    private static InventoryItem inventoryItemFor(ScanRow row) {
        return new InventoryItem(
                row.sourcePath(),
                row.originalFilename(),
                row.extension(),
                row.sourcePath().getParent(),
                InventoryItemType.SUPPORTED_VIDEO,
                true);
    }

    private void onProposeRename() {
        if (aiPatternAssistant == null) {
            aiChatPanel.appendAssistantText("Assistant IA local indisponible.");
            return;
        }
        java.util.List<ScanRow> selected = selectedRowsForAiContext();
        if (selected.isEmpty()) {
            ScanRow anchor = selectedRow.get();
            if (anchor == null) {
                aiChatPanel.appendAssistantText("Sélectionne au moins un fichier pour proposer un renommage.");
                return;
            }
            selected = java.util.List.of(anchor);
        }
        final java.util.List<ScanRow> rowsForRun = java.util.List.copyOf(selected);
        java.util.List<String> filenames = rowsForRun.stream().map(ScanRow::originalFilename).toList();
        java.util.List<String> parentChain = parentFolderChain(rowsForRun.get(0).sourcePath());
        java.util.List<java.util.List<String>> perFileChains = rowsForRun.stream()
                .map(r -> parentFolderChain(r.sourcePath()))
                .map(java.util.List::copyOf)
                .toList();
        aiChatPanel.setBusy(true,
                "Analyse IA en cours sur " + rowsForRun.size()
                        + (rowsForRun.size() > 1 ? " fichiers…" : " fichier…"));
        Thread worker = new Thread(() -> {
            AiPatternSuggestion suggestion;
            try {
                suggestion = aiPatternAssistant.suggestPattern(
                        new AiPatternSuggestionRequest(filenames, "", parentChain, perFileChains));
            } catch (RuntimeException ex) {
                javafx.application.Platform.runLater(() -> {
                    aiChatPanel.setBusy(false, "");
                    aiChatPanel.appendAssistantText("Analyse IA en échec : "
                            + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
                });
                return;
            }
            javafx.application.Platform.runLater(() -> handleProposeResult(rowsForRun, suggestion));
        }, "episort-chat-propose");
        worker.setDaemon(true);
        worker.start();
    }

    private void handleProposeResult(java.util.List<ScanRow> rowsForRun, AiPatternSuggestion suggestion) {
        aiChatPanel.setBusy(false, "");
        String explanation = suggestion.explanation() == null || suggestion.explanation().isBlank()
                ? "Analyse IA terminée."
                : suggestion.explanation();
        StringBuilder summary = new StringBuilder(explanation);
        if (!suggestion.suggestedPatterns().isEmpty()) {
            summary.append("\n\nPatterns détectés : ")
                    .append(String.join(", ", suggestion.suggestedPatterns()));
        }
        suggestion.classifiedType().ifPresent(type -> {
            String label = switch (type) {
                case LIKELY_SERIES -> "Série";
                case LIKELY_MOVIE -> "Film";
                default -> "Inconnu";
            };
            summary.append("\nType : ").append(label);
            suggestion.classificationConfidence().ifPresent(c ->
                    summary.append(" (confiance ").append(Math.round(c * 100)).append("%)"));
        });
        aiChatPanel.appendAssistantText(summary.toString());

        int cards = 0;
        for (ScanRow row : rowsForRun) {
            java.util.List<AiChatToolCall> calls = buildStructuredCalls(row, suggestion);
            if (calls.isEmpty()) continue;
            aiChatPanel.appendAggregatedProposalCard(calls, row);
            cards++;
        }
        if (cards == 0) {
            aiChatPanel.appendAssistantText(
                    "Aucune nouvelle proposition de renommage (les noms actuels correspondent déjà ou l'IA n'a rien détecté).");
        }
    }

    /**
     * Derives the structured tool calls (setMediaType, setSeries, setYear,
     * setTitle) that, when confirmed, will reproduce the AI's parse on the row
     * and trigger the canonical recompute via {@link ScanRowToolbox}. Returns
     * only calls that would actually change the row's current state — when
     * the AI's parse already matches what's on the row, nothing is emitted.
     */
    private java.util.List<AiChatToolCall> buildStructuredCalls(ScanRow row, AiPatternSuggestion suggestion) {
        Optional<ScanInputParse> aiParse = aiParseFor(row, suggestion)
                .map(p -> defendFolderDerivedSeries(p, parentFolderChain(row.sourcePath())));
        if (aiParse.isEmpty()) return java.util.List.of();
        ScanInputParse parse = aiParse.get();

        Optional<ScanMediaType> globalType = suggestion.classifiedType()
                .filter(t -> suggestion.classificationConfidence().orElse(0.0) >= 0.6)
                .map(ScanScreen::toScanMediaType);
        ScanMediaType inferred;
        if (globalType.filter(t -> t == ScanMediaType.MOVIE).isPresent()) {
            inferred = ScanMediaType.MOVIE;
        } else {
            inferred = mediaTypeFromParse(parse)
                    .orElseGet(() -> globalType.orElse(row.mediaType()));
        }

        java.util.List<AiChatToolCall> calls = new java.util.ArrayList<>();
        if (inferred != row.mediaType()
                && (inferred == ScanMediaType.MOVIE || inferred == ScanMediaType.SERIES)) {
            com.google.gson.JsonObject args = new com.google.gson.JsonObject();
            args.addProperty("type", inferred == ScanMediaType.MOVIE ? "movie" : "series");
            calls.add(new AiChatToolCall("setMediaType", args, args.toString()));
        }

        Optional<String> currentSeries = row.inputParse().flatMap(p -> p.tokenValue(ScanInputRole.SERIES));
        parse.tokenValue(ScanInputRole.SERIES)
                .filter(v -> !v.isBlank())
                .filter(v -> currentSeries.filter(v::equalsIgnoreCase).isEmpty())
                .ifPresent(v -> {
                    com.google.gson.JsonObject args = new com.google.gson.JsonObject();
                    args.addProperty("series", v);
                    calls.add(new AiChatToolCall("setSeries", args, args.toString()));
                });

        Optional<String> currentTitle = row.inputParse().flatMap(p -> p.tokenValue(ScanInputRole.TITLE));
        parse.tokenValue(ScanInputRole.TITLE)
                .filter(v -> !v.isBlank())
                .filter(v -> currentTitle.filter(v::equalsIgnoreCase).isEmpty())
                .ifPresent(v -> {
                    com.google.gson.JsonObject args = new com.google.gson.JsonObject();
                    args.addProperty("title", v);
                    calls.add(new AiChatToolCall("setTitle", args, args.toString()));
                });

        Optional<String> currentYear = row.inputParse().flatMap(p -> p.tokenValue(ScanInputRole.YEAR));
        parse.tokenValue(ScanInputRole.YEAR)
                .filter(v -> v.matches("(19|20)\\d{2}"))
                .filter(v -> currentYear.filter(v::equals).isEmpty())
                .ifPresent(v -> {
                    com.google.gson.JsonObject args = new com.google.gson.JsonObject();
                    args.addProperty("year", v);
                    calls.add(new AiChatToolCall("setYear", args, args.toString()));
                });

        return calls;
    }

    private Optional<String> computeProposedName(ScanRow row, AiPatternSuggestion suggestion) {
        Optional<ScanInputParse> aiParse = aiParseFor(row, suggestion)
                .map(p -> defendFolderDerivedSeries(p, parentFolderChain(row.sourcePath())))
                .map(p -> p.withSource(ScanInputParseSource.AI));
        Optional<ScanInputParse> originalParse = row.inputParse();
        ScanMediaType originalType = row.mediaType();
        try {
            // Per-row media-type takes precedence in the SERIES direction (a
            // single SxxExx-tagged file inside an otherwise-movie batch is
            // legitimately an episode). But a confident global MOVIE classification
            // overrides per-file SERIES guesses, because a 1.7B model routinely
            // mis-parses files like "Movie (1985).mkv" — the year digits get
            // grabbed as an episode number and the file gets a phantom S01E85.
            // Falling back to the global classification only when the per-file
            // pass produced nothing avoids forcing a heterogeneous batch into
            // a single type.
            Optional<ScanMediaType> globalType = suggestion.classifiedType()
                    .filter(t -> suggestion.classificationConfidence().orElse(0.0) >= 0.6)
                    .map(ScanScreen::toScanMediaType);
            ScanMediaType inferred;
            if (globalType.filter(t -> t == ScanMediaType.MOVIE).isPresent()) {
                inferred = ScanMediaType.MOVIE;
            } else {
                inferred = aiParse
                        .map(ScanScreen::mediaTypeFromParse)
                        .flatMap(java.util.function.Function.identity())
                        .orElseGet(() -> globalType.orElse(originalType));
            }
            row.setMediaType(inferred);
            aiParse.ifPresent(parse -> row.setInputParse(Optional.of(parse)));
            String pattern = "{series} - S{season}E{episode} - {title}";
            return ScanPatternFormatter.format(row, pattern);
        } finally {
            row.setInputParse(originalParse);
            row.setMediaType(originalType);
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
        boolean hasVisibleRows = filtered.stream().anyMatch(row -> !row.isIgnored());
        boolean allVisibleSelected = hasVisibleRows
                && filtered.stream().filter(row -> !row.isIgnored()).allMatch(ScanRow::isSelected);
        selectAllCheckbox.setDisable(!hasVisibleRows);
        selectAllCheckbox.setSelected(allVisibleSelected);
    }

    private static void updateRowStyle(TableRow<ScanRow> tableRow, ScanRow item) {
        tableRow.getStyleClass().remove("ignored-row");
        if (!tableRow.isEmpty() && item != null && item.isIgnored()) {
            tableRow.getStyleClass().add("ignored-row");
        }
    }

    private TvdbEpisodeOrderMapper.EpisodeOrderMappingResult remapAcrossTvdbOrders(
            ScanRow row, TvdbSeriesDetails series, TvdbEpisodeOrder targetOrder) {
        Optional<int[]> parsed = parsedSeasonEpisode(row);
        if (parsed.isEmpty()) {
            return new TvdbEpisodeOrderMapper.EpisodeOrderMappingResult(
                    Optional.empty(), Optional.of("Episode not found in selected order"));
        }
        int season = parsed.orElseThrow()[0];
        int episode = parsed.orElseThrow()[1];
        return orderMapper.map(series, sourceOrder(row, targetOrder), targetOrder, row.sourcePath(),
                season, episode, Optional.empty());
    }

    private static TvdbEpisodeOrder sourceOrder(ScanRow row, TvdbEpisodeOrder targetOrder) {
        return row.inputParse()
                .map(ScanInputParse::source)
                .filter(ScanInputParseSource.TVDB::equals)
                .isPresent()
                ? targetOrder
                : null;
    }

    private Optional<int[]> parsedSeasonEpisode(ScanRow row) {
        Matcher matcher = SXXEXX_PATTERN.matcher(row.originalFilename());
        if (matcher.find()) {
            return Optional.of(new int[] {Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))});
        }
        return row.inputParse()
                .flatMap(parse -> parse.tokenValue(ScanInputRole.SEASON)
                        .flatMap(season -> parse.tokenValue(ScanInputRole.EPISODE)
                                .map(episode -> new int[] {safeInt(season), safeInt(episode)})));
    }

    private static int safeInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private void updateManualMatchNotice() {
        long count = rows.stream().filter(ScanScreen::requiresManualTvdbMatch).count();
        boolean visible = count > 0;
        resolveManualMatchesButton.setVisible(visible);
        resolveManualMatchesButton.setManaged(visible);
        if (visible) {
            resolveManualMatchesButton.setText(UiText.tvdbResolveNow(currentLanguage));
            workflowHelp.setText(UiText.tvdbManualRequired(currentLanguage, (int) count));
        } else {
            workflowHelp.setText(UiText.scanSafetyBanner(currentLanguage));
        }
    }

    private Optional<ScanRow> firstManualMatchRow() {
        return rows.stream().filter(ScanScreen::requiresManualTvdbMatch).findFirst();
    }

    private static boolean requiresManualTvdbMatch(ScanRow row) {
        return row.status() == ScanRowStatus.REVIEW
                || row.status() == ScanRowStatus.META
                || row.status() == ScanRowStatus.AI
                || row.status() == ScanRowStatus.TYPE
                || row.mediaType() == ScanMediaType.UNKNOWN
                || row.tvdbMatch().isEmpty();
    }

    private ContextMenu buildContextMenu(ScanRow row) {
        ContextMenu menu = new ContextMenu();
        MenuItem properties = new MenuItem(UiText.filePropertiesAction(currentLanguage));
        properties.setOnAction(event -> new FilePropertiesDialog(
                table.getScene() == null ? null : table.getScene().getWindow(),
                currentLanguage,
                row).show());

        MenuItem ignore = new MenuItem(row.isIgnored()
                ? UiText.scanContextStopIgnoring(currentLanguage)
                : UiText.scanContextIgnore(currentLanguage));
        ignore.setOnAction(event -> {
            if (row.isIgnored()) {
                row.setStatus(row.tvdbMatch().isPresent() ? ScanRowStatus.TVDB : ScanRowStatus.REVIEW);
                if (row.mediaType() == ScanMediaType.IGNORED) {
                    row.setMediaType(ScanMediaType.UNKNOWN);
                }
            } else {
                row.setSelected(false);
                row.setStatus(ScanRowStatus.IGNORED);
            }
            table.refresh();
            updateMetricsFromRows();
            updateFilterPredicate();
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

        menu.getItems().setAll(properties, ignore, reset, copyPath, openFolder);
        return menu;
    }

    private void updateMetricsFromRows() {
        int total = rows.size();
        long ignored = rows.stream().filter(ScanRow::isIgnored).count();
        long series = rows.stream().filter(row -> !row.isIgnored() && row.mediaType() == ScanMediaType.SERIES).count();
        long movies = rows.stream().filter(row -> !row.isIgnored() && row.mediaType() == ScanMediaType.MOVIE).count();
        long unknown = rows.stream().filter(row -> !row.isIgnored() && ScanRowTableSupport.isUnknownOrNeedsUnderstanding(row)).count();
        long conflicts = rows.stream().filter(row -> !row.isIgnored()
                && (row.status() == ScanRowStatus.CONFLICT || row.status() == ScanRowStatus.DUPLICATE)).count();
        long warnings = rows.stream().filter(row -> !row.isIgnored()
                && (row.alertText().isPresent() || row.status() == ScanRowStatus.ERROR)).count();
        cardTotal.setValue(String.valueOf(total));
        cardSeries.setValue(String.valueOf(series));
        cardMovies.setValue(String.valueOf(movies));
        cardUnknown.setValue(String.valueOf(unknown));
        cardIgnored.setValue(String.valueOf(ignored));
        cardToProcess.setValue(String.valueOf(total - ignored));
        cardConflicts.setValue(String.valueOf(conflicts));
        cardWarnings.setValue(String.valueOf(warnings));
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

    /**
     * JavaFX's stock TextFieldTableCell only commits an edit when the user
     * presses Enter; clicking outside cancels it, which is surprising. This
     * subclass installs a focus-loss listener on the editor the first time it
     * appears and turns focus-out into a commit.
     */
    private static class CommitOnFocusLossStringCell<S> extends TextFieldTableCell<S, String> {
        private boolean focusListenerInstalled;

        CommitOnFocusLossStringCell() {
            super(new DefaultStringConverter());
        }

        @Override
        public void startEdit() {
            super.startEdit();
            if (!isEditing() || focusListenerInstalled) return;
            if (getGraphic() instanceof TextField field) {
                focusListenerInstalled = true;
                field.focusedProperty().addListener((obs, was, isFocused) -> {
                    if (!isFocused && isEditing()) {
                        commitEdit(field.getText());
                    }
                });
            }
        }
    }
}
