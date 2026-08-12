package com.episort.ui.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SeriesIdentityDialogLayoutTest {
    @Test
    void keepsASingleResolvedIdentityDialogCompact() {
        assertEquals(300.0, SeriesIdentityDialog.dialogHeightFor(1, false));
    }

    @Test
    void reservesRoomForTheUnresolvedWarning() {
        assertEquals(338.0, SeriesIdentityDialog.dialogHeightFor(1, true));
    }

    @Test
    void capsLongIdentityListsAndLetsTheScrollPaneTakeOver() {
        assertEquals(600.0, SeriesIdentityDialog.dialogHeightFor(20, true));
    }
}
