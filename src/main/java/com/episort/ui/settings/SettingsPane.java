package com.episort.ui.settings;

import com.episort.ui.AppLanguage;
import com.episort.ui.AppShellViewModel;
import com.episort.ui.UiText;
import com.episort.workflow.TmdbGatewayStatus;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

public final class SettingsPane {
    static final String TMDB_ATTRIBUTION_URL = "https://www.themoviedb.org";
    private static final String TMDB_LOGO_RESOURCE = "/assets/tmdb-logo-dark.png";

    private final VBox root;
    private final Label pageEyebrow;
    private final Label pageTitle;
    private final Label pageSubtitle;
    private final Label workspaceTitle;
    private final Label workspaceDescription;
    private final Label workspaceValue;
    private final Label preferencesTitle;
    private final Label preferencesDescription;
    private final Label tmdbTitle;
    private final Label tmdbDescription;
    private final Label tmdbStatus;
    private final Label tmdbAttribution;
    private final Hyperlink tmdbAttributionLink;
    private final Region tmdbStatusDot;
    private final Button chooseWorkspace;
    private final ComboBox<AppLanguage> languageCombo;
    private final Supplier<Optional<Path>> currentWorkspace;
    private final TmdbGatewayStatus tmdbConfiguration;
    @SuppressWarnings("unused")
    private final Runnable onClose;
    private AppLanguage currentLanguage = AppLanguage.FRENCH;
    private final List<Consumer<AppLanguage>> extraSectionLanguageHooks = new ArrayList<>();

    public SettingsPane(
            Function<Path, AppShellViewModel> configureWorkspace,
            Supplier<Optional<Path>> currentWorkspace,
            Consumer<AppLanguage> onLanguageChange,
            TmdbGatewayStatus tmdbConfiguration,
            Consumer<String> openExternalLink,
            Runnable onClose,
            Consumer<AppShellViewModel> onConfigured) {
        this.currentWorkspace = currentWorkspace;
        this.tmdbConfiguration = tmdbConfiguration;
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
            directoryChooser.setTitle(UiText.chooserWorkspaceTitle(currentLanguage));
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

        // ---- TMDB section ------------------------------------------
        tmdbTitle = new Label();
        tmdbTitle.getStyleClass().add("settings-section-title");

        tmdbDescription = new Label();
        tmdbDescription.getStyleClass().add("settings-section-description");
        tmdbDescription.setWrapText(true);

        tmdbStatusDot = new Region();
        tmdbStatusDot.getStyleClass().addAll("dot", "dot-idle");
        tmdbStatus = new Label();
        tmdbStatus.getStyleClass().add("settings-section-description");
        tmdbStatus.setWrapText(true);
        HBox tmdbInputs = new HBox(12, tmdbStatusDot, tmdbStatus);
        HBox.setHgrow(tmdbStatus, Priority.ALWAYS);
        tmdbInputs.setAlignment(Pos.CENTER_LEFT);
        tmdbInputs.getStyleClass().add("settings-row");

        ImageView tmdbLogo = new ImageView(new Image(Objects.requireNonNull(
                SettingsPane.class.getResource(TMDB_LOGO_RESOURCE),
                "Missing TMDB attribution logo").toExternalForm()));
        tmdbLogo.setFitWidth(84);
        tmdbLogo.setFitHeight(46);
        tmdbLogo.setPreserveRatio(true);
        tmdbLogo.setAccessibleText("TMDB");

        tmdbAttribution = new Label();
        tmdbAttribution.getStyleClass().add("tmdb-attribution-copy");
        tmdbAttribution.setWrapText(true);

        tmdbAttributionLink = new Hyperlink();
        tmdbAttributionLink.getStyleClass().add("tmdb-attribution-link");
        tmdbAttributionLink.setDisable(openExternalLink == null);
        if (openExternalLink != null) {
            tmdbAttributionLink.setOnAction(event -> openExternalLink.accept(TMDB_ATTRIBUTION_URL));
        }

        VBox tmdbAttributionText = new VBox(2, tmdbAttribution, tmdbAttributionLink);
        HBox.setHgrow(tmdbAttributionText, Priority.ALWAYS);
        HBox tmdbAttributionRow = new HBox(16, tmdbLogo, tmdbAttributionText);
        tmdbAttributionRow.setAlignment(Pos.CENTER_LEFT);
        tmdbAttributionRow.getStyleClass().add("tmdb-attribution");

        VBox tmdbSection = new VBox(
                10,
                tmdbTitle,
                tmdbDescription,
                divider(),
                tmdbInputs,
                divider(),
                tmdbAttributionRow);
        tmdbSection.getStyleClass().add("settings-section");

        applyLanguage(AppLanguage.FRENCH);
        refreshWorkspaceValue(currentWorkspace.get());

        root = new VBox(18, header, workspaceSection, tmdbSection, preferencesSection);
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
        // The combo sits alone on its row, with no label of its own.
        languageCombo.setAccessibleText(UiText.languageLabel(language));
        tmdbTitle.setText(UiText.tmdbSettingsSectionTitle(language));
        tmdbDescription.setText(UiText.tmdbSettingsSectionDescription(language));
        tmdbAttribution.setText(UiText.tmdbSettingsAttribution(language));
        tmdbAttributionLink.setText(UiText.tmdbSettingsAttributionLink(language));
        tmdbAttributionLink.setAccessibleText(UiText.tmdbSettingsAttributionLinkAccessible(language));
        applyTmdbStatus(language);

        languageCombo.setValue(language);
        refreshWorkspaceValue(currentWorkspace.get());
    }

    public void refreshWorkspace() {
        refreshWorkspaceValue(currentWorkspace.get());
    }

    private void refreshWorkspaceValue(Optional<Path> workspace) {
        workspaceValue.setText(UiText.workspaceValue(currentLanguage, workspace));
    }

    private void applyTmdbStatus(AppLanguage language) {
        TmdbStatusPresentation presentation = TmdbStatusPresentation.from(tmdbConfiguration, language);
        tmdbStatus.setText(presentation.text());
        tmdbStatusDot.getStyleClass().setAll("dot", presentation.dotStyleClass());
    }

    private static Region divider() {
        Region div = new Region();
        div.getStyleClass().add("settings-section-divider");
        return div;
    }
}
