package com.episort.ui.scan;

import com.episort.tvdb.TvdbCandidate;
import com.episort.ui.AppLanguage;
import com.episort.ui.UiText;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

final class FilePropertiesDialog {
    private static final String EMPTY = "\u2014";

    private final AppLanguage language;
    private final ScanRow row;
    private final Stage stage = new Stage();
    private final Label copiedFeedback = new Label();
    private double dragOffsetX;
    private double dragOffsetY;

    FilePropertiesDialog(Window owner, AppLanguage language, ScanRow row) {
        this.language = language;
        this.row = row;
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setTitle(UiText.filePropertiesTitle(language));
        stage.setScene(buildScene());
        stage.setMinWidth(600);
        stage.setMinHeight(500);
    }

    void show() {
        stage.show();
    }

    private Scene buildScene() {
        Label title = new Label(UiText.filePropertiesTitle(language));
        title.getStyleClass().add("file-properties-title");
        Button closeIcon = new Button("X");
        closeIcon.getStyleClass().addAll("icon-button", "file-properties-close-icon");
        closeIcon.setOnAction(event -> stage.close());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, title, spacer, closeIcon);
        header.getStyleClass().add("file-properties-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setOnMousePressed(event -> {
            dragOffsetX = event.getSceneX();
            dragOffsetY = event.getSceneY();
        });
        header.setOnMouseDragged(event -> {
            stage.setX(event.getScreenX() - dragOffsetX);
            stage.setY(event.getScreenY() - dragOffsetY);
        });

        VBox content = new VBox(12,
                section(UiText.filePropertiesSectionFile(language), fileRows()),
                section(UiText.filePropertiesSectionDetection(language), detectionRows()),
                section(UiText.filePropertiesSectionTvdb(language), tvdbRows()),
                mediaSection());
        content.getStyleClass().add("file-properties-content");
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().addAll("file-properties-scroll", "detail-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        copiedFeedback.getStyleClass().add("file-properties-feedback");
        copiedFeedback.setVisible(false);
        copiedFeedback.setManaged(false);
        Button copyPath = new Button(UiText.filePropertiesCopyPath(language));
        copyPath.getStyleClass().add("primary");
        copyPath.setOnAction(event -> copyPath());
        Button close = new Button(UiText.filePropertiesClose(language));
        close.getStyleClass().add("ghost");
        close.setOnAction(event -> stage.close());
        HBox footer = new HBox(10, copiedFeedback, copyPath, close);
        footer.getStyleClass().add("file-properties-footer");
        footer.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(12, header, scroll, footer);
        root.getStyleClass().add("file-properties-dialog");
        root.setPadding(new Insets(16));
        Scene scene = new Scene(root, 680, 620);
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                stage.close();
            }
        });
        scene.getStylesheets().add(FilePropertiesDialog.class.getResource("/styles/app.css").toExternalForm());
        return scene;
    }

    private VBox section(String title, java.util.List<PropertyRow> rows) {
        Label sectionTitle = new Label(title);
        sectionTitle.getStyleClass().add("file-properties-section-title");
        GridPane grid = new GridPane();
        grid.getStyleClass().add("file-properties-grid");
        grid.setHgap(14);
        grid.setVgap(8);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(150);
        labelColumn.setPrefWidth(180);
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().setAll(labelColumn, valueColumn);
        for (int index = 0; index < rows.size(); index++) {
            PropertyRow property = rows.get(index);
            Label label = new Label(property.label());
            label.getStyleClass().add("file-properties-label");
            Region value = property.valueNode();
            grid.add(label, 0, index);
            grid.add(value, 1, index);
        }
        VBox box = new VBox(10, sectionTitle, grid);
        box.getStyleClass().add("file-properties-section");
        return box;
    }

    private java.util.List<PropertyRow> fileRows() {
        Path absolute = row.sourcePath().toAbsolutePath().normalize();
        return java.util.List.of(
                textRow(UiText.filePropertiesCurrentName(language), row.originalFilename()),
                textRow(UiText.filePropertiesProposedName(language), row.proposedFilename().orElse(EMPTY)),
                textRow(UiText.filePropertiesExtension(language), blankToEmpty(row.extension())),
                textRow(UiText.filePropertiesSize(language), formatSize(absolute)),
                textRow(UiText.filePropertiesLocation(language), pathText(absolute.getParent())),
                textAreaRow(UiText.filePropertiesFullPath(language), absolute.toString()),
                textRow(UiText.filePropertiesLastModified(language), formatLastModified(absolute)));
    }

    private java.util.List<PropertyRow> detectionRows() {
        Optional<ScanInputParse> parse = row.inputParse();
        return java.util.List.of(
                textRow(UiText.filePropertiesDetectedType(language), mediaTypeLabel(row.mediaType())),
                textRow(UiText.filePropertiesSeason(language),
                        parse.flatMap(p -> p.tokenValue(ScanInputRole.SEASON)).orElse(EMPTY)),
                textRow(UiText.filePropertiesEpisode(language),
                        parse.flatMap(p -> p.tokenValue(ScanInputRole.EPISODE)).orElse(EMPTY)),
                textRow(UiText.filePropertiesDetectedTitle(language),
                        parse.flatMap(p -> p.tokenValue(ScanInputRole.TITLE)).orElse(EMPTY)),
                textRow(UiText.filePropertiesInputPattern(language), row.inputPattern().orElse(EMPTY)),
                textRow(UiText.filePropertiesConfidence(language), confidence(row.confidence())),
                textRow(UiText.filePropertiesSource(language),
                        parse.map(p -> p.source().name()).orElse(EMPTY)));
    }

    private java.util.List<PropertyRow> tvdbRows() {
        Optional<TvdbCandidate> candidate = row.tvdbCandidate();
        return java.util.List.of(
                textRow(UiText.filePropertiesStatus(language), tvdbStatus()),
                textRow(UiText.filePropertiesTvdbTitle(language),
                        candidate.map(c -> c.identity().displayName()).or(() -> row.tvdbMatch()).orElse(EMPTY)),
                textRow(UiText.filePropertiesTvdbType(language),
                        candidate.map(c -> c.identity().mediaType().name()).orElse(EMPTY)),
                textRow(UiText.filePropertiesYear(language),
                        candidate.flatMap(TvdbCandidate::year).map(String::valueOf).orElse(EMPTY)),
                textRow(UiText.filePropertiesTvdbId(language),
                        candidate.map(c -> c.identity().id()).orElse(EMPTY)),
                textRow(UiText.filePropertiesOrder(language), row.order().orElse(EMPTY)),
                textRow(UiText.filePropertiesSource(language), tvdbSource()));
    }

    private VBox mediaSection() {
        Label message = new Label(UiText.filePropertiesMediaUnavailable(language));
        message.getStyleClass().add("file-properties-empty");
        message.setWrapText(true);
        return section(UiText.filePropertiesSectionMedia(language), java.util.List.of(
                new PropertyRow("", message)));
    }

    private PropertyRow textRow(String label, String value) {
        Label valueLabel = new Label(blankToEmpty(value));
        valueLabel.getStyleClass().add("file-properties-value");
        valueLabel.setWrapText(true);
        valueLabel.setMaxWidth(Double.MAX_VALUE);
        return new PropertyRow(label, valueLabel);
    }

    private PropertyRow textAreaRow(String label, String value) {
        TextArea area = new TextArea(blankToEmpty(value));
        area.getStyleClass().add("file-properties-path");
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefRowCount(2);
        area.setMinHeight(54);
        area.setMaxHeight(92);
        return new PropertyRow(label, area);
    }

    private void copyPath() {
        ClipboardContent content = new ClipboardContent();
        content.putString(row.sourcePath().toAbsolutePath().normalize().toString());
        Clipboard.getSystemClipboard().setContent(content);
        copiedFeedback.setText(UiText.filePropertiesPathCopied(language));
        copiedFeedback.setVisible(true);
        copiedFeedback.setManaged(true);
    }

    private String tvdbStatus() {
        if (row.tvdbSelectedByUser()) {
            return language == AppLanguage.FRENCH ? "choix utilisateur" : "user choice";
        }
        if (row.tvdbMatch().isEmpty() && row.tvdbCandidate().isEmpty()) {
            return language == AppLanguage.FRENCH ? "aucune" : "none";
        }
        if (row.status() == ScanRowStatus.OK && row.tvdbMatch().isPresent()) {
            return language == AppLanguage.FRENCH ? "appliquée" : "applied";
        }
        return language == AppLanguage.FRENCH ? "proposée" : "proposed";
    }

    private String tvdbSource() {
        if (row.tvdbSelectedByUser()) {
            return language == AppLanguage.FRENCH ? "Choix utilisateur" : "User choice";
        }
        if (row.tvdbMatch().isPresent() || row.tvdbCandidate().isPresent()) {
            return language == AppLanguage.FRENCH ? "TVDB automatique" : "Automatic TVDB";
        }
        return language == AppLanguage.FRENCH ? "Inconnu" : "Unknown";
    }

    private String mediaTypeLabel(ScanMediaType type) {
        return switch (type) {
            case SERIES -> UiText.scanMediaTypeSeries(language);
            case MOVIE -> UiText.scanMediaTypeMovie(language);
            case UNKNOWN -> UiText.scanMediaTypeUnknown(language);
            case IGNORED -> UiText.scanMediaTypeIgnored(language);
        };
    }

    private static String formatSize(Path path) {
        try {
            long bytes = Files.size(path);
            if (bytes < 1024) {
                return bytes + " B";
            }
            double value = bytes;
            String[] units = {"KB", "MB", "GB", "TB"};
            int unit = -1;
            while (value >= 1024 && unit < units.length - 1) {
                value /= 1024;
                unit++;
            }
            return NumberFormat.getNumberInstance(Locale.getDefault()).format(Math.round(value * 10.0) / 10.0)
                    + " " + units[unit];
        } catch (IOException | SecurityException ex) {
            return EMPTY;
        }
    }

    private static String formatLastModified(Path path) {
        try {
            FileTime time = Files.getLastModifiedTime(path);
            return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                    .format(time.toInstant().atZone(ZoneId.systemDefault()));
        } catch (IOException | SecurityException ex) {
            return EMPTY;
        }
    }

    private static String confidence(OptionalDouble confidence) {
        return confidence.isPresent() ? Math.round(confidence.getAsDouble() * 100) + "%" : EMPTY;
    }

    private static String blankToEmpty(String value) {
        return value == null || value.isBlank() ? EMPTY : value;
    }

    private static String pathText(Path path) {
        return path == null ? EMPTY : path.toString();
    }

    private record PropertyRow(String label, Region valueNode) {
    }
}
