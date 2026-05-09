package com.episort.ui;

import com.episort.persistence.RunEventStore;
import com.episort.scanner.InventoryScanResult;
import com.episort.ui.history.HistoryScreen;
import com.episort.ui.scan.ScanScreen;
import com.episort.ui.settings.SettingsPane;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

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

    private final Function<Path, CompletableFuture<AppShellViewModel>> selectInputFolder;
    private final Supplier<Optional<Path>> currentWorkspace;
    private AppShellViewModel currentViewModel;
    private AppView currentView = AppView.SCAN;
    private Optional<Path> lastInputFolder = Optional.empty();
    private String scanSearchQuery = "";
    private String historySearchQuery = "";

    public AppShell() {
        this(AppShellViewModel.initial());
    }

    public AppShell(AppShellViewModel viewModel) {
        this(viewModel, null);
    }

    public AppShell(AppShellViewModel viewModel, Function<Path, AppShellViewModel> configureWorkspace) {
        this(viewModel, configureWorkspace, null, null);
    }

    public AppShell(
            AppShellViewModel viewModel,
            Function<Path, AppShellViewModel> configureWorkspace,
            java.util.function.BiFunction<String, Optional<String>, AppShellViewModel> configureTvdb) {
        this(viewModel, configureWorkspace, null, configureTvdb);
    }

    public AppShell(
            AppShellViewModel viewModel,
            Function<Path, AppShellViewModel> configureWorkspace,
            Function<Path, AppShellViewModel> selectInputFolder,
            java.util.function.BiFunction<String, Optional<String>, AppShellViewModel> configureTvdb) {
        this(viewModel, configureWorkspace, selectInputFolder, configureTvdb, () -> false, () -> {});
    }

    public AppShell(
            AppShellViewModel viewModel,
            Function<Path, AppShellViewModel> configureWorkspace,
            Function<Path, AppShellViewModel> selectInputFolder,
            java.util.function.BiFunction<String, Optional<String>, AppShellViewModel> configureTvdb,
            BooleanSupplier canContinue,
            Runnable onContinue) {
        this(viewModel, configureWorkspace, selectInputFolder, configureTvdb, Optional::empty, canContinue, onContinue);
    }

    public AppShell(
            AppShellViewModel viewModel,
            Function<Path, AppShellViewModel> configureWorkspace,
            Function<Path, CompletableFuture<AppShellViewModel>> selectInputFolder,
            java.util.function.BiFunction<String, Optional<String>, AppShellViewModel> configureTvdb,
            Supplier<Optional<Path>> currentWorkspace,
            BooleanSupplier canContinue,
            Runnable onContinue,
            boolean asyncInputSelection) {
        this(
                viewModel,
                configureWorkspace,
                selectInputFolder,
                configureTvdb,
                currentWorkspace,
                canContinue,
                onContinue,
                null);
    }

    public AppShell(
            AppShellViewModel viewModel,
            Function<Path, AppShellViewModel> configureWorkspace,
            Function<Path, AppShellViewModel> selectInputFolder,
            java.util.function.BiFunction<String, Optional<String>, AppShellViewModel> configureTvdb,
            Supplier<Optional<Path>> currentWorkspace,
            BooleanSupplier canContinue,
            Runnable onContinue) {
        this(
                viewModel,
                configureWorkspace,
                selectInputFolder == null ? null : path -> CompletableFuture.completedFuture(selectInputFolder.apply(path)),
                configureTvdb,
                currentWorkspace,
                canContinue,
                onContinue,
                null);
    }

    public AppShell(
            AppShellViewModel viewModel,
            Function<Path, AppShellViewModel> configureWorkspace,
            Function<Path, CompletableFuture<AppShellViewModel>> selectInputFolder,
            java.util.function.BiFunction<String, Optional<String>, AppShellViewModel> configureTvdb,
            Supplier<Optional<Path>> currentWorkspace,
            BooleanSupplier canContinue,
            Runnable onContinue,
            RunEventStore runEventStore) {
        Fonts.loadAll();
        this.currentViewModel = viewModel;
        this.selectInputFolder = selectInputFolder;
        this.currentWorkspace = currentWorkspace == null ? Optional::empty : currentWorkspace;
        this.runEventStore = runEventStore == null ? new InMemoryRunEventStore() : runEventStore;

        if (configureWorkspace != null) {
            settingsPane = new SettingsPane(
                    configureWorkspace,
                    this.currentWorkspace,
                    this::applyLanguage,
                    this::onSettingsClose,
                    this::apply);
        } else {
            settingsPane = null;
        }

        sidebar = new Sidebar(logoImage(), this::showView);
        topBar = new TopBar(
                this::onPrimaryAction,
                this::onSearchChange,
                this::openLoadFolderDialog,
                this::resetScanPreview,
                this::reanalyzeLastFolder);

        scanScreen = new ScanScreen();
        historyScreen = new HistoryScreen(this.runEventStore);

        settingsScroll = buildSettingsView();

        viewHost = new StackPane();
        viewHost.getStyleClass().add("view-host");

        root = new BorderPane();
        root.getStyleClass().add("app-shell");
        root.setTop(topBar.root());
        root.setLeft(sidebar.root());
        root.setCenter(viewHost);
        root.getStylesheets().add(
                java.util.Objects.requireNonNull(
                                AppShell.class.getResource("/styles/app.css"),
                                "Missing stylesheet /styles/app.css")
                        .toExternalForm());
        root.getStyleClass().add(currentViewModel.theme() == Theme.DARK ? "theme-dark" : "theme-light");
        root.widthProperty().addListener((observable, oldValue, newValue) -> applyResponsiveLayout(newValue.doubleValue()));

        applyLanguageInternal(currentViewModel.language());
        refreshShellState();
        applyResponsiveLayout(1180);
        showView(AppView.SCAN);
    }

    public Parent root() {
        return root;
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
        currentView = view;
        sidebar.setActive(view);
        Region screenRoot = switch (view) {
            case SCAN -> scanScreen.root();
            case HISTORY -> historyScreen.root();
            case SETTINGS -> settingsScroll;
        };
        viewHost.getChildren().setAll(screenRoot);

        if (view == AppView.HISTORY) {
            historyScreen.refresh();
        }
        if (view == AppView.SETTINGS && settingsPane != null) {
            settingsPane.refreshWorkspace();
        }
        topBar.setSearchVisible(view != AppView.SETTINGS);
        topBar.setSearchText(searchQueryFor(view));
        applySearchToCurrentView();
        refreshPrimaryAction();
        if (view != AppView.SCAN) {
            setTopSecondaryActionsVisible(false);
        }
    }

    private void onPrimaryAction() {
        switch (currentView) {
            case SCAN -> {
                if (!scanScreen.hasLoadedFolder()) {
                    openLoadFolderDialog();
                }
            }
            case HISTORY -> historyScreen.refresh();
            case SETTINGS -> {
                // No-op; primary action is hidden / disabled on settings.
            }
        }
    }

    private void onSearchChange(String query) {
        if (currentView == AppView.SCAN) {
            scanSearchQuery = sanitizeSearchQuery(query);
        } else if (currentView == AppView.HISTORY) {
            historySearchQuery = sanitizeSearchQuery(query);
        }
        applySearchToCurrentView(query);
    }

    private void applySearchToCurrentView() {
        applySearchToCurrentView(topBar.searchField().getText());
    }

    private void applySearchToCurrentView(String query) {
        if (currentView == AppView.SCAN) {
            scanScreen.setSearchFilter(query);
        } else if (currentView == AppView.HISTORY) {
            historyScreen.setSearchFilter(query);
        }
    }

    private String searchQueryFor(AppView view) {
        return switch (view) {
            case SCAN -> scanSearchQuery;
            case HISTORY -> historySearchQuery;
            case SETTINGS -> "";
        };
    }

    private static String sanitizeSearchQuery(String query) {
        return query == null ? "" : query;
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
        chooser.setTitle(currentViewModel.language() == AppLanguage.ENGLISH
                ? "Choose folder to load"
                : "Choisir le dossier à charger");
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
            selectInputFolder.apply(selected.toPath())
                    .thenAccept(viewModel -> Platform.runLater(() -> apply(viewModel)))
                    .exceptionally(exception -> {
                        Platform.runLater(() -> apply(new AppShellViewModel(
                                "Episort",
                                "Scan impossible",
                                "Le dossier n'a pas pu être scanné.",
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                currentViewModel.theme(),
                                currentViewModel.language())));
                        return null;
                    });
        }
    }

    private void apply(AppShellViewModel viewModel) {
        currentViewModel = AppShellViewModel.preservingTheme(currentViewModel, viewModel);
        refreshShellState();
    }

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
    }

    private void reanalyzeLastFolder() {
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
        selectInputFolder.apply(folder)
                .thenAccept(viewModel -> Platform.runLater(() -> apply(viewModel)))
                .exceptionally(exception -> {
                    Platform.runLater(() -> apply(new AppShellViewModel(
                            "Episort",
                            "Scan impossible",
                            "Le dossier n'a pas pu être scanné.",
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            currentViewModel.theme(),
                            currentViewModel.language())));
                    return null;
                });
    }

    private void refreshShellState() {
        root.getStyleClass().removeAll("theme-dark", "theme-light");
        root.getStyleClass().add(currentViewModel.theme() == Theme.DARK ? "theme-dark" : "theme-light");

        topBar.setStatusPill(currentViewModel.errorCode(), currentViewModel.language());
        topBar.setWorkspace(currentWorkspace.get());

        Optional<InventoryScanResult> result = currentViewModel.inventoryScanResult();
        scanScreen.apply(result);
        if (currentView == AppView.HISTORY) {
            historyScreen.refresh();
        }
        refreshPrimaryAction();
    }

    private void refreshPrimaryAction() {
        AppLanguage language = currentViewModel.language();
        boolean workspaceReady = currentWorkspace.get().isPresent();
        switch (currentView) {
            case SCAN -> {
                if (scanScreen.hasLoadedFolder()) {
                    topBar.setPrimaryActionText(UiText.primaryActionValidate(language));
                    topBar.setPrimaryActionDisabled(false);
                } else {
                    topBar.setPrimaryActionText(UiText.primaryActionLoad(language));
                    topBar.setPrimaryActionDisabled(!(workspaceReady && selectInputFolder != null));
                }
                topBar.primaryAction().setVisible(true);
                topBar.primaryAction().setManaged(true);
                setTopSecondaryActionsVisible(true);
                topBar.changeFolderAction().setDisable(!(scanScreen.hasLoadedFolder() && workspaceReady && selectInputFolder != null));
                topBar.resetFolderAction().setDisable(!scanScreen.hasLoadedFolder());
                topBar.rescanAction().setDisable(selectInputFolder == null || lastInputFolder.isEmpty());
            }
            case HISTORY -> {
                topBar.setPrimaryActionText(language == AppLanguage.ENGLISH ? "Refresh" : "Rafraîchir");
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
        topBar.changeFolderAction().setVisible(visible);
        topBar.changeFolderAction().setManaged(visible);
        topBar.resetFolderAction().setVisible(visible);
        topBar.resetFolderAction().setManaged(visible);
        topBar.rescanAction().setVisible(visible);
        topBar.rescanAction().setManaged(visible);
    }

    private void applyResponsiveLayout(double width) {
        boolean stackedDetail = width < STACK_BREAKPOINT;
        scanScreen.setStackedLayout(stackedDetail);
        historyScreen.setStackedLayout(stackedDetail);
    }

    private void applyLanguage(AppLanguage language) {
        currentViewModel = currentViewModel.withLanguage(language);
        applyLanguageInternal(language);
    }

    private void applyLanguageInternal(AppLanguage language) {
        sidebar.applyLanguage(language);
        topBar.applyLanguage(language);
        topBar.setStatusPill(currentViewModel.errorCode(), language);
        topBar.setWorkspace(currentWorkspace.get());
        scanScreen.applyLanguage(language);
        historyScreen.applyLanguage(language);
        if (settingsPane != null) {
            settingsPane.applyLanguage(language);
        }
        refreshPrimaryAction();
    }

    private void onSettingsClose() {
        if (settingsPane == null) {
            return;
        }
        showView(AppView.SCAN);
    }

    public static Image logoImage() {
        return new Image(java.util.Objects.requireNonNull(
                        AppShell.class.getResourceAsStream("/assets/episort-logo.png"),
                        "Missing logo /assets/episort-logo.png"));
    }

    private static final class InMemoryRunEventStore implements RunEventStore {
        private final java.util.List<com.episort.persistence.RunEvent> events = new java.util.ArrayList<>();

        @Override
        public void append(com.episort.persistence.RunEvent event) {
            events.add(event);
        }

        @Override
        public java.util.List<com.episort.persistence.RunEvent> readAll() {
            return java.util.List.copyOf(events);
        }

        @Override
        public void clear() {
            events.clear();
        }
    }
}
