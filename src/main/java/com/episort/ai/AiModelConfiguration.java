package com.episort.ai;

import java.nio.file.Path;
import java.util.Optional;

public record AiModelConfiguration(AiBundledModel model, Optional<Path> externalModelPath) {
    public AiModelConfiguration {
        if (model == null) {
            throw new IllegalArgumentException("Bundled AI model is required.");
        }
        externalModelPath = externalModelPath == null ? Optional.empty() : externalModelPath;
        if (externalModelPath.isPresent()) {
            throw new IllegalArgumentException("External AI model paths are not supported.");
        }
    }

    public static AiModelConfiguration bundledOnly() {
        return new AiModelConfiguration(AiBundledModel.QWEN3_1_7B, Optional.empty());
    }

    public static AiModelConfiguration external(Path ignoredExternalModelPath) {
        throw new IllegalArgumentException("External AI model paths are not supported.");
    }
}
