package com.episort.ui.scan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import org.junit.jupiter.api.Test;

class ScanSelectionInteractionTest {
    @Test
    void editableCellsKeepTheirDoubleClickInsteadOfRefreshingTheRow() {
        assertFalse(ScanSelectionController.shouldFocusRow(MouseButton.PRIMARY, false, true));
    }

    @Test
    void ordinaryPrimaryClickStillFocusesTheRow() {
        assertTrue(ScanSelectionController.shouldFocusRow(MouseButton.PRIMARY, false, false));
        assertFalse(ScanSelectionController.shouldFocusRow(MouseButton.SECONDARY, false, false));
        assertFalse(ScanSelectionController.shouldFocusRow(MouseButton.PRIMARY, true, false));
    }

    @Test
    void controlASelectsTheTableExceptWhileEditingText() {
        assertTrue(ScanSelectionController.shouldSelectAllRows(KeyCode.A, true, false));
        assertFalse(ScanSelectionController.shouldSelectAllRows(KeyCode.A, false, false));
        assertFalse(ScanSelectionController.shouldSelectAllRows(KeyCode.C, true, false));
        assertFalse(ScanSelectionController.shouldSelectAllRows(KeyCode.A, true, true));
    }
}
