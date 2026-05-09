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
    private final Label pageEyebrow;
    private final Label pageTitle;
    private final Label pageSubtitle;
    private final Label workspaceTitle;
    private final Label workspaceDescription;
    private final Label workspaceValue;
    private final Label preferencesTitle;
    private final Label preferencesDescription;
    private final Button chooseWorkspace;
    private final ComboBox<AppLanguage> languageCombo;
    private final Supplier<Optional<Path>> currentWorkspace;
    @SuppressWarnings("unused")
    private final Runnable onClose;
    private AppLanguage currentLanguage = AppLanguage.FRENCH;

    public SettingsPane(
            Function<Path, AppShellViewModel> configureWorkspace,
            Supplier<Optional<Path>> currentWorkspace,
            Consumer<AppLanguage> onLanguageChange,
            Runnable onClose,
            Consumer<AppShellViewModel> onConfigured) {
        this.currentWorkspace = currentWorkspace;
        this.onClose = onClose;

        pageEyebrow = new Label();
        pageEyebrow.getStyleClass().add("page-title");

        pageTitle = new Label();
        pageTitle.getStyleClass().add("page-heading");

        pageSubtitle = new Label();
        pageSubtitle.getStyleClass().add("page-subtitle");
        pageSubtitle.setWrapText(true);

        VBox header = new VBox(4, pageEyebrow, pageTitle, pageSubtitle);

        // ---- Workspace section -------------------------------------
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

        HBox workspaceAction = new HBox(12, chooseWorkspace, workspaceValue);
        workspaceAction.setAlignment(Pos.CENTER_LEFT);
        workspaceAction.getStyleClass().add("settings-row");
        HBox.setHgrow(workspaceValue, Priority.ALWAYS);

        VBox workspaceSection = new VBox(10, workspaceTitle, workspaceDescription, divider(), workspaceAction);
        workspaceSection.getStyleClass().add("settings-section");

        // ---- Preferences section -----------------------------------
        preferencesTitle = new Label();
        preferencesTitle.getStyleClass().add("settings-section-title");

        preferencesDescription = new Label();
        preferencesDescription.getStyleClass().add("settings-section-description");
        preferencesDescription.setWrapText(true);

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

        HBox preferencesRow = new HBox(12, languageCombo);
        preferencesRow.setAlignment(Pos.CENTER_LEFT);
        preferencesRow.getStyleClass().add("settings-row");

        VBox preferencesSection = new VBox(10, preferencesTitle, preferencesDescription, divider(), preferencesRow);
        preferencesSection.getStyleClass().add("settings-section");

        applyLanguage(AppLanguage.FRENCH);
        refreshWorkspaceValue(currentWorkspace.get());

        root = new VBox(18, header, workspaceSection, preferencesSection);
        root.getStyleClass().add("settings-page");
        root.setMaxWidth(960);
    }

    public VBox root() {
        return root;
    }

    public void applyLanguage(AppLanguage language) {
        currentLanguage = language;
        pageEyebrow.setText(UiText.settingsHeading(language));
        pageTitle.setText(UiText.settingsPageTitle(language));
        pageSubtitle.setText(UiText.settingsPageSubtitle(language));

        workspaceTitle.setText(UiText.workspaceSectionTitle(language));
        workspaceDescription.setText(UiText.workspaceSectionDescription(language));
        chooseWorkspace.setText(UiText.chooseWorkspaceButton(language));

        preferencesTitle.setText(UiText.preferencesSectionTitle(language));
        preferencesDescription.setText(UiText.preferencesSectionDescription(language));

        languageCombo.setValue(language);
        refreshWorkspaceValue(currentWorkspace.get());
    }

    public void refreshWorkspace() {
        refreshWorkspaceValue(currentWorkspace.get());
    }

    private void refreshWorkspaceValue(Optional<Path> workspace) {
        workspaceValue.setText(UiText.workspaceValue(currentLanguage, workspace));
    }

    private static Region divider() {
        Region div = new Region();
        div.getStyleClass().add("settings-section-divider");
        return div;
    }
}
