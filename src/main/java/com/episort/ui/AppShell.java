package com.episort.ui;

import com.episort.persistence.ExecutionJournal;
import com.episort.persistence.FileExecutionJournal;
import com.episort.persistence.FileRollbackPlanStore;
import com.episort.persistence.RunEvent;
import com.episort.persistence.RunEventStatus;
import com.episort.persistence.RunEventStore;
import com.episort.persistence.RunEventType;
import com.episort.planning.ApprovedPlan;
import com.episort.planning.OperationPlan;
import com.episort.scanner.InventoryScanResult;
import com.episort.ui.execution.PlanReviewPane;
import com.episort.ui.history.HistoryScreen;
import com.episort.ui.platform.StageDecorations;
import com.episort.ui.platform.WindowManager;
import com.episort.ui.scan.ScanScreen;
import com.episort.ui.settings.SettingsPane;
import com.episort.workflow.ExecutionRecap;
import com.episort.workflow.ExecutionService;
import com.episort.workflow.LastPlanRollbackService;
import com.episort.workflow.PlanApprovalService;
import com.episort.workflow.TmdbGatewayStatus;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

public final class AppShell {
    private static final double STACK_BREAKPOINT = 1200;

    private final BorderPane root;
    private final Sidebar sidebar;
    private final TopBar topBar;
    private final StackPane viewHost;
    private final ScanScreen scanScreen;
    private final HistoryScreen historyScreen;
    private final ScrollPane settingsScroll;
    private final SettingsPane settingsPane;
    private final RunEventStore runEventStore;
    private final LastPlanRollbackService rollbackService =
            new LastPlanRollbackService(FileRollbackPlanStore.userProfileStore());
    private final PlanApprovalService planApproval = new PlanApprovalService();
    private ExecutionJournal executionJournal =
            FileExecutionJournal.userProfileJournal();

    private final Function<Path, CompletableFuture<AppShellViewModel>> selectInputFolder;
    private Function<List<Path>, CompletableFuture<AppShellViewModel>> selectInputSources;
    private final Supplier<Optional<Path>> currentWorkspace;
    private AppShellViewModel currentViewModel;
    private AppView currentView = AppView.SCAN;
    private Optional<Path> lastInputFolder = Optional.empty();
    private Consumer<AppLanguage> languageChangeListener = lang -> {};
    private boolean loading;
    private final PrerequisiteOverlay prereqOverlay =
            new PrerequisiteOverlay(() -> showView(AppView.SETTINGS));
    private final LoadingOverlay loadingOverlay = new LoadingOverlay(this::requestScanCancel);
    private Runnable scanCancelHandler;
    private boolean loadingCancellable;
    /**
     * Incremented on every analysis start. A run whose generation no longer
     * matches has been superseded (or cancelled) and must not touch the UI.
     */
    private long scanGeneration;
    private Region currentScreenRoot;
    /** The plan review while it occupies the content area, null the rest of the time. */
    private PlanReviewPane planReviewPane;
    /** The About screen while it occupies the content area, null the rest of the time. */
    private AboutPane aboutPane;

    /**
     * The application shell. Every collaborator is injected so the shell itself
     * stays free of startup and filesystem concerns.
     *
     * <p>Eight convenience overloads used to sit in front of this constructor,
     * none of them called and one silently dropping the flag it was handed.
     */
    public AppShell(
            AppShellViewModel viewModel,
            Function<Path, AppShellViewModel> configureWorkspace,
            Function<Path, CompletableFuture<AppShellViewModel>> selectInputFolder,
            TmdbGatewayStatus tmdbConfiguration,
            Supplier<Optional<Path>> currentWorkspace,
            BooleanSupplier canContinue,
            Runnable onContinue,
            RunEventStore runEventStore,
            Consumer<String> openExternalLink) {
        Fonts.loadAll();
        this.currentViewModel = viewModel;
        this.selectInputFolder = selectInputFolder;
        this.selectInputSources = selectInputFolder == null ? null : paths -> {
            if (paths == null || paths.isEmpty()) {
                return CompletableFuture.completedFuture(currentViewModel);
            }
            return selectInputFolder.apply(paths.getFirst());
        };
        this.currentWorkspace = currentWorkspace == null ? Optional::empty : currentWorkspace;
        this.runEventStore = runEventStore == null ? new InMemoryRunEventStore() : runEventStore;

        if (configureWorkspace != null) {
            settingsPane = new SettingsPane(
                    configureWorkspace,
                    this.currentWorkspace,
                    this::applyLanguage,
                    tmdbConfiguration,
                    openExternalLink,
                    this::onSettingsClose,
                    this::apply);
        } else {
            settingsPane = null;
        }

        sidebar = new Sidebar(logoImage(), this::showView);
        topBar = new TopBar(new TopBarActions(
                this::onPrimaryAction,
                this::openLoadFolderDialog,
                this::openLoadFilesDialog,
                this::openAddFolderDialog,
                this::openAddFilesDialog,
                this::resetScanPreview,
                this::reanalyzeLastFolder,
                this::showView,
                this::showAbout,
                this::requestQuit));

        scanScreen = new ScanScreen();
        scanScreen.setOnReviewStateChanged(this::refreshPrimaryAction);
        historyScreen = new HistoryScreen(
                this.runEventStore,
                rollbackService,
                this::onRollbackCompleted,
                this::onPlanReviewExecutingChanged);

        settingsScroll = buildSettingsView();

        viewHost = new StackPane();
        viewHost.getStyleClass().add("view-host");

        root = new BorderPane();
        root.getStyleClass().add("app-shell");
        root.setTop(topBar.root());
        root.setLeft(sidebar.root());
        root.setCenter(viewHost);
        root.getStylesheets().add(
                Objects.requireNonNull(
                                AppShell.class.getResource("/styles/app.css"),
                                "Missing stylesheet /styles/app.css")
                        .toExternalForm());
        root.getStyleClass().add(currentViewModel.theme() == Theme.DARK ? "theme-dark" : "theme-light");
        root.widthProperty().addListener((observable, oldValue, newValue) -> applyResponsiveLayout(newValue.doubleValue()));

        applyLanguageInternal(currentViewModel.language());
        refreshShellState();
        applyResponsiveLayout(1180);
        showView(AppView.SCAN);
        installCancelShortcut();
    }

    public Parent root() {
        return root;
    }

    public SettingsPane settingsPane() {
        return settingsPane;
    }

    public ScanScreen scanScreen() {
        return scanScreen;
    }

    public void setInputSourcesLoader(Function<List<Path>, CompletableFuture<AppShellViewModel>> loader) {
        this.selectInputSources = loader == null ? this.selectInputSources : loader;
    }

    public AppShellViewModel currentViewModel() {
        return currentViewModel;
    }

    public void refreshPrerequisitesGate() {
        boolean workspaceMissing = currentWorkspace.get().isEmpty();
        boolean show = workspaceMissing && currentView != AppView.SETTINGS;

        AppLanguage language = currentViewModel.language();
        prereqOverlay.show(
                show,
                workspaceMissing ? List.of(UiText.prereqMissingWorkspace(language)) : List.of(),
                language);
        if (currentScreenRoot != null) {
            currentScreenRoot.setMouseTransparent(show);
            currentScreenRoot.setOpacity(show ? 0.35 : 1.0);
        }
    }

    public AppLanguage currentLanguage() {
        return currentViewModel.language();
    }

    public void updateLoadingText(String text) {
        loadingOverlay.setMessage(text);
    }

    /**
     * Sets a determinate progress value [0..1] on the loader. Pass a negative
     * value to hide the bar and fall back to the indeterminate spinner only.
     */
    public void setLoadingProgress(double progress) {
        loadingOverlay.setProgress(progress);
    }

    public void setLoading(boolean loading, String text) {
        setLoading(loading, text, false);
    }

    /**
     * @param cancellable when true the loader offers an explicit cancel button
     *                    and reacts to the Esc key. Only the analysis pipeline
     *                    is cancellable; startup and settings work is not.
     */
    public void setLoading(boolean loading, String text, boolean cancellable) {
        this.loading = loading;
        this.loadingCancellable = loading && cancellable && scanCancelHandler != null;
        loadingOverlay.setMessage(text == null || text.isBlank()
                ? UiText.loadingStartup(currentViewModel.language())
                : text);
        loadingOverlay.setVisible(loading);
        loadingOverlay.setCancellable(loadingCancellable, currentViewModel.language());
        if (!loading) {
            setLoadingProgress(-1);
        }
        if (currentScreenRoot != null) {
            boolean blocked = loading || prereqOverlay.isVisible();
            currentScreenRoot.setMouseTransparent(blocked);
            currentScreenRoot.setOpacity(loading ? 0.45 : prereqOverlay.isVisible() ? 0.35 : 1.0);
        }
        refreshPrimaryAction();
    }

    /**
     * Registers the callback that aborts the running analysis. Set by the
     * application wiring; without it the loader stays non-cancellable.
     */
    public void setScanCancelHandler(Runnable handler) {
        this.scanCancelHandler = handler;
    }

    /**
     * Aborts the running analysis. The background pipeline only notices at its
     * next checkpoint, so the UI is released immediately and the abandoned run
     * is fenced out by {@link #scanGeneration} when it finally unwinds.
     */
    public boolean requestScanCancel() {
        if (!loading || !loadingCancellable || scanCancelHandler == null) {
            return false;
        }
        loadingCancellable = false;
        scanGeneration++;
        scanCancelHandler.run();
        scanScreen.setLoading(false);
        scanScreen.setWorkflowPhase(WorkflowPhase.CHOOSE_FOLDER, false);
        setLoading(false, "");
        return true;
    }

    private void installCancelShortcut() {
        root.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene == null) {
                return;
            }
            newScene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE && requestScanCancel()) {
                    event.consume();
                }
            });
            // Otherwise JavaFX focuses the first traversable node, which is the
            // search field: the window would open with the search box lit while
            // the step the user actually needs is loading a folder.
            Platform.runLater(topBar::focusDefault);
        });
    }

    private ScrollPane buildSettingsView() {
        Region content = settingsPane == null ? new VBox() : settingsPane.root();
        StackPane host = new StackPane(content);
        StackPane.setAlignment(content, Pos.TOP_CENTER);

        ScrollPane scroll = new ScrollPane(host);
        scroll.getStyleClass().add("content-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    private void showView(AppView view) {
        // About borrows the content area; asking for any view is asking to leave it.
        aboutPane = null;
        if (historyScreen.rollbackExecuting()) {
            sidebar.setActive(currentView);
            return;
        }
        if (historyScreen.reviewingRollback() && view != AppView.HISTORY) {
            historyScreen.closeRollbackReview();
        }
        if (planReviewPane != null) {
            // A run in flight owns the window: navigating away would leave the
            // moves happening with nothing on screen to say so.
            if (planReviewPane.executing()) {
                sidebar.setActive(currentView);
                return;
            }
            // Leaving the review is leaving it: the pane gets to close itself and
            // report what happened, then puts the requested view back up.
            currentView = view;
            planReviewPane.close();
            return;
        }
        currentView = view;
        sidebar.setActive(view);
        Region screenRoot = switch (view) {
            case SCAN -> scanScreen.root();
            case HISTORY -> historyScreen.root();
            case SETTINGS -> settingsScroll;
        };
        currentScreenRoot = screenRoot;
        viewHost.getChildren().setAll(screenRoot, prereqOverlay.root(), loadingOverlay.root());
        refreshPrerequisitesGate();

        if (view == AppView.HISTORY) {
            historyScreen.refresh();
        }
        if (view == AppView.SETTINGS && settingsPane != null) {
            settingsPane.refreshWorkspace();
        }
        topBar.setActiveView(view);
        topBar.setAboutEnabled(true);
        refreshPrimaryAction();
        if (view != AppView.SCAN) {
            setTopSecondaryActionsVisible(false);
        }
    }

    /**
     * The primary action takes the user straight to the plan: the pattern gate
     * closes on the way there rather than costing a click of its own, since
     * reviewing the plan is what validating the pattern was ever for. Both gates
     * are still crossed explicitly, and nothing touches the disk before the user
     * validates inside the plan window.
     */
    private void onPrimaryAction() {
        switch (currentView) {
            case SCAN -> {
                if (!scanScreen.hasLoadedFolder()) {
                    openLoadFolderDialog();
                } else {
                    openPlanReview();
                }
            }
            case HISTORY -> historyScreen.refresh();
            case SETTINGS -> {
                // No-op; primary action is hidden / disabled on settings.
            }
        }
    }

    /**
     * Closes the pattern gate if it is still open, generates the exact plan, and
     * shows it in the content area — no second window. Reviewing, resolving the
     * conflicts, validating, and running all happen there, in the same window the
     * user was already looking at; nothing is touched before the validate click.
     */
    private void openPlanReview() {
        // Re-validated on every open, not only while the gate is still shut: it
        // refreezes the planner input on what is on screen right now, which is
        // what the user is about to review.
        if (!scanScreen.validatePattern()) {
            // The pattern gate refused; the workflow banner already says why.
            refreshPrimaryAction();
            return;
        }
        Optional<OperationPlan> plan = scanScreen.generateOperationPlan();
        if (plan.isEmpty()) {
            refreshPrimaryAction();
            return;
        }
        planReviewPane = new PlanReviewPane(
                currentViewModel.language(),
                plan.orElseThrow(),
                // Not the plan captured above: the pane resolves conflicts in
                // place, and that resolved plan is what must be approved.
                this::approveForExecution,
                new ExecutionService(executionJournal),
                this::onPlanReviewClosed,
                this::onPlanReviewExecutingChanged);
        currentScreenRoot = planReviewPane.root();
        viewHost.getChildren().setAll(currentScreenRoot, prereqOverlay.root(), loadingOverlay.root());
        // The plan is on screen: the top bar must not offer to build another one,
        // nor to swap the plan for the About screen.
        topBar.setPrimaryActionDisabled(true);
        topBar.setAboutEnabled(false);
        setTopSecondaryActionsVisible(false);
    }

    /**
     * Back from the review: the pane leaves the content area and the screen the
     * user came from returns, with whatever the run did folded into the shell.
     */
    private void onPlanReviewClosed(Optional<ExecutionRecap> recap) {
        planReviewPane = null;
        showView(currentView);
        if (recap.isEmpty() && !scanScreen.exactPlanValidated()) {
            // Reviewed and dismissed without validating: nothing ran, nothing to clean up.
            refreshPrimaryAction();
            return;
        }
        recap.ifPresent(this::recordExecutionRun);
        // The plan describes a filesystem that no longer exists: force a replan.
        scanScreen.discardOperationPlan();
        boolean anythingChanged = recap
                .map(result -> !result.moved().isEmpty()
                        || !result.renamed().isEmpty()
                        || !result.deleted().isEmpty())
                .orElse(false);
        if (anythingChanged) {
            // The preview rows now point at files that have moved or are gone.
            // Clearing it is the only honest option: a stale preview reads as
            // current state.
            resetScanPreview();
            sidebar.refreshWorkspace();
        }
        if (currentView == AppView.HISTORY) {
            historyScreen.refresh();
        }
        refreshPrimaryAction();
    }

    /**
     * Locks the shell for the duration of the run: while files are moving, the
     * only thing on screen that answers is the progress view itself.
     */
    private void onPlanReviewExecutingChanged(boolean running) {
        sidebar.root().setDisable(running);
        topBar.setChromeDisabled(running);
    }

    /**
     * Closes the exact-plan gate and locks the plan, from inside the review
     * pane. Empty means a gate reopened between the click and now — the run must
     * not start, and the review stays on screen.
     */
    private Optional<ApprovedPlan> approveForExecution(
            OperationPlan plan) {
        if (!scanScreen.validateExactPlan()) {
            return Optional.empty();
        }
        try {
            return Optional.of(planApproval.approve(scanScreen.reviewSession(), plan));
        } catch (IllegalStateException blocked) {
            return Optional.empty();
        }
    }

    private void recordExecutionRun(ExecutionRecap recap) {
        // A run that only removed duplicates still did what it was asked to do:
        // deletions count as work, or the history would read it as a failure.
        int processed = recap.moved().size() + recap.renamed().size() + recap.deleted().size();
        RunEventStatus status;
        if (recap.aborted()) {
            status = RunEventStatus.WARNING;
        } else if (recap.completeSuccess()) {
            status = RunEventStatus.SUCCESS;
        } else if (recap.partialSuccess()) {
            status = RunEventStatus.WARNING;
        } else {
            status = RunEventStatus.FAILED;
        }
        Map<String, String> metrics = new LinkedHashMap<>();
        metrics.put("moved", String.valueOf(recap.moved().size()));
        metrics.put("renamed", String.valueOf(recap.renamed().size()));
        metrics.put("deleted", String.valueOf(recap.deleted().size()));
        metrics.put("foldersTagged", String.valueOf(recap.renamedSourceFolders().size()));
        metrics.put("failed", String.valueOf(recap.failed().size()));
        metrics.put("skipped", String.valueOf(recap.skipped().size()));
        metrics.put("untouched", String.valueOf(recap.untouched().size()));
        metrics.put("runId", recap.report().runId().toString());
        rollbackService.record(recap.report());
        runEventStore.append(RunEvent.of(
                processed > 0
                        ? RunEventType.EXECUTION_COMPLETED
                        : RunEventType.EXECUTION_FAILED,
                status,
                currentWorkspace.get(),
                Optional.empty(),
                UiText.execRecapTitle(currentViewModel.language()),
                metrics));
    }

    private void onRollbackCompleted() {
        lastInputFolder = Optional.empty();
        scanScreen.discardOperationPlan();
        apply(new AppShellViewModel(
                currentViewModel.title(),
                currentViewModel.primaryStatus(),
                currentViewModel.description(),
                Optional.empty(),
                currentViewModel.errorCode(),
                Optional.empty(),
                currentViewModel.theme(),
                currentViewModel.language()));
        sidebar.refreshWorkspace();
        historyScreen.refresh();
    }

    /**
     * Warns once at startup when a previous run never reached a terminal state,
     * and points at the diagnostic journal (Story 7.4).
     */
    public void reportInterruptedExecution() {
        executionJournal.interruptedRun().ifPresent(entry -> {
            AppLanguage language = currentViewModel.language();
            String message = UiText.execInterruptedMessage(language)
                    + " " + UiText.execInterruptedProgress(language)
                    + " " + entry.completedOperations() + " / " + entry.totalOperations()
                    + ". " + UiText.execRecapDiagnostics(language) + ": " + executionJournal.location();
            apply(new AppShellViewModel(
                    currentViewModel.title(),
                    UiText.execInterruptedTitle(language),
                    message,
                    currentViewModel.errorCode(),
                    currentViewModel.errorDetails(),
                    currentViewModel.inventoryScanResult(),
                    currentViewModel.theme(),
                    language));
        });
    }

    public void setExecutionJournal(ExecutionJournal journal) {
        this.executionJournal = journal == null ? this.executionJournal : journal;
    }

    private void openLoadFolderDialog() {
        if (selectInputFolder == null) {
            return;
        }
        Optional<Path> workspace = currentWorkspace.get();
        if (workspace.isEmpty()) {
            return;
        }
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(UiText.chooserLoadFolderTitle(currentViewModel.language()));
        File initial = workspace.get().toFile();
        if (initial.isDirectory()) {
            chooser.setInitialDirectory(initial);
        }
        Window owner = root.getScene() == null ? null : root.getScene().getWindow();
        File selected = chooser.showDialog(owner);
        if (selected != null) {
            lastInputFolder = Optional.of(selected.toPath().toAbsolutePath().normalize());
            apply(new AppShellViewModel(
                    "Episort",
                    UiText.scanRowStatusPreview(currentViewModel.language()),
                    selected.toPath().toAbsolutePath().normalize().toString(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    currentViewModel.theme(),
                    currentViewModel.language()));
            beginScan();
            trackScan(
                    selectInputFolder.apply(selected.toPath()),
                    false,
                    UiText.scanFailedFolder(currentViewModel.language()));
        }
    }

    private void openLoadFilesDialog() {
        openFilesDialog(false);
    }

    private void openAddFolderDialog() {
        openFolderDialog(true);
    }

    private void openAddFilesDialog() {
        openFilesDialog(true);
    }

    private void openFilesDialog(boolean append) {
        if (selectInputSources == null || currentWorkspace.get().isEmpty()) {
            return;
        }
        AppLanguage language = currentViewModel.language();
        FileChooser chooser = new FileChooser();
        chooser.setTitle(UiText.chooserLoadFilesTitle(language));
        File initial = currentWorkspace.get().orElseThrow().toFile();
        if (initial.isDirectory()) {
            chooser.setInitialDirectory(initial);
        }
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                UiText.chooserVideoFiles(language), "*.avi", "*.mp4", "*.mkv"));
        Window owner = root.getScene() == null ? null : root.getScene().getWindow();
        List<File> selected = chooser.showOpenMultipleDialog(owner);
        if (selected == null || selected.isEmpty()) {
            return;
        }
        List<Path> paths = selected.stream()
                .map(file -> file.toPath().toAbsolutePath().normalize())
                .toList();
        if (!append) {
            lastInputFolder = Optional.empty();
        }
        beginScan();
        trackScan(selectInputSources.apply(paths), append, UiText.scanFailedFiles(language));
    }

    private void openFolderDialog(boolean append) {
        if (selectInputFolder == null) {
            return;
        }
        Optional<Path> workspace = currentWorkspace.get();
        if (workspace.isEmpty()) {
            return;
        }
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(UiText.chooserAddFolderTitle(currentViewModel.language()));
        File initial = workspace.get().toFile();
        if (initial.isDirectory()) {
            chooser.setInitialDirectory(initial);
        }
        Window owner = root.getScene() == null ? null : root.getScene().getWindow();
        File selected = chooser.showDialog(owner);
        if (selected == null) {
            return;
        }
        beginScan();
        trackScan(selectInputFolder.apply(selected.toPath()), append, null);
    }

    /**
     * Marks the start of an analysis: any run still in flight is fenced out and
     * the loader comes up in its cancellable form.
     */
    private void beginScan() {
        scanGeneration++;
        scanScreen.setLoading(true);
        setLoading(true, UiText.loadingScan(currentViewModel.language()), true);
    }

    /**
     * Wires an analysis future back into the shell. Every callback first checks
     * that its run is still the current one: a cancelled or superseded run must
     * not apply its (stale or partial) view model, nor tear down the loader of
     * the scan that replaced it.
     *
     * @param failureDescription description shown when the run fails, or {@code null} to stay silent.
     */
    private void trackScan(
            CompletableFuture<AppShellViewModel> future, boolean append, String failureDescription) {
        final long generation = scanGeneration;
        future.thenAccept(viewModel -> Platform.runLater(() -> {
                    if (generation != scanGeneration) {
                        return;
                    }
                    if (append) {
                        scanScreen.append(viewModel.inventoryScanResult());
                        topBar.setAppendActionsEnabled(scanScreen.hasLoadedFolder());
                    } else {
                        apply(viewModel);
                    }
                    setLoading(false, "");
                }))
                .whenComplete((ignored, exception) -> Platform.runLater(() -> {
                    if (generation != scanGeneration) {
                        return;
                    }
                    scanScreen.setLoading(false);
                    setLoading(false, "");
                }))
                .exceptionally(exception -> {
                    if (failureDescription == null) {
                        return null;
                    }
                    Platform.runLater(() -> {
                        if (generation != scanGeneration) {
                            return;
                        }
                        apply(new AppShellViewModel(
                                "Episort",
                                UiText.scanFailedTitle(currentViewModel.language()),
                                failureDescription,
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                currentViewModel.theme(),
                                currentViewModel.language()));
                    });
                    return null;
                });
    }

    private void apply(AppShellViewModel viewModel) {
        currentViewModel = AppShellViewModel.preservingTheme(currentViewModel, viewModel);
        refreshShellState();
    }

    /**
     * Returns the shell to the start state: no loaded folder, no preview, no
     * search, and the scan view in front. Called both by the reset action and
     * after a run applied its plan.
     */
    private void resetScanPreview() {
        lastInputFolder = Optional.empty();
        apply(new AppShellViewModel(
                currentViewModel.title(),
                currentViewModel.primaryStatus(),
                currentViewModel.description(),
                Optional.empty(),
                currentViewModel.errorCode(),
                Optional.empty(),
                currentViewModel.theme(),
                currentViewModel.language()));
        if (currentView != AppView.SCAN) {
            showView(AppView.SCAN);
        }
    }

    public void reanalyzeLastFolder() {
        if (selectInputFolder == null || lastInputFolder.isEmpty()) {
            return;
        }
        Path folder = lastInputFolder.orElseThrow();
        apply(new AppShellViewModel(
                "Episort",
                UiText.scanRowStatusPreview(currentViewModel.language()),
                folder.toString(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                currentViewModel.theme(),
                currentViewModel.language()));
        beginScan();
        trackScan(
                selectInputFolder.apply(folder),
                false,
                UiText.scanFailedFolder(currentViewModel.language()));
    }

    private void refreshShellState() {
        root.getStyleClass().removeAll("theme-dark", "theme-light");
        root.getStyleClass().add(currentViewModel.theme() == Theme.DARK ? "theme-dark" : "theme-light");

        topBar.setStatusPill(currentViewModel.errorCode(), currentViewModel.language());
        topBar.setWorkspace(currentWorkspace.get());
        sidebar.setWorkspace(currentWorkspace.get());

        scanScreen.setWorkspaceRoot(currentWorkspace.get());
        Optional<InventoryScanResult> result = currentViewModel.inventoryScanResult();
        scanScreen.apply(result);
        topBar.setAppendActionsEnabled(scanScreen.hasLoadedFolder());
        if (currentView == AppView.HISTORY) {
            historyScreen.refresh();
        }
        refreshPrimaryAction();
        refreshPrerequisitesGate();
    }

    private void refreshPrimaryAction() {
        if (aboutPane != null) {
            // The About screen answers to nothing in the bar; refreshing the bar
            // for the view hidden underneath would put its primary action back.
            return;
        }
        AppLanguage language = currentViewModel.language();
        boolean workspaceReady = currentWorkspace.get().isPresent();
        switch (currentView) {
            case SCAN -> {
                // One label all the way through: the plan window is the only
                // destination, and it is where validating and running happen.
                topBar.setPrimaryActionText(UiText.primaryActionReviewPlan(language));
                topBar.setPrimaryActionDisabled(loading
                        || !scanScreen.hasLoadedFolder()
                        || !(scanScreen.patternValidated() || scanScreen.canValidatePattern()));
                topBar.primaryAction().setVisible(true);
                topBar.primaryAction().setManaged(true);
                setTopSecondaryActionsVisible(true);
                topBar.setLoadActionEnabled(!loading && workspaceReady && selectInputFolder != null);
                topBar.setAppendActionsEnabled(scanScreen.hasLoadedFolder());
                topBar.setResetActionEnabled(!loading && scanScreen.hasLoadedFolder());
                topBar.setRescanActionEnabled(!loading && selectInputFolder != null && lastInputFolder.isPresent());
            }
            case HISTORY -> {
                topBar.setPrimaryActionText(UiText.topActionRefresh(language));
                topBar.setPrimaryActionDisabled(false);
                topBar.primaryAction().setVisible(true);
                topBar.primaryAction().setManaged(true);
            }
            case SETTINGS -> {
                topBar.primaryAction().setVisible(false);
                topBar.primaryAction().setManaged(false);
            }
        }
    }

    private void setTopSecondaryActionsVisible(boolean visible) {
        topBar.setScanActionsVisible(visible);
    }

    private void applyResponsiveLayout(double width) {
        boolean stackedDetail = width < STACK_BREAKPOINT;
        scanScreen.setStackedLayout(stackedDetail);
        historyScreen.setStackedLayout(stackedDetail);
    }

    private void applyLanguage(AppLanguage language) {
        currentViewModel = currentViewModel.withLanguage(language);
        applyLanguageInternal(language);
        languageChangeListener.accept(language);
    }

    public void setLanguageChangeListener(Consumer<AppLanguage> listener) {
        this.languageChangeListener = listener == null ? lang -> {} : listener;
    }

    private void applyLanguageInternal(AppLanguage language) {
        sidebar.applyLanguage(language);
        topBar.applyLanguage(language);
        topBar.setStatusPill(currentViewModel.errorCode(), language);
        topBar.setWorkspace(currentWorkspace.get());
        sidebar.setWorkspace(currentWorkspace.get());
        scanScreen.applyLanguage(language);
        historyScreen.applyLanguage(language);
        if (settingsPane != null) {
            settingsPane.applyLanguage(language);
        }
        loadingOverlay.setCancellable(loadingCancellable, currentViewModel.language());
        if (aboutPane != null) {
            // Built once from the language it was opened in; switching languages
            // under it would leave half a screen in the old one.
            showAbout();
        }
        refreshPrimaryAction();
        refreshPrerequisitesGate();
    }

    private void onSettingsClose() {
        if (settingsPane == null) {
            return;
        }
        showView(AppView.SCAN);
    }

    /**
     * Hands the window buttons their stage and re-implements what an
     * undecorated stage no longer gets from Windows: drag, double-click
     * maximize, and edge resizing. Call it once the scene is set.
     */
    public WindowManager installWindowDecorations(Stage stage) {
        Objects.requireNonNull(stage, "stage");
        WindowManager windowManager = StageDecorations.install(stage, topBar.root());
        topBar.windowControls().attach(stage, windowManager);
        return windowManager;
    }

    private void requestQuit() {
        Window window = root.getScene() == null ? null : root.getScene().getWindow();
        if (window instanceof Stage stage) {
            // Through the close request, so anything listening for it still runs.
            stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
            stage.close();
        } else {
            Platform.exit();
        }
    }

    /**
     * Shows About in the content area, like every other full-frame surface. It
     * used to be a native {@code Alert}: a Windows dialog with a system icon, in
     * an application whose own window has no Windows chrome left.
     *
     * <p>Refused while the plan review holds the content area — the menu entry is
     * disabled there, and this is the second lock on the same door.
     */
    private void showAbout() {
        if (planReviewPane != null) {
            return;
        }
        aboutPane = new AboutPane(currentViewModel.language(), () -> showView(currentView));
        currentScreenRoot = aboutPane.root();
        viewHost.getChildren().setAll(currentScreenRoot, prereqOverlay.root(), loadingOverlay.root());
        // Reading what the application is does not require a configured workspace:
        // the missing-workspace gate guards the work, not the documentation.
        prereqOverlay.show(false, List.of(), currentViewModel.language());
        // About is not a step of the run: nothing in the bar acts on what is on
        // screen, so nothing in the bar stays offering to.
        topBar.primaryAction().setVisible(false);
        topBar.primaryAction().setManaged(false);
        setTopSecondaryActionsVisible(false);
        topBar.setAboutEnabled(false);
    }

    public static Image logoImage() {
        return new Image(Objects.requireNonNull(
                        AppShell.class.getResourceAsStream("/assets/episort-logo.png"),
                        "Missing logo /assets/episort-logo.png"));
    }

    private static final class InMemoryRunEventStore implements RunEventStore {
        private final List<RunEvent> events = new ArrayList<>();

        @Override
        public void append(RunEvent event) {
            events.add(event);
        }

        @Override
        public List<RunEvent> readAll() {
            return List.copyOf(events);
        }

        @Override
        public void clear() {
            events.clear();
        }
    }
}
