package com.episort.ui.scan;

import com.episort.tmdb.TmdbCandidate;
import com.episort.tmdb.TmdbEpisode;
import com.episort.tmdb.TmdbEpisodeOrder;
import com.episort.ui.AppLanguage;
import com.episort.ui.UiText;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public final class RowDetailPanel {

    private final VBox root;

    private final VBox emptyState;
    private final Label emptyTitle;
    private final Label emptyHint;
    private final VBox content;
    private final ScrollPane contentScroll;

    private final Label filenameValue;
    private final Label folderValue;
    private final Label extensionValue;

    private final Label mediaTypeValue;
    private final Label statusValue;
    private final Label confidenceValue;
    private final Label seasonEpisodeValue;
    private final TextArea inputPatternEditor = new TextArea();
    private final Button inputPatternApply = new Button();

    private final Label tmdbSeedValue;
    private final Label tmdbTypeValue;
    private final Label tmdbStatusValue;
    private final ImageView tmdbPoster = new ImageView();
    private final Label tmdbPosterPlaceholder = new Label();
    private final Label tmdbMatchTitle = new Label();
    private final Label tmdbMatchMeta = new Label();
    private final Label tmdbMatchId = new Label();
    private final Label tmdbMatchOverview = new Label();
    private final Label tmdbTargetHint = new Label();
    private final VBox tmdbMatchCard;
    private final ComboBox<String> tmdbCandidate = new ComboBox<>();
    private final ComboBox<String> tmdbOrder = new ComboBox<>();
    private final Label tmdbFirstEpisodeLabel;
    private final MenuButton tmdbFirstEpisode = new MenuButton();
    private TmdbEpisode selectedFirstEpisode;
    private List<TmdbEpisode> availableFirstEpisodes = List.of();
    private TmdbEpisodeOrder firstEpisodeOrder = TmdbEpisodeOrder.AIRED;
    private final Button tmdbApplySequence = new Button();
    private final Button tmdbSearch = new Button();
    private final Button tmdbApply = new Button();
    private final Button tmdbReset = new Button();
    private final ProgressIndicator tmdbBusy = new ProgressIndicator();
    private final Label tmdbBusyLabel = new Label();

    private final Label proposedValue;
    private final Label destinationValue;
    private final Label conflictValue;
    private final Label alertValue;
    private final Label noteValue;

    private final Label sourceHeading;
    private final Label detectionHeading;
    private final Label tmdbHeading;
    private final Label destinationHeading;
    private final Label notesHeading;

    private final Label filenameLabel;
    private final Label folderLabel;
    private final Label extensionLabel;
    private final Label mediaTypeLabel;
    private final Label statusLabel;
    private final Label confidenceLabel;
    private final Label seasonEpisodeLabel;
    private final Label inputPatternLabel;
    private final Label tmdbSeedLabel;
    private final Label tmdbTypeLabel;
    private final Label tmdbStatusLabel;
    private final Label proposedLabel;
    private final Label destinationLabel;
    private final Label conflictLabel;
    private final Label alertLabel;
    private final Label noteLabel;

    private AppLanguage currentLanguage = AppLanguage.FRENCH;
    private ScanRow currentRow;
    private BatchTmdbMatch currentGroupMatch;
    private int tmdbTargetCount = 0;
    /** False for rows whose group names no media — sidecars have no identity to look up. */
    private boolean tmdbSearchable = true;
    private boolean tmdbBusyActive;
    private BiConsumer<ScanRow, String> onApplyCandidate = (r, c) -> {};
    private BiConsumer<ScanRow, String> onApplyInputPattern = (r, p) -> {};
    private BiConsumer<ScanRow, TmdbEpisodeOrder> onApplySelectedMatch = (r, o) -> {};
    private BiConsumer<ScanRow, TmdbEpisode> onApplyEpisodeSequence = (r, e) -> {};
    private Consumer<ScanRow> onSearchMatch = r -> {};
    private Consumer<ScanRow> onResetMatch = r -> {};

    public RowDetailPanel() {
        Label emptyIcon = new Label("◌");
        emptyIcon.getStyleClass().add("detail-panel-empty-icon");
        emptyTitle = new Label();
        emptyTitle.getStyleClass().add("detail-panel-empty-title");
        emptyHint = new Label();
        emptyHint.getStyleClass().add("detail-panel-empty-hint");
        emptyHint.setWrapText(true);
        emptyHint.setMaxWidth(280);
        emptyState = new VBox(8, emptyIcon, emptyTitle, emptyHint);
        emptyState.getStyleClass().add("detail-panel-empty-state");
        emptyState.setAlignment(Pos.CENTER);

        sourceHeading = sectionHeading();
        detectionHeading = sectionHeading();
        tmdbHeading = sectionHeading();
        destinationHeading = sectionHeading();
        notesHeading = sectionHeading();

        filenameLabel = fieldLabel();
        folderLabel = fieldLabel();
        extensionLabel = fieldLabel();
        mediaTypeLabel = fieldLabel();
        statusLabel = fieldLabel();
        confidenceLabel = fieldLabel();
        seasonEpisodeLabel = fieldLabel();
        inputPatternLabel = fieldLabel();
        tmdbSeedLabel = fieldLabel();
        tmdbTypeLabel = fieldLabel();
        tmdbStatusLabel = fieldLabel();
        tmdbFirstEpisodeLabel = fieldLabel();
        proposedLabel = fieldLabel();
        destinationLabel = fieldLabel();
        conflictLabel = fieldLabel();
        alertLabel = fieldLabel();
        noteLabel = fieldLabel();

        filenameValue = monoValue();
        folderValue = monoValue();
        extensionValue = monoValue();
        mediaTypeValue = proseValue();
        statusValue = proseValue();
        confidenceValue = monoValue();
        seasonEpisodeValue = monoValue();
        tmdbSeedValue = monoValue();
        tmdbTypeValue = proseValue();
        tmdbStatusValue = proseValue();
        proposedValue = monoValue();
        proposedValue.getStyleClass().add("proposed-name-value");
        destinationValue = monoValue();
        conflictValue = proseValue();
        alertValue = proseValue();
        noteValue = proseValue();
        noteValue.setWrapText(true);

        inputPatternEditor.getStyleClass().add("pattern-editor");
        inputPatternEditor.setWrapText(true);
        inputPatternEditor.setPrefRowCount(5);
        inputPatternEditor.setPrefHeight(128);
        inputPatternEditor.setMinHeight(104);
        inputPatternEditor.setMaxWidth(Double.MAX_VALUE);
        inputPatternApply.getStyleClass().add("ghost");
        inputPatternApply.setOnAction(e -> {
            if (currentRow != null) {
                onApplyInputPattern.accept(currentRow, inputPatternEditor.getText());
            }
        });

        tmdbPoster.setFitWidth(92);
        tmdbPoster.setFitHeight(138);
        tmdbPoster.setPreserveRatio(true);
        tmdbPoster.getStyleClass().add("tmdb-poster-image");
        tmdbPosterPlaceholder.getStyleClass().add("tmdb-poster-placeholder");
        tmdbPosterPlaceholder.setAlignment(Pos.CENTER);
        StackPane posterPane = new StackPane(tmdbPosterPlaceholder, tmdbPoster);
        posterPane.getStyleClass().add("tmdb-poster-frame");
        tmdbMatchTitle.getStyleClass().add("tmdb-match-title");
        tmdbMatchMeta.getStyleClass().add("tmdb-match-meta");
        tmdbMatchId.getStyleClass().add("tmdb-match-meta");
        tmdbMatchOverview.getStyleClass().add("tmdb-match-overview");
        tmdbMatchOverview.setWrapText(true);
        tmdbMatchOverview.setMaxHeight(62);
        tmdbTargetHint.getStyleClass().add("tmdb-match-meta");
        tmdbTargetHint.setWrapText(true);
        VBox matchText = new VBox(6, tmdbMatchTitle, tmdbMatchMeta, tmdbMatchId, tmdbMatchOverview);
        HBox.setHgrow(matchText, Priority.ALWAYS);
        StackPane posterHost = new StackPane(posterPane);
        posterHost.getStyleClass().add("tmdb-detail-poster-host");
        HBox matchBody = new HBox(10, posterHost, matchText);
        matchBody.setAlignment(Pos.TOP_LEFT);
        tmdbMatchCard = new VBox(10, matchBody);
        tmdbMatchCard.getStyleClass().add("tmdb-selected-card");

        tmdbCandidate.setMaxWidth(Double.MAX_VALUE);
        tmdbCandidate.setVisible(false);
        tmdbCandidate.setManaged(false);
        tmdbSearch.getStyleClass().add("ghost");
        tmdbSearch.setOnAction(e -> {
            if (currentRow != null) {
                onSearchMatch.accept(currentRow);
            }
        });
        // Deliberately no variant: the screen's one solid-orange action is
        // "Voir le plan" in the top bar. Two full-orange buttons on screen at
        // once read as two next steps, so this one keeps the default .button
        // treatment and still outranks the .ghost controls beside it.
        tmdbReset.getStyleClass().add("ghost");
        tmdbApply.setOnAction(e -> {
            String selected = tmdbCandidate.getValue();
            boolean selectedExistingMatch = isApplyingExistingMatch(currentRow, selected);
            if (selectedExistingMatch) {
                onApplySelectedMatch.accept(currentRow, selectedOrder());
                return;
            }
            if (selected != null && !selected.isBlank() && currentRow != null) {
                onApplyCandidate.accept(currentRow, selected);
                return;
            }
            if (currentRow != null && currentRow.tmdbCandidate().isPresent()) {
                onApplySelectedMatch.accept(currentRow, selectedOrder());
            }
        });
        tmdbReset.setOnAction(e -> {
            if (currentRow != null) {
                onResetMatch.accept(currentRow);
            }
        });
        tmdbFirstEpisode.getStyleClass().add("tmdb-episode-picker");
        tmdbFirstEpisode.setMaxWidth(Double.MAX_VALUE);
        tmdbApplySequence.setMaxWidth(Double.MAX_VALUE);
        tmdbApplySequence.setDisable(true);
        tmdbApplySequence.setOnAction(e -> {
            TmdbEpisode first = selectedFirstEpisode;
            if (currentRow != null && first != null) {
                onApplyEpisodeSequence.accept(currentRow, first);
            }
        });
        tmdbSearch.setMaxWidth(Double.MAX_VALUE);
        tmdbApply.setMaxWidth(Double.MAX_VALUE);
        tmdbReset.setMaxWidth(Double.MAX_VALUE);
        HBox tmdbSecondaryControls = new HBox(8, tmdbSearch, tmdbReset);
        tmdbSecondaryControls.getStyleClass().add("tmdb-detail-secondary-controls");
        HBox.setHgrow(tmdbSearch, Priority.ALWAYS);
        HBox.setHgrow(tmdbReset, Priority.ALWAYS);
        tmdbBusy.setMaxSize(18, 18);
        tmdbBusy.setVisible(false);
        tmdbBusy.setManaged(false);
        tmdbBusyLabel.getStyleClass().add("tmdb-match-meta");
        tmdbBusyLabel.setVisible(false);
        tmdbBusyLabel.setManaged(false);
        HBox tmdbBusyRow = new HBox(8, tmdbBusy, tmdbBusyLabel);
        tmdbBusyRow.setAlignment(Pos.CENTER_LEFT);
        VBox tmdbSequenceControls = new VBox(6,
                tmdbFirstEpisodeLabel, tmdbFirstEpisode, tmdbApplySequence);
        VBox tmdbControls = new VBox(8,
                tmdbSecondaryControls, tmdbOrder, tmdbApply, tmdbSequenceControls, tmdbBusyRow);
        tmdbControls.getStyleClass().add("tmdb-detail-controls");

        VBox sourceSection = new VBox(6,
                sourceHeading,
                fieldRow(filenameLabel, filenameValue),
                fieldRow(folderLabel, folderValue),
                fieldRow(extensionLabel, extensionValue));
        sourceSection.getStyleClass().add("detail-panel-section");

        VBox detectionSection = new VBox(6,
                detectionHeading,
                fieldRow(mediaTypeLabel, mediaTypeValue),
                fieldRow(statusLabel, statusValue),
                fieldRow(confidenceLabel, confidenceValue),
                fieldRow(seasonEpisodeLabel, seasonEpisodeValue),
                fieldRow(inputPatternLabel, inputPatternEditor),
                inputPatternApply);
        detectionSection.getStyleClass().add("detail-panel-section");

        VBox tmdbSection = new VBox(6,
                tmdbHeading,
                fieldRow(tmdbSeedLabel, tmdbSeedValue),
                fieldRow(tmdbTypeLabel, tmdbTypeValue),
                fieldRow(tmdbStatusLabel, tmdbStatusValue),
                tmdbMatchCard,
                tmdbTargetHint,
                tmdbControls);
        tmdbSection.getStyleClass().add("detail-panel-section");

        VBox destinationSection = new VBox(6,
                destinationHeading,
                fieldRow(proposedLabel, proposedValue),
                fieldRow(destinationLabel, destinationValue));
        destinationSection.getStyleClass().add("detail-panel-section");

        VBox notesSection = new VBox(6,
                notesHeading,
                fieldRow(conflictLabel, conflictValue),
                fieldRow(alertLabel, alertValue),
                fieldRow(noteLabel, noteValue));
        notesSection.getStyleClass().add("detail-panel-section");

        content = new VBox(12, tmdbSection, destinationSection, notesSection);

        contentScroll = new ScrollPane(content);
        contentScroll.getStyleClass().add("detail-scroll");
        contentScroll.setFitToWidth(true);
        contentScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        contentScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        contentScroll.setVisible(false);
        contentScroll.setManaged(false);
        contentScroll.setMinHeight(0);
        contentScroll.setPrefHeight(0);

        root = new VBox(12, emptyState, contentScroll);
        root.getStyleClass().add("detail-panel");
        root.setMinWidth(320);
        root.setMinHeight(0);
        root.setPrefWidth(360);
        root.setMaxWidth(420);
        root.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(contentScroll, Priority.ALWAYS);

        applyLanguage(AppLanguage.FRENCH);
    }

    public Region root() {
        return root;
    }

    public void setOnApplyCandidate(BiConsumer<ScanRow, String> handler) {
        this.onApplyCandidate = handler == null ? (r, c) -> {} : handler;
    }

    public void setOnResetMatch(Consumer<ScanRow> handler) {
        this.onResetMatch = handler == null ? r -> {} : handler;
    }

    public void setOnApplyInputPattern(BiConsumer<ScanRow, String> handler) {
        this.onApplyInputPattern = handler == null ? (r, p) -> {} : handler;
    }

    public void setOnApplySelectedMatch(BiConsumer<ScanRow, TmdbEpisodeOrder> handler) {
        this.onApplySelectedMatch = handler == null ? (r, o) -> {} : handler;
    }

    public void setOnApplyEpisodeSequence(BiConsumer<ScanRow, TmdbEpisode> handler) {
        this.onApplyEpisodeSequence = handler == null ? (r, e) -> {} : handler;
    }

    public void setTmdbEpisodeOptions(
            List<TmdbEpisode> episodes, String selectedEpisodeId, TmdbEpisodeOrder order) {
        availableFirstEpisodes = episodes == null ? List.of() : List.copyOf(episodes);
        firstEpisodeOrder = order == null ? TmdbEpisodeOrder.AIRED : order;
        selectedFirstEpisode = availableFirstEpisodes.stream()
                .filter(episode -> episode.id().equals(selectedEpisodeId))
                .findFirst()
                .orElse(null);
        rebuildEpisodeMenu();
        tmdbFirstEpisode.setDisable(!tmdbSearchable || availableFirstEpisodes.isEmpty());
        tmdbApplySequence.setDisable(!tmdbSearchable || selectedFirstEpisode == null);
    }

    public void setOnSearchMatch(Consumer<ScanRow> handler) {
        this.onSearchMatch = handler == null ? r -> {} : handler;
    }

    public void setTmdbTargetCount(int count) {
        tmdbTargetCount = Math.max(0, count);
        updateTmdbTargetHint();
    }

    public void setTmdbCandidateOptions(List<String> candidates) {
        String current = tmdbCandidate.getValue();
        tmdbCandidate.getItems().setAll(candidates == null ? List.of() : candidates);
        if (current != null && tmdbCandidate.getItems().contains(current)) {
            tmdbCandidate.setValue(current);
        }
    }

    public void setTmdbBusy(boolean busy, String message) {
        tmdbBusyActive = busy;
        refreshTmdbControlAvailability(busy);
        tmdbBusy.setVisible(busy);
        tmdbBusy.setManaged(busy);
        tmdbBusyLabel.setText(message == null ? "" : message);
        tmdbBusyLabel.setVisible(busy);
        tmdbBusyLabel.setManaged(busy);
    }

    private void refreshTmdbControlAvailability(boolean busy) {
        tmdbSearch.setDisable(busy || !tmdbSearchable);
        tmdbOrder.setDisable(busy || !tmdbSearchable);
        tmdbApply.setDisable(busy || !tmdbSearchable || currentRow == null || currentRow.tmdbCandidate().isEmpty());
        tmdbReset.setDisable(busy || !tmdbSearchable || currentRow == null || currentRow.tmdbMatch().isEmpty());
        tmdbFirstEpisode.setDisable(busy || !tmdbSearchable || availableFirstEpisodes.isEmpty());
        tmdbApplySequence.setDisable(busy || !tmdbSearchable || selectedFirstEpisode == null);
    }

    public void applyLanguage(AppLanguage language) {
        currentLanguage = language;
        emptyTitle.setText(UiText.detailEmptyTitle(language));
        emptyHint.setText(UiText.detailEmptyHint(language));

        sourceHeading.setText(UiText.detailSectionSource(language));
        detectionHeading.setText(UiText.detailSectionDetection(language));
        tmdbHeading.setText(UiText.detailSectionTmdb(language));
        destinationHeading.setText(UiText.detailSectionDestination(language));
        notesHeading.setText(UiText.detailSectionNotes(language));

        filenameLabel.setText(UiText.detailFieldFilename(language));
        folderLabel.setText(UiText.detailFieldFolder(language));
        extensionLabel.setText(UiText.detailFieldExtension(language));
        mediaTypeLabel.setText(UiText.detailFieldMediaType(language));
        statusLabel.setText(UiText.detailFieldStatus(language));
        confidenceLabel.setText(UiText.detailFieldConfidence(language));
        seasonEpisodeLabel.setText(UiText.detailFieldSeasonEpisode(language));
        inputPatternLabel.setText(UiText.detailFieldInputPattern(language));
        inputPatternApply.setText(UiText.detailApplyToSelection(language));
        tmdbSeedLabel.setText(UiText.detailFieldGroup(language));
        tmdbTypeLabel.setText(UiText.detailFieldMediaType(language));
        tmdbStatusLabel.setText(UiText.detailFieldStatus(language));
        tmdbFirstEpisodeLabel.setText(UiText.tmdbFirstEpisode(language));
        proposedLabel.setText(UiText.detailFieldProposed(language));
        destinationLabel.setText(UiText.detailFieldDestination(language));
        conflictLabel.setText(UiText.detailFieldConflict(language));
        alertLabel.setText(UiText.detailFieldAlert(language));
        noteLabel.setText(UiText.detailFieldNote(language));

        tmdbCandidate.setPromptText(UiText.scanBatchTmdbCandidatePlaceholder(language));
        tmdbSearch.setText(UiText.tmdbSearchForMatch(language));
        tmdbOrder.setPromptText(UiText.scanBatchTmdbOrderPlaceholder(language));
        tmdbOrder.getItems().setAll(
                UiText.scanBatchTmdbOrderAired(language),
                UiText.scanBatchTmdbOrderDvd(language),
                UiText.scanBatchTmdbOrderAbsolute(language));
        tmdbApply.setText(UiText.tmdbApply(language));
        tmdbReset.setText(UiText.detailResetButton(language));
        rebuildEpisodeMenu();
        tmdbApplySequence.setText(UiText.tmdbApplySequence(language));
        updateTmdbCard(currentRow);
        updateTmdbTargetHint();
    }

    public void show(ScanRow row) {
        show(row, null);
    }

    public void show(ScanRow row, BatchTmdbMatch groupMatch) {
        if (row == null) {
            clear();
            return;
        }
        currentRow = row;
        currentGroupMatch = groupMatch;
        tmdbSearchable = isTmdbSearchable(groupMatch);
        emptyState.setVisible(false);
        emptyState.setManaged(false);
        contentScroll.setVisible(true);
        contentScroll.setManaged(true);

        filenameValue.setText(row.originalFilename());
        installTooltip(filenameValue, row.originalFilename());

        Path parent = row.sourcePath().getParent();
        String parentText = parent == null ? UiText.EMPTY : parent.toAbsolutePath().normalize().toString();
        folderValue.setText(parentText);
        installTooltip(folderValue, parentText);

        extensionValue.setText(row.extension().isEmpty() ? UiText.EMPTY : row.extension());

        mediaTypeValue.setText(ScanRowText.mediaType(row.mediaType(), currentLanguage));
        statusValue.setText(ScanRowText.status(row.status(), currentLanguage));
        confidenceValue.setText(confidenceText(row.confidence()));
        seasonEpisodeValue.setText(row.order().orElse(UiText.EMPTY));
        inputPatternEditor.setText(patternDetail(row));

        if (groupMatch == null) {
            tmdbSeedValue.setText(UiText.EMPTY);
            tmdbTypeValue.setText(UiText.EMPTY);
            tmdbStatusValue.setText(UiText.EMPTY);
        } else {
            tmdbSeedValue.setText(groupMatch.seedText(currentLanguage));
            tmdbTypeValue.setText(groupMatch.typeText(currentLanguage));
            tmdbStatusValue.setText(groupMatch.statusText(currentLanguage));
        }
        String currentMatch = row.tmdbMatch().orElse(null);
        if (currentMatch != null && !tmdbCandidate.getItems().contains(currentMatch)) {
            tmdbCandidate.getItems().add(currentMatch);
        }
        tmdbCandidate.setValue(currentMatch);
        tmdbOrder.setValue(orderLabel(row.appliedTmdbOrder().orElse(TmdbEpisodeOrder.AIRED)));
        tmdbReset.setDisable(row.tmdbMatch().isEmpty());
        updateTmdbCard(row);
        // Recompute both enabled and disabled states. A panel that previously
        // showed an ignored row must re-enable manual search as soon as that
        // row is reactivated into a media-bearing group.
        refreshTmdbControlAvailability(tmdbBusyActive);

        proposedValue.setText(row.proposedFilename().orElse(UiText.EMPTY));
        installTooltip(proposedValue, proposedValue.getText());
        destinationValue.setText(destinationText(row.destination()));
        installTooltip(destinationValue, destinationValue.getText());
        conflictValue.setText(row.conflictText().orElse(UiText.EMPTY));
        alertValue.setText(row.alertText().orElse(UiText.EMPTY));
        noteValue.setText(row.noteText().orElse(UiText.EMPTY));
        installTooltip(noteValue, noteValue.getText());
    }

    private void updateTmdbCard(ScanRow row) {
        Optional<TmdbCandidate> candidate = row == null ? Optional.empty() : row.tmdbCandidate();
        if (candidate.isEmpty()) {
            tmdbPoster.setImage(null);
            tmdbPoster.setVisible(false);
            tmdbPosterPlaceholder.setText("TMDB");
            tmdbMatchTitle.setText(UiText.tmdbNoMatchSelected(currentLanguage));
            installTooltip(tmdbMatchTitle, tmdbMatchTitle.getText());
            tmdbMatchMeta.setText(UiText.EMPTY);
            tmdbMatchId.setText(UiText.EMPTY);
            tmdbMatchOverview.setText("");
            tmdbReset.setDisable(true);
            tmdbApply.setDisable(true);
            return;
        }
        TmdbCandidate value = candidate.orElseThrow();
        tmdbMatchTitle.setText(value.identity().displayName());
        installTooltip(tmdbMatchTitle, tmdbMatchTitle.getText());
        tmdbMatchMeta.setText(ScanRowText.mediaType(value.identity().mediaType(), currentLanguage)
                + value.year().map(year -> " • " + year).orElse(""));
        tmdbMatchId.setText(UiText.tmdbIdLabel(currentLanguage) + ": " + value.identity().id());
        tmdbMatchOverview.setText(shortOverview(localizedOverview(value).filter(v -> !v.isBlank())
                .orElseGet(() -> UiText.tmdbNoDescription(currentLanguage))));
        tmdbPosterPlaceholder.setText("TMDB");
        tmdbPoster.setVisible(false);
        tmdbPoster.setImage(null);
        value.posterUrl().ifPresent(url -> {
            Image image = new Image(url, true);
            image.errorProperty().addListener((obs, was, failed) -> {
                if (failed) {
                    System.getLogger(RowDetailPanel.class.getName())
                            .log(System.Logger.Level.DEBUG, "TMDB poster failed to load: " + url);
                    tmdbPoster.setImage(null);
                    tmdbPoster.setVisible(false);
                }
            });
            tmdbPoster.setImage(image);
            tmdbPoster.setVisible(true);
        });
        tmdbReset.setDisable(false);
        tmdbApply.setDisable(false);
        updateTmdbTargetHint();
    }

    private static String shortOverview(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 360) {
            return normalized;
        }
        return normalized.substring(0, 357).stripTrailing() + "...";
    }

    private Optional<String> localizedOverview(TmdbCandidate candidate) {
        Optional<String> primary = currentLanguage == AppLanguage.FRENCH
                ? candidate.frenchOverview()
                : candidate.englishOverview();
        Optional<String> secondary = currentLanguage == AppLanguage.FRENCH
                ? candidate.englishOverview()
                : candidate.frenchOverview();
        return primary.or(() -> secondary).or(candidate::overview);
    }

    private TmdbEpisodeOrder selectedOrder() {
        String value = tmdbOrder.getValue();
        if (value == null || value.isBlank()) {
            return TmdbEpisodeOrder.AIRED;
        }
        if (value.equals(UiText.scanBatchTmdbOrderDvd(currentLanguage))) {
            return TmdbEpisodeOrder.DVD;
        }
        if (value.equals(UiText.scanBatchTmdbOrderAbsolute(currentLanguage))) {
            return TmdbEpisodeOrder.ABSOLUTE;
        }
        return TmdbEpisodeOrder.AIRED;
    }

    private void rebuildEpisodeMenu() {
        tmdbFirstEpisode.getItems().clear();
        for (TmdbEpisodeMenuModel.Group group
                : TmdbEpisodeMenuModel.groups(availableFirstEpisodes, firstEpisodeOrder)) {
            Menu groupMenu = new Menu(group.kind() == TmdbEpisodeMenuModel.GroupKind.SEASON
                    ? group.start() == 0
                            ? UiText.tmdbEpisodeSpecials(currentLanguage)
                            : UiText.tmdbEpisodeSeason(currentLanguage, group.start())
                    : UiText.tmdbEpisodeAbsoluteRange(currentLanguage, group.start(), group.end()));
            for (TmdbEpisode episode : group.episodes()) {
                MenuItem item = new MenuItem(episodeLabel(episode));
                item.setOnAction(event -> selectFirstEpisode(episode));
                groupMenu.getItems().add(item);
            }
            tmdbFirstEpisode.getItems().add(groupMenu);
        }
        tmdbFirstEpisode.setText(selectedFirstEpisode == null
                ? UiText.tmdbFirstEpisodePlaceholder(currentLanguage)
                : episodeLabel(selectedFirstEpisode));
    }

    private void selectFirstEpisode(TmdbEpisode episode) {
        selectedFirstEpisode = episode;
        tmdbFirstEpisode.setText(episodeLabel(episode));
        tmdbApplySequence.setDisable(!tmdbSearchable);
    }

    private String episodeLabel(TmdbEpisode episode) {
        return TmdbEpisodeMenuModel.episodeCode(episode, firstEpisodeOrder) + " · " + episode.title();
    }

    static boolean isApplyingExistingMatch(ScanRow row, String selectedLabel) {
        return row != null
                && selectedLabel != null
                && row.tmdbMatch().filter(selectedLabel::equals).isPresent()
                && row.tmdbCandidate().isPresent();
    }

    static boolean isTmdbSearchable(BatchTmdbMatch groupMatch) {
        return groupMatch == null || groupMatch.namesAMedia();
    }

    private String orderLabel(TmdbEpisodeOrder order) {
        return switch (order) {
            case DVD -> UiText.scanBatchTmdbOrderDvd(currentLanguage);
            case ABSOLUTE -> UiText.scanBatchTmdbOrderAbsolute(currentLanguage);
            case AIRED -> UiText.scanBatchTmdbOrderAired(currentLanguage);
        };
    }

    private void updateTmdbTargetHint() {
        if (tmdbTargetCount <= 0) {
            tmdbTargetHint.setText(UiText.tmdbNoFileSelected(currentLanguage));
            return;
        }
        tmdbTargetHint.setText(UiText.tmdbMatchWillApplyTo(currentLanguage, tmdbTargetCount));
    }

    public void clear() {
        currentRow = null;
        currentGroupMatch = null;
        tmdbSearchable = true;
        emptyState.setVisible(true);
        emptyState.setManaged(true);
        contentScroll.setVisible(false);
        contentScroll.setManaged(false);
    }

    private static Label sectionHeading() {
        Label heading = new Label();
        heading.getStyleClass().addAll("section-heading", "section-heading-accent");
        return heading;
    }

    private static Label fieldLabel() {
        Label label = new Label();
        label.getStyleClass().add("detail-panel-label");
        return label;
    }

    private static Label monoValue() {
        Label value = new Label(UiText.EMPTY);
        value.getStyleClass().add("detail-panel-value-mono");
        value.setWrapText(false);
        value.setMaxWidth(Double.MAX_VALUE);
        return value;
    }

    private static Label proseValue() {
        Label value = new Label(UiText.EMPTY);
        value.getStyleClass().add("detail-panel-value");
        value.setMaxWidth(Double.MAX_VALUE);
        return value;
    }

    private static VBox fieldRow(Label label, Label value) {
        VBox row = new VBox(2, label, value);
        VBox.setVgrow(value, Priority.NEVER);
        return row;
    }

    private static VBox fieldRow(Label label, TextArea value) {
        VBox row = new VBox(2, label, value);
        VBox.setVgrow(value, Priority.NEVER);
        return row;
    }

    private static String patternDetail(ScanRow row) {
        if (row.inputParse().isEmpty()) {
            return row.inputPattern().orElse("");
        }
        ScanInputParse parse = row.inputParse().orElseThrow();
        StringBuilder sb = new StringBuilder();
        sb.append(parse.summary().isBlank() ? parse.label() : parse.summary());
        if (!parse.positionsSummary().isBlank()) {
            sb.append('\n').append(parse.positionsSummary());
        }
        sb.append('\n').append("source=").append(parse.source());
        return sb.toString();
    }

    /** Key under which a label remembers the tooltip currently installed on it. */
    private static final Object TOOLTIP_KEY = new Object();

    /**
     * Tooltip.uninstall only removes the instance it is given, and this runs on
     * every refresh: without remembering what was installed, tooltips stack up
     * and a field that loses its value keeps showing the previous one.
     */
    private static void installTooltip(Label label, String text) {
        Object previous = label.getProperties().remove(TOOLTIP_KEY);
        if (previous instanceof Tooltip installed) {
            Tooltip.uninstall(label, installed);
        }
        if (text == null || text.isBlank() || UiText.EMPTY.equals(text)) {
            return;
        }
        Tooltip tooltip = new Tooltip(text);
        Tooltip.install(label, tooltip);
        label.getProperties().put(TOOLTIP_KEY, tooltip);
    }

    static String confidenceText(OptionalDouble confidence) {
        if (confidence.isEmpty()) {
            return UiText.EMPTY;
        }
        return String.format("%.0f%%", confidence.orElseThrow() * 100.0);
    }

    static String destinationText(Optional<Path> destination) {
        return destination.map(path -> path.toAbsolutePath().normalize().toString()).orElse(UiText.EMPTY);
    }
}
