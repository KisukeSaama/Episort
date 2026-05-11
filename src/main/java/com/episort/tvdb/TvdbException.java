package com.episort.tvdb;

import com.episort.workflow.ApplicationError;
import com.episort.workflow.ErrorSeverity;

public final class TvdbException extends RuntimeException {
    private final ApplicationError error;
    private final TvdbErrorCode code;
    private final boolean retryable;

    public TvdbException(ApplicationError error) {
        super(error == null ? "TVDB error" : error.safeMessage());
        this.error = error;
        this.code = TvdbErrorCode.REQUEST_FAILED;
        this.retryable = false;
    }

    public TvdbException(TvdbErrorCode code, String message, boolean recoverable, boolean retryable) {
        this(code, message, recoverable, retryable, null);
    }

    public TvdbException(TvdbErrorCode code, String message, boolean recoverable, boolean retryable, Throwable cause) {
        super(message, cause);
        this.code = code == null ? TvdbErrorCode.REQUEST_FAILED : code;
        this.retryable = retryable;
        this.error = ApplicationError.recoverable(
                this.code.name(),
                ErrorSeverity.BLOCKING,
                message,
                "TVDB diagnostics intentionally exclude credentials, PINs, bearer tokens, and response bodies.");
    }

    public ApplicationError error() {
        return error;
    }

    public TvdbErrorCode code() {
        return code;
    }

    public boolean recoverable() {
        return error != null && error.recoverable();
    }

    public boolean retryable() {
        return retryable;
    }

    public static TvdbException recoverable(String code, String message, String details) {
        return new TvdbException(ApplicationError.recoverable(code, ErrorSeverity.BLOCKING, message, details));
    }
}
