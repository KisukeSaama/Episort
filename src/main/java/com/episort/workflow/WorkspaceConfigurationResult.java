package com.episort.workflow;

import com.episort.config.AppSettings;
import java.util.Objects;
import java.util.Optional;

public record WorkspaceConfigurationResult(
        AppSettings settings,
        Optional<ApplicationError> error) {
    public WorkspaceConfigurationResult {
        settings = Objects.requireNonNull(settings);
        error = Objects.requireNonNull(error);
    }

    public static WorkspaceConfigurationResult success(AppSettings settings) {
        if (settings.workspaceDirectory().isEmpty()) {
            throw new IllegalArgumentException("Successful workspace configuration requires a workspace directory.");
        }
        return new WorkspaceConfigurationResult(settings, Optional.empty());
    }

    public static WorkspaceConfigurationResult failure(AppSettings settings, ApplicationError error) {
        return new WorkspaceConfigurationResult(settings, Optional.of(error));
    }

    public boolean success() {
        return error.isEmpty();
    }
}
