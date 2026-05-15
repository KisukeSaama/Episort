package com.episort.ui.scan;

import com.episort.ui.AppLanguage;
import com.episort.ui.UiText;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.BiConsumer;
import com.episort.tvdb.TvdbCandidate;
import com.episort.tvdb.TvdbEpisodeOrder;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public final class RowDetailPanel {
    private static final String EMPTY = "—";

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

    private final Label tvdbSeedValue;
    private final Label tvdbTypeValue;
    private final Label tvdbStatusValue;
    private final ImageView tvdbPoster = new ImageView();
    private final Label tvdbPosterPlaceholder = new Label();
    private final Label tvdbMatchTitle = new Label();
    private final Label tvdbMatchMeta = new Label();
    private final Label tvdbMatchId = new Label();
    private final Label tvdbMatchOverview = new Label();
    private final Label tvdbTargetHint = new Label();
    private final VBox tvdbMatchCard;
    private final ComboBox<String> tvdbCandidate = new ComboBox<>();
    private final ComboBox<String> tvdbOrder = new ComboBox<>();
    private final Button tvdbSearch = new Button();
    private final Button tvdbApply = new Button();
    private final Button tvdbReset = new Button();
    private final ProgressIndicator tvdbBusy = new ProgressIndicator();
    private final Label tvdbBusyLabel = new Label();

    private final Label proposedValue;
    private final Label destinationValue;
    private final Label conflictValue;
    private final Label alertValue;
    private final Label noteValue;

    private final Label sourceHeading;
    private final Label detectionHeading;
    private final Label tvdbHeading;
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
    private final Label tvdbSeedLabel;
    private final Label tvdbTypeLabel;
    private final Label tvdbStatusLabel;
    private final Label proposedLabel;
    private final Label destinationLabel;
    private final Label conflictLabel;
    private final Label alertLabel;
    private final Label noteLabel;

    private AppLanguage currentLanguage = AppLanguage.FRENCH;
    private ScanRow currentRow;
    private BatchTvdbMatch currentGroupMatch;
    private int tvdbTargetCount = 0;
    private BiConsumer<ScanRow, String> onApplyCandidate = (r, c) -> {};
    private BiConsumer<ScanRow, String> onApplyInputPattern = (r, p) -> {};
    private java.util.function.BiConsumer<ScanRow, TvdbEpisodeOrder> onApplySelectedMatch = (r, o) -> {};
    private java.util.function.Consumer<ScanRow> onSearchMatch = r -> {};
    private java.util.function.Consumer<ScanRow> onResetMatch = r -> {};

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
        tvdbHeading = sectionHeading();
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
        tvdbSeedLabel = fieldLabel();
        tvdbTypeLabel = fieldLabel();
        tvdbStatusLabel = fieldLabel();
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
        tvdbSeedValue = monoValue();
        tvdbTypeValue = proseValue();
        tvdbStatusValue = proseValue();
        proposedValue = monoValue();
        proposedValue.getStyleClass().add("proposed-name-value");
        destinationValue = monoValue();
        conflictValue = proseValue();
        alertValue = proseValue();
        noteValue = proseValue();

        inputPatternEditor.getStyleClass().add("ai-chat-input");
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

        tvdbPoster.setFitWidth(118);
        tvdbPoster.setFitHeight(176);
        tvdbPoster.setPreserveRatio(true);
        tvdbPoster.getStyleClass().add("tvdb-poster-image");
        tvdbPosterPlaceholder.getStyleClass().add("tvdb-poster-placeholder");
        tvdbPosterPlaceholder.setAlignment(Pos.CENTER);
        StackPane posterPane = new StackPane(tvdbPosterPlaceholder, tvdbPoster);
        posterPane.getStyleClass().add("tvdb-poster-frame");
        tvdbMatchTitle.getStyleClass().add("tvdb-match-title");
        tvdbMatchMeta.getStyleClass().add("tvdb-match-meta");
        tvdbMatchId.getStyleClass().add("tvdb-match-meta");
        tvdbMatchOverview.getStyleClass().add("tvdb-match-overview");
        tvdbMatchOverview.setWrapText(true);
        tvdbMatchOverview.setMaxHeight(62);
        tvdbTargetHint.getStyleClass().add("tvdb-match-meta");
        tvdbTargetHint.setWrapText(true);
        VBox matchText = new VBox(6, tvdbMatchTitle, tvdbMatchMeta, tvdbMatchId, tvdbMatchOverview);
        HBox.setHgrow(matchText, Priority.ALWAYS);
        StackPane posterHost = new StackPane(posterPane);
        posterHost.getStyleClass().add("tvdb-detail-poster-host");
        HBox matchBody = new HBox(10, posterHost, matchText);
        matchBody.setAlignment(Pos.TOP_LEFT);
        tvdbMatchCard = new VBox(10, matchBody);
        tvdbMatchCard.getStyleClass().add("tvdb-selected-card");

        tvdbCandidate.setMaxWidth(Double.MAX_VALUE);
        tvdbCandidate.setVisible(false);
        tvdbCandidate.setManaged(false);
        tvdbSearch.getStyleClass().add("ghost");
        tvdbSearch.setOnAction(e -> {
            if (currentRow != null) {
                onSearchMatch.accept(currentRow);
            }
        });
        tvdbApply.getStyleClass().add("primary");
        tvdbReset.getStyleClass().add("ghost");
        tvdbApply.setOnAction(e -> {
            String selected = tvdbCandidate.getValue();
            boolean selectedExistingMatch = isApplyingExistingMatch(currentRow, selected);
            if (selectedExistingMatch) {
                onApplySelectedMatch.accept(currentRow, selectedOrder());
                return;
            }
            if (selected != null && !selected.isBlank() && currentRow != null) {
                onApplyCandidate.accept(currentRow, selected);
                return;
            }
            if (currentRow != null && currentRow.tvdbCandidate().isPresent()) {
                onApplySelectedMatch.accept(currentRow, selectedOrder());
            }
        });
        tvdbReset.setOnAction(e -> {
            if (currentRow != null) {
                onResetMatch.accept(currentRow);
            }
        });
        tvdbSearch.setMaxWidth(Double.MAX_VALUE);
        tvdbApply.setMaxWidth(Double.MAX_VALUE);
        tvdbReset.setMaxWidth(Double.MAX_VALUE);
        HBox tvdbSecondaryControls = new HBox(8, tvdbSearch, tvdbReset);
        tvdbSecondaryControls.getStyleClass().add("tvdb-detail-secondary-controls");
        HBox.setHgrow(tvdbSearch, Priority.ALWAYS);
        HBox.setHgrow(tvdbReset, Priority.ALWAYS);
        tvdbBusy.setMaxSize(18, 18);
        tvdbBusy.setVisible(false);
        tvdbBusy.setManaged(false);
        tvdbBusyLabel.getStyleClass().add("tvdb-match-meta");
        tvdbBusyLabel.setVisible(false);
        tvdbBusyLabel.setManaged(false);
        HBox tvdbBusyRow = new HBox(8, tvdbBusy, tvdbBusyLabel);
        tvdbBusyRow.setAlignment(Pos.CENTER_LEFT);
        VBox tvdbControls = new VBox(8, tvdbSecondaryControls, tvdbOrder, tvdbApply, tvdbBusyRow);
        tvdbControls.getStyleClass().add("tvdb-detail-controls");

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

        VBox tvdbSection = new VBox(6,
                tvdbHeading,
                fieldRow(tvdbSeedLabel, tvdbSeedValue),
                fieldRow(tvdbTypeLabel, tvdbTypeValue),
                fieldRow(tvdbStatusLabel, tvdbStatusValue),
                tvdbMatchCard,
                tvdbTargetHint,
                tvdbControls);
        tvdbSection.getStyleClass().add("detail-panel-section");

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

        content = new VBox(12, tvdbSection, destinationSection, notesSection);

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

    public void setOnResetMatch(java.util.function.Consumer<ScanRow> handler) {
        this.onResetMatch = handler == null ? r -> {} : handler;
    }

    public void setOnApplyInputPattern(BiConsumer<ScanRow, String> handler) {
        this.onApplyInputPattern = handler == null ? (r, p) -> {} : handler;
    }

    public void setOnApplySelectedMatch(java.util.function.BiConsumer<ScanRow, TvdbEpisodeOrder> handler) {
        this.onApplySelectedMatch = handler == null ? (r, o) -> {} : handler;
    }

    public void setOnSearchMatch(java.util.function.Consumer<ScanRow> handler) {
        this.onSearchMatch = handler == null ? r -> {} : handler;
    }

    public void setTvdbTargetCount(int count) {
        tvdbTargetCount = Math.max(0, count);
        updateTvdbTargetHint();
    }

    public void setTvdbCandidateOptions(java.util.List<String> candidates) {
        String current = tvdbCandidate.getValue();
        tvdbCandidate.getItems().setAll(candidates == null ? java.util.List.of() : candidates);
        if (current != null && tvdbCandidate.getItems().contains(current)) {
            tvdbCandidate.setValue(current);
        }
    }

    public void setTvdbBusy(boolean busy, String message) {
        tvdbSearch.setDisable(busy);
        tvdbOrder.setDisable(busy);
        tvdbApply.setDisable(busy || currentRow == null || currentRow.tvdbCandidate().isEmpty());
        tvdbReset.setDisable(busy || currentRow == null || currentRow.tvdbMatch().isEmpty());
        tvdbBusy.setVisible(busy);
        tvdbBusy.setManaged(busy);
        tvdbBusyLabel.setText(message == null ? "" : message);
        tvdbBusyLabel.setVisible(busy);
        tvdbBusyLabel.setManaged(busy);
    }

    public void applyLanguage(AppLanguage language) {
        currentLanguage = language;
        emptyTitle.setText(UiText.detailEmptyTitle(language));
        emptyHint.setText(UiText.detailEmptyHint(language));

        sourceHeading.setText(UiText.detailSectionSource(language));
        detectionHeading.setText(UiText.detailSectionDetection(language));
        tvdbHeading.setText(UiText.detailSectionTvdb(language));
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
        tvdbSeedLabel.setText(language == AppLanguage.ENGLISH ? "Group" : "Groupe");
        tvdbTypeLabel.setText(UiText.detailFieldMediaType(language));
        tvdbStatusLabel.setText(UiText.detailFieldStatus(language));
        proposedLabel.setText(UiText.detailFieldProposed(language));
        destinationLabel.setText(UiText.detailFieldDestination(language));
        conflictLabel.setText(UiText.detailFieldConflict(language));
        alertLabel.setText(UiText.detailFieldAlert(language));
        noteLabel.setText(UiText.detailFieldNote(language));

        tvdbCandidate.setPromptText(UiText.scanBatchTvdbCandidatePlaceholder(language));
        tvdbSearch.setText(UiText.tvdbSearchForMatch(language));
        tvdbOrder.setPromptText(UiText.scanBatchTvdbOrderPlaceholder(language));
        tvdbOrder.getItems().setAll(
                UiText.scanBatchTvdbOrderAired(language),
                UiText.scanBatchTvdbOrderDvd(language),
                UiText.scanBatchTvdbOrderAbsolute(language));
        tvdbApply.setText(UiText.tvdbApply(language));
        tvdbReset.setText(UiText.detailResetButton(language));
        updateTvdbCard(currentRow);
        updateTvdbTargetHint();
    }

    public void show(ScanRow row) {
        show(row, null);
    }

    public void show(ScanRow row, BatchTvdbMatch groupMatch) {
        if (row == null) {
            clear();
            return;
        }
        currentRow = row;
        currentGroupMatch = groupMatch;
        emptyState.setVisible(false);
        emptyState.setManaged(false);
        contentScroll.setVisible(true);
        contentScroll.setManaged(true);

        filenameValue.setText(row.originalFilename());
        installTooltip(filenameValue, row.originalFilename());

        Path parent = row.sourcePath().getParent();
        String parentText = parent == null ? EMPTY : parent.toAbsolutePath().normalize().toString();
        folderValue.setText(parentText);
        installTooltip(folderValue, parentText);

        extensionValue.setText(row.extension().isEmpty() ? EMPTY : row.extension());

        mediaTypeValue.setText(mediaTypeText(row.mediaType(), currentLanguage));
        statusValue.setText(statusText(row.status(), currentLanguage));
        confidenceValue.setText(confidenceText(row.confidence()));
        seasonEpisodeValue.setText(row.order().orElse(EMPTY));
        inputPatternEditor.setText(patternDetail(row));

        if (groupMatch == null) {
            tvdbSeedValue.setText(EMPTY);
            tvdbTypeValue.setText(EMPTY);
            tvdbStatusValue.setText(EMPTY);
        } else {
            tvdbSeedValue.setText(groupMatch.seedName());
            tvdbTypeValue.setText(groupMatch.typeText(currentLanguage));
            tvdbStatusValue.setText(groupMatch.statusText(currentLanguage));
        }
        String currentMatch = row.tvdbMatch().orElse(null);
        if (currentMatch != null && !tvdbCandidate.getItems().contains(currentMatch)) {
            tvdbCandidate.getItems().add(currentMatch);
        }
        tvdbCandidate.setValue(currentMatch);
        tvdbOrder.setValue(orderLabel(row.appliedTvdbOrder().orElse(TvdbEpisodeOrder.AIRED)));
        tvdbReset.setDisable(row.tvdbMatch().isEmpty());
        updateTvdbCard(row);

        proposedValue.setText(row.proposedFilename().orElse(EMPTY));
        destinationValue.setText(destinationText(row.destination()));
        conflictValue.setText(row.conflictText().orElse(EMPTY));
        alertValue.setText(row.alertText().orElse(EMPTY));
        noteValue.setText(row.noteText().orElse(EMPTY));
    }

    private void updateTvdbCard(ScanRow row) {
        Optional<TvdbCandidate> candidate = row == null ? Optional.empty() : row.tvdbCandidate();
        if (candidate.isEmpty()) {
            tvdbPoster.setImage(null);
            tvdbPoster.setVisible(false);
            tvdbPosterPlaceholder.setText("TVDB");
            tvdbMatchTitle.setText(UiText.tvdbNoMatchSelected(currentLanguage));
            tvdbMatchMeta.setText(EMPTY);
            tvdbMatchId.setText(EMPTY);
            tvdbMatchOverview.setText("");
            tvdbReset.setDisable(true);
            tvdbApply.setDisable(true);
            return;
        }
        TvdbCandidate value = candidate.orElseThrow();
        tvdbMatchTitle.setText(value.identity().displayName());
        tvdbMatchMeta.setText(mediaTypeText(value.identity().mediaType(), currentLanguage)
                + value.year().map(year -> " • " + year).orElse(""));
        tvdbMatchId.setText(UiText.tvdbIdLabel(currentLanguage) + ": " + value.identity().id());
        tvdbMatchOverview.setText(shortOverview(localizedOverview(value).filter(v -> !v.isBlank())
                .orElseGet(() -> UiText.tvdbNoDescription(currentLanguage))));
        tvdbPosterPlaceholder.setText("TVDB");
        tvdbPoster.setVisible(false);
        tvdbPoster.setImage(null);
        value.posterUrl().ifPresent(url -> {
            Image image = new Image(url, true);
            image.errorProperty().addListener((obs, was, failed) -> {
                if (failed) {
                    System.getLogger(RowDetailPanel.class.getName())
                            .log(System.Logger.Level.DEBUG, "TVDB poster failed to load: " + url);
                    tvdbPoster.setImage(null);
                    tvdbPoster.setVisible(false);
                }
            });
            tvdbPoster.setImage(image);
            tvdbPoster.setVisible(true);
        });
        tvdbReset.setDisable(false);
        tvdbApply.setDisable(false);
        updateTvdbTargetHint();
    }

    private static String shortOverview(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 360) {
            return normalized;
        }
        return normalized.substring(0, 357).stripTrailing() + "...";
    }

    private Optional<String> localizedOverview(TvdbCandidate candidate) {
        Optional<String> primary = currentLanguage == AppLanguage.FRENCH
                ? candidate.frenchOverview()
                : candidate.englishOverview();
        Optional<String> secondary = currentLanguage == AppLanguage.FRENCH
                ? candidate.englishOverview()
                : candidate.frenchOverview();
        return primary.or(() -> secondary).or(candidate::overview);
    }

    private TvdbEpisodeOrder selectedOrder() {
        String value = tvdbOrder.getValue();
        if (value == null || value.isBlank()) {
            return TvdbEpisodeOrder.AIRED;
        }
        if (value.equals(UiText.scanBatchTvdbOrderDvd(currentLanguage))) {
            return TvdbEpisodeOrder.DVD;
        }
        if (value.equals(UiText.scanBatchTvdbOrderAbsolute(currentLanguage))) {
            return TvdbEpisodeOrder.ABSOLUTE;
        }
        return TvdbEpisodeOrder.AIRED;
    }

    static boolean isApplyingExistingMatch(ScanRow row, String selectedLabel) {
        return row != null
                && selectedLabel != null
                && row.tvdbMatch().filter(selectedLabel::equals).isPresent()
                && row.tvdbCandidate().isPresent();
    }

    private String orderLabel(TvdbEpisodeOrder order) {
        return switch (order) {
            case DVD -> UiText.scanBatchTvdbOrderDvd(currentLanguage);
            case ABSOLUTE -> UiText.scanBatchTvdbOrderAbsolute(currentLanguage);
            case AIRED -> UiText.scanBatchTvdbOrderAired(currentLanguage);
        };
    }

    private void updateTvdbTargetHint() {
        if (tvdbTargetCount <= 0) {
            tvdbTargetHint.setText(UiText.tvdbNoFileSelected(currentLanguage));
            return;
        }
        tvdbTargetHint.setText(UiText.tvdbMatchWillApplyTo(currentLanguage, tvdbTargetCount));
    }

    private static String mediaTypeText(com.episort.tvdb.TvdbMediaType mediaType, AppLanguage language) {
        return mediaType == com.episort.tvdb.TvdbMediaType.MOVIE
                ? UiText.tvdbMovie(language)
                : UiText.tvdbSeries(language);
    }

    public void clear() {
        currentRow = null;
        currentGroupMatch = null;
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
        Label value = new Label(EMPTY);
        value.getStyleClass().add("detail-panel-value-mono");
        value.setWrapText(false);
        value.setMaxWidth(Double.MAX_VALUE);
        return value;
    }

    private static Label proseValue() {
        Label value = new Label(EMPTY);
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

    private static void installTooltip(Label label, String text) {
        if (text == null || text.isBlank() || EMPTY.equals(text)) {
            Tooltip.uninstall(label, null);
            return;
        }
        Tooltip.install(label, new Tooltip(text));
    }

    static String mediaTypeText(ScanMediaType mediaType, AppLanguage language) {
        return switch (mediaType) {
            case SERIES -> UiText.scanMediaTypeSeries(language);
            case MOVIE -> UiText.scanMediaTypeMovie(language);
            case UNKNOWN -> UiText.scanMediaTypeUnknown(language);
            case IGNORED -> UiText.scanMediaTypeIgnored(language);
        };
    }

    static String statusText(ScanRowStatus status, AppLanguage language) {
        return switch (status) {
            case OK -> UiText.scanRowStatusOk(language);
            case REVIEW -> UiText.scanRowStatusReview(language);
            case AI -> UiText.scanRowStatusAi(language);
            case TVDB -> UiText.scanRowStatusTvdb(language);
            case TYPE -> UiText.scanRowStatusType(language);
            case EXT -> UiText.scanRowStatusExt(language);
            case PATTERN -> UiText.scanRowStatusPattern(language);
            case META -> UiText.scanRowStatusMeta(language);
            case CONFLICT -> UiText.scanRowStatusConflict(language);
            case DUPLICATE -> UiText.scanRowStatusDuplicate(language);
            case PATH -> UiText.scanRowStatusPath(language);
            case ERROR -> UiText.scanRowStatusError(language);
            case IGNORED -> UiText.scanRowStatusIgnored(language);
        };
    }

    static String confidenceText(OptionalDouble confidence) {
        if (confidence.isEmpty()) {
            return EMPTY;
        }
        return String.format("%.0f%%", confidence.orElseThrow() * 100.0);
    }

    static String destinationText(Optional<Path> destination) {
        return destination.map(path -> path.toAbsolutePath().normalize().toString()).orElse(EMPTY);
    }
}
