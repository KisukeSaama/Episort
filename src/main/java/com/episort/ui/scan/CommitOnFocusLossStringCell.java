package com.episort.ui.scan;

import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.util.converter.DefaultStringConverter;

/**
 * A text cell that keeps what the user typed when they click away.
 *
 * <p>JavaFX's stock {@link TextFieldTableCell} only commits on Enter; clicking
 * elsewhere silently discards the edit, which reads as data loss. This subclass
 * installs a focus listener on the editor the first time it appears and turns
 * focus-out into a commit. Escape still cancels.
 */
class CommitOnFocusLossStringCell<S> extends TextFieldTableCell<S, String> {

    private boolean focusListenerInstalled;
    private boolean cancelingEdit;

    CommitOnFocusLossStringCell() {
        super(new DefaultStringConverter());
    }

    @Override
    public void startEdit() {
        super.startEdit();
        if (!isEditing() || focusListenerInstalled) {
            return;
        }
        if (getGraphic() instanceof TextField field) {
            focusListenerInstalled = true;
            field.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ESCAPE) {
                    cancelingEdit = true;
                    cancelEdit();
                    clearTableEdit();
                    event.consume();
                }
            });
            field.focusedProperty().addListener((observable, was, isFocused) -> {
                if (!isFocused && isEditing() && !cancelingEdit) {
                    commitEdit(field.getText());
                }
            });
        }
    }

    @Override
    public void commitEdit(String newValue) {
        if (isEditing()) {
            super.commitEdit(newValue);
        }
        cancelingEdit = false;
        clearTableEdit();
        updateItem(newValue, isEmpty());
    }

    @Override
    public void cancelEdit() {
        cancelingEdit = true;
        super.cancelEdit();
        clearTableEdit();
        cancelingEdit = false;
    }

    private void clearTableEdit() {
        TableView<S> tableView = getTableView();
        if (tableView != null) {
            tableView.edit(-1, null);
        }
    }
}
