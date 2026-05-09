package com.episort.ui.settings;

import com.episort.ui.AppLanguage;
import com.episort.ui.AppShellViewModel;
import com.episort.ui.UiText;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

public final class SettingsPane {
    private final VBox root;
    private final Label prerequisitesHeading;
    private final Label preferencesHeading;
    private final Label workspaceTitle;
    private final Label workspaceDescription;
    private final Label workspaceValue;
    private final Label languageTitle;
    private final Label languageDescription;
    private final Button chooseWorkspace;
    private final Button closeButton;
    private final ComboBox<AppLanguage> languageCombo;
    private final Supplier<Optional<Path>> currentWorkspace;
    private AppLanguage currentLanguage = AppLanguage.FRENCH;

    public SettingsPane(
            Function<Path, AppShellViewModel> configureWorkspace,
            Supplier<Optional<Path>> currentWorkspace,
            Consumer<AppLanguage> onLanguageChange,
            Runnable onClose,
            Consumer<AppShellViewModel> onConfigured) {
        this.currentWorkspace = currentWorkspace;

        prerequisitesHeading = new Label();
        prerequisitesHeading.getStyleClass().addAll("section-heading", "section-heading-accent");

        preferencesHeading = new Label();
        preferencesHeading.getStyleClass().addAll("section-heading", "section-heading-accent");

        workspaceTitle = new Label();
        workspaceTitle.getStyleClass().add("settings-section-title");

        workspaceDescription = new Label();
        workspaceDescription.getStyleClass().add("settings-section-description");
        workspaceDescription.setWrapText(true);

        workspaceValue = new Label();
        workspaceValue.getStyleClass().add("workspace-value");
        workspaceValue.setWrapText(true);

        chooseWorkspace = new Button();
        chooseWorkspace.setOnAction(event -> {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle("Choose Episort workspace");
            currentWorkspace.get().ifPresent(path -> {
                File initial = path.toFile();
                if (initial.isDirectory()) {
                    directoryChooser.setInitialDirectory(initial);
                }
            });
            Window owner = chooseWorkspace.getScene() == null ? null : chooseWorkspace.getScene().getWindow();
            File selectedDirectory = directoryChooser.showDialog(owner);
            if (selectedDirectory != null) {
                onConfigured.accept(configureWorkspace.apply(selectedDirectory.toPath()));
                refreshWorkspaceValue(currentWorkspace.get());
            }
        });

        HBox workspaceAction = new HBox(10, chooseWorkspace, workspaceValue);
        workspaceAction.setAlignment(Pos.CENTER_LEFT);

        VBox workspaceSection = new VBox(8, workspaceTitle, workspaceDescription, workspaceAction);
        workspaceSection.getStyleClass().add("settings-section");

        languageTitle = new Label();
        languageTitle.getStyleClass().add("settings-section-title");

        languageDescription = new Label();
        languageDescription.getStyleClass().add("settings-section-description");
        languageDescription.setWrapText(true);

        languageCombo = new ComboBox<>(FXCollections.observableArrayList(AppLanguage.FRENCH, AppLanguage.ENGLISH));
        languageCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(AppLanguage value) {
                return value == null ? "" : value.displayName();
            }

            @Override
            public AppLanguage fromString(String value) {
                return AppLanguage.FRENCH.displayName().equals(value) ? AppLanguage.FRENCH : AppLanguage.ENGLISH;
            }
        });
        languageCombo.setOnAction(event -> {
            AppLanguage selected = languageCombo.getValue();
            if (selected != null && selected != currentLanguage) {
                onLanguageChange.accept(selected);
            }
        });

        VBox languageSection = new VBox(8, languageTitle, languageDescription, languageCombo);
        languageSection.getStyleClass().add("settings-section");

        closeButton = new Button();
        closeButton.getStyleClass().add("primary");
        closeButton.setOnAction(event -> onClose.run());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actionRow = new HBox(spacer, closeButton);
        actionRow.setAlignment(Pos.CENTER_RIGHT);

        applyLanguage(AppLanguage.FRENCH);
        refreshWorkspaceValue(currentWorkspace.get());

        root = new VBox(14,
                prerequisitesHeading,
                workspaceSection,
                preferencesHeading,
                languageSection,
                actionRow);
    }

    public VBox root() {
        return root;
    }

    public void applyLanguage(AppLanguage language) {
        currentLanguage = language;
        prerequisitesHeading.setText(UiText.prerequisitesHeading(language));
        preferencesHeading.setText(UiText.preferencesHeading(language));
        workspaceTitle.setText(UiText.workspaceSectionTitle(language));
        workspaceDescription.setText(UiText.workspaceSectionDescription(language));
        chooseWorkspace.setText(UiText.chooseWorkspaceButton(language));
        languageTitle.setText(UiText.languageLabel(language));
        languageDescription.setText(language == AppLanguage.ENGLISH
                ? "Choose the interface language."
                : "Choisis la langue de l'interface.");
        closeButton.setText(UiText.closeSettingsButton(language));
        languageCombo.setValue(language);
        refreshWorkspaceValue(currentWorkspace.get());
    }

    public void refreshWorkspace() {
        refreshWorkspaceValue(currentWorkspace.get());
    }

    private void refreshWorkspaceValue(Optional<Path> workspace) {
        workspaceValue.setText(UiText.workspaceValue(currentLanguage, workspace));
    }
}
