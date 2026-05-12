package com.episort.workflow;

import com.episort.scanner.InventoryProgressListener;
import com.episort.scanner.MediaInventoryScanner;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class InventoryWorkflowService {
    private final MediaInventoryScanner scanner;
    private final Executor executor;

    public InventoryWorkflowService(MediaInventoryScanner scanner, Executor executor) {
        this.scanner = Objects.requireNonNull(scanner, "scanner");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public CompletableFuture<InventoryScanWorkflowResult> scan(
            Path inputFolder, InventoryProgressListener progressListener) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var result = scanner.scan(inputFolder, progressListener);
                return new InventoryScanWorkflowResult(result.items(), result.groups(), result.summary());
            } catch (Exception exception) {
                throw new InventoryWorkflowException("Inventory scan failed", exception);
            }
        }, executor);
    }

    public CompletableFuture<InventoryScanWorkflowResult> scanSources(
            List<Path> inputSources, InventoryProgressListener progressListener) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var result = inputSources.size() == 1 && java.nio.file.Files.isDirectory(inputSources.getFirst())
                        ? scanner.scan(inputSources.getFirst(), progressListener)
                        : scanner.scanFiles(inputSources, progressListener);
                return new InventoryScanWorkflowResult(result.items(), result.groups(), result.summary());
            } catch (Exception exception) {
                throw new InventoryWorkflowException("Inventory scan failed", exception);
            }
        }, executor);
    }
}
