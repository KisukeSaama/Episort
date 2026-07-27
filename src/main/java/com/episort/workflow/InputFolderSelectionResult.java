package com.episort.workflow;

import java.nio.file.Path;
import java.util.Optional;

public record InputFolderSelectionResult(Optional<Path> inputFolder, Optional<ApplicationError> error) {
    public static InputFolderSelectionResult success(Path inputFolder) {
        return new InputFolderSelectionResult(Optional.of(inputFolder), Optional.empty());
    }

    public static InputFolderSelectionResult failure(ApplicationError error) {
        return new InputFolderSelectionResult(Optional.empty(), Optional.of(error));
    }

    public boolean success() {
        return error.isEmpty();
    }
}
