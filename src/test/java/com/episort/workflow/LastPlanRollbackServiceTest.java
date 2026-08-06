package com.episort.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.persistence.FileRollbackPlanStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LastPlanRollbackServiceTest {
    @Test
    void restoresMovedFilesAndRemovesOnlyEmptyDestinationFolders(@TempDir Path tempDir) throws IOException {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        Path original = workspace.resolve("incoming/episode.mkv");
        Path current = workspace.resolve("Show/Season 01/episode.mkv");
        Files.createDirectories(current.getParent());
        Files.writeString(current, "video");
        FileRollbackPlanStore store = new FileRollbackPlanStore(tempDir.resolve("rollback.txt"));
        LastPlanRollbackService service = new LastPlanRollbackService(store);
        UUID runId = UUID.randomUUID();
        service.record(new ExecutionReport(
                runId, workspace, List.of(FileExecutionResult.succeeded(original, current)),
                false, Optional.empty()));

        assertEquals(1, service.rollback(runId));

        assertEquals("video", Files.readString(original));
        assertFalse(Files.exists(current));
        assertFalse(Files.exists(workspace.resolve("Show")));
        assertTrue(service.availablePlan(runId).isEmpty(), "a rollback can run only once");
    }

    @Test
    void refusesTheWholeRollbackBeforeMovingAnythingWhenAnOriginalPathIsOccupied(@TempDir Path tempDir)
            throws IOException {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        Path original = workspace.resolve("incoming.mkv");
        Path current = workspace.resolve("Show/episode.mkv");
        Files.writeString(original, "new occupant");
        Files.createDirectories(current.getParent());
        Files.writeString(current, "moved video");
        FileRollbackPlanStore store = new FileRollbackPlanStore(tempDir.resolve("rollback.txt"));
        LastPlanRollbackService service = new LastPlanRollbackService(store);
        UUID runId = UUID.randomUUID();
        service.record(new ExecutionReport(
                runId, workspace, List.of(FileExecutionResult.succeeded(original, current)),
                false, Optional.empty()));

        assertThrows(IOException.class, () -> service.rollback(runId));
        assertEquals("new occupant", Files.readString(original));
        assertEquals("moved video", Files.readString(current));
        assertTrue(service.availablePlan(runId).isPresent());
    }

    @Test
    void reportsTheExactMoveAndProgressWhileRestoring(@TempDir Path tempDir) throws IOException {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        Path original = workspace.resolve("incoming/episode.mkv");
        Path current = workspace.resolve("Show/Season 01/episode.mkv");
        Files.createDirectories(current.getParent());
        Files.writeString(current, "video");
        FileRollbackPlanStore store = new FileRollbackPlanStore(tempDir.resolve("rollback.txt"));
        LastPlanRollbackService service = new LastPlanRollbackService(store);
        UUID runId = UUID.randomUUID();
        service.record(new ExecutionReport(
                runId, workspace, List.of(FileExecutionResult.succeeded(original, current)),
                false, Optional.empty()));
        List<RollbackProgress> updates = new ArrayList<>();

        assertEquals(1, service.rollback(runId, updates::add));

        assertEquals(List.of(0, 1), updates.stream().map(RollbackProgress::completed).toList());
        assertTrue(updates.stream().allMatch(update -> update.total() == 1));
        assertTrue(updates.stream().allMatch(update -> update.currentPath().equals(current)));
        assertTrue(updates.stream().allMatch(update -> update.originalPath().equals(original)));
    }

    @Test
    void refusesRollbackWhenTheCurrentFileContentNoLongerMatchesTheRecordedFingerprint(
            @TempDir Path tempDir) throws IOException {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        Path original = workspace.resolve("incoming/episode.mkv");
        Path current = workspace.resolve("Show/Season 01/episode.mkv");
        Files.createDirectories(current.getParent());
        Files.writeString(current, "original video bytes");
        FileRollbackPlanStore store = new FileRollbackPlanStore(tempDir.resolve("rollback.txt"));
        LastPlanRollbackService service = new LastPlanRollbackService(store);
        UUID runId = UUID.randomUUID();
        service.record(new ExecutionReport(
                runId, workspace, List.of(FileExecutionResult.succeeded(original, current)),
                false, Optional.empty()));

        Files.writeString(current, "different video byte");

        IOException failure = assertThrows(IOException.class, () -> service.rollback(runId));
        assertTrue(failure.getMessage().contains("changed"));
        assertFalse(Files.exists(original));
        assertEquals("different video byte", Files.readString(current));
        assertTrue(service.availablePlan(runId).isPresent());
    }

    @Test
    void restoresTwoIndependentRecordedPlans(@TempDir Path tempDir) throws IOException {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        FileRollbackPlanStore store = new FileRollbackPlanStore(tempDir.resolve("rollback.txt"));
        LastPlanRollbackService service = new LastPlanRollbackService(store);
        UUID firstRun = recordMovedFile(service, workspace, "one");
        UUID secondRun = recordMovedFile(service, workspace, "two");

        assertEquals(1, service.rollback(secondRun));
        assertEquals(1, service.rollback(firstRun));

        assertEquals("video one", Files.readString(workspace.resolve("incoming-one.mkv")));
        assertEquals("video two", Files.readString(workspace.resolve("incoming-two.mkv")));
        assertTrue(store.loadAll().isEmpty());
    }

    private static UUID recordMovedFile(LastPlanRollbackService service, Path workspace, String name)
            throws IOException {
        Path original = workspace.resolve("incoming-" + name + ".mkv");
        Path current = workspace.resolve("Show/Season 01/episode-" + name + ".mkv");
        Files.createDirectories(current.getParent());
        Files.writeString(current, "video " + name);
        UUID runId = UUID.randomUUID();
        service.record(new ExecutionReport(
                runId, workspace, List.of(FileExecutionResult.succeeded(original, current)),
                false, Optional.empty()));
        return runId;
    }
}
