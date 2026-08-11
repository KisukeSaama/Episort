package com.episort.ui.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.scanner.InventoryGroupType;
import com.episort.ui.AppLanguage;
import com.episort.ui.UiText;
import org.junit.jupiter.api.Test;

class BatchTmdbMatchTest {

    private static final AppLanguage FR = AppLanguage.FRENCH;

    @Test
    void mediaGroupKeepsItsSeedAndReportsResolution() {
        BatchTmdbMatch match = new BatchTmdbMatch("My Hero Academia", InventoryGroupType.LIKELY_SERIES, 13, false);

        assertTrue(match.namesAMedia());
        assertEquals("My Hero Academia", match.seedText(FR));
        assertEquals(UiText.scanBatchTmdbStatusUnresolved(FR), match.statusText(FR));
    }

    @Test
    void sidecarGroupHidesItsInternalSeed() {
        BatchTmdbMatch match = new BatchTmdbMatch("sidecar", InventoryGroupType.SIDECAR, 2, false);

        assertFalse(match.namesAMedia());
        assertEquals(UiText.scanMediaTypeIgnored(FR), match.seedText(FR));
    }

    @Test
    void sidecarGroupIsNeverUnresolved() {
        BatchTmdbMatch match = new BatchTmdbMatch("sidecar", InventoryGroupType.SIDECAR, 2, false);

        assertEquals(UiText.EMPTY, match.statusText(FR));
    }

    @Test
    void unsupportedAndIgnoredGroupsBehaveLikeSidecars() {
        BatchTmdbMatch unsupported = new BatchTmdbMatch("unsupported", InventoryGroupType.UNSUPPORTED, 1, false);
        BatchTmdbMatch ignored = new BatchTmdbMatch("ignored", InventoryGroupType.IGNORED, 1, false);

        assertEquals(UiText.scanMediaTypeIgnored(FR), unsupported.seedText(FR));
        assertEquals(UiText.EMPTY, unsupported.statusText(FR));
        assertEquals(UiText.scanMediaTypeIgnored(FR), ignored.seedText(FR));
        assertEquals(UiText.EMPTY, ignored.statusText(FR));
    }
}
