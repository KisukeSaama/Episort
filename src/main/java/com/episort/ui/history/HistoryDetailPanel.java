package com.episort.ui.history;

import com.episort.persistence.RunEvent;
import com.episort.ui.AppLanguage;
import com.episort.ui.UiText;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

final class HistoryDetailPanel {
    private static final String EMPTY = "—";
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final VBox root;
    private final VBox emptyState;
    private final Label emptyTitle;
    private final Label emptyHint;
    private final VBox content;

    private final Label summaryHeading;
    private final Label metricsHeading;

    private final Label timestampValue;
    private final Label workspaceValue;
    private final Label sourceValue;
    private final Label statusValue;
    private final Label summaryValue;

    private final VBox metricsList;

    HistoryDetailPanel() {
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

        summaryHeading = new Label();
        summaryHeading.getStyleClass().addAll("section-heading", "section-heading-accent");

        metricsHeading = new Label();
        metricsHeading.getStyleClass().addAll("section-heading", "section-heading-accent");

        timestampValue = monoValue();
        workspaceValue = monoValue();
        sourceValue = monoValue();
        statusValue = proseValue();
        summaryValue = proseValue();
        summaryValue.setWrapText(true);

        VBox summarySection = new VBox(8,
                summaryHeading,
                fieldRow(label(), timestampValue),
                fieldRow(label(), workspaceValue),
                fieldRow(label(), sourceValue),
                fieldRow(label(), statusValue),
                fieldRow(label(), summaryValue));
        summarySection.getStyleClass().add("detail-panel-section");

        metricsList = new VBox(4);

        VBox metricsSection = new VBox(6, metricsHeading, metricsList);
        metricsSection.getStyleClass().add("detail-panel-section");

        content = new VBox(12, summarySection, metricsSection);
        content.setVisible(false);
        content.setManaged(false);

        root = new VBox(12, emptyState, content);
        root.getStyleClass().add("detail-panel");
        root.setMinWidth(320);
        root.setPrefWidth(360);
        root.setMaxWidth(420);
    }

    Region root() {
        return root;
    }

    void applyLanguage(AppLanguage language) {
        emptyTitle.setText(UiText.historyDetailEmptyTitle(language));
        emptyHint.setText(UiText.historyDetailEmptyHint(language));
        summaryHeading.setText(UiText.historyDetailSectionSummary(language));
        metricsHeading.setText(UiText.historyDetailSectionMetrics(language));
        applyFieldLabels(language);
    }

    private void applyFieldLabels(AppLanguage language) {
        VBox summarySection = (VBox) content.getChildren().get(0);
        // children: heading, then 5 fieldRow VBox elements (each: VBox(2, labelLabel, valueLabel))
        setFieldLabel(summarySection, 1, UiText.historyColumnTimestamp(language));
        setFieldLabel(summarySection, 2, language == AppLanguage.ENGLISH ? "Workspace" : "Workspace");
        setFieldLabel(summarySection, 3, UiText.historyColumnSource(language));
        setFieldLabel(summarySection, 4, UiText.historyColumnStatus(language));
        setFieldLabel(summarySection, 5, UiText.historyColumnSummary(language));
    }

    private void setFieldLabel(VBox section, int rowIndex, String text) {
        if (rowIndex >= section.getChildren().size()) {
            return;
        }
        if (section.getChildren().get(rowIndex) instanceof VBox row && !row.getChildren().isEmpty()) {
            if (row.getChildren().get(0) instanceof Label label) {
                label.setText(text);
            }
        }
    }

    void show(RunEvent event, AppLanguage language) {
        emptyState.setVisible(false);
        emptyState.setManaged(false);
        content.setVisible(true);
        content.setManaged(true);

        timestampValue.setText(TIMESTAMP_FORMAT.format(event.occurredAt()));
        workspaceValue.setText(event.workspace().map(path -> path.toAbsolutePath().normalize().toString()).orElse(EMPTY));
        sourceValue.setText(event.subjectPath().map(path -> path.toAbsolutePath().normalize().toString()).orElse(EMPTY));
        statusValue.setText(HistoryScreen.statusText(event.status(), language));
        summaryValue.setText(event.summary().isBlank() ? EMPTY : event.summary());

        metricsList.getChildren().clear();
        if (event.metrics().isEmpty()) {
            Label emptyMetrics = new Label(EMPTY);
            emptyMetrics.getStyleClass().add("detail-panel-empty");
            metricsList.getChildren().add(emptyMetrics);
        } else {
            for (Map.Entry<String, String> entry : event.metrics().entrySet()) {
                Label name = new Label(entry.getKey());
                name.getStyleClass().add("detail-panel-label");
                Label value = new Label(entry.getValue());
                value.getStyleClass().add("detail-panel-value-mono");
                HBox row = new HBox(8, name, value);
                row.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(value, Priority.ALWAYS);
                metricsList.getChildren().add(row);
            }
        }
    }

    void clear() {
        emptyState.setVisible(true);
        emptyState.setManaged(true);
        content.setVisible(false);
        content.setManaged(false);
    }

    private static Label label() {
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

    private static VBox fieldRow(Label labelNode, Label valueNode) {
        VBox row = new VBox(2, labelNode, valueNode);
        return row;
    }
}
