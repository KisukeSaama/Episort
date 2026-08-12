package com.episort.ui.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ScanTableColumnsTest {

    @Test
    void ignoredAndUnknownTypesAreNeverDisplayedAsSeries() {
        assertNull(ScanTableColumns.editablePickerValue(ScanMediaType.IGNORED));
        assertNull(ScanTableColumns.editablePickerValue(ScanMediaType.UNKNOWN));
        assertEquals(ScanMediaType.SERIES,
                ScanTableColumns.editablePickerValue(ScanMediaType.SERIES));
        assertEquals(ScanMediaType.MOVIE,
                ScanTableColumns.editablePickerValue(ScanMediaType.MOVIE));
    }

    @Test
    void typePickerUnlocksAsSoonAsAnEpisodeIsReactivated() {
        ScanRow episode = new ScanRow(
                Path.of("Detective Conan - S16E30.mkv"),
                "Detective Conan - S16E30.mkv",
                "mkv",
                ScanMediaType.IGNORED,
                ScanRowStatus.IGNORED);
        episode.setInputParse(ScanInputPatternParser.parse(episode.originalFilename()));

        assertTrue(ScanTableColumns.isTypePickerDisabled(episode));

        episode.stopIgnoring();

        assertFalse(ScanTableColumns.isTypePickerDisabled(episode));
        assertEquals(ScanMediaType.SERIES,
                ScanTableColumns.editablePickerValue(episode.mediaType()));
    }
}
