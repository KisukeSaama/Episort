package com.episort.ui.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.scanner.InventoryGroupType;
import com.episort.ui.AppLanguage;
import com.episort.ui.UiText;
import org.junit.jupiter.api.Test;

class BatchTvdbMatchTest {

    private static final AppLanguage FR = AppLanguage.FRENCH;

    @Test
    void mediaGroupKeepsItsSeedAndReportsResolution() {
        BatchTvdbMatch match = new BatchTvdbMatch("My Hero Academia", InventoryGroupType.LIKELY_SERIES, 13, false);

        assertTrue(match.namesAMedia());
        assertEquals("My Hero Academia", match.seedText(FR));
        assertEquals(UiText.scanBatchTvdbStatusUnresolved(FR), match.statusText(FR));
    }

    @Test
    void sidecarGroupHidesItsInternalSeed() {
        BatchTvdbMatch match = new BatchTvdbMatch("sidecar", InventoryGroupType.SIDECAR, 2, false);

        assertFalse(match.namesAMedia());
        assertEquals(UiText.scanMediaTypeIgnored(FR), match.seedText(FR));
    }

    @Test
    void sidecarGroupIsNeverUnresolved() {
        BatchTvdbMatch match = new BatchTvdbMatch("sidecar", InventoryGroupType.SIDECAR, 2, false);

        assertEquals(UiText.EMPTY, match.statusText(FR));
    }

    @Test
    void unsupportedAndIgnoredGroupsBehaveLikeSidecars() {
        BatchTvdbMatch unsupported = new BatchTvdbMatch("unsupported", InventoryGroupType.UNSUPPORTED, 1, false);
        BatchTvdbMatch ignored = new BatchTvdbMatch("ignored", InventoryGroupType.IGNORED, 1, false);

        assertEquals(UiText.scanMediaTypeIgnored(FR), unsupported.seedText(FR));
        assertEquals(UiText.EMPTY, unsupported.statusText(FR));
        assertEquals(UiText.scanMediaTypeIgnored(FR), ignored.seedText(FR));
        assertEquals(UiText.EMPTY, ignored.statusText(FR));
    }
}
