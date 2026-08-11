package com.episort.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class VolumeSpaceTest {
    @Test
    void computesUsedFractionFromLogicalVolumeCapacity() {
        VolumeSpace space = new VolumeSpace(4_000L, 1_500L, 2_400L);

        assertEquals(0.375, space.usedFraction(), 0.0001);
    }

    @Test
    void rejectsValuesOutsideTheLogicalVolume() {
        assertThrows(IllegalArgumentException.class, () -> new VolumeSpace(1_000L, 1_001L, 500L));
        assertThrows(IllegalArgumentException.class, () -> new VolumeSpace(1_000L, 500L, 1_001L));
    }
}
