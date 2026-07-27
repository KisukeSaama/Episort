package com.episort.ui;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.ScrollEvent;

/**
 * Keeps a {@link TableView} usable at every window size by letting it scroll
 * horizontally instead of squeezing the columns.
 *
 * <p>Columns keep their preferred widths, so a narrow viewport simply overflows
 * and the native horizontal scroll bar shows up. When the viewport is wider
 * than the sum of those widths, the surplus is handed to the flexible columns
 * so no empty filler is left on the right edge.
 *
 * <p>A manual column resize is not fought against: the new width becomes the
 * column's base width for later redistributions.
 */
public final class HorizontalScrollTable {

    private static final double EPSILON = 0.5;

    private HorizontalScrollTable() {
    }

    public static <S> void install(TableView<S> table, List<TableColumn<S, ?>> flexibleColumns) {
        new Installer<>(table, flexibleColumns).install();
    }

    private static final class Installer<S> {
        private final TableView<S> table;
        private final List<TableColumn<S, ?>> flexibleColumns;
        private final Map<TableColumn<S, ?>, Double> baseWidths = new IdentityHashMap<>();
        private final Map<TableColumn<S, ?>, Double> appliedWidths = new IdentityHashMap<>();
        private boolean applying;
        private ScrollBar verticalBar;
        private ScrollBar horizontalBar;

        Installer(TableView<S> table, List<TableColumn<S, ?>> flexibleColumns) {
            this.table = table;
            this.flexibleColumns = List.copyOf(flexibleColumns);
        }

        void install() {
            table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
            // Let the parent layout shrink the table below its column widths;
            // the horizontal scroll bar takes over from there.
            table.setMinWidth(0);

            for (TableColumn<S, ?> column : table.getColumns()) {
                baseWidths.put(column, column.getPrefWidth());
                column.widthProperty().addListener(
                        (observable, oldValue, newValue) -> onColumnWidthChanged(column, newValue.doubleValue()));
                column.visibleProperty().addListener((observable, oldValue, newValue) -> redistribute());
            }
            table.widthProperty().addListener((observable, oldValue, newValue) -> redistribute());
            table.skinProperty().addListener((observable, oldValue, newValue) -> redistribute());

            installShiftWheelScrolling();
            redistribute();
        }

        private void onColumnWidthChanged(TableColumn<S, ?> column, double width) {
            if (applying) {
                return;
            }
            Double expected = appliedWidths.get(column);
            if (expected != null && Math.abs(expected - width) < EPSILON) {
                return;
            }
            // User-driven resize: remember it as the new base width.
            baseWidths.put(column, width);
        }

        private void redistribute() {
            double available = availableWidth();
            if (available <= 0) {
                return;
            }
            double fixedSum = 0;
            double flexibleSum = 0;
            for (TableColumn<S, ?> column : table.getColumns()) {
                if (!column.isVisible()) {
                    continue;
                }
                double base = baseWidth(column);
                if (flexibleColumns.contains(column)) {
                    flexibleSum += base;
                } else {
                    fixedSum += base;
                }
            }
            double surplus = available - fixedSum - flexibleSum;

            applying = true;
            try {
                for (TableColumn<S, ?> column : table.getColumns()) {
                    if (!column.isVisible()) {
                        continue;
                    }
                    double base = baseWidth(column);
                    double target = base;
                    if (surplus > 0 && flexibleSum > 0 && flexibleColumns.contains(column)) {
                        target = base + surplus * (base / flexibleSum);
                    }
                    target = clamp(target, column.getMinWidth(), column.getMaxWidth());
                    appliedWidths.put(column, target);
                    if (Math.abs(column.getPrefWidth() - target) >= EPSILON) {
                        column.setPrefWidth(target);
                    }
                }
            } finally {
                applying = false;
            }
        }

        private double baseWidth(TableColumn<S, ?> column) {
            Double base = baseWidths.get(column);
            return base == null ? column.getPrefWidth() : base;
        }

        private double availableWidth() {
            Insets insets = table.getInsets();
            double width = table.getWidth() - insets.getLeft() - insets.getRight();
            ScrollBar vertical = verticalBar();
            if (vertical != null && vertical.isVisible()) {
                width -= vertical.getWidth();
            }
            return width;
        }

        private void installShiftWheelScrolling() {
            table.addEventFilter(ScrollEvent.SCROLL, event -> {
                if (!event.isShiftDown() || event.getDeltaY() == 0) {
                    return;
                }
                ScrollBar horizontal = horizontalBar();
                if (horizontal == null || !horizontal.isVisible()) {
                    return;
                }
                double value = horizontal.getValue() - event.getDeltaY();
                horizontal.setValue(clamp(value, horizontal.getMin(), horizontal.getMax()));
                event.consume();
            });
        }

        private ScrollBar verticalBar() {
            if (verticalBar == null) {
                verticalBar = findScrollBar(Orientation.VERTICAL);
                if (verticalBar != null) {
                    verticalBar.visibleProperty().addListener((observable, oldValue, newValue) -> redistribute());
                    verticalBar.widthProperty().addListener((observable, oldValue, newValue) -> redistribute());
                }
            }
            return verticalBar;
        }

        private ScrollBar horizontalBar() {
            if (horizontalBar == null) {
                horizontalBar = findScrollBar(Orientation.HORIZONTAL);
            }
            return horizontalBar;
        }

        private ScrollBar findScrollBar(Orientation orientation) {
            for (Node node : table.lookupAll(".scroll-bar")) {
                if (node instanceof ScrollBar bar && bar.getOrientation() == orientation) {
                    return bar;
                }
            }
            return null;
        }

        private static double clamp(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
