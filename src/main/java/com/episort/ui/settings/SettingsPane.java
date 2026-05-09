package com.episort.ui.settings;

import com.episort.ui.AppLanguage;
import com.episort.ui.AppShellViewModel;
import com.episort.ui.UiText;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

public final class SettingsPane {
    private final VBox root;
    private final Label workspaceTitle;
    private final Label workspaceDescription;
    private final Label workspaceValue;
    private final Button chooseWorkspace;
    private final Button continueButton;
    private AppLanguage currentLanguage = AppLanguage.FRENCH;

    public SettingsPane(
            Function<Path, AppShellViewModel> configureWorkspace,
            Supplier<Optional<Path>> currentWorkspace,
            BooleanSupplier canContinue,
            Runnable onContinue,
            Consumer<AppShellViewModel> onConfigured) {
        workspaceTitle = new Label();
        workspaceTitle.getStyleClass().add("settings-section-title");

        workspaceDescription = new Label();
        workspaceDescription.getStyleClass().add("settings-section-description");
        workspaceDescription.setWrapText(true);

        workspaceValue = new Label();
        workspaceValue.getStyleClass().add("workspace-value");

        continueButton = new Button();
        continueButton.setDisable(!canContinue.getAsBoolean());
        continueButton.setOnAction(event -> onContinue.run());

        chooseWorkspace = new Button();
        chooseWorkspace.setOnAction(event -> {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle("Choose Episort workspace");
            Window owner = chooseWorkspace.getScene() == null ? null : chooseWorkspace.getScene().getWindow();
            File selectedDirectory = directoryChooser.showDialog(owner);
            if (selectedDirectory != null) {
                onConfigured.accept(configureWorkspace.apply(selectedDirectory.toPath()));
                refreshWorkspaceValue(currentWorkspace.get());
                continueButton.setDisable(!canContinue.getAsBoolean());
            }
        });

        applyLanguage(AppLanguage.FRENCH);
        refreshWorkspaceValue(currentWorkspace.get());

        HBox workspaceAction = new HBox(10, chooseWorkspace, workspaceValue);
        workspaceAction.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        VBox workspaceSection = new VBox(8, workspaceTitle, workspaceDescription, workspaceAction);
        workspaceSection.getStyleClass().add("settings-section");

        root = new VBox(18, workspaceSection, continueButton);
    }

    public VBox root() {
        return root;
    }

    public void applyLanguage(AppLanguage language) {
        currentLanguage = language;
        if (language == AppLanguage.ENGLISH) {
            workspaceTitle.setText(UiText.workspaceSectionTitle(language));
            workspaceDescription.setText(UiText.workspaceSectionDescription(language));
            chooseWorkspace.setText(UiText.chooseWorkspaceButton(language));
            continueButton.setText(UiText.continueButton(language));
        } else {
            workspaceTitle.setText(UiText.workspaceSectionTitle(language));
            workspaceDescription.setText(UiText.workspaceSectionDescription(language));
            chooseWorkspace.setText(UiText.chooseWorkspaceButton(language));
            continueButton.setText(UiText.continueButton(language));
        }
        refreshWorkspaceValue(Optional.ofNullable(workspaceValue.getUserData()).map(Path.class::cast));
    }

    public void refreshContinueState(boolean enabled) {
        continueButton.setDisable(!enabled);
    }

    private void refreshWorkspaceValue(Optional<Path> workspace) {
        workspaceValue.setUserData(workspace.orElse(null));
        workspaceValue.setText(UiText.workspaceValue(currentLanguage, workspace));
    }
}
