package com.episort.ui.scan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScanFilterBarLayoutTest {

    @Test
    void groupsUseOppositeEdgesOnlyWhenBothFitWithTheRequiredGap() {
        assertTrue(ScanFilterBar.groupsFitOnOneLine(920, 280, 620));
        assertFalse(ScanFilterBar.groupsFitOnOneLine(919, 280, 620));
    }
}
