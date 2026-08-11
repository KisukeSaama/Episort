package com.episort.ui.scan;

import com.episort.tmdb.TmdbQueryCleaner;
import com.episort.filename.SeasonEpisodePattern;
import com.episort.config.JanusConfiguration;
import com.episort.matching.EpisodeMovieMatchService;
import com.episort.matching.MediaMatchProposal;
import com.episort.matching.MediaMatchType;
import com.episort.matching.TmdbEpisodeMetadata;
import com.episort.matching.TmdbEpisodeOrderMapper;
import com.episort.matching.TmdbMovieMetadata;
import com.episort.matching.TmdbSeriesMetadata;
import com.episort.planning.OperationPlan;
import com.episort.planning.OperationPlanner;
import com.episort.planning.PlanSourceItem;
import com.episort.scanner.InventoryGroup;
import com.episort.scanner.InventoryGroupType;
import com.episort.scanner.InventoryItem;
import com.episort.scanner.InventoryItemType;
import com.episort.scanner.InventoryScanResult;
import com.episort.tmdb.OptionalDoubleScore;
import com.episort.tmdb.TmdbCandidate;
import com.episort.tmdb.TmdbCandidateScorer;
import com.episort.tmdb.TmdbClient;
import com.episort.tmdb.TmdbEpisode;
import com.episort.tmdb.TmdbEpisodeOrder;
import com.episort.tmdb.TmdbException;
import com.episort.tmdb.TmdbIdentity;
import com.episort.tmdb.TmdbMediaType;
import com.episort.tmdb.TmdbMovieDetails;
import com.episort.tmdb.TmdbSearchResult;
import com.episort.tmdb.TmdbSeriesDetails;
import com.episort.ui.AppLanguage;
import com.episort.ui.RoundedClip;
import com.episort.ui.UiText;
import com.episort.ui.WorkflowPhase;
import com.episort.ui.WorkflowStepper;
import com.episort.workflow.ReviewSession;
import com.episort.workflow.ReviewValidationSnapshot;
import com.episort.workflow.TmdbBatchMatchResult;
import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

public final class ScanScreen {

    private final VBox root;
    private final Label heading;
    private final WorkflowStepper workflowStepper;
    private final Label workflowHelp;
    private WorkflowPhase workflowPhase = WorkflowPhase.CHOOSE_FOLDER;
    private boolean workflowInProgress = false;

    private final ScanMetricCards metricCards = new ScanMetricCards();

    private final TableView<ScanRow> table = new TableView<>();
    private final ObservableList<ScanRow> rows = FXCollections.observableArrayList();
    private final FilteredList<ScanRow> filtered = new FilteredList<>(rows, row -> true);
    private final SortedList<ScanRow> sorted = new SortedList<>(filtered);
    private final ScanFilterBar filterBar = new ScanFilterBar(this::onFilterChanged);
    private final Button resolveManualMatchesButton = new Button();
    private final ScanTableColumns columns = new ScanTableColumns(new ColumnHost());
    private ContextMenu activeContextMenu;

    private final RowDetailPanel detailPanel = new RowDetailPanel();
    private final Region detailRoot;
    private final VBox detailStack;
    private final ScanGroupIndex groups = new ScanGroupIndex();
    private final Map<String, TmdbCandidate> tmdbCandidatesByLabel = new HashMap<>();
    private final Map<String, TmdbSeriesDetails> tmdbSeriesDetailsById = new HashMap<>();
    private final TmdbCandidateScorer candidateScorer = new TmdbCandidateScorer();
    private final EpisodeMovieMatchService matchService = new EpisodeMovieMatchService();
    private final TmdbEpisodeOrderMapper orderMapper = new TmdbEpisodeOrderMapper();
    private final ReviewSession reviewSession = new ReviewSession();
    private final OperationPlanner planner = new OperationPlanner();
    private List<PlanSourceItem> validatedPlanItems = List.of();
    private Optional<OperationPlan> operationPlan = Optional.empty();
    private final ScanSelectionController selection;
    private boolean loadedFolder;
    private boolean loading;
    private Optional<Path> workspaceRoot = Optional.empty();

    private Pane currentBody;
    private boolean stackedLayout = false;
    private AppLanguage currentLanguage = AppLanguage.FRENCH;
    private TmdbClient tmdbClient;
    private Supplier<Optional<JanusConfiguration>> tmdbCredentialsSupplier = Optional::empty;
    private Runnable reviewStateChanged = () -> {};

    public ScanScreen() {
        selection = new ScanSelectionController(table, rows, filtered, sorted, new SelectionHost());
        heading = new Label();
        heading.getStyleClass().addAll("section-heading", "section-heading-accent");

        workflowStepper = new WorkflowStepper(AppLanguage.FRENCH);

        workflowHelp = new Label();
        workflowHelp.getStyleClass().add("workflow-help");
        workflowHelp.setWrapText(true);

        VBox workflow = new VBox(8, workflowStepper.root(), workflowHelp);
        workflow.getStyleClass().add("workflow-progress");

        configureTable();
        detailPanel.setOnApplyCandidate(this::applyTmdbCandidate);
        detailPanel.setOnApplySelectedMatch(this::applySelectedTmdbMatchToSelection);
        detailPanel.setOnApplyEpisodeSequence(this::applyTmdbEpisodeSequence);
        detailPanel.setOnResetMatch(this::resetTmdbMatch);
        detailPanel.setOnApplyInputPattern(this::applyInputPatternToSelection);
        detailPanel.setOnSearchMatch(this::openTmdbManualMatchDialog);
        detailRoot = detailPanel.root();
        detailRoot.setMinWidth(320);
        detailRoot.setPrefWidth(380);
        detailRoot.setMaxWidth(420);
        configureResolveManualMatchesButton();
        detailStack = new VBox(10, resolveManualMatchesButton, detailRoot);
        detailStack.getStyleClass().add("scan-detail-stack");
        VBox tableStack = new VBox(12, filterBar.root(), table);
        VBox.setVgrow(table, Priority.ALWAYS);
        HBox initialBody = new HBox(16, tableStack, detailStack);
        HBox.setHgrow(tableStack, Priority.ALWAYS);
        HBox.setHgrow(table, Priority.ALWAYS);
        HBox.setHgrow(detailStack, Priority.NEVER);
        VBox.setVgrow(detailRoot, Priority.ALWAYS);
        VBox.setVgrow(detailStack, Priority.ALWAYS);
        currentBody = initialBody;

        root = new VBox(14, heading, workflow, metricCards.root(), currentBody);
        root.getStyleClass().add("screen-root");
        VBox.setVgrow(currentBody, Priority.ALWAYS);
        root.addEventFilter(MouseEvent.MOUSE_PRESSED,
                event -> selection.clearOnClickOutside(event, table, detailStack));

        applyLanguage(AppLanguage.FRENCH);
        clear();
    }

    public Region root() {
        return root;
    }

    /**
     * Notifies the shell when an edit or an asynchronous TMDB result changes
     * whether the exact-plan action is available.
     */
    public void setOnReviewStateChanged(Runnable listener) {
        reviewStateChanged = listener == null ? () -> {} : listener;
    }

    public void applyLanguage(AppLanguage language) {
        currentLanguage = language;
        heading.setText(UiText.scanHeading(language));
        workflowStepper.applyLanguage(language);
        updateWorkflowActiveStep();
        workflowHelp.setText(UiText.scanSafetyBanner(language));

        metricCards.applyLanguage(language);

        columns.applyLanguage(language);
        filterBar.applyLanguage(language);
        resolveManualMatchesButton.setText(UiText.tmdbResolveNow(language));

        table.setPlaceholder(ScanTableCells.emptyState(
                "≡",
                UiText.scanEmptyTitle(language),
                UiText.scanEmptyHint(language)));

        detailPanel.applyLanguage(language);
        table.refresh();
        updateManualMatchNotice();
        refreshWorkflowHelp();
    }

    public void setTmdbLookup(TmdbClient tmdbClient, Supplier<Optional<JanusConfiguration>> credentialsSupplier) {
        this.tmdbClient = tmdbClient;
        this.tmdbCredentialsSupplier = credentialsSupplier == null ? Optional::empty : credentialsSupplier;
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
        updateWorkflowActiveStep();
    }

    private void updateWorkflowActiveStep() {
        boolean inProgress = workflowInProgress || (loading && workflowPhase == WorkflowPhase.CHOOSE_FOLDER);
        workflowStepper.setPhase(workflowPhase, inProgress);
    }

    public void setWorkflowPhase(WorkflowPhase phase, boolean inProgress) {
        if (phase == null) return;
        this.workflowPhase = phase;
        this.workflowInProgress = inProgress;
        updateWorkflowActiveStep();
    }

    /** Where the strip stands, so a screen taking over the content area can carry it on. */
    public WorkflowPhase workflowPhase() {
        return workflowPhase;
    }

    public void setWorkspaceRoot(Optional<Path> root) {
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
        groups.replaceGroups(scan.groups());
        groups.rebuild(List.copyOf(rows));
        rebuildReviewSessionFromRows();
        updateMetricsFromRows();
        selection.clearFocus();
        detailPanel.clear();
        table.getSelectionModel().clearSelection();
        selection.updateSelectAllState();
        updateManualMatchNotice();
    }

    /** Adds a second folder's worth of rows without dropping what is already loaded. */
    public void append(Optional<InventoryScanResult> result) {
        if (result == null || result.isEmpty()) {
            return;
        }
        InventoryScanResult scan = result.orElseThrow();
        loading = false;
        loadedFolder = true;
        Set<String> existingPaths = rows.stream()
                .map(row -> row.sourcePath().toAbsolutePath().normalize().toString())
                .collect(Collectors.toSet());
        List<ScanRow> additions = ScanRowFactory.from(scan).stream()
                .filter(row -> existingPaths.add(row.sourcePath().toAbsolutePath().normalize().toString()))
                .toList();
        rows.addAll(additions);
        groups.addGroups(scan.groups());
        groups.rebuild(List.copyOf(rows));
        rebuildReviewSessionFromRows();
        updateMetricsFromRows();
        updateFilterPredicate();
        selection.updateSelectAllState();
        updateManualMatchNotice();
        table.refresh();
    }

    /**
     * Returns the screen to its start state: no rows, no validated gate, no
     * plan, and the workflow stepper back on the first step. Anything left
     * behind here reads as current state on the next run.
     */
    public void clear() {
        rows.clear();
        selection.resetAnchor();
        loadedFolder = false;
        loading = false;
        workflowPhase = WorkflowPhase.CHOOSE_FOLDER;
        workflowInProgress = false;
        updateWorkflowActiveStep();
        operationPlan = Optional.empty();
        validatedPlanItems = List.of();
        reviewSession.reset();
        filterBar.reset();
        groups.clear();
        metricCards.clear();
        selection.clearFocus();
        detailPanel.clear();
        table.getSelectionModel().clearSelection();
        selection.updateSelectAllState();
        updateManualMatchNotice();
        refreshWorkflowHelp();
        reviewStateChanged.run();
    }

    public boolean hasRows() {
        return !rows.isEmpty();
    }

    public boolean hasReadyActiveRows() {
        return rows.stream().anyMatch(row -> !row.isIgnored()
                && row.proposedFilename().filter(value -> !value.isBlank()).isPresent());
    }

    public boolean hasLoadedFolder() {
        return loadedFolder;
    }

    /**
     * Applies the post-scan batch TMDB resolution: for every row whose group
     * was successfully matched, paints the identity, the per-row metadata
     * (S/E numbers, proposed filename, confidence), and the canonical "TMDB"
     * status. Rows without a group match keep their previous status so the
     * user can still kick off a manual search from the detail panel.
     */
    public void applyTmdbBatchResult(TmdbBatchMatchResult result) {
        applyTmdbBatchResultInternal(result, 0);
    }

    private void applyTmdbBatchResultInternal(TmdbBatchMatchResult result, int attempt) {
        if (result == null || result.isEmpty()) {
            return;
        }
        // The post-scan pipeline may enqueue this call BEFORE the viewmodel
        // apply runLater has populated rows (JavaFX can dispatch the outer
        // runLater of the double-runLater scheduler before the executor
        // thread finishes wiring the thenAccept continuation). Defer until
        // rows are present, with a small bound to avoid spinning forever.
        if (rows.isEmpty() && attempt < 20) {
            Platform.runLater(
                    () -> applyTmdbBatchResultInternal(result, attempt + 1));
            return;
        }
        int totalRows = rows.size();
        int rowsWithGroup = 0;
        int rowsApplied = 0;
        Set<String> seenSeeds = new HashSet<>();
        for (ScanRow row : rows) {
            InventoryGroup group = groups.groupOf(row);
            if (group == null) continue;
            rowsWithGroup++;
            seenSeeds.add(group.seedName());
            TmdbBatchMatchResult.GroupMatch match = result.matchesBySeed().get(group.seedName());
            if (match == null) continue;
            applyBatchGroupMatchToRow(row, match);
            rowsApplied++;
        }
        ScanTrace.publishApply(totalRows, rowsWithGroup, result.matchesBySeed().size(), rowsApplied,
                "seeds(rows)=" + seenSeeds + " seeds(matches)=" + result.matchesBySeed().keySet());
        rebuildReviewSessionFromRows();
        updateMetricsFromRows();
        updateFilterPredicate();
        table.refresh();
        updateManualMatchNotice();
        if (selection.focused() != null) {
            showDetail(selection.focused());
        }
        // Deferred: the batch apply runs inside a runLater chain, and the gate
        // is modal — opening it here would block the rest of that pass.
        Platform.runLater(this::openIdentityValidation);
    }

    /**
     * Shows the per-group identity gate once TMDB has answered.
     *
     * <p>This is the cheapest place to catch a wrong match: one line per group
     * instead of one row per file, and correcting a group of twenty-five files
     * costs the same as correcting one.
     */
    private void openIdentityValidation() {
        if (groups.isEmpty() || tmdbClient == null) {
            return;
        }
        Window owner = detailRoot.getScene() == null ? null : detailRoot.getScene().getWindow();
        new SeriesIdentityDialog(owner, currentLanguage, this::groupIdentityRows, this::fixGroupIdentity)
                .showAndWait();
        table.refresh();
    }

    private List<GroupIdentityRow> groupIdentityRows() {
        List<GroupIdentityRow> identities = new ArrayList<>();
        groups.rowsByGroupName().forEach((groupName, members) -> {
            ScanRow representative = members.stream()
                    .filter(member -> member.tmdbMatch().isPresent())
                    .findFirst()
                    .orElse(members.getFirst());
            Optional<String> match = representative.tmdbMatch().filter(value -> !value.isBlank());
            boolean manual = representative.tmdbSelectedByUser();
            boolean automatic = representative.status() == ScanRowStatus.TMDB;
            String state;
            if (match.isEmpty()) {
                state = UiText.scanIdentitiesUnresolved(currentLanguage);
            } else if (manual) {
                state = UiText.scanIdentitiesManual(currentLanguage);
            } else if (automatic) {
                state = UiText.scanIdentitiesAutomatic(currentLanguage);
            } else {
                state = UiText.scanIdentitiesSuggested(currentLanguage);
            }
            identities.add(new GroupIdentityRow(
                    groupName,
                    members.size(),
                    match.orElseGet(() -> UiText.scanIdentitiesUnresolved(currentLanguage)),
                    state,
                    match.isPresent(),
                    manual || automatic));
        });
        return identities;
    }

    /**
     * Opens the TMDB search for one group and applies the chosen identity to
     * every file of that group — never to the table selection, which is not
     * what the identity screen is talking about.
     */
    private void fixGroupIdentity(String groupName, Window owner) {
        List<ScanRow> members = groups.membersOf(groupName);
        if (members.isEmpty() || tmdbClient == null) {
            return;
        }
        Optional<JanusConfiguration> credentials = tmdbCredentialsSupplier.get();
        if (credentials.isEmpty()) {
            return;
        }
        TmdbManualMatchDialog dialog = new TmdbManualMatchDialog(
                owner,
                currentLanguage,
                tmdbClient,
                credentials.orElseThrow(),
                TmdbQueryCleaner.clean(groupName),
                new ArrayList<>(tmdbCandidatesByLabel.values()));
        dialog.showAndWait().ifPresent(candidate ->
                applyTmdbCandidateTo(members, members.getFirst(), candidate, TmdbEpisodeOrder.AIRED));
    }

    private void applyBatchGroupMatchToRow(ScanRow row, TmdbBatchMatchResult.GroupMatch match) {
        match.series().ifPresent(series -> tmdbSeriesDetailsById.put(series.identity().id(), series));
        if (row.tmdbSelectedByUser()) {
            return;
        }
        String label = batchCandidateLabel(match);
        row.setTmdbMatch(Optional.of(label));
        row.setTmdbCandidate(Optional.of(match.candidate().orElseGet(() -> new TmdbCandidate(
                match.identity(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                OptionalDoubleScore.empty(),
                0))));
        row.setTmdbSelectedByUser(false);
        row.setStatus(match.automatic() ? ScanRowStatus.TMDB : ScanRowStatus.REVIEW);
        MediaMatchProposal proposal = match.proposalsByPath().get(row.sourcePath());
        String extension = row.extension().isBlank() ? "" : "." + row.extension().toLowerCase(Locale.ROOT);
        if (!match.automatic()) {
            row.setConfidence(OptionalDouble.of(match.score()));
            row.setNoteText(Optional.of(UiText.tmdbMatchSuggested(currentLanguage)));
            row.setAlertText(match.alert().or(() -> Optional.of(UiText.tmdbSuggestionNeedsValidation(currentLanguage))));
            ScanTrace.publishAlert(row, "RECALCULATED", "TMDB", row.alertText().orElse(""),
                    "TMDB candidate suggested but not selected");
            return;
        }
        if (match.movie().isPresent()) {
            TmdbMovieDetails movie = match.movie().orElseThrow();
            String year = movie.releaseYear().map(value -> " (" + value + ")").orElse("");
            row.setMediaType(ScanMediaType.MOVIE);
            row.setProposedFilename(Optional.of(
                    ScanPatternFormatter.sanitizeSegment(movie.identity().displayName()) + year + extension));
            row.setOrder(Optional.of("N/A"));
            if (proposal != null && proposal.confidence().isPresent()) {
                row.setConfidence(proposal.confidence());
            }
            row.setInputParse(Optional.of(tmdbMovieParse(
                    movie.identity().displayName(),
                    movie.releaseYear().map(Object::toString).orElse(""))));
            row.setNoteText(Optional.of(UiText.tmdbAutomaticMatch(currentLanguage) + ": "
                    + movie.identity().displayName() + "."));
            if (proposal != null && proposal.type() == MediaMatchType.UNMATCHED) {
                row.setAlertText(Optional.of(proposal.reason()));
                ScanTrace.publishAlert(row, "RECALCULATED", "TMDB", proposal.reason(),
                        "Movie metadata did not produce a match");
            } else {
                row.setAlertText(Optional.empty());
                ScanTrace.publishAlert(row, "CLEARED", "TMDB", "", "Movie metadata produced a match");
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
            // TMDB titles can contain path separators (e.g. "Makoto/Truth"); left
            // as-is the row would keep only the last segment of the name.
            String episodeTitle = ScanPatternFormatter.sanitizeSegment(
                    proposal.title().orElse("Untitled episode"));
            String proposed = String.format("%s - S%02dE%02d - %s%s",
                    ScanPatternFormatter.sanitizeSegment(match.identity().displayName()),
                    season, episode, episodeTitle, extension);
            row.setProposedFilename(Optional.of(proposed));
            row.setConfidence(proposal.confidence());
            row.setInputParse(Optional.of(tmdbEpisodeParse(
                    match.identity().displayName(),
                    String.format("%02d", season),
                    String.format("%02d", episode),
                    episodeTitle)));
            row.setAppliedTmdbOrder(Optional.of(TmdbEpisodeOrder.AIRED));
            row.setAlertText(Optional.empty());
            ScanTrace.publishAlert(row, "CLEARED", "TMDB", "", "Series metadata produced a match");
            row.setNoteText(Optional.of(UiText.tmdbAutomaticMatch(currentLanguage) + "."));
            return;
        }
        if (proposal != null && proposal.type() == MediaMatchType.UNMATCHED) {
            row.setAlertText(Optional.of(proposal.reason()));
            ScanTrace.publishAlert(row, "RECALCULATED", "TMDB", proposal.reason(),
                    "Series identity selected but no episode match");
            row.setNoteText(Optional.of(UiText.scanNoteTmdbIdentityNoEpisode(
                    currentLanguage, match.identity().displayName())));
        } else {
            row.setNoteText(Optional.of(UiText.scanNoteTmdbIdentityApplied(
                    currentLanguage, match.identity().displayName())));
        }
    }

    private static ScanInputParse tmdbMovieParse(String title, String year) {
        List<ScanInputToken> tokens = new ArrayList<>();
        if (title != null && !title.isBlank()) {
            tokens.add(new ScanInputToken(ScanInputRole.TITLE, title, title, 0, 0));
        }
        if (year != null && !year.isBlank()) {
            tokens.add(new ScanInputToken(ScanInputRole.YEAR, year, year, 0, 0));
        }
        return new ScanInputParse("TMDB", tokens, Optional.empty(),
                OptionalDouble.empty(), ScanInputParseSource.TMDB);
    }

    private static ScanInputParse tmdbEpisodeParse(String series, String season, String episode, String title) {
        List<ScanInputToken> tokens = new ArrayList<>();
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
        return new ScanInputParse("TMDB", tokens,
                order == null ? Optional.empty() : Optional.of(order),
                OptionalDouble.empty(), ScanInputParseSource.TMDB);
    }

    private static String batchCandidateLabel(TmdbBatchMatchResult.GroupMatch match) {
        StringBuilder label = new StringBuilder(match.identity().displayName());
        match.movie().flatMap(TmdbMovieDetails::releaseYear)
                .ifPresent(year -> label.append(" (").append(year).append(")"));
        label.append(" [TMDB ").append(match.identity().id()).append("]");
        return label.toString();
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

        VBox tableStack = new VBox(12, filterBar.root(), table);
        VBox.setVgrow(table, Priority.ALWAYS);
        Pane next;
        if (stacked) {
            detailRoot.setMaxWidth(Double.MAX_VALUE);
            detailStack.setMaxWidth(Double.MAX_VALUE);
            VBox stack = new VBox(16, tableStack, detailStack);
            VBox.setVgrow(tableStack, Priority.ALWAYS);
            next = stack;
        } else {
            detailRoot.setMaxWidth(420);
            detailRoot.setPrefWidth(380);
            detailStack.setMaxWidth(420);
            detailStack.setPrefWidth(380);
            HBox row = new HBox(16, tableStack, detailStack);
            HBox.setHgrow(tableStack, Priority.ALWAYS);
            HBox.setHgrow(detailStack, Priority.NEVER);
            next = row;
        }
        VBox.setVgrow(detailRoot, Priority.ALWAYS);
        VBox.setVgrow(detailStack, Priority.ALWAYS);
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

    private void configureTable() {
        // Its own class on top of the shared one: the accent bar marking a picked
        // row belongs to the tables the user acts on, not to the history log.
        table.getStyleClass().addAll("preview-table", "scan-table");
        RoundedClip.install(table, 14);
        table.setEditable(true);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        // The group column writes its name only where the group changes, so a
        // cell's rendering depends on its neighbour. Sorting or filtering moves
        // the neighbours without changing the cell's own item, which would leave
        // a stale blank behind: repaint the visible cells whenever the view
        // order or its contents change.
        table.comparatorProperty().addListener((observable, oldValue, newValue) -> table.refresh());
        sorted.addListener((ListChangeListener<ScanRow>) change -> table.refresh());
        selection.install();

        columns.installOn(table);

        table.setRowFactory(view -> {
            TableRow<ScanRow> row = new TableRow<>();
            row.setOnContextMenuRequested(event -> {
                if (row.isEmpty()) {
                    return;
                }
                ScanRow item = row.getItem();
                if (!table.getSelectionModel().getSelectedItems().contains(item)) {
                    selection.focusForContextMenu(item);
                }
                selection.anchorOn(item);
                showContextMenu(row, item, event.getScreenX(), event.getScreenY());
                event.consume();
            });
            row.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (row.isEmpty() || event.getButton() != MouseButton.SECONDARY) {
                    return;
                }
                ScanRow item = row.getItem();
                if (!table.getSelectionModel().getSelectedItems().contains(item)) {
                    selection.focusForContextMenu(item);
                }
                selection.anchorOn(item);
            });
            row.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (row.isEmpty()
                        || event.getButton() != MouseButton.PRIMARY
                        || ScanSelectionController.isCheckboxCellEvent(event)) {
                    return;
                }
                selection.focusOnly(row.getItem());
            });
            row.itemProperty().addListener((observable, oldItem, newItem) -> {
                ScanSelectionController.updateRowStyle(row, newItem);
                updateGroupBlockStyle(row);
            });
            row.emptyProperty().addListener((observable, wasEmpty, isEmpty) -> {
                ScanSelectionController.updateRowStyle(row, row.getItem());
                updateGroupBlockStyle(row);
            });
            row.indexProperty().addListener((observable, oldIndex, newIndex) -> updateGroupBlockStyle(row));
            return row;
        });
    }

    private String groupDisplayName(ScanRow row) {
        return groups.displayName(row, currentLanguage);
    }

    private boolean groupIsIgnored(ScanRow row) {
        return groups.groupOf(row) != null && !groups.namesAMedia(row);
    }

    /**
     * Whether the row at this index in the sorted view opens a group block.
     *
     * <p>The ignored flag is part of the key: an ignored block and an active
     * block of the same name are two different things to review, and the second
     * one has to announce itself rather than continue the first.
     */
    private boolean startsGroupBlock(int viewIndex) {
        if (viewIndex <= 0 || viewIndex >= sorted.size()) {
            return viewIndex == 0;
        }
        ScanRow row = sorted.get(viewIndex);
        ScanRow above = sorted.get(viewIndex - 1);
        return !(groupDisplayName(row).equals(groupDisplayName(above))
                && groupIsIgnored(row) == groupIsIgnored(above));
    }

    /** Draws the hairline that separates one group block from the next. */
    private void updateGroupBlockStyle(TableRow<ScanRow> row) {
        boolean opensBlock = !row.isEmpty()
                && row.getItem() != null
                && row.getIndex() > 0
                && startsGroupBlock(row.getIndex());
        row.getStyleClass().remove("group-block-start");
        if (opensBlock) {
            row.getStyleClass().add("group-block-start");
        }
    }

    /** Applies one edited token to every row the edit is meant to cover. */
    private void applyTokenEditToSelection(ScanRow anchor, ScanInputRole role, String newValue) {
        if (anchor == null) {
            return;
        }
        String value = newValue == null ? "" : newValue.trim();
        if (UiText.EMPTY.equals(value)) {
            value = "";
        }
        for (ScanRow row : rowsForBatchEdit(anchor)) {
            ScanRowEditor.applyTokenEdit(row, role, value);
        }
        refreshAfterBatchEdit(anchor);
    }

    /** Re-applies the filter chips to the table; a fresh predicate forces a re-filter. */
    private void updateFilterPredicate() {
        filtered.setPredicate(filterBar::test);
        selection.restoreTableSelection();
    }

    /** The filter bar changed: re-filter, and keep the header checkbox honest. */
    private void onFilterChanged() {
        updateFilterPredicate();
    }

    private void applyMediaTypeToSelection(ScanRow anchor, ScanMediaType mediaType) {
        for (ScanRow row : rowsForBatchEdit(anchor)) {
            row.setMediaType(mediaType);
            ScanRowEditor.recomputeProposedName(row);
        }
        refreshAfterBatchEdit(anchor);
    }

    private void applyInputPatternToSelection(ScanRow anchor, String patternText) {
        if (anchor == null) {
            return;
        }
        String value = patternText == null ? "" : patternText.trim();
        for (ScanRow row : rowsForBatchEdit(anchor)) {
            ScanRowEditor.applyManualInputPattern(row, value);
        }
        refreshAfterBatchEdit(anchor);
    }

    private List<ScanRow> rowsForBatchEdit(ScanRow anchor) {
        List<ScanRow> selected = selection.checkedRows();
        if (selected.contains(anchor)) {
            return selected;
        }
        return List.of(anchor);
    }

    private void refreshAfterBatchEdit(ScanRow anchor) {
        table.refresh();
        ScanRow focus = selection.focused() == null ? anchor : selection.focused();
        if (focus != null) {
            showDetail(focus);
        }
        if (selection.focused() != null) {
            detailPanel.setTmdbTargetCount(tmdbTargetRows(selection.focused()).size());
        }
    }

    /** Repaints the detail panel for a row, carrying its group match along. */
    private void showDetail(ScanRow row) {
        detailPanel.show(row, groups.matchOf(row));
        configureEpisodeSequenceOptions(row);
    }

    private void configureEpisodeSequenceOptions(ScanRow row) {
        if (row == null || row.tmdbCandidate().isEmpty()) {
            detailPanel.setTmdbEpisodeOptions(List.of(), "", TmdbEpisodeOrder.AIRED);
            return;
        }
        TmdbSeriesDetails details = tmdbSeriesDetailsById.get(row.tmdbCandidate().orElseThrow().identity().id());
        if (details == null) {
            detailPanel.setTmdbEpisodeOptions(List.of(), "", TmdbEpisodeOrder.AIRED);
            return;
        }
        TmdbEpisodeOrder order = row.appliedTmdbOrder().orElse(TmdbEpisodeOrder.AIRED);
        String selectedId = episodeForRow(details.episodesFor(order), row).map(TmdbEpisode::id).orElse("");
        detailPanel.setTmdbEpisodeOptions(details.episodesFor(order), selectedId, order);
    }

    private void applyTmdbCandidate(ScanRow row, String candidate) {
        TmdbCandidate selectedCandidate = tmdbCandidatesByLabel.get(candidate);
        Optional<JanusConfiguration> credentials = tmdbCredentialsSupplier.get();
        if (selectedCandidate != null && credentials.isPresent()) {
            applyManualTmdbCandidate(row, selectedCandidate);
            return;
        }
        row.setTmdbMatch(Optional.of(candidate));
        row.setStatus(ScanRowStatus.TMDB);
        row.setNoteText(Optional.of(UiText.scanNoteTmdbIdentitySelected(currentLanguage)));
        row.setTmdbCandidate(Optional.ofNullable(selectedCandidate));
        row.setTmdbSelectedByUser(selectedCandidate != null);
        table.refresh();
        updateManualMatchNotice();
        if (selection.focused() == row) {
            showDetail(row);
        }
    }

    private void resetTmdbMatch(ScanRow row) {
        ScanRowEditor.resetTmdbMatches(tmdbTargetRows(row));
        updateMetricsFromRows();
        updateFilterPredicate();
        table.refresh();
        updateManualMatchNotice();
        if (selection.focused() == row) {
            showDetail(row);
        }
    }

    private void openTmdbManualMatchDialog(ScanRow row) {
        if (row == null || tmdbClient == null) {
            return;
        }
        Optional<JanusConfiguration> credentials = tmdbCredentialsSupplier.get();
        if (credentials.isEmpty()) {
            row.setNoteText(Optional.of(UiText.scanNoteJanusConfigurationMissing(currentLanguage)));
            table.refresh();
            showDetail(row);
            return;
        }
        List<TmdbCandidate> existing = new ArrayList<>(tmdbCandidatesByLabel.values());
        Window owner = detailRoot.getScene() == null ? null : detailRoot.getScene().getWindow();
        TmdbManualMatchDialog dialog = new TmdbManualMatchDialog(
                owner,
                currentLanguage,
                tmdbClient,
                credentials.orElseThrow(),
                tmdbQuery(row),
                existing);
        dialog.showAndWait().ifPresent(candidate -> applyManualTmdbCandidate(row, candidate));
    }

    private void applyManualTmdbCandidate(ScanRow row, TmdbCandidate candidate) {
        applyManualTmdbCandidate(row, candidate, TmdbEpisodeOrder.AIRED);
    }

    private void applyManualTmdbCandidate(ScanRow row, TmdbCandidate candidate, TmdbEpisodeOrder order) {
        List<ScanRow> targets = tmdbTargetRows(row);
        if (targets.isEmpty()) {
            row.setNoteText(Optional.of(UiText.tmdbNoFileSelected(currentLanguage)));
            table.refresh();
            showDetail(row);
            return;
        }
        Optional<List<ScanRow>> confirmed = confirmTargetsAcrossGroups(row, targets);
        if (confirmed.isEmpty()) {
            return;
        }
        applyTmdbCandidateTo(confirmed.orElseThrow(), row, candidate, order);
    }

    /**
     * Guards the "one identity, many rows" gesture against a selection that
     * covers several groups.
     *
     * <p>Empty means the user cancelled; otherwise the returned list is what to
     * actually rename — either the whole selection or just the anchor's group.
     */
    private Optional<List<ScanRow>> confirmTargetsAcrossGroups(
            ScanRow anchor, List<ScanRow> targets) {
        Map<String, List<ScanRow>> byGroup = new LinkedHashMap<>();
        for (ScanRow target : targets) {
            byGroup.computeIfAbsent(groups.displayName(target, currentLanguage), ignored -> new ArrayList<>()).add(target);
        }
        if (byGroup.size() <= 1) {
            return Optional.of(targets);
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        byGroup.forEach((name, members) -> counts.put(name, members.size()));
        String anchorGroup = groups.displayName(anchor, currentLanguage);
        Window owner = detailRoot.getScene() == null ? null : detailRoot.getScene().getWindow();
        return switch (MixedGroupDialog.ask(owner, currentLanguage, anchorGroup, counts)) {
            case APPLY_TO_ALL -> Optional.of(targets);
            case LIMIT_TO_GROUP -> Optional.of(byGroup.getOrDefault(anchorGroup, List.of(anchor)));
            case CANCEL -> Optional.empty();
        };
    }

    private void applyTmdbCandidateTo(
            List<ScanRow> targets, ScanRow row, TmdbCandidate candidate, TmdbEpisodeOrder order) {
        String label = candidateLabel(candidate);
        tmdbCandidatesByLabel.put(label, candidate);
        for (ScanRow target : targets) {
            setManualTmdbCandidate(target, candidate, label);
            target.setNoteText(Optional.of(UiText.scanNoteTmdbIdentitySelected(currentLanguage)));
        }
        detailPanel.setTmdbBusy(true, UiText.tmdbApplyingOrder(currentLanguage));
        loadSelectedTmdbMetadata(targets, candidate.identity(), tmdbCredentialsSupplier.get().orElseThrow(), order);
        table.refresh();
        updateManualMatchNotice();
        if (selection.focused() != null) {
            showDetail(selection.focused());
            detailPanel.setTmdbTargetCount(tmdbTargetRows(selection.focused()).size());
        }
    }

    private void applySelectedTmdbMatchToSelection(ScanRow anchor, TmdbEpisodeOrder order) {
        if (anchor == null || anchor.tmdbCandidate().isEmpty()) {
            if (anchor != null) {
                anchor.setNoteText(Optional.of(UiText.tmdbNoResultSelected(currentLanguage)));
                table.refresh();
                showDetail(anchor);
            }
            return;
        }
        Optional<JanusConfiguration> credentials = tmdbCredentialsSupplier.get();
        if (credentials.isEmpty()) {
            anchor.setNoteText(Optional.of(UiText.scanNoteJanusConfigurationMissing(currentLanguage)));
            table.refresh();
            showDetail(anchor);
            return;
        }
        applyManualTmdbCandidate(anchor, anchor.tmdbCandidate().orElseThrow(), order);
    }

    private void setManualTmdbCandidate(ScanRow row, TmdbCandidate candidate, String label) {
        row.setTmdbMatch(Optional.of(label));
        row.setTmdbCandidate(Optional.of(candidate));
        row.setTmdbSelectedByUser(true);
        row.setStatus(ScanRowStatus.TMDB);
        row.setConfidence(OptionalDouble.of(1.0));
        row.setAlertText(Optional.empty());
    }

    private List<ScanRow> tmdbTargetRows(ScanRow anchor) {
        List<ScanRow> selected = selection.checkedRows();
        if (!selected.isEmpty()) {
            return selected;
        }
        if (anchor != null && !anchor.isIgnored()) {
            return List.of(anchor);
        }
        return List.of();
    }

    private void loadTmdbCandidates(ScanRow row) {
        if (tmdbClient == null || row == null
                || row.mediaType() == ScanMediaType.UNKNOWN
                || row.mediaType() == ScanMediaType.IGNORED) {
            detailPanel.setTmdbCandidateOptions(List.of());
            return;
        }
        Optional<JanusConfiguration> credentials = tmdbCredentialsSupplier.get();
        if (credentials.isEmpty()) {
            detailPanel.setTmdbCandidateOptions(List.of());
            row.setNoteText(Optional.of(UiText.scanNoteJanusConfigurationMissing(currentLanguage)));
            table.refresh();
            return;
        }
        String query = tmdbQuery(row);
        if (query.isBlank()) {
            detailPanel.setTmdbCandidateOptions(List.of());
            return;
        }
        CompletableFuture
                .supplyAsync(() -> tmdbClient.search(query, credentials.orElseThrow()))
                .thenAccept(result -> Platform.runLater(() -> applyTmdbCandidates(row, result)))
                .exceptionally(throwable -> {
                    Platform.runLater(() -> applyTmdbLookupFailure(row, throwable));
                    return null;
                });
    }

    private void applyTmdbCandidates(ScanRow row, TmdbSearchResult result) {
        if (selection.focused() != row) {
            return;
        }
        List<TmdbCandidate> candidates = candidateScorer.rankedByRelevance(
                tmdbQuery(row),
                row.mediaType() == ScanMediaType.MOVIE
                        ? InventoryGroupType.LIKELY_MOVIE
                        : InventoryGroupType.LIKELY_SERIES,
                row.mediaType() == ScanMediaType.MOVIE
                        ? result.movieCandidates()
                        : result.seriesCandidates());
        tmdbCandidatesByLabel.clear();
        List<String> labels = new ArrayList<>();
        for (TmdbCandidate candidate : candidates) {
            String label = candidateLabel(candidate);
            labels.add(label);
            tmdbCandidatesByLabel.put(label, candidate);
        }
        detailPanel.setTmdbCandidateOptions(labels);
        row.setNoteText(labels.isEmpty()
                ? Optional.of(UiText.scanNoteTmdbNoCandidates(currentLanguage, tmdbQuery(row)))
                : Optional.of(UiText.scanNoteTmdbCandidatesLoaded(currentLanguage)));
        table.refresh();
        showDetail(row);
    }

    private void applyTmdbLookupFailure(ScanRow row, Throwable throwable) {
        applyTmdbLookupFailure(List.of(row), throwable);
    }

    private void applyTmdbLookupFailure(List<ScanRow> targetRows, Throwable throwable) {
        ScanRow active = selection.focused();
        if (active != null && !targetRows.contains(active)) {
            return;
        }
        Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
                ? throwable.getCause()
                : throwable;
        String message = cause instanceof TmdbException tmdbException
                ? tmdbException.error().safeMessage()
                : "TMDB lookup failed.";
        for (ScanRow row : targetRows) {
            row.setStatus(ScanRowStatus.ERROR);
            row.setAlertText(Optional.of(message));
        }
        detailPanel.setTmdbBusy(false, "");
        updateMetricsFromRows();
        updateFilterPredicate();
        table.refresh();
        if (active != null) {
            showDetail(active);
            detailPanel.setTmdbTargetCount(tmdbTargetRows(active).size());
        }
    }

    private String tmdbQuery(ScanRow row) {
        InventoryGroup group = groups.groupOf(row);
        // Only a media-naming group seeds the query: "sidecar" and "unsupported"
        // are internal bucket names and would search TMDB for themselves.
        if (group != null
                && ScanGroupIndex.namesAMedia(group.type())
                && group.seedName() != null
                && !group.seedName().isBlank()) {
            return TmdbQueryCleaner.clean(group.seedName());
        }
        if (row.mediaType() == ScanMediaType.MOVIE) {
            return TmdbQueryCleaner.clean(ScanRowEditor.derivedMovieTitle(row));
        }
        String query = row.inputParse()
                .flatMap(parse -> parse.tokenValue(ScanInputRole.SERIES))
                .orElse(row.originalFilename());
        return TmdbQueryCleaner.clean(query);
    }

    private static String candidateLabel(TmdbCandidate candidate) {
        StringBuilder label = new StringBuilder();
        label.append(candidate.identity().displayName());
        candidate.year().ifPresent(year -> label.append(" (").append(year).append(")"));
        label.append(" [TMDB ").append(candidate.identity().id()).append("]");
        candidate.network().ifPresent(network -> label.append(" · ").append(network));
        candidate.country().ifPresent(country -> label.append(" · ").append(country));
        return label.toString();
    }

    private void loadSelectedTmdbMetadata(
            List<ScanRow> targetRows,
            TmdbIdentity identity,
            JanusConfiguration credentials,
            TmdbEpisodeOrder order) {
        List<ScanRow> targets = List.copyOf(targetRows);
        CompletableFuture
                .supplyAsync(() -> identity.mediaType() == TmdbMediaType.MOVIE
                        ? tmdbClient.movieDetails(identity, credentials)
                        : tmdbClient.seriesDetails(identity, order, credentials))
                .thenAccept(details -> Platform.runLater(() -> applySelectedTmdbMetadata(targets, details, order)))
                .exceptionally(throwable -> {
                    Platform.runLater(() -> applyTmdbLookupFailure(targets, throwable));
                    return null;
                });
    }

    private void applySelectedTmdbMetadata(List<ScanRow> targetRows, Object details, TmdbEpisodeOrder order) {
        if (details instanceof TmdbSeriesDetails series) {
            tmdbSeriesDetailsById.put(series.identity().id(), series);
        }
        int updated = 0;
        for (ScanRow row : targetRows) {
            applySelectedTmdbMetadataToRow(row, details, order);
            updated++;
        }
        String message = UiText.tmdbFilesUpdated(currentLanguage, updated);
        for (ScanRow row : targetRows) {
            row.setNoteText(Optional.of(message));
        }
        detailPanel.setTmdbBusy(false, "");
        updateMetricsFromRows();
        updateFilterPredicate();
        table.refresh();
        updateManualMatchNotice();
        if (selection.focused() != null) {
            showDetail(selection.focused());
            detailPanel.setTmdbTargetCount(tmdbTargetRows(selection.focused()).size());
        }
    }

    private void applyTmdbEpisodeSequence(ScanRow anchor, TmdbEpisode firstEpisode) {
        if (anchor == null || firstEpisode == null || anchor.tmdbCandidate().isEmpty()) {
            return;
        }
        TmdbSeriesDetails series = tmdbSeriesDetailsById.get(
                anchor.tmdbCandidate().orElseThrow().identity().id());
        if (series == null) {
            return;
        }
        List<ScanRow> targets = new ArrayList<>(tmdbTargetRows(anchor));
        Optional<List<ScanRow>> confirmed = confirmTargetsAcrossGroups(anchor, targets);
        if (confirmed.isEmpty()) {
            return;
        }
        targets = new ArrayList<>(confirmed.orElseThrow());
        targets.sort(Comparator.comparingInt(row -> {
            int index = sorted.indexOf(row);
            return index < 0 ? Integer.MAX_VALUE : index;
        }));
        TmdbEpisodeOrder order = anchor.appliedTmdbOrder().orElse(TmdbEpisodeOrder.AIRED);
        List<TmdbEpisode> sequence = TmdbEpisodeSequence.startingAt(
                series.episodesFor(order), firstEpisode.id(), targets.size());
        for (int index = 0; index < sequence.size(); index++) {
            applyTmdbEpisode(targets.get(index), series, order, sequence.get(index));
        }
        String message = sequence.size() == targets.size()
                ? UiText.tmdbFilesUpdated(currentLanguage, sequence.size())
                : UiText.tmdbSequenceIncomplete(currentLanguage, sequence.size(), targets.size());
        targets.forEach(row -> row.setNoteText(Optional.of(message)));
        updateMetricsFromRows();
        updateFilterPredicate();
        table.refresh();
        showDetail(anchor);
        detailPanel.setTmdbTargetCount(targets.size());
    }

    private void applyTmdbEpisode(
            ScanRow row, TmdbSeriesDetails series, TmdbEpisodeOrder order, TmdbEpisode episode) {
        row.setMediaType(ScanMediaType.SERIES);
        row.setStatus(ScanRowStatus.TMDB);
        row.setOrder(Optional.of(String.format("S%02dE%02d",
                episode.seasonNumber(), episode.episodeNumber())));
        row.setInputParse(Optional.of(tmdbEpisodeParse(
                series.identity().displayName(),
                String.format("%02d", episode.seasonNumber()),
                String.format("%02d", episode.episodeNumber()),
                episode.title())));
        ScanRowEditor.recomputeProposedName(row);
        row.setAppliedTmdbOrder(Optional.of(order));
        row.setTmdbSelectedByUser(true);
        row.setConfidence(OptionalDouble.of(1.0));
        row.setAlertText(Optional.empty());
    }

    private static Optional<TmdbEpisode> episodeForRow(List<TmdbEpisode> episodes, ScanRow row) {
        String value = row.order().orElse("");
        if (!value.matches("S\\d+E\\d+")) {
            return Optional.empty();
        }
        int marker = value.indexOf('E');
        int season = Integer.parseInt(value.substring(1, marker));
        int episode = Integer.parseInt(value.substring(marker + 1));
        return episodes.stream()
                .filter(candidate -> candidate.seasonNumber() == season
                        && candidate.episodeNumber() == episode)
                .findFirst();
    }

    private void applySelectedTmdbMetadataToRow(ScanRow row, Object details, TmdbEpisodeOrder order) {
        if (details instanceof TmdbMovieDetails movie) {
            applyMovieMetadata(row, movie);
        } else if (details instanceof TmdbSeriesDetails series) {
            applySeriesMetadata(row, series, order);
        }
    }

    private void applyMovieMetadata(ScanRow row, TmdbMovieDetails movie) {
        row.setMediaType(ScanMediaType.MOVIE);
        applyTmdbParseUnlessUserOwned(row, tmdbMovieParse(
                movie.identity().displayName(),
                movie.releaseYear().map(Object::toString).orElse("")));
        ScanRowEditor.recomputeProposedName(row);
        row.setOrder(Optional.of("N/A"));
        row.setNoteText(Optional.of(UiText.scanNoteTmdbMovieApplied(
                currentLanguage, movie.identity().displayName())));
        MediaMatchProposal proposal = matchService.proposeMovieMatches(
                List.of(inventoryItemFor(row)),
                new TmdbMovieMetadata(movie.identity().id(), movie.identity().displayName(), movie.releaseYear()))
                .getFirst();
        if (proposal.type() == MediaMatchType.UNMATCHED) {
            row.setAlertText(Optional.of(proposal.reason()));
        } else {
            row.setAlertText(Optional.empty());
        }
    }

    private void applySeriesMetadata(ScanRow row, TmdbSeriesDetails series, TmdbEpisodeOrder order) {
        if (!series.supportedOrders().contains(order)) {
            row.setAlertText(Optional.of(UiText.scanAlertTmdbOrderUnavailable(currentLanguage)));
            row.setNoteText(Optional.of(UiText.scanNoteTmdbOrderUnavailable(currentLanguage)));
            return;
        }
        List<MediaMatchProposal> proposals = matchService.proposeSeriesMatches(
                List.of(inventoryItemFor(row)),
                new TmdbSeriesMetadata(
                        series.identity().id(),
                        series.identity().displayName(),
                        series.episodesFor(order).stream().map(ScanScreen::toMatchingEpisode).toList()));
        MediaMatchProposal proposal = proposals.isEmpty()
                ? MediaMatchProposal.unmatched(row.sourcePath(), "No TMDB episode proposal.")
                : proposals.getFirst();
        if (proposal.type() == MediaMatchType.UNMATCHED) {
            TmdbEpisodeOrderMapper.EpisodeOrderMappingResult mapped = remapAcrossTmdbOrders(row, series, order);
            proposal = mapped.proposal().orElse(proposal);
            mapped.error().ifPresent(error -> row.setAlertText(Optional.of(error)));
        }
        if (proposal.type() == MediaMatchType.UNMATCHED) {
            row.setAlertText(Optional.of(proposal.reason()));
            row.setNoteText(Optional.of(UiText.scanNoteTmdbNoEpisodeMatch(currentLanguage)));
            return;
        }
        row.setMediaType(ScanMediaType.SERIES);
        row.setOrder(Optional.of(String.format("S%02dE%02d",
                proposal.seasonNumber().orElse(0),
                proposal.episodeNumber().orElse(0))));
        applyTmdbParseUnlessUserOwned(row, tmdbEpisodeParse(
                series.identity().displayName(),
                String.format("%02d", proposal.seasonNumber().orElse(0)),
                String.format("%02d", proposal.episodeNumber().orElse(0)),
                proposal.title().orElse("Untitled episode")));
        ScanRowEditor.recomputeProposedName(row);
        row.setConfidence(proposal.confidence());
        row.setAppliedTmdbOrder(Optional.of(order));
        row.setAlertText(Optional.empty());
        row.setNoteText(Optional.of(UiText.scanNoteTmdbEpisodeApplied(
                currentLanguage, series.identity().displayName())));
    }

    private static void applyTmdbParseUnlessUserOwned(ScanRow row, ScanInputParse tmdbParse) {
        if (row.inputParse().map(ScanInputParse::source).filter(ScanInputParseSource.USER::equals).isEmpty()) {
            row.setInputParse(Optional.of(tmdbParse));
        }
    }

    private static TmdbEpisodeMetadata toMatchingEpisode(TmdbEpisode episode) {
        return new TmdbEpisodeMetadata(
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

    private TmdbEpisodeOrderMapper.EpisodeOrderMappingResult remapAcrossTmdbOrders(
            ScanRow row, TmdbSeriesDetails series, TmdbEpisodeOrder targetOrder) {
        Optional<int[]> parsed = parsedSeasonEpisode(row);
        if (parsed.isEmpty()) {
            return new TmdbEpisodeOrderMapper.EpisodeOrderMappingResult(
                    Optional.empty(), Optional.of("Episode not found in selected order"));
        }
        int season = parsed.orElseThrow()[0];
        int episode = parsed.orElseThrow()[1];
        return orderMapper.map(series, row.appliedTmdbOrder().orElse(null), targetOrder, row.sourcePath(),
                season, episode, Optional.empty());
    }

    private Optional<int[]> parsedSeasonEpisode(ScanRow row) {
        Matcher matcher = SeasonEpisodePattern.LENIENT.matcher(row.originalFilename());
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
        long count = rows.stream().filter(ScanScreen::requiresManualTmdbMatch).count();
        boolean visible = count > 0;
        resolveManualMatchesButton.setVisible(visible);
        resolveManualMatchesButton.setManaged(visible);
        if (visible) {
            resolveManualMatchesButton.setText(UiText.tmdbResolveNow(currentLanguage));
            workflowHelp.setText(UiText.tmdbManualRequired(currentLanguage, (int) count));
        } else {
            workflowHelp.setText(UiText.scanSafetyBanner(currentLanguage));
        }
    }

    private Optional<ScanRow> firstManualMatchRow() {
        return rows.stream().filter(ScanScreen::requiresManualTmdbMatch).findFirst();
    }

    /**
     * A row is only waiting on the user when it names a media Episort intends to
     * rename. Ignored rows — sidecars, unsupported files, user-ignored entries —
     * never carry a TMDB identity, so counting them here raised a permanent
     * "N files need a manual match" notice that nothing could clear.
     */
    private static boolean requiresManualTmdbMatch(ScanRow row) {
        if (row.isIgnored()
                || row.status() == ScanRowStatus.IGNORED
                || row.status() == ScanRowStatus.EXT
                || row.mediaType() == ScanMediaType.IGNORED) {
            return false;
        }
        return row.status() == ScanRowStatus.REVIEW
                || row.status() == ScanRowStatus.META
                || row.status() == ScanRowStatus.TYPE
                || row.mediaType() == ScanMediaType.UNKNOWN
                || row.tmdbMatch().isEmpty();
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
            boolean wasIgnored = row.isIgnored();
            ScanRowStatus previousStatus = row.status();
            int previousAlerts = ScanRowTableSupport.hasAlert(row) ? 1 : 0;
            if (row.isIgnored()) {
                row.stopIgnoring();
            } else {
                row.markIgnored();
            }
            rebuildReviewSessionFromRows();
            ScanTrace.publishIgnore(row, wasIgnored, previousStatus, previousAlerts);
            table.refresh();
            updateMetricsFromRows();
            updateFilterPredicate();
            if (selection.focused() == row) {
                showDetail(row);
            }
        });

        MenuItem reset = new MenuItem(UiText.scanContextResetMatch(currentLanguage));
        reset.setDisable(row.tmdbMatch().isEmpty());
        reset.setOnAction(event -> {
            row.setTmdbMatch(Optional.empty());
            row.setTmdbCandidate(Optional.empty());
            row.setTmdbSelectedByUser(false);
            row.setAppliedTmdbOrder(Optional.empty());
            row.setOrder(Optional.empty());
            row.setProposedFilename(Optional.empty());
            row.setDestination(Optional.empty());
            table.refresh();
            if (selection.focused() == row) {
                showDetail(row);
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

    private void showContextMenu(TableRow<ScanRow> owner, ScanRow row, double screenX, double screenY) {
        if (activeContextMenu != null) {
            activeContextMenu.hide();
        }

        ContextMenu menu = buildContextMenu(row);
        activeContextMenu = menu;
        menu.setOnHidden(event -> {
            if (activeContextMenu == menu) {
                activeContextMenu = null;
            }
        });
        menu.show(owner, screenX, screenY);
    }

    private void updateMetricsFromRows() {
        metricCards.show(ScanMetrics.from(List.copyOf(rows)));
        refreshWorkflowHelp();
        reviewStateChanged.run();
    }

    public boolean canValidatePattern() {
        rebuildReviewSessionFromRows();
        return hasReadyActiveRows() && !reviewSession.hasBlockingConflicts();
    }

    public boolean validatePattern() {
        rebuildReviewSessionFromRows();
        boolean validated = reviewSession.validatePattern();
        if (validated) {
            // Freeze the planner input at the moment of validation, so the exact
            // plan can only ever describe the state the user actually reviewed.
            validatedPlanItems = ScanPlanMapper.toPlanItems(List.copyOf(rows));
            workflowPhase = WorkflowPhase.PLAN_REVIEW;
            updateWorkflowActiveStep();
            refreshWorkflowHelp();
        } else {
            workflowHelp.setText(UiText.scanPatternBlocked(currentLanguage));
        }
        return validated;
    }

    public boolean patternValidated() {
        rebuildReviewSessionFromRows();
        return reviewSession.patternValidated();
    }

    public boolean exactPlanValidated() {
        rebuildReviewSessionFromRows();
        return reviewSession.exactPlanValidated();
    }

    public Optional<OperationPlan> operationPlan() {
        rebuildReviewSessionFromRows();
        return operationPlan;
    }

    public ReviewSession reviewSession() {
        rebuildReviewSessionFromRows();
        return reviewSession;
    }

    /**
     * Generates the exact operation plan from the frozen validated pattern
     * (Story 6.3). Reads the filesystem to detect conflicts and reusable
     * folders; creates nothing and moves nothing.
     */
    public Optional<OperationPlan> generateOperationPlan() {
        rebuildReviewSessionFromRows();
        if (!reviewSession.patternValidated() || workspaceRoot.isEmpty() || validatedPlanItems.isEmpty()) {
            return Optional.empty();
        }
        try {
            OperationPlan plan = planner.plan(workspaceRoot.orElseThrow(), validatedPlanItems);
            operationPlan = Optional.of(plan);
            reviewSession.setExactPlanExists(true);
            applyPlanDestinations(plan);
            table.refresh();
            refreshWorkflowHelp();
            return operationPlan;
        } catch (IOException exception) {
            operationPlan = Optional.empty();
            reviewSession.setExactPlanExists(false);
            workflowHelp.setText(UiText.planFailed(currentLanguage, exception.getMessage()));
            return Optional.empty();
        }
    }

    /** Closes the second gate after the user reviewed every source and destination. */
    public boolean validateExactPlan() {
        rebuildReviewSessionFromRows();
        boolean validated = reviewSession.validateExactPlan();
        if (validated) {
            workflowPhase = WorkflowPhase.APPLY;
            updateWorkflowActiveStep();
        }
        refreshWorkflowHelp();
        return validated;
    }

    /**
     * Drops the generated plan after execution so the stale destinations cannot
     * be re-approved; the user must rescan and replan.
     */
    public void discardOperationPlan() {
        operationPlan = Optional.empty();
        validatedPlanItems = List.of();
        reviewSession.setExactPlanExists(false);
        rows.forEach(row -> row.setDestination(Optional.empty()));
        table.refresh();
        refreshWorkflowHelp();
    }

    /**
     * The planner emits exactly one operation per input item, in order, so the
     * frozen input list is what maps a destination back to its row — the
     * operation's own source path is canonicalized and may not match the row's.
     */
    private void applyPlanDestinations(OperationPlan plan) {
        Map<String, ScanRow> rowsBySource = new HashMap<>();
        for (ScanRow row : rows) {
            rowsBySource.put(pathKey(row.sourcePath()), row);
            row.setDestination(Optional.empty());
        }
        if (plan.operations().size() != validatedPlanItems.size()) {
            return;
        }
        for (int index = 0; index < validatedPlanItems.size(); index++) {
            ScanRow row = rowsBySource.get(pathKey(validatedPlanItems.get(index).sourcePath()));
            if (row != null) {
                row.setDestination(plan.operations().get(index).destinationPath());
            }
        }
    }

    private static String pathKey(Path path) {
        return path.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
    }

    /**
     * Restates the safety promise and, once a folder is loaded, which validation
     * gate still blocks execution (Story 5.5). Every value comes from the review
     * session — nothing here is fabricated.
     */
    private void refreshWorkflowHelp() {
        if (!loadedFolder) {
            workflowHelp.setText(UiText.scanSafetyBanner(currentLanguage));
            return;
        }
        rebuildReviewSessionFromRows();
        workflowHelp.setText(UiText.scanExecutionBlockers(
                currentLanguage, reviewSession.executionEligibility().blockers()));
    }

    public ReviewValidationSnapshot reviewValidationSnapshot() {
        rebuildReviewSessionFromRows();
        return reviewSession.snapshot();
    }

    /**
     * Refreshes the review session from the current rows. A correction reopens
     * the gates, which makes any generated plan stale: it is dropped here rather
     * than left on screen as fabricated state.
     */
    private void rebuildReviewSessionFromRows() {
        reviewSession.replaceItems(ScanReviewMapper.toReviewItems(List.copyOf(rows)));
        if (!reviewSession.patternValidated() && !validatedPlanItems.isEmpty()) {
            validatedPlanItems = List.of();
        }
        if (!reviewSession.exactPlanExists() && operationPlan.isPresent()) {
            operationPlan = Optional.empty();
            rows.forEach(row -> row.setDestination(Optional.empty()));
        }
    }

    /**
     * Bridges the table columns back to the screen. Each callback keeps the
     * semantics the column cannot know: an edit applies to the whole selection
     * when the edited row is part of it, and every gesture ends with one
     * refresh rather than one per row.
     */
    private final class ColumnHost implements ScanTableColumns.Host {

        @Override
        public AppLanguage language() {
            return currentLanguage;
        }

        @Override
        public String groupDisplayName(ScanRow row) {
            return ScanScreen.this.groupDisplayName(row);
        }

        @Override
        public boolean groupIsIgnored(ScanRow row) {
            return ScanScreen.this.groupIsIgnored(row);
        }

        @Override
        public boolean startsGroupBlock(int viewIndex) {
            return ScanScreen.this.startsGroupBlock(viewIndex);
        }

        @Override
        public void onSelectAllToggled(boolean selected) {
            selection.setAllVisibleChecked(selected);
        }

        @Override
        public void onRowCheckboxToggled(ScanRow row, boolean selected) {
            selection.setRowChecked(row, selected);
        }

        @Override
        public void onSelectionCellPressed(ScanRow row, MouseEvent event) {
            selection.onCheckboxCellPressed(row, event);
        }

        @Override
        public void onProposedNameEdited(ScanRow row, String value) {
            String trimmed = value == null ? "" : value.trim();
            row.setProposedFilename(trimmed.isBlank() || UiText.EMPTY.equals(trimmed)
                    ? Optional.empty()
                    : Optional.of(trimmed));
            table.refresh();
            if (selection.focused() == row) {
                showDetail(row);
            }
        }

        @Override
        public void onTokenEdited(ScanRow row, ScanInputRole role, String value) {
            applyTokenEditToSelection(row, role, value);
        }

        @Override
        public void onOrderEdited(ScanRow row, String value) {
            String trimmed = value == null ? "" : value.trim();
            row.setOrder(trimmed.isBlank() || UiText.EMPTY.equals(trimmed)
                    ? Optional.empty()
                    : Optional.of(trimmed));
            ScanRowEditor.recomputeProposedName(row);
            refreshAfterBatchEdit(row);
        }

        @Override
        public void onMediaTypeChanged(ScanRow row, ScanMediaType mediaType) {
            applyMediaTypeToSelection(row, mediaType);
        }
    }

    /**
     * Jumps to the first row still needing a manual TMDB match and opens the
     * search for it. Hidden unless there is such a row — see
     * {@link #updateManualMatchNotice()}.
     */
    private void configureResolveManualMatchesButton() {
        resolveManualMatchesButton.getStyleClass().add("ghost");
        resolveManualMatchesButton.setVisible(false);
        resolveManualMatchesButton.setManaged(false);
        resolveManualMatchesButton.setOnAction(event -> firstManualMatchRow().ifPresent(row -> {
            selection.reveal(row);
            openTmdbManualMatchDialog(row);
        }));
    }

    /**
     * Translates selection events into detail-panel work. The split between
     * these four calls mirrors what each gesture actually needs to refresh.
     */
    private final class SelectionHost implements ScanSelectionController.Host {

        @Override
        public void clearDetail() {
            detailPanel.clear();
        }

        @Override
        public void showDetail(ScanRow row) {
            ScanScreen.this.showDetail(row);
        }

        @Override
        public int targetCount(ScanRow anchor) {
            return tmdbTargetRows(anchor).size();
        }

        @Override
        public void setTargetCount(int count) {
            detailPanel.setTmdbTargetCount(count);
        }

        @Override
        public void loadCandidates(ScanRow row) {
            loadTmdbCandidates(row);
        }

        @Override
        public void onSelectAllStateChanged(boolean hasSelectableRows, boolean allSelected) {
            columns.updateSelectAll(hasSelectableRows, allSelected);
        }
    }
}
