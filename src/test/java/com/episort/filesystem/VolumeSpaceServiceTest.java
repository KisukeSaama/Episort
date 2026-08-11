package com.episort.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VolumeSpaceServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void readsTheContainingLogicalVolumeRatherThanDirectoryContents() throws Exception {
        Files.writeString(temporaryDirectory.resolve("small-file.txt"), "episort");

        VolumeSpace space = new VolumeSpaceService().read(temporaryDirectory).orElseThrow();
        long fileStoreCapacity = Files.getFileStore(temporaryDirectory).getTotalSpace();

        assertEquals(fileStoreCapacity, space.totalBytes());
        assertTrue(space.totalBytes() > Files.size(temporaryDirectory.resolve("small-file.txt")));
    }
}
