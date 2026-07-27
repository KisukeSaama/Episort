package com.episort.scanner;

import java.nio.file.Path;
import java.util.Objects;

public record InventoryItem(
        Path sourcePath,
        String filename,
        String extension,
        Path parentFolder,
        InventoryItemType type,
        boolean operationCandidate) {
    public InventoryItem {
        Objects.requireNonNull(sourcePath, "sourcePath");
        Objects.requireNonNull(filename, "filename");
        Objects.requireNonNull(extension, "extension");
        Objects.requireNonNull(parentFolder, "parentFolder");
        Objects.requireNonNull(type, "type");
    }
}
