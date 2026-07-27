package com.episort.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.episort.scanner.InventoryScanProgress;
import com.episort.scanner.MediaInventoryScanner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InventoryWorkflowServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void scansOnExecutorAndReturnsProgressSnapshots() throws Exception {
        Path input = Files.createDirectory(tempDir.resolve("input"));
        Files.createFile(input.resolve("Show.S01E01.mkv"));
        ArrayList<InventoryScanProgress> progressEvents = new ArrayList<>();
        var executor = Executors.newSingleThreadExecutor();
        try {
            InventoryWorkflowService service = new InventoryWorkflowService(new MediaInventoryScanner(), executor);

            InventoryScanWorkflowResult result = service.scan(input, progressEvents::add).get();

            assertFalse(result.summary().patternValidated());
            assertEquals(1, result.summary().supportedVideoCount());
            assertEquals(1, progressEvents.getLast().processedFiles());
        } finally {
            executor.shutdownNow();
        }
    }
}
