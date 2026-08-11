package com.episort.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.episort.filesystem.VolumeSpace;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StorageUsagePresentationTest {
    @Test
    void formatsRealCapacityAndUsageInFrench() {
        VolumeSpace space = new VolumeSpace(
                4L * 1024 * 1024 * 1024 * 1024,
                1536L * 1024 * 1024 * 1024,
                2550L * 1024 * 1024 * 1024);

        StorageUsagePresentation presentation = StorageUsagePresentation.from(
                Optional.of(space), AppLanguage.FRENCH);

        assertEquals("38 % utilisé", presentation.percentage());
        assertEquals("1,5 To / 4 To", presentation.capacity());
        assertEquals("2,5 To disponibles", presentation.available());
        assertEquals(0.375, presentation.progress(), 0.0001);
    }

    @Test
    void usesHonestEmptyValuesWhenNoVolumeIsAvailable() {
        StorageUsagePresentation presentation = StorageUsagePresentation.from(
                Optional.empty(), AppLanguage.ENGLISH);

        assertEquals(UiText.EMPTY, presentation.percentage());
        assertEquals(UiText.EMPTY, presentation.capacity());
        assertEquals(UiText.EMPTY, presentation.available());
        assertEquals(0, presentation.progress());
    }
}
