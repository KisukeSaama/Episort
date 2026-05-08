package com.episort.ui;

import com.episort.ui.settings.SettingsPane;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

public final class AppShell {
    private final BorderPane root;
    private final Label status;
    private final Label description;

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
            BiFunction<String, Optional<String>, AppShellViewModel> configureTvdb) {
        this(viewModel, configureWorkspace, null, configureTvdb);
    }

    public AppShell(
            AppShellViewModel viewModel,
            Function<Path, AppShellViewModel> configureWorkspace,
            Function<Path, AppShellViewModel> selectInputFolder,
            BiFunction<String, Optional<String>, AppShellViewModel> configureTvdb) {
        Label title = new Label(viewModel.title());
        title.getStyleClass().add("app-title");

        status = new Label(viewModel.primaryStatus());
        status.getStyleClass().add("app-status");

        description = new Label(viewModel.description());
        description.setWrapText(true);
        description.getStyleClass().add("app-description");

        VBox content = new VBox(12, title, status, description);
        if (configureWorkspace != null && selectInputFolder != null && configureTvdb != null) {
            content.getChildren().add(new SettingsPane(configureWorkspace, selectInputFolder, configureTvdb, this::apply).root());
        }
        content.setPadding(new Insets(24));

        root = new BorderPane(content);
        root.getStyleClass().add("app-shell");
    }

    public Parent root() {
        return root;
    }

    private void apply(AppShellViewModel viewModel) {
        status.setText(viewModel.primaryStatus());
        description.setText(viewModel.description());
    }
}
