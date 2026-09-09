package com.episort.config;

/**
 * Stores the secret as it is.
 *
 * <p>Used where the platform offers no per-user encryption API. The settings
 * file lives in the user's private configuration directory, which is the same
 * protection an OpenSSH client gives its own keys and config on those systems.
 * The format tag makes the choice visible in the file rather than implicit.
 */
final class UnprotectedCredentialProtector implements CredentialProtector {
    static final String FORMAT = "unprotected-v1";

    @Override
    public String format() {
        return FORMAT;
    }

    @Override
    public byte[] protect(byte[] plaintext) {
        return plaintext.clone();
    }

    @Override
    public byte[] unprotect(byte[] protectedData) {
        return protectedData.clone();
    }
}
