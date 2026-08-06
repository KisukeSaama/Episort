package com.episort.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.workflow.ExecutionReport;
import com.episort.workflow.FileExecutionResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileRollbackPlanStoreTest {
    @Test
    void keepsSeveralPlansAndConsumesOnlyTheSelectedOne(@TempDir Path tempDir) throws Exception {
        FileRollbackPlanStore store = new FileRollbackPlanStore(tempDir.resolve("rollback.txt"));
        UUID firstRunId = UUID.randomUUID();
        UUID secondRunId = UUID.randomUUID();
        Path workspace = tempDir.resolve("workspace");
        Path firstSource = workspace.resolve("incoming-1.mkv");
        Path firstDestination = workspace.resolve("Show/Season 01/episode-1.mkv");
        Path secondSource = workspace.resolve("incoming-2.mkv");
        Path secondDestination = workspace.resolve("Show/Season 01/episode-2.mkv");
        Files.createDirectories(firstDestination.getParent());
        Files.writeString(firstDestination, "video one");
        Files.writeString(secondDestination, "video two");

        store.save(new ExecutionReport(
                firstRunId, workspace, List.of(FileExecutionResult.succeeded(firstSource, firstDestination)),
                false, Optional.empty()));
        store.save(new ExecutionReport(
                secondRunId, workspace, List.of(FileExecutionResult.succeeded(secondSource, secondDestination)),
                false, Optional.empty()));

        assertEquals(firstRunId, store.load(firstRunId).orElseThrow().runId());
        assertEquals(secondRunId, store.load(secondRunId).orElseThrow().runId());

        store.remove(secondRunId);

        assertTrue(store.load(secondRunId).isEmpty());
        assertTrue(store.load(firstRunId).isPresent());
        store.clear();
        assertTrue(store.loadAll().isEmpty());
    }

    @Test
    void retainsOnlyTheConfiguredNumberOfNewestPlans(@TempDir Path tempDir) throws Exception {
        FileRollbackPlanStore store = new FileRollbackPlanStore(tempDir.resolve("rollback.txt"), 2);
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        UUID first = saveRun(store, workspace, 1);
        UUID second = saveRun(store, workspace, 2);
        UUID third = saveRun(store, workspace, 3);

        assertFalse(store.load(first).isPresent());
        assertTrue(store.load(second).isPresent());
        assertTrue(store.load(third).isPresent());
        assertEquals(2, store.loadAll().size());
    }

    @Test
    void nonReversibleExecutionDoesNotDiscardRetainedPlans(@TempDir Path tempDir) throws Exception {
        FileRollbackPlanStore store = new FileRollbackPlanStore(tempDir.resolve("rollback.txt"));
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        UUID reversibleRun = saveRun(store, workspace, 1);
        Path deleted = workspace.resolve("duplicate.mkv");

        store.save(new ExecutionReport(
                UUID.randomUUID(), workspace, List.of(FileExecutionResult.deleted(deleted)),
                false, Optional.empty()));

        assertTrue(store.load(reversibleRun).isPresent());
        assertEquals(1, store.loadAll().size());
    }

    private static UUID saveRun(FileRollbackPlanStore store, Path workspace, int index) throws Exception {
        UUID runId = UUID.randomUUID();
        Path source = workspace.resolve("incoming-" + index + ".mkv");
        Path destination = workspace.resolve("Show/Season 01/episode-" + index + ".mkv");
        Files.createDirectories(destination.getParent());
        Files.writeString(destination, "video " + index);
        store.save(new ExecutionReport(
                runId, workspace, List.of(FileExecutionResult.succeeded(source, destination)),
                false, Optional.empty()));
        return runId;
    }
}
