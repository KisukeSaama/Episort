package com.episort.fankai;

public final class FankaiException extends RuntimeException {
    public FankaiException(String message) {
        super(message);
    }

    public FankaiException(String message, Throwable cause) {
        super(message, cause);
    }
}
