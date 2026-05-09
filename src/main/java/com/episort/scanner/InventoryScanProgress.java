package com.episort.scanner;

public record InventoryScanProgress(int processedFiles, int totalFiles, boolean complete) {
}
