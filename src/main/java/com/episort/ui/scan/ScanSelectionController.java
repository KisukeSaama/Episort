package com.episort.ui.scan;

import java.util.ArrayList;
import java.util.List;
import javafx.event.Event;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.Node;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/**
 * Keeps the two notions of "selected" in the scan table in step.
 *
 * <p>The table has a JavaFX selection model, and each row carries its own
 * checkbox state. They must never disagree, which is why every gesture goes
 * through here: a mutation sets the row state, re-derives the table selection
 * from it, and only then lets listeners run again. The {@code syncing} guard
 * exists so those internal rewrites do not re-enter as if the user had clicked.
 *
 * <p>One row is additionally <em>focused</em> — the one the detail panel
 * describes. It is not always the same as "checked": a plain click focuses one
 * row without checking any.
 */
final class ScanSelectionController {

    /**
     * What the controller needs from the screen. Deliberately granular: the
     * combination of calls differs per gesture (a de-selection repaints the
     * detail panel but does not re-count TMDB targets), and collapsing them
     * would quietly change behaviour.
     */
    interface Host {
        void clearDetail();

        void showDetail(ScanRow row);

        /** How many rows a TMDB action anchored on this row would touch. */
        int targetCount(ScanRow anchor);

        /** Publishes that count to the detail panel. */
        void setTargetCount(int count);

        /** Fetches TMDB candidates for a row the user just clicked. */
        void loadCandidates(ScanRow row);

        void onSelectAllStateChanged(boolean hasSelectableRows, boolean allSelected);
    }

    private final TableView<ScanRow> table;
    private final ObservableList<ScanRow> rows;
    private final FilteredList<ScanRow> filtered;
    private final SortedList<ScanRow> sorted;
    private final Host host;

    private ScanRow focused;
    private boolean syncing;
    /** Where a shift-click range starts; -1 when there is no anchor. */
    private int anchorIndex = -1;

    ScanSelectionController(
            TableView<ScanRow> table,
            ObservableList<ScanRow> rows,
            FilteredList<ScanRow> filtered,
            SortedList<ScanRow> sorted,
            Host host) {
        this.table = table;
        this.rows = rows;
        this.filtered = filtered;
        this.sorted = sorted;
        this.host = host;
    }

    /** Wires the table's own selection model to this controller. */
    void install() {
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.getSelectionModel().getSelectedItems().addListener((ListChangeListener<ScanRow>) change -> {
            if (syncing) {
                return;
            }
            if (focused != null) {
                host.setTargetCount(host.targetCount(focused));
            }
        });
        table.getSelectionModel().selectedItemProperty().addListener((observable, previous, current) -> {
            focused = current;
            if (current == null) {
                host.clearDetail();
            } else {
                host.showDetail(current);
                host.setTargetCount(host.targetCount(current));
                host.loadCandidates(current);
            }
        });
    }

    /** The row the detail panel describes, or {@code null}. */
    ScanRow focused() {
        return focused;
    }

    void clearFocus() {
        focused = null;
    }

    /** Every checked, actionable row — ignored rows can never be a target. */
    List<ScanRow> checkedRows() {
        List<ScanRow> checked = new ArrayList<>();
        for (ScanRow row : rows) {
            if (row.isSelected() && !row.isIgnored()) {
                checked.add(row);
            }
        }
        return checked;
    }

    boolean anyChecked() {
        for (ScanRow row : rows) {
            if (row.isSelected()) {
                return true;
            }
        }
        return false;
    }

    /** Remembers where a subsequent shift-click range should start. */
    void anchorOn(ScanRow row) {
        anchorIndex = sorted.indexOf(row);
    }

    void resetAnchor() {
        anchorIndex = -1;
    }

    /**
     * A click in the checkbox column: shift extends from the anchor, otherwise
     * the row toggles and becomes the new anchor.
     */
    void onCheckboxCellPressed(ScanRow row, MouseEvent event) {
        if (row.isIgnored()) {
            return;
        }
        int clickedIndex = sorted.indexOf(row);
        if (clickedIndex < 0) {
            return;
        }
        if (event.isShiftDown() && anchorIndex >= 0) {
            selectVisibleRange(anchorIndex, clickedIndex);
            return;
        }
        setRowChecked(row, !row.isSelected());
        anchorIndex = clickedIndex;
    }

    void setRowChecked(ScanRow row, boolean checked) {
        if (row == null || row.isIgnored()) {
            if (row != null) {
                row.setSelected(false);
            }
            updateSelectAllState();
            return;
        }
        inSync(() -> {
            row.setSelected(checked);
            syncTableSelectionFromRows();
            if (!checked && focused == row) {
                // Losing the focused row: fall back to whatever the table still
                // has, so the detail panel never describes a deselected row.
                ScanRow fallback = table.getSelectionModel().getSelectedItem();
                focused = fallback;
                if (fallback == null) {
                    host.clearDetail();
                } else {
                    host.showDetail(fallback);
                }
            }
        });
    }

    /** Checks or unchecks every row currently passing the filters. */
    void setAllVisibleChecked(boolean checked) {
        inSync(() -> {
            anchorIndex = -1;
            for (ScanRow row : filtered) {
                row.setSelected(checked && !row.isIgnored());
            }
            syncTableSelectionFromRows();
            ScanRow current = table.getSelectionModel().getSelectedItem();
            anchorIndex = current == null ? -1 : sorted.indexOf(current);
            focused = current;
            if (current == null) {
                host.clearDetail();
            } else {
                host.showDetail(current);
                host.setTargetCount(host.targetCount(current));
            }
        });
    }

    /** A plain click: focus this row alone and drop every checkbox. */
    void focusOnly(ScanRow row) {
        if (row == null) {
            return;
        }
        inSync(() -> {
            for (ScanRow candidate : rows) {
                candidate.setSelected(false);
            }
            anchorIndex = sorted.indexOf(row);
            table.getSelectionModel().clearSelection();
            table.getSelectionModel().select(row);
            focused = row;
            host.showDetail(row);
            host.setTargetCount(host.targetCount(row));
        });
    }

    /**
     * A right-click outside the current selection: the menu must act on the row
     * under the cursor, so it becomes the selection without touching checkboxes.
     */
    void focusForContextMenu(ScanRow row) {
        inSync(() -> {
            table.getSelectionModel().clearSelection();
            table.getSelectionModel().select(row);
            focused = row;
            host.showDetail(row);
            // An ignored row is never itself a target, even when other rows are
            // checked — the menu would otherwise promise work it will not do.
            host.setTargetCount(row.isIgnored() ? 0 : host.targetCount(row));
        });
    }

    /** Reveals a row and makes it the sole selection, scrolling if needed. */
    void reveal(ScanRow row) {
        table.getSelectionModel().select(row);
        table.scrollTo(row);
        focused = row;
        host.showDetail(row);
    }

    void clearAll() {
        inSync(() -> {
            for (ScanRow row : rows) {
                row.setSelected(false);
            }
            anchorIndex = -1;
            table.getSelectionModel().clearSelection();
            focused = null;
            host.clearDetail();
        });
    }

    /**
     * A left click landing outside the table and the detail column drops the
     * selection — the same "click away to deselect" reflex as a file explorer.
     */
    void clearOnClickOutside(MouseEvent event, Node... insideRegions) {
        if (event.getButton() != MouseButton.PRIMARY || !anyChecked()) {
            return;
        }
        for (Node region : insideRegions) {
            if (isEventInside(event, region)) {
                return;
            }
        }
        clearAll();
    }

    /** Reports whether the header checkbox should read checked, and be enabled. */
    void updateSelectAllState() {
        boolean hasSelectableRows = filtered.stream().anyMatch(row -> !row.isIgnored());
        boolean allSelected = hasSelectableRows
                && filtered.stream().filter(row -> !row.isIgnored()).allMatch(ScanRow::isSelected);
        host.onSelectAllStateChanged(hasSelectableRows, allSelected);
    }

    /** Restores JavaFX's row highlight after a filter refresh clears it. */
    void restoreTableSelection() {
        inSync(this::syncTableSelectionFromRows);
    }

    /** Whether a press originated in the checkbox column rather than the row. */
    static boolean isCheckboxCellEvent(MouseEvent event) {
        return ancestorMatches(event, node -> node.getStyleClass().contains("selection-cell"));
    }

    /** Inline editors own their click sequence so JavaFX can detect a double-click. */
    static boolean shouldFocusRow(MouseEvent event) {
        return shouldFocusRow(
                event.getButton(),
                isCheckboxCellEvent(event),
                ancestorMatches(event, node -> node instanceof CommitOnFocusLossStringCell<?>));
    }

    static boolean shouldFocusRow(MouseButton button, boolean checkboxCell, boolean inlineEditCell) {
        return button == MouseButton.PRIMARY && !checkboxCell && !inlineEditCell;
    }

    static boolean shouldSelectAllRows(KeyEvent event) {
        return shouldSelectAllRows(
                event.getCode(),
                event.isShortcutDown(),
                ancestorMatches(event, node -> node instanceof TextInputControl));
    }

    static boolean shouldSelectAllRows(KeyCode code, boolean shortcutDown, boolean editingText) {
        return code == KeyCode.A && shortcutDown && !editingText;
    }

    /** Ignored rows are greyed out so they visibly sit outside the run. */
    static void updateRowStyle(TableRow<ScanRow> tableRow, ScanRow item) {
        tableRow.getStyleClass().remove("ignored-row");
        if (!tableRow.isEmpty() && item != null && item.isIgnored()) {
            tableRow.getStyleClass().add("ignored-row");
        }
    }

    private void selectVisibleRange(int anchor, int clicked) {
        int start = Math.max(0, Math.min(anchor, clicked));
        int end = Math.min(sorted.size() - 1, Math.max(anchor, clicked));
        if (start > end) {
            return;
        }
        inSync(() -> {
            for (int index = start; index <= end; index++) {
                ScanRow row = sorted.get(index);
                if (row.isIgnored()) {
                    row.setSelected(false);
                    continue;
                }
                row.setSelected(true);
                if (!table.getSelectionModel().getSelectedItems().contains(row)) {
                    table.getSelectionModel().select(row);
                }
            }
            syncTableSelectionFromRows();
            ScanRow clickedRow = sorted.get(clicked);
            focused = clickedRow;
            host.showDetail(clickedRow);
            host.setTargetCount(host.targetCount(clickedRow));
        });
    }

    /** Row checkbox state is the source of truth; the table follows it. */
    private void syncTableSelectionFromRows() {
        table.getSelectionModel().clearSelection();
        for (ScanRow row : sorted) {
            if (row.isSelected() && !row.isIgnored()) {
                table.getSelectionModel().select(row);
            }
        }
    }

    /**
     * Runs a selection rewrite with listeners muted, then repaints once. Without
     * the guard each intermediate write would look like a fresh user gesture.
     */
    private void inSync(Runnable mutation) {
        syncing = true;
        try {
            mutation.run();
        } finally {
            syncing = false;
            updateSelectAllState();
            table.refresh();
        }
    }

    private static boolean isEventInside(MouseEvent event, Node ancestor) {
        return ancestorMatches(event, node -> node == ancestor);
    }

    private static boolean ancestorMatches(Event event, java.util.function.Predicate<Node> test) {
        if (!(event.getTarget() instanceof Node target)) {
            return false;
        }
        for (Node current = target; current != null; current = current.getParent()) {
            if (test.test(current)) {
                return true;
            }
        }
        return false;
    }
}
