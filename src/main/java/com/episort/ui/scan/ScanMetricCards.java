package com.episort.ui.scan;

import com.episort.ui.AppLanguage;
import com.episort.ui.UiText;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/**
 * The headline metric strip above the scan table.
 *
 * <p>Owns the eight cards and nothing else: it renders a {@link ScanMetrics}
 * or, before a folder is loaded, the {@code —} placeholder. It never computes a
 * count itself, so the numbers on screen and the numbers under test are the
 * same numbers.
 */
final class ScanMetricCards {

    private final MetricCard total = new MetricCard();
    private final MetricCard series = new MetricCard();
    private final MetricCard movies = new MetricCard();
    private final MetricCard unknown = new MetricCard();
    private final MetricCard ignored = new MetricCard();
    private final MetricCard toProcess = new MetricCard();
    private final MetricCard conflicts = new MetricCard();
    private final MetricCard warnings = new MetricCard();
    private final FlowPane root;

    ScanMetricCards() {
        root = new FlowPane();
        root.getStyleClass().add("metric-grid");
        root.setHgap(12);
        root.setVgap(12);
        root.getChildren().addAll(
                total.root(), series.root(), movies.root(), unknown.root(),
                ignored.root(), toProcess.root(), conflicts.root(), warnings.root());
    }

    FlowPane root() {
        return root;
    }

    void applyLanguage(AppLanguage language) {
        total.setTitle(UiText.scanMetricTotal(language));
        series.setTitle(UiText.scanMetricSeries(language));
        movies.setTitle(UiText.scanMetricMovies(language));
        unknown.setTitle(UiText.scanMetricUnknown(language));
        ignored.setTitle(UiText.scanMetricIgnored(language));
        toProcess.setTitle(UiText.scanMetricToProcess(language));
        conflicts.setTitle(UiText.scanMetricConflicts(language));
        warnings.setTitle(UiText.scanMetricWarnings(language));
    }

    void show(ScanMetrics metrics) {
        total.setValue(String.valueOf(metrics.total()));
        series.setValue(String.valueOf(metrics.series()));
        movies.setValue(String.valueOf(metrics.movies()));
        unknown.setValue(String.valueOf(metrics.unknown()));
        ignored.setValue(String.valueOf(metrics.ignored()));
        toProcess.setValue(String.valueOf(metrics.toProcess()));
        conflicts.setValue(String.valueOf(metrics.conflicts()));
        warnings.setValue(String.valueOf(metrics.warnings()));
    }

    /** No folder loaded: every card reads the placeholder rather than a zero. */
    void clear() {
        for (MetricCard card : new MetricCard[] {
                total, series, movies, unknown, ignored, toProcess, conflicts, warnings}) {
            card.setValue(UiText.EMPTY);
        }
    }

    private static final class MetricCard {
        private final Label title = new Label();
        private final Label value = new Label(UiText.EMPTY);
        private final VBox root;

        MetricCard() {
            title.getStyleClass().add("card-title");
            value.getStyleClass().add("card-value-mono");
            root = new VBox(4, title, value);
            root.getStyleClass().add("card");
        }

        VBox root() {
            return root;
        }

        void setTitle(String text) {
            title.setText(text);
        }

        void setValue(String text) {
            value.setText(text);
        }
    }
}
