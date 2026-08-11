package com.episort.workflow;

import java.util.Optional;

public final class TmdbGatewayStatus {
    private final boolean success;
    private final boolean organizationAllowed;
    private final Optional<ApplicationError> error;

    private TmdbGatewayStatus(
            boolean success,
            boolean organizationAllowed,
            Optional<ApplicationError> error) {
        this.success = success;
        this.organizationAllowed = organizationAllowed;
        this.error = error;
    }

    public static TmdbGatewayStatus passed() {
        return new TmdbGatewayStatus(true, true, Optional.empty());
    }

    public static TmdbGatewayStatus failure(ApplicationError error) {
        return new TmdbGatewayStatus(false, false, Optional.of(error));
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
