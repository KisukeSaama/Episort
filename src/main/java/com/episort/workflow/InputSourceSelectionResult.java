package com.episort.workflow;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public record InputSourceSelectionResult(boolean success, List<Path> sources, Optional<ApplicationError> error) {
    public InputSourceSelectionResult {
        sources = sources == null ? List.of() : List.copyOf(sources);
        error = error == null ? Optional.empty() : error;
    }

    public static InputSourceSelectionResult success(List<Path> sources) {
        return new InputSourceSelectionResult(true, sources, Optional.empty());
    }

    public static InputSourceSelectionResult failure(ApplicationError error) {
        return new InputSourceSelectionResult(false, List.of(), Optional.of(error));
    }
}
