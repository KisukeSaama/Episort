package com.episort.config;

public class InvalidSettingsException extends SettingsStoreException {
    public InvalidSettingsException(String message, Throwable cause) {
        super(message, cause);
    }
}
