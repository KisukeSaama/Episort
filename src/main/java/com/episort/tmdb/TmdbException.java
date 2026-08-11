package com.episort.tmdb;

import com.episort.workflow.ApplicationError;
import com.episort.workflow.ErrorSeverity;

public final class TmdbException extends RuntimeException {
    private final ApplicationError error;
    private final TmdbErrorCode code;
    private final boolean retryable;

    public TmdbException(ApplicationError error) {
        super(error == null ? "TMDB error" : error.safeMessage());
        this.error = error;
        this.code = TmdbErrorCode.REQUEST_FAILED;
        this.retryable = false;
    }

    public TmdbException(TmdbErrorCode code, String message, boolean recoverable, boolean retryable) {
        this(code, message, recoverable, retryable, null);
    }

    public TmdbException(TmdbErrorCode code, String message, boolean recoverable, boolean retryable, Throwable cause) {
        super(message, cause);
        this.code = code == null ? TmdbErrorCode.REQUEST_FAILED : code;
        this.retryable = retryable;
        this.error = ApplicationError.recoverable(
                this.code.name(),
                ErrorSeverity.BLOCKING,
                message,
                "TMDB diagnostics intentionally exclude Janus caller keys, upstream credentials, and response bodies.");
    }

    public ApplicationError error() {
        return error;
    }

    public TmdbErrorCode code() {
        return code;
    }

    public boolean recoverable() {
        return error != null && error.recoverable();
    }

    public boolean retryable() {
        return retryable;
    }

    public static TmdbException recoverable(String code, String message, String details) {
        return new TmdbException(ApplicationError.recoverable(code, ErrorSeverity.BLOCKING, message, details));
    }
}
