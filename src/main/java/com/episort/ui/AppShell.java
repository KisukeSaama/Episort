package com.episort.ui;

import com.episort.ui.settings.SettingsPane;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public final class AppShell {
    private final BorderPane root;
    private final Label status;
    private final Label description;
    private final SettingsPane settingsPane;
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
        this.currentViewModel = viewModel;
        ImageView logo = new ImageView(logoImage());
        logo.setFitWidth(48);
        logo.setFitHeight(48);
        logo.setPreserveRatio(true);

        Label title = new Label(viewModel.title());
        title.getStyleClass().add("app-title");

        status = new Label(viewModel.primaryStatus());
        status.getStyleClass().add("app-status");

        description = new Label(viewModel.description());
        description.setWrapText(true);
        description.getStyleClass().add("app-description");

        SettingsPane pane = null;
        HBox brand = new HBox(12, logo, title);
        brand.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(12, brand, status, description);
        if (configureWorkspace != null) {
            pane = new SettingsPane(
                            configureWorkspace,
                            currentWorkspace,
                            canContinue,
                            onContinue,
                            this::apply);
            content.getChildren().add(pane.root());
        }
        settingsPane = pane;
        content.setPadding(new Insets(24));

        root = new BorderPane(content);
        root.setTop(languageSelector());
        root.getStyleClass().add("app-shell");
        root.getStylesheets().add(
                java.util.Objects.requireNonNull(
                                AppShell.class.getResource("/styles/app.css"),
                                "Missing stylesheet /styles/app.css")
                        .toExternalForm());
        root.getStyleClass().add(currentViewModel.theme() == Theme.DARK ? "theme-dark" : "theme-light");
    }

    public Parent root() {
        return root;
    }

    private void apply(AppShellViewModel viewModel) {
        currentViewModel = AppShellViewModel.preservingTheme(currentViewModel, viewModel);
        refreshShellText();
    }

    private void refreshShellText() {
        status.setText(currentViewModel.primaryStatus());
        description.setText(currentViewModel.description());
        root.getStyleClass().removeAll("theme-dark", "theme-light");
        root.getStyleClass().add(currentViewModel.theme() == Theme.DARK ? "theme-dark" : "theme-light");
    }

    private void applyLanguage(AppLanguage language) {
        currentViewModel = currentViewModel.withLanguage(language);
        refreshShellText();
        if (settingsPane != null) {
            settingsPane.applyLanguage(language);
        }
    }

    private HBox languageSelector() {
        Label languageLabel = new Label();
        languageLabel.getStyleClass().add("language-label");
        ComboBox<AppLanguage> language = new ComboBox<>(FXCollections.observableArrayList(AppLanguage.FRENCH, AppLanguage.ENGLISH));
        language.setValue(currentViewModel.language());
        language.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(AppLanguage value) {
                return value == null ? "" : value.displayName();
            }

            @Override
            public AppLanguage fromString(String value) {
                return AppLanguage.FRENCH.displayName().equals(value) ? AppLanguage.FRENCH : AppLanguage.ENGLISH;
            }
        });
        Runnable updateLabel = () -> languageLabel.setText(UiText.languageLabel(language.getValue()));
        updateLabel.run();
        language.setOnAction(event -> {
            updateLabel.run();
            applyLanguage(language.getValue());
        });
        HBox bar = new HBox(8, languageLabel, language);
        bar.setAlignment(Pos.CENTER_RIGHT);
        bar.setPadding(new Insets(12, 24, 0, 24));
        return bar;
    }

    public static Image logoImage() {
        return new Image(java.util.Objects.requireNonNull(
                        AppShell.class.getResourceAsStream("/assets/episort-logo.png"),
                        "Missing logo /assets/episort-logo.png"));
    }
}
