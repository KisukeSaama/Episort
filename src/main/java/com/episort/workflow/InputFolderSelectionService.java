package com.episort.workflow;

import com.episort.config.AppSettings;
import com.episort.filesystem.WorkspaceBoundary;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class InputFolderSelectionService {
    public InputFolderSelectionResult selectInputFolder(AppSettings settings, Path inputFolder) {
        if (settings.workspaceDirectory().isEmpty()) {
            return InputFolderSelectionResult.failure(workspaceRequired());
        }
        if (inputFolder == null || inputFolder.toString().isBlank()) {
            return InputFolderSelectionResult.failure(inputRequired());
        }
        if (!Files.isDirectory(inputFolder)) {
            return InputFolderSelectionResult.failure(inputInvalid(inputFolder));
        }

        Path workspace = settings.workspaceDirectory().orElseThrow();
        WorkspaceBoundary boundary;
        try {
            boundary = new WorkspaceBoundary(workspace);
        } catch (IOException exception) {
            return InputFolderSelectionResult.failure(workspaceUnavailable(workspace));
        }

        Optional<Path> resolved;
        try {
            resolved = boundary.resolveInside(inputFolder);
        } catch (IOException exception) {
            return InputFolderSelectionResult.failure(inputInvalid(inputFolder));
        }

        if (resolved.isEmpty()) {
            return InputFolderSelectionResult.failure(inputOutsideWorkspace(inputFolder));
        }
        return InputFolderSelectionResult.success(resolved.get());
    }

    private ApplicationError workspaceRequired() {
        return ApplicationError.recoverable(
                "WORKSPACE_REQUIRED",
                ErrorSeverity.BLOCKING,
                "Choose a workspace directory before selecting an input folder.",
                "No workspace is configured.");
    }

    private ApplicationError workspaceUnavailable(Path workspace) {
        return ApplicationError.recoverable(
                "WORKSPACE_UNAVAILABLE",
                ErrorSeverity.BLOCKING,
                "Re-check the configured workspace; it cannot be accessed.",
                "Workspace path is unreachable: " + workspace);
    }

    private ApplicationError inputRequired() {
        return ApplicationError.recoverable(
                "INPUT_FOLDER_REQUIRED",
                ErrorSeverity.BLOCKING,
                "Choose an input folder inside the configured workspace.",
                "Input folder selection was empty.");
    }

    private ApplicationError inputInvalid(Path inputFolder) {
        return ApplicationError.recoverable(
                "INPUT_FOLDER_INVALID",
                ErrorSeverity.BLOCKING,
                "Choose an existing readable input folder.",
                "Input folder is invalid or inaccessible: " + inputFolder);
    }

    private ApplicationError inputOutsideWorkspace(Path inputFolder) {
        return ApplicationError.recoverable(
                "INPUT_OUTSIDE_WORKSPACE",
                ErrorSeverity.BLOCKING,
                "Choose an input folder inside the configured workspace.",
                "Input folder resolves outside workspace: " + inputFolder);
    }
}
