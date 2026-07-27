package com.episort.ui.platform;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class NoOpTaskbarProgressAdapterTest {
    @Test
    void reportsItIsNotSupported() {
        TaskbarProgressAdapter adapter = new NoOpTaskbarProgressAdapter();

        assertFalse(adapter.isSupported());
    }

    @Test
    void allOperationsAreSilentNoOps() {
        TaskbarProgressAdapter adapter = new NoOpTaskbarProgressAdapter();

        assertDoesNotThrow(() -> {
            adapter.showIndeterminate("Scanning");
            adapter.showProgress("Scanning", 0.5);
            adapter.showNotification("Done", "All files scanned.");
            adapter.clear();
        });
    }
}
