package com.episort.ui;

import com.episort.scanner.InventoryItem;
import com.episort.scanner.InventoryScanResult;
import com.episort.scanner.InventorySummary;
import com.episort.ui.settings.SettingsPane;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.Scene;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

public final class AppShell {
    private final BorderPane root;
    private final StackPane viewHost;
    private final Parent mainView;
    private final Parent settingsView;
    private final Label brandTitle;
    private final Label mainStatus;
    private final Label mainDescription;
    private final VBox scanResultPanel;
    private final Label scannedFolderValue;
    private final Label supportedValue;
    private final Label ignoredValue;
    private final Label seriesValue;
    private final Label moviesValue;
    private final Label unknownValue;
    private final ListView<String> sourceFilesList;
    private final ListView<String> proposedNamesList;
    private Pane renameWorkbench;
    private VBox sourceColumn;
    private VBox actionColumn;
    private VBox resultColumn;
    private final Button loadFolderButton;
    private final Button continueButton;
    private final Button settingsButton;
    private final Button analyzeButton;
    private final Button renameButton;
    private final Button resetButton;
    private final SettingsPane settingsPane;
    private Stage settingsDialog;
    private final Function<Path, CompletableFuture<AppShellViewModel>> selectInputFolder;
    private final Supplier<Optional<Path>> currentWorkspace;
    private AppShellViewModel currentViewModel;

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
        this(viewModel, configureWorkspace, selectInputFolder, configureTvdb, currentWorkspace, canContinue, onContinue, true, 0);
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
                true,
                0);
    }

    private AppShell(
            AppShellViewModel viewModel,
            Function<Path, AppShellViewModel> configureWorkspace,
            Function<Path, CompletableFuture<AppShellViewModel>> selectInputFolder,
            java.util.function.BiFunction<String, Optional<String>, AppShellViewModel> configureTvdb,
            Supplier<Optional<Path>> currentWorkspace,
            BooleanSupplier canContinue,
            Runnable onContinue,
            boolean asyncCore,
            int coreMarker) {
        Fonts.loadAll();
        this.currentViewModel = viewModel;
        this.selectInputFolder = selectInputFolder;
        this.currentWorkspace = currentWorkspace == null ? Optional::empty : currentWorkspace;

        ImageView logo = new ImageView(logoImage());
        logo.setFitWidth(96);
        logo.setFitHeight(96);
        logo.setPreserveRatio(true);

        brandTitle = new Label("Episort");
        brandTitle.getStyleClass().add("brand-title");

        mainStatus = new Label();
        mainStatus.setWrapText(true);
        mainStatus.getStyleClass().add("app-status");

        mainDescription = new Label();
        mainDescription.setWrapText(true);
        mainDescription.getStyleClass().add("app-description");

        scannedFolderValue = new Label("\u2014");
        supportedValue = new Label("0");
        ignoredValue = new Label("0");
        seriesValue = new Label("0");
        moviesValue = new Label("0");
        unknownValue = new Label("0");
        sourceFilesList = new ListView<>();
        proposedNamesList = new ListView<>();
        configureFileList(sourceFilesList);
        configureFileList(proposedNamesList);

        loadFolderButton = new Button();
        loadFolderButton.getStyleClass().add("primary");
        loadFolderButton.setOnAction(event -> openLoadFolderDialog());

        continueButton = new Button();
        continueButton.getStyleClass().add("ghost");
        continueButton.setDisable(true);

        settingsButton = new Button();
        settingsButton.getStyleClass().add("ghost");
        settingsButton.setOnAction(event -> showSettingsDialog());

        analyzeButton = new Button("Analyser");
        analyzeButton.getStyleClass().add("primary");
        analyzeButton.setDisable(true);
        analyzeButton.setMinWidth(128);

        renameButton = new Button("Renommer");
        renameButton.getStyleClass().add("ghost");
        renameButton.setDisable(true);
        renameButton.setMinWidth(128);

        resetButton = new Button("R\u00e9initialiser");
        resetButton.getStyleClass().add("ghost");
        resetButton.setDisable(true);
        resetButton.setMinWidth(128);
        resetButton.setOnAction(event -> resetScanSelections());

        scanResultPanel = buildScanResultPanel();

        if (configureWorkspace != null) {
            settingsPane = new SettingsPane(
                    configureWorkspace,
                    this.currentWorkspace,
                    this::applyLanguage,
                    this::closeSettingsDialog,
                    this::apply);
        } else {
            settingsPane = null;
        }

        mainView = buildMainView(logo);
        settingsView = settingsPane == null ? new VBox() : buildSettingsView();

        viewHost = new StackPane();
        viewHost.getStyleClass().add("view-host");

        root = new BorderPane(viewHost);
        root.getStyleClass().add("app-shell");
        root.getStylesheets().add(
                java.util.Objects.requireNonNull(
                                AppShell.class.getResource("/styles/app.css"),
                                "Missing stylesheet /styles/app.css")
                        .toExternalForm());
        root.getStyleClass().add(currentViewModel.theme() == Theme.DARK ? "theme-dark" : "theme-light");
        root.widthProperty().addListener((observable, oldValue, newValue) -> refreshWorkbenchLayout(newValue.doubleValue()));

        refreshLocalizedText();
        refreshShellText();
        refreshLoadFolderState();
        refreshScanResult();
        refreshWorkbenchLayout(960);

        showMain();
    }

    public Parent root() {
        return root;
    }

    private Parent buildMainView(ImageView logo) {
        logo.setFitWidth(56);
        logo.setFitHeight(56);
        HBox brandRow = new HBox(14, logo, brandTitle);
        brandRow.setAlignment(Pos.CENTER_LEFT);
        brandRow.setMinWidth(190);
        brandTitle.setMinWidth(Region.USE_PREF_SIZE);

        VBox statusBlock = new VBox(4, mainStatus, mainDescription);
        statusBlock.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(statusBlock, Priority.ALWAYS);

        HBox actions = new HBox(12, loadFolderButton, continueButton, settingsButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setMinWidth(390);
        loadFolderButton.setMinWidth(150);
        continueButton.setMinWidth(104);
        settingsButton.setMinWidth(118);

        HBox header = new HBox(16, brandRow, statusBlock, actions);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("rename-header");

        VBox content = new VBox(10, scanResultPanel);
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(10, 20, 18, 20));
        content.getStyleClass().add("main-view");
        VBox.setVgrow(scanResultPanel, Priority.ALWAYS);

        ScrollPane scroll = new ScrollPane(content);
        scroll.getStyleClass().add("content-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        BorderPane layout = new BorderPane(scroll);
        layout.setTop(header);
        return layout;
    }

    private Parent buildLegacyMainView(ImageView logo) {
        HBox brandRow = new HBox(14, logo, brandTitle);
        brandRow.setAlignment(Pos.CENTER_LEFT);

        VBox statusBlock = new VBox(4, mainStatus, mainDescription);
        statusBlock.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(statusBlock, Priority.ALWAYS);

        HBox actions = new HBox(12, loadFolderButton, continueButton, settingsButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        HBox header = new HBox(16, brandRow, statusBlock, actions);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox column = new VBox(14, header, scanResultPanel);
        column.setAlignment(Pos.TOP_CENTER);
        column.setPadding(new Insets(18, 20, 18, 20));
        column.getStyleClass().add("main-view");
        VBox.setVgrow(scanResultPanel, Priority.ALWAYS);

        ScrollPane scroll = new ScrollPane(column);
        scroll.getStyleClass().add("content-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    private VBox buildScanResultPanel() {
        Label heading = new Label("// SCAN");
        heading.getStyleClass().addAll("section-heading", "section-heading-accent");

        VBox folderWidget = widget("Dossier", scannedFolderValue);
        HBox metrics = new HBox(
                8,
                metric("Vid\u00e9os", supportedValue),
                metric("Ignor\u00e9s", ignoredValue),
                metric("S\u00e9ries", seriesValue),
                metric("Films", moviesValue),
                metric("Unknown", unknownValue));
        metrics.setAlignment(Pos.CENTER_LEFT);

        sourceFilesList.getStyleClass().add("activity-table");
        sourceFilesList.setPlaceholder(new Label("Aucun fichier scann\u00e9."));
        sourceFilesList.setFixedCellSize(28);
        sourceFilesList.setPrefHeight(520);
        sourceFilesList.setMinHeight(320);
        sourceFilesList.setMaxHeight(Double.MAX_VALUE);

        proposedNamesList.getStyleClass().add("activity-table");
        proposedNamesList.setPlaceholder(new Label("Lancez l\u2019analyse IA pour g\u00e9n\u00e9rer les nouveaux noms."));
        proposedNamesList.setFixedCellSize(28);
        proposedNamesList.setPrefHeight(520);
        proposedNamesList.setMinHeight(320);
        proposedNamesList.setMaxHeight(Double.MAX_VALUE);

        sourceColumn = new VBox(8, widgetTitle("Fichiers scann\u00e9s"), sourceFilesList);
        sourceColumn.getStyleClass().add("widget");
        sourceColumn.setMinWidth(280);
        sourceColumn.setPrefWidth(0);
        sourceColumn.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(sourceFilesList, Priority.ALWAYS);

        actionColumn = new VBox(10, analyzeButton, renameButton, resetButton);
        actionColumn.setAlignment(Pos.CENTER);
        actionColumn.getStyleClass().add("rename-action-column");
        actionColumn.setMinWidth(144);
        actionColumn.setPrefWidth(144);
        actionColumn.setMaxWidth(144);

        resultColumn = new VBox(8, widgetTitle("Fichiers renomm\u00e9s"), proposedNamesList);
        resultColumn.getStyleClass().add("widget");
        resultColumn.setMinWidth(280);
        resultColumn.setPrefWidth(0);
        resultColumn.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(proposedNamesList, Priority.ALWAYS);

        HBox comparison = new HBox(12, sourceColumn, actionColumn, resultColumn);
        comparison.setAlignment(Pos.TOP_CENTER);
        HBox.setHgrow(sourceColumn, Priority.ALWAYS);
        HBox.setHgrow(resultColumn, Priority.ALWAYS);
        renameWorkbench = comparison;

        VBox panel = new VBox(10, heading, folderWidget, metrics, renameWorkbench);
        panel.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(renameWorkbench, Priority.ALWAYS);
        return panel;
    }

    private VBox metric(String title, Label value) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("card-title");
        value.getStyleClass().add("card-value-mono");
        VBox card = new VBox(4, titleLabel, value);
        card.getStyleClass().add("card");
        card.setMinWidth(88);
        card.setPrefWidth(112);
        return card;
    }

    private VBox widget(String title, Label value) {
        value.getStyleClass().add("widget-line");
        value.setWrapText(true);
        VBox widget = new VBox(6, widgetTitle(title), value);
        widget.getStyleClass().add("widget");
        return widget;
    }

    private Label widgetTitle(String title) {
        Label label = new Label(title);
        label.getStyleClass().add("widget-title");
        return label;
    }

    private Parent buildSettingsView() {
        VBox content = new VBox(settingsPane.root());
        content.setPadding(new Insets(28, 32, 28, 32));
        content.setMaxWidth(720);
        content.getStyleClass().add("settings-view");

        StackPane centered = new StackPane(content);
        StackPane.setAlignment(content, Pos.TOP_CENTER);

        ScrollPane scroll = new ScrollPane(centered);
        scroll.getStyleClass().add("content-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    private void showMain() {
        viewHost.getChildren().setAll(mainView);
    }

    private void showSettings() {
        if (settingsPane == null) {
            return;
        }
        settingsPane.refreshWorkspace();
        viewHost.getChildren().setAll(settingsView);
    }

    private void showSettingsDialog() {
        if (settingsPane == null) {
            return;
        }
        settingsPane.refreshWorkspace();
        if (settingsDialog == null) {
            settingsDialog = new Stage(StageStyle.TRANSPARENT);
            settingsDialog.initModality(Modality.WINDOW_MODAL);
            Window owner = root.getScene() == null ? null : root.getScene().getWindow();
            if (owner != null) {
                settingsDialog.initOwner(owner);
            }
            settingsDialog.setTitle("Param\u00e8tres");
            BorderPane modalRoot = new BorderPane(settingsPane.root());
            modalRoot.getStyleClass().addAll("app-shell", "theme-dark", "settings-dialog");
            modalRoot.getStylesheets().addAll(root.getStylesheets());
            Scene scene = new Scene(modalRoot, 760, 460);
            scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
            settingsDialog.setScene(scene);
            settingsDialog.setMinWidth(720);
            settingsDialog.setMinHeight(420);
            settingsDialog.setResizable(false);
        }
        settingsDialog.show();
        settingsDialog.toFront();
    }

    private void closeSettingsDialog() {
        Window window = settingsPane.root().getScene() == null ? null : settingsPane.root().getScene().getWindow();
        if (window != null) {
            window.hide();
        }
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
        chooser.setTitle("Choose folder to load");
        File initial = workspace.get().toFile();
        if (initial.isDirectory()) {
            chooser.setInitialDirectory(initial);
        }
        Window owner = loadFolderButton.getScene() == null ? null : loadFolderButton.getScene().getWindow();
        File selected = chooser.showDialog(owner);
        if (selected != null) {
            apply(new AppShellViewModel(
                    "Episort",
                    "Scan en cours",
                    "Lecture du dossier : " + selected.toPath().toAbsolutePath().normalize(),
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
                                "Le dossier n'a pas pu \u00eatre scann\u00e9.",
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
        refreshShellText();
        refreshLoadFolderState();
        refreshScanResult();
    }

    private void refreshShellText() {
        mainStatus.setText(currentViewModel.primaryStatus());
        mainDescription.setText(currentViewModel.description());
        root.getStyleClass().removeAll("theme-dark", "theme-light");
        root.getStyleClass().add(currentViewModel.theme() == Theme.DARK ? "theme-dark" : "theme-light");
    }

    private void refreshLoadFolderState() {
        boolean enabled = selectInputFolder != null && currentWorkspace.get().isPresent();
        loadFolderButton.setDisable(!enabled);
    }

    private void resetScanSelections() {
        sourceFilesList.getSelectionModel().clearSelection();
        proposedNamesList.getSelectionModel().clearSelection();
        sourceFilesList.getItems().clear();
        proposedNamesList.getItems().clear();
        resetScanMetrics();
        analyzeButton.setDisable(true);
        renameButton.setDisable(true);
        resetButton.setDisable(true);
        currentViewModel = new AppShellViewModel(
                currentViewModel.title(),
                currentViewModel.primaryStatus(),
                currentViewModel.description(),
                currentViewModel.errorCode(),
                currentViewModel.errorDetails(),
                Optional.empty(),
                currentViewModel.theme(),
                currentViewModel.language());
    }

    private void refreshScanResult() {
        Optional<InventoryScanResult> scan = currentViewModel.inventoryScanResult();
        if (scan.isEmpty()) {
            resetScanMetrics();
            sourceFilesList.getSelectionModel().clearSelection();
            proposedNamesList.getSelectionModel().clearSelection();
            sourceFilesList.getItems().clear();
            proposedNamesList.getItems().clear();
            analyzeButton.setDisable(true);
            renameButton.setDisable(true);
            resetButton.setDisable(true);
            return;
        }
        InventoryScanResult result = scan.orElseThrow();
        InventorySummary summary = result.summary();
        scannedFolderValue.setText(currentViewModel.description().replace("Dossier scann\u00e9 :", "").trim());
        supportedValue.setText(String.valueOf(summary.supportedVideoCount()));
        ignoredValue.setText(String.valueOf(summary.sidecarCount() + summary.unsupportedCount() + summary.ignoredCount()));
        seriesValue.setText(String.valueOf(summary.likelySeriesGroupCount()));
        moviesValue.setText(String.valueOf(summary.likelyMovieGroupCount()));
        unknownValue.setText(String.valueOf(summary.unknownItemCount()));
        sourceFilesList.getItems().setAll(sourceFileNames(result.items()));
        proposedNamesList.getItems().clear();
        analyzeButton.setDisable(false);
        resetButton.setDisable(false);
    }

    private void resetScanMetrics() {
        scannedFolderValue.setText("\u2014");
        supportedValue.setText("0");
        ignoredValue.setText("0");
        seriesValue.setText("0");
        moviesValue.setText("0");
        unknownValue.setText("0");
    }

    private void refreshWorkbenchLayout(double width) {
        if (renameWorkbench == null || sourceColumn == null || actionColumn == null || resultColumn == null) {
            return;
        }
        if (width < 820 && renameWorkbench instanceof HBox) {
            VBox vertical = new VBox(12, sourceColumn, actionColumn, resultColumn);
            vertical.setAlignment(Pos.TOP_CENTER);
            VBox.setVgrow(sourceColumn, Priority.ALWAYS);
            VBox.setVgrow(resultColumn, Priority.ALWAYS);
            sourceColumn.setMaxWidth(Double.MAX_VALUE);
            resultColumn.setMaxWidth(Double.MAX_VALUE);
            replaceWorkbench(vertical);
        } else if (width >= 820 && renameWorkbench instanceof VBox) {
            HBox horizontal = new HBox(12, sourceColumn, actionColumn, resultColumn);
            horizontal.setAlignment(Pos.TOP_CENTER);
            HBox.setHgrow(sourceColumn, Priority.ALWAYS);
            HBox.setHgrow(resultColumn, Priority.ALWAYS);
            sourceColumn.setMaxWidth(Double.MAX_VALUE);
            resultColumn.setMaxWidth(Double.MAX_VALUE);
            replaceWorkbench(horizontal);
        }
    }

    private void replaceWorkbench(Pane replacement) {
        VBox parent = (VBox) renameWorkbench.getParent();
        int index = parent.getChildren().indexOf(renameWorkbench);
        parent.getChildren().set(index, replacement);
        renameWorkbench = replacement;
        VBox.setVgrow(renameWorkbench, Priority.ALWAYS);
    }

    private void configureFileList(ListView<String> listView) {
        listView.setCellFactory(ignored -> new ListCell<>() {
            private final Label filename = new Label();
            private final Label extension = new Label();
            private final HBox row = new HBox(10, filename, extension);
            private final Tooltip tooltip = new Tooltip();

            {
                filename.getStyleClass().add("file-row-name");
                extension.getStyleClass().add("extension-badge");
                filename.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(filename, Priority.ALWAYS);
                row.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setGraphic(null);
                    setText(null);
                    setTooltip(null);
                    return;
                }
                filename.setText(filenameWithoutExtension(item));
                extension.setText(fileExtension(item));
                tooltip.setText(item);
                setGraphic(row);
                setText(null);
                setTooltip(tooltip);
            }
        });
    }

    private String filenameWithoutExtension(String filename) {
        int separator = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        int dot = filename.lastIndexOf('.');
        if (dot <= separator || dot == 0) {
            return filename;
        }
        return filename.substring(0, dot);
    }

    private String fileExtension(String filename) {
        int separator = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        int dot = filename.lastIndexOf('.');
        if (dot <= separator || dot == filename.length() - 1) {
            return "\u2014";
        }
        return filename.substring(dot + 1).toUpperCase(java.util.Locale.ROOT);
    }

    private List<String> sourceFileNames(List<InventoryItem> items) {
        return items.stream()
                .map(InventoryItem::filename)
                .toList();
    }

    private void refreshLocalizedText() {
        AppLanguage language = currentViewModel.language();
        loadFolderButton.setText(UiText.loadFolderButton(language));
        settingsButton.setText(UiText.settingsButton(language));
        continueButton.setText(UiText.continueButton(language));
    }

    private void applyLanguage(AppLanguage language) {
        currentViewModel = currentViewModel.withLanguage(language);
        refreshLocalizedText();
        refreshShellText();
        if (settingsPane != null) {
            settingsPane.applyLanguage(language);
        }
    }

    public static Image logoImage() {
        return new Image(java.util.Objects.requireNonNull(
                        AppShell.class.getResourceAsStream("/assets/episort-logo.png"),
                        "Missing logo /assets/episort-logo.png"));
    }
}

