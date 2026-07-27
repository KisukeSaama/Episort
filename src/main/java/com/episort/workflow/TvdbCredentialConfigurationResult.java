package com.episort.workflow;

import java.util.Optional;

public final class TvdbCredentialConfigurationResult {
    private final boolean success;
    private final boolean organizationAllowed;
    private final Optional<ApplicationError> error;

    private TvdbCredentialConfigurationResult(
            boolean success,
            boolean organizationAllowed,
            Optional<ApplicationError> error) {
        this.success = success;
        this.organizationAllowed = organizationAllowed;
        this.error = error;
    }

    public static TvdbCredentialConfigurationResult passed() {
        return new TvdbCredentialConfigurationResult(true, true, Optional.empty());
    }

    public static TvdbCredentialConfigurationResult failure(ApplicationError error) {
        return new TvdbCredentialConfigurationResult(false, false, Optional.of(error));
    }

    public boolean success() {
        return success;
    }

    public boolean organizationAllowed() {
        return organizationAllowed;
    }

    public Optional<ApplicationError> error() {
        return error;
    }
}
