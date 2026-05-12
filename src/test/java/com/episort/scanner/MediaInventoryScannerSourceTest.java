package com.episort.scanner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MediaInventoryScannerSourceTest {
    @TempDir
    Path temp;

    @Test
    void scansSingleSelectedFile() throws Exception {
        Path file = Files.createFile(temp.resolve("Movie.2024.mkv"));

        InventoryScanResult result = new MediaInventoryScanner().scanFiles(List.of(file), progress -> {});

        assertEquals(1, result.items().size());
        assertEquals(file, result.items().getFirst().sourcePath());
        assertEquals(InventoryItemType.SUPPORTED_VIDEO, result.items().getFirst().type());
    }

    @Test
    void scansMultipleSelectedFilesWithoutWalkingWholeFolder() throws Exception {
        Path first = Files.createFile(temp.resolve("Show.S01E01.mkv"));
        Path second = Files.createFile(temp.resolve("Show.S10E01.mkv"));
        Files.createFile(temp.resolve("ignored-by-selection.mkv"));

        InventoryScanResult result = new MediaInventoryScanner().scanFiles(List.of(second, first), progress -> {});

        assertEquals(2, result.items().size());
        assertEquals(List.of(first, second), result.items().stream().map(InventoryItem::sourcePath).toList());
    }
}
