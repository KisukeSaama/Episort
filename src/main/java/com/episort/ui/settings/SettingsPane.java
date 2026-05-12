package com.episort.ui.settings;

import com.episort.ui.AppLanguage;
import com.episort.ui.AppShellViewModel;
import com.episort.ui.UiText;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.BiFunction;
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
    private final Label tvdbTitle;
    private final Label tvdbDescription;
    private final Label tvdbStatus;
    private final Region tvdbStatusDot;
    private final Button chooseWorkspace;
    private final Button tvdbTestAndSave;
    private final Button tvdbResetCache;
    private final ComboBox<AppLanguage> languageCombo;
    private final Supplier<Optional<Path>> currentWorkspace;
    @SuppressWarnings("unused")
    private final Runnable onClose;
    private AppLanguage currentLanguage = AppLanguage.FRENCH;
    private final List<Consumer<AppLanguage>> extraSectionLanguageHooks = new ArrayList<>();

    public SettingsPane(
            Function<Path, AppShellViewModel> configureWorkspace,
            Supplier<Optional<Path>> currentWorkspace,
            Consumer<AppLanguage> onLanguageChange,
            BiFunction<String, Optional<String>, AppShellViewModel> configureTvdb,
            Supplier<Integer> resetTvdbCache,
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

        languageCombo = new ComboBox<>(FXCollections.observableArrayList(AppLanguage.values()));
        languageCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(AppLanguage value) {
                return value == null ? "" : value.displayName();
            }

            @Override
            public AppLanguage fromString(String value) {
                for (AppLanguage candidate : AppLanguage.values()) {
                    if (candidate.displayName().equals(value)) {
                        return candidate;
                    }
                }
                return AppLanguage.DEFAULT;
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

        // ---- TVDB section ------------------------------------------
        tvdbTitle = new Label();
        tvdbTitle.getStyleClass().add("settings-section-title");

        tvdbDescription = new Label();
        tvdbDescription.getStyleClass().add("settings-section-description");
        tvdbDescription.setWrapText(true);

        tvdbTestAndSave = new Button();
        tvdbTestAndSave.getStyleClass().add("primary");
        tvdbStatusDot = new Region();
        tvdbStatusDot.getStyleClass().addAll("dot", "dot-idle");
        tvdbStatus = new Label();
        tvdbStatus.getStyleClass().add("settings-section-description");
        tvdbStatus.setWrapText(true);
        tvdbTestAndSave.setOnAction(event -> {
            if (configureTvdb == null) {
                tvdbStatus.setText(UiText.tvdbSettingsUnavailable(currentLanguage));
                setTvdbStatusDot(false);
                return;
            }
            tvdbStatus.setText(UiText.tvdbSettingsChecking(currentLanguage));
            tvdbStatusDot.getStyleClass().setAll("dot", "dot-warn");
            AppShellViewModel result = configureTvdb.apply("", Optional.empty());
            onConfigured.accept(result);
            boolean ok = result.errorCode().isEmpty();
            setTvdbStatusDot(ok);
            tvdbStatus.setText(ok ? UiText.tvdbSettingsOnline(currentLanguage) : result.description());
        });

        tvdbResetCache = new Button();
        tvdbResetCache.getStyleClass().add("secondary");
        tvdbResetCache.setOnAction(event -> {
            if (resetTvdbCache == null) {
                tvdbStatus.setText(UiText.tvdbSettingsUnavailable(currentLanguage));
                setTvdbStatusDot(false);
                return;
            }
            int cleared = resetTvdbCache.get();
            tvdbStatus.setText(cleared > 0
                    ? UiText.tvdbSettingsCacheCleared(currentLanguage, cleared)
                    : UiText.tvdbSettingsCacheAlreadyEmpty(currentLanguage));
            tvdbStatusDot.getStyleClass().setAll("dot", "dot-good");
        });

        HBox tvdbInputs = new HBox(12, tvdbStatusDot, tvdbStatus, tvdbResetCache, tvdbTestAndSave);
        HBox.setHgrow(tvdbStatus, Priority.ALWAYS);
        tvdbInputs.setAlignment(Pos.CENTER_LEFT);
        tvdbInputs.getStyleClass().add("settings-row");

        VBox tvdbSection = new VBox(10, tvdbTitle, divider(), tvdbInputs);
        tvdbSection.getStyleClass().add("settings-section");

        applyLanguage(AppLanguage.FRENCH);
        refreshWorkspaceValue(currentWorkspace.get());

        root = new VBox(18, header, workspaceSection, tvdbSection, preferencesSection);
        root.getStyleClass().add("settings-page");
        root.setMaxWidth(960);
    }

    public VBox root() {
        return root;
    }

    public void attachExtraSection(Region section, Consumer<AppLanguage> applyLanguageHook) {
        if (section == null) {
            return;
        }
        root.getChildren().add(section);
        if (applyLanguageHook != null) {
            extraSectionLanguageHooks.add(applyLanguageHook);
            applyLanguageHook.accept(currentLanguage);
        }
    }

    public void applyLanguage(AppLanguage language) {
        currentLanguage = language;
        for (Consumer<AppLanguage> hook : extraSectionLanguageHooks) {
            hook.accept(language);
        }
        pageEyebrow.setText(UiText.settingsHeading(language));
        pageTitle.setText(UiText.settingsPageTitle(language));
        pageSubtitle.setText(UiText.settingsPageSubtitle(language));

        workspaceTitle.setText(UiText.workspaceSectionTitle(language));
        workspaceDescription.setText(UiText.workspaceSectionDescription(language));
        chooseWorkspace.setText(UiText.chooseWorkspaceButton(language));

        preferencesTitle.setText(UiText.preferencesSectionTitle(language));
        preferencesDescription.setText(UiText.preferencesSectionDescription(language));
        tvdbTitle.setText(UiText.tvdbSettingsSectionTitle(language));
        tvdbDescription.setText(UiText.tvdbSettingsSectionDescription(language));
        tvdbTestAndSave.setText(UiText.tvdbSettingsTestAndSave(language));
        tvdbResetCache.setText(UiText.tvdbSettingsResetCache(language));
        if (tvdbStatus.getText() == null || tvdbStatus.getText().isBlank()) {
            tvdbStatus.setText(UiText.tvdbSettingsNotChecked(language));
        }

        languageCombo.setValue(language);
        refreshWorkspaceValue(currentWorkspace.get());
    }

    public void refreshWorkspace() {
        refreshWorkspaceValue(currentWorkspace.get());
    }

    private void refreshWorkspaceValue(Optional<Path> workspace) {
        workspaceValue.setText(UiText.workspaceValue(currentLanguage, workspace));
    }

    private void setTvdbStatusDot(boolean ok) {
        tvdbStatusDot.getStyleClass().setAll("dot", ok ? "dot-good" : "dot-error");
    }

    private static Region divider() {
        Region div = new Region();
        div.getStyleClass().add("settings-section-divider");
        return div;
    }
}
