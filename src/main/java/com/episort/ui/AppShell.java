package com.episort.ui;

import com.episort.ui.settings.SettingsPane;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

public final class AppShell {
    private final BorderPane root;
    private final StackPane viewHost;
    private final Parent mainView;
    private final Parent settingsView;
    private final Label brandTitle;
    private final Label mainStatus;
    private final Label mainDescription;
    private final Button loadFolderButton;
    private final Button settingsButton;
    private final SettingsPane settingsPane;
    private final Function<Path, AppShellViewModel> selectInputFolder;
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
            Function<Path, AppShellViewModel> selectInputFolder,
            java.util.function.BiFunction<String, Optional<String>, AppShellViewModel> configureTvdb,
            Supplier<Optional<Path>> currentWorkspace,
            BooleanSupplier canContinue,
            Runnable onContinue) {
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

        loadFolderButton = new Button();
        loadFolderButton.getStyleClass().add("primary");
        loadFolderButton.setOnAction(event -> openLoadFolderDialog());

        settingsButton = new Button();
        settingsButton.getStyleClass().add("ghost");
        settingsButton.setOnAction(event -> showSettings());

        if (configureWorkspace != null) {
            settingsPane = new SettingsPane(
                    configureWorkspace,
                    this.currentWorkspace,
                    this::applyLanguage,
                    this::showMain,
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

        refreshLocalizedText();
        refreshShellText();
        refreshLoadFolderState();

        if (settingsPane != null && this.currentWorkspace.get().isEmpty()) {
            showSettings();
        } else {
            showMain();
        }
    }

    public Parent root() {
        return root;
    }

    private Parent buildMainView(ImageView logo) {
        HBox brandRow = new HBox(14, logo, brandTitle);
        brandRow.setAlignment(Pos.CENTER);

        VBox statusBlock = new VBox(4, mainStatus, mainDescription);
        statusBlock.setAlignment(Pos.CENTER);
        statusBlock.setMaxWidth(520);

        HBox actions = new HBox(12, loadFolderButton, settingsButton);
        actions.setAlignment(Pos.CENTER);

        VBox column = new VBox(22, brandRow, statusBlock, actions);
        column.setAlignment(Pos.CENTER);
        column.setPadding(new Insets(40));
        column.getStyleClass().add("main-view");

        StackPane wrapper = new StackPane(column);
        StackPane.setAlignment(column, Pos.CENTER);
        return wrapper;
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
            apply(selectInputFolder.apply(selected.toPath()));
        }
    }

    private void apply(AppShellViewModel viewModel) {
        currentViewModel = AppShellViewModel.preservingTheme(currentViewModel, viewModel);
        refreshShellText();
        refreshLoadFolderState();
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

    private void refreshLocalizedText() {
        AppLanguage language = currentViewModel.language();
        loadFolderButton.setText(UiText.loadFolderButton(language));
        settingsButton.setText(UiText.settingsButton(language));
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
