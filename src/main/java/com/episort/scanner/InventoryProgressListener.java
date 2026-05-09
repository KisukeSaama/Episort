package com.episort.scanner;

@FunctionalInterface
public interface InventoryProgressListener {
    void onProgress(InventoryScanProgress progress);
}
