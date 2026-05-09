package com.episort.workflow;

import com.episort.scanner.InventoryProgressListener;
import com.episort.scanner.MediaInventoryScanner;
import java.nio.file.Path;
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
}
