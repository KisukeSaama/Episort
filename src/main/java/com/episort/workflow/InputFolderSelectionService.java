package com.episort.workflow;

import com.episort.config.AppSettings;
import com.episort.filesystem.WorkspaceBoundary;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class InputFolderSelectionService {
    public InputFolderSelectionResult selectInputFolder(AppSettings settings, Path inputFolder) {
        if (settings.workspaceDirectory().isEmpty()) {
            return InputFolderSelectionResult.failure(workspaceRequired());
        }
        if (inputFolder == null) {
            return InputFolderSelectionResult.failure(inputRequired());
        }
        if (!Files.isDirectory(inputFolder)) {
            return InputFolderSelectionResult.failure(inputInvalid(inputFolder));
        }

        WorkspaceBoundary boundary = new WorkspaceBoundary(settings.workspaceDirectory().orElseThrow());
        if (!boundary.contains(inputFolder)) {
            return InputFolderSelectionResult.failure(inputOutsideWorkspace(inputFolder));
        }

        try {
            return InputFolderSelectionResult.success(inputFolder.toRealPath());
        } catch (IOException exception) {
            return InputFolderSelectionResult.failure(inputInvalid(inputFolder));
        }
    }

    private ApplicationError workspaceRequired() {
        return ApplicationError.recoverable(
                "WORKSPACE_REQUIRED",
                ErrorSeverity.BLOCKING,
                "Choose a workspace directory before selecting an input folder.",
                "No workspace is configured.");
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
