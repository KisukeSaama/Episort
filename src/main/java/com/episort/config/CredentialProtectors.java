package com.episort.config;

import java.util.Locale;

/** Picks the secret protection the running platform offers. */
final class CredentialProtectors {
    private CredentialProtectors() {
    }

    static CredentialProtector forCurrentPlatform() {
        return forPlatform(System.getProperty("os.name", ""));
    }

    static CredentialProtector forPlatform(String osName) {
        if (osName.toLowerCase(Locale.ROOT).contains("win")) {
            return new WindowsDpapiCredentialProtector();
        }
        return new UnprotectedCredentialProtector();
    }

    /**
     * Finds the protector able to read a stored secret, whatever platform
     * wrote it. A secret protected by DPAPI on another machine cannot be read
     * here, and that is reported rather than guessed around.
     */
    static CredentialProtector forFormat(String format) {
        if (WindowsDpapiCredentialProtector.FORMAT.equals(format)) {
            return new WindowsDpapiCredentialProtector();
        }
        if (UnprotectedCredentialProtector.FORMAT.equals(format)) {
            return new UnprotectedCredentialProtector();
        }
        throw new InvalidSettingsException("Unknown credential protection format: " + format, null);
    }
}
