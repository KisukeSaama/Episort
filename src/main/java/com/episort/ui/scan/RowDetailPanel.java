package com.episort.ui.scan;

import com.episort.ui.AppLanguage;
import com.episort.ui.UiText;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalDouble;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public final class RowDetailPanel {
    private static final String EMPTY = "—";

    private final VBox root;

    private final VBox emptyState;
    private final Label emptyTitle;
    private final Label emptyHint;
    private final VBox content;

    private final Label filenameValue;
    private final Label folderValue;
    private final Label extensionValue;

    private final Label mediaTypeValue;
    private final Label statusValue;
    private final Label confidenceValue;
    private final Label seasonEpisodeValue;

    private final Label proposedValue;
    private final Label destinationValue;
    private final Label conflictValue;
    private final Label alertValue;
    private final Label noteValue;

    private final Label sourceHeading;
    private final Label detectionHeading;
    private final Label destinationHeading;
    private final Label notesHeading;

    private final Label filenameLabel;
    private final Label folderLabel;
    private final Label extensionLabel;
    private final Label mediaTypeLabel;
    private final Label statusLabel;
    private final Label confidenceLabel;
    private final Label seasonEpisodeLabel;
    private final Label proposedLabel;
    private final Label destinationLabel;
    private final Label conflictLabel;
    private final Label alertLabel;
    private final Label noteLabel;

    private AppLanguage currentLanguage = AppLanguage.FRENCH;

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
        destinationHeading = sectionHeading();
        notesHeading = sectionHeading();

        filenameLabel = fieldLabel();
        folderLabel = fieldLabel();
        extensionLabel = fieldLabel();
        mediaTypeLabel = fieldLabel();
        statusLabel = fieldLabel();
        confidenceLabel = fieldLabel();
        seasonEpisodeLabel = fieldLabel();
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
        proposedValue = monoValue();
        destinationValue = monoValue();
        conflictValue = proseValue();
        alertValue = proseValue();
        noteValue = proseValue();

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
                fieldRow(seasonEpisodeLabel, seasonEpisodeValue));
        detectionSection.getStyleClass().add("detail-panel-section");

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

        content = new VBox(12, sourceSection, detectionSection, destinationSection, notesSection);
        content.setVisible(false);
        content.setManaged(false);

        root = new VBox(12, emptyState, content);
        root.getStyleClass().add("detail-panel");
        root.setMinWidth(320);
        root.setPrefWidth(360);
        root.setMaxWidth(420);

        applyLanguage(AppLanguage.FRENCH);
    }

    public Region root() {
        return root;
    }

    public void applyLanguage(AppLanguage language) {
        currentLanguage = language;
        emptyTitle.setText(UiText.detailEmptyTitle(language));
        emptyHint.setText(UiText.detailEmptyHint(language));

        sourceHeading.setText(UiText.detailSectionSource(language));
        detectionHeading.setText(UiText.detailSectionDetection(language));
        destinationHeading.setText(UiText.detailSectionDestination(language));
        notesHeading.setText(UiText.detailSectionNotes(language));

        filenameLabel.setText(UiText.detailFieldFilename(language));
        folderLabel.setText(UiText.detailFieldFolder(language));
        extensionLabel.setText(UiText.detailFieldExtension(language));
        mediaTypeLabel.setText(UiText.detailFieldMediaType(language));
        statusLabel.setText(UiText.detailFieldStatus(language));
        confidenceLabel.setText(UiText.detailFieldConfidence(language));
        seasonEpisodeLabel.setText(UiText.detailFieldSeasonEpisode(language));
        proposedLabel.setText(UiText.detailFieldProposed(language));
        destinationLabel.setText(UiText.detailFieldDestination(language));
        conflictLabel.setText(UiText.detailFieldConflict(language));
        alertLabel.setText(UiText.detailFieldAlert(language));
        noteLabel.setText(UiText.detailFieldNote(language));
    }

    public void show(ScanRow row) {
        if (row == null) {
            clear();
            return;
        }
        emptyState.setVisible(false);
        emptyState.setManaged(false);
        content.setVisible(true);
        content.setManaged(true);

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
        seasonEpisodeValue.setText(EMPTY);
        proposedValue.setText(row.proposedFilename().orElse(EMPTY));
        destinationValue.setText(destinationText(row.destination()));
        conflictValue.setText(row.conflictText().orElse(EMPTY));
        alertValue.setText(row.alertText().orElse(EMPTY));
        noteValue.setText(row.noteText().orElse(EMPTY));
    }

    public void clear() {
        emptyState.setVisible(true);
        emptyState.setManaged(true);
        content.setVisible(false);
        content.setManaged(false);
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
            case PREVIEW -> UiText.scanRowStatusPreview(language);
            case READY -> UiText.scanRowStatusReady(language);
            case WARNING -> UiText.scanRowStatusWarning(language);
            case CONFLICT -> UiText.scanRowStatusConflict(language);
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
