package com.episort.ui.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.episort.ui.AppLanguage;
import com.episort.ui.UiText;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class ScanTableCellsTest {

    @Test
    void confidenceRendersAsAWholePercentage() {
        assertEquals("85%", ScanTableCells.formatConfidence(OptionalDouble.of(0.847)));
        assertEquals("100%", ScanTableCells.formatConfidence(OptionalDouble.of(1.0)));
        assertEquals("0%", ScanTableCells.formatConfidence(OptionalDouble.of(0.0)));
    }

    @Test
    void absentConfidenceReadsThePlaceholderRatherThanZero() {
        assertEquals(UiText.EMPTY, ScanTableCells.formatConfidence(OptionalDouble.empty()));
    }

    @Test
    void placeholderOrdersAreTranslatedAndRealOnesAreNot() {
        assertEquals(UiText.scanOrderToDefine(AppLanguage.FRENCH),
                ScanTableCells.orderText("TO_DEFINE", AppLanguage.FRENCH));
        assertEquals(UiText.scanOrderUnavailable(AppLanguage.FRENCH),
                ScanTableCells.orderText("UNAVAILABLE", AppLanguage.FRENCH));
        assertEquals("S01E02", ScanTableCells.orderText("S01E02", AppLanguage.FRENCH));
    }

    @Test
    void blankOrNullOrderReadsThePlaceholder() {
        assertEquals(UiText.EMPTY, ScanTableCells.orderText("", AppLanguage.FRENCH));
        assertEquals(UiText.EMPTY, ScanTableCells.orderText("   ", AppLanguage.FRENCH));
        assertEquals(UiText.EMPTY, ScanTableCells.orderText(null, AppLanguage.FRENCH));
    }

    @Test
    void everyStatusMapsToAStyle() {
        for (ScanRowStatus status : ScanRowStatus.values()) {
            assertNotEquals(null, ScanTableCells.statusStyle(status), "unmapped status: " + status);
        }
        assertEquals("conflict", ScanTableCells.statusStyle(ScanRowStatus.CONFLICT));
        assertEquals("conflict", ScanTableCells.statusStyle(ScanRowStatus.DUPLICATE));
        assertEquals("warning", ScanTableCells.statusStyle(ScanRowStatus.REVIEW));
    }

    /**
     * A clean scan is a column of identical statuses. If they wear a pill, the
     * one row in conflict is the one that stops being visible.
     */
    @Test
    void nothingToActOnStaysQuietAndOnlyActionableRowsGetAPill() {
        assertEquals("quiet", ScanTableCells.statusStyle(ScanRowStatus.OK));
        assertEquals("quiet-muted", ScanTableCells.statusStyle(ScanRowStatus.IGNORED));
        for (ScanRowStatus status : ScanRowStatus.values()) {
            if (status != ScanRowStatus.OK && status != ScanRowStatus.IGNORED) {
                assertNotEquals("quiet", ScanTableCells.statusStyle(status),
                        status + " needs the user to act, so it must keep a shape");
                assertNotEquals("quiet-muted", ScanTableCells.statusStyle(status),
                        status + " needs the user to act, so it must keep a shape");
            }
        }
    }
}
