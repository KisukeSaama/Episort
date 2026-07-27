package com.episort.ui.scan;

import com.episort.ui.AppLanguage;
import com.episort.ui.UiText;
import java.util.OptionalDouble;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

/**
 * Shared cell rendering and value formatting for the scan table.
 *
 * <p>Pure presentation: every method turns row state into text or a style
 * class and touches nothing else. Keeping the mapping here means the table and
 * the detail panel cannot drift into showing the same value two ways.
 */
final class ScanTableCells {

    private ScanTableCells() {
    }

    /**
     * Monospaced text with a hover tooltip, muted when there is no real value.
     *
     * <p>Filenames are long and the columns are narrow: without the tooltip the
     * only way to read a truncated name is to widen the column.
     */
    static Callback<TableColumn<ScanRow, String>, TableCell<ScanRow, String>> monoEllipsis() {
        return column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                    getStyleClass().removeAll("cell-mono", "cell-muted");
                    return;
                }
                setText(item);
                if (!getStyleClass().contains("cell-mono")) {
                    getStyleClass().add("cell-mono");
                }
                if (UiText.EMPTY.equals(item)) {
                    if (!getStyleClass().contains("cell-muted")) {
                        getStyleClass().add("cell-muted");
                    }
                    setTooltip(null);
                } else {
                    getStyleClass().remove("cell-muted");
                    setTooltip(new Tooltip(item));
                }
            }
        };
    }

    /** A wide tooltip that can hold a multi-line parse breakdown. */
    static Tooltip wideTooltip(String text, double maxWidth) {
        Tooltip tooltip = new Tooltip(text);
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(maxWidth);
        return tooltip;
    }

    /** The empty-table state: an icon, a title and a hint on what to do next. */
    static VBox emptyState(String iconText, String title, String hint) {
        Label icon = new Label(iconText);
        icon.getStyleClass().add("table-empty-icon");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("table-empty-title");
        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().add("table-empty-hint");
        hintLabel.setWrapText(true);
        hintLabel.setMaxWidth(360);
        VBox box = new VBox(10, icon, titleLabel, hintLabel);
        box.getStyleClass().add("table-empty-state");
        box.setAlignment(Pos.CENTER);
        return box;
    }

    static String formatConfidence(OptionalDouble confidence) {
        if (confidence.isEmpty()) {
            return UiText.EMPTY;
        }
        return String.format("%.0f%%", confidence.orElseThrow() * 100.0);
    }

    /**
     * Placeholder orders carry a translated label; a real "S01E02" reads as-is.
     *
     * <p>The blank check comes before the switch: switching on a null string
     * throws, and the caller's own {@code orElse("")} is the only reason that
     * never surfaced.
     */
    static String orderText(String order, AppLanguage language) {
        if (order == null || order.isBlank()) {
            return UiText.EMPTY;
        }
        return switch (order) {
            case "TO_DEFINE" -> UiText.scanOrderToDefine(language);
            case "UNAVAILABLE" -> UiText.scanOrderUnavailable(language);
            default -> order;
        };
    }

    /**
     * The shape a row status takes in the table.
     *
     * <p>A clean scan is a column of identical pills, and the one row in
     * conflict drowns in it. What is fine says so in plain text; a pill is kept
     * for the rows the user still has to act on, so it stays worth spotting.
     */
    static String statusStyle(ScanRowStatus status) {
        return switch (status) {
            case OK -> "quiet";
            case REVIEW, LOW_CONFIDENCE, TVDB, TYPE, EXT, PATTERN, META, PATH, ERROR -> "warning";
            case CONFLICT, DUPLICATE -> "conflict";
            case IGNORED -> "quiet-muted";
        };
    }
}
