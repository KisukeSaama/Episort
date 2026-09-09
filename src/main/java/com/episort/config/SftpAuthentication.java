package com.episort.config;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * How Episort proves who it is to an SFTP server.
 *
 * <p>A password, or a private key file with an optional passphrase. The secret
 * never appears in {@code toString()} so a settings object can be logged
 * without leaking it.
 */
public sealed interface SftpAuthentication {
    /** The secret that must be protected before it is persisted, empty for none. */
    Optional<String> secret();

    /** Same method, secret removed. */
    SftpAuthentication withoutSecret();

    /** Same method with the secret restored after it was read back from disk. */
    SftpAuthentication withSecret(Optional<String> secret);

    record Password(Optional<String> password) implements SftpAuthentication {
        public Password {
            password = Objects.requireNonNull(password, "password").filter(value -> !value.isEmpty());
        }

        public static Password of(String password) {
            return new Password(Optional.ofNullable(password));
        }

        @Override
        public Optional<String> secret() {
            return password;
        }

        @Override
        public SftpAuthentication withoutSecret() {
            return new Password(Optional.empty());
        }

        @Override
        public SftpAuthentication withSecret(Optional<String> secret) {
            return new Password(secret);
        }

        @Override
        public String toString() {
            return "Password[" + (password.isPresent() ? "set" : "empty") + "]";
        }
    }

    record PrivateKey(Path keyFile, Optional<String> passphrase) implements SftpAuthentication {
        public PrivateKey {
            keyFile = Objects.requireNonNull(keyFile, "keyFile").toAbsolutePath().normalize();
            passphrase = Objects.requireNonNull(passphrase, "passphrase").filter(value -> !value.isEmpty());
        }

        public static PrivateKey of(Path keyFile, String passphrase) {
            return new PrivateKey(keyFile, Optional.ofNullable(passphrase));
        }

        @Override
        public Optional<String> secret() {
            return passphrase;
        }

        @Override
        public SftpAuthentication withoutSecret() {
            return new PrivateKey(keyFile, Optional.empty());
        }

        @Override
        public SftpAuthentication withSecret(Optional<String> secret) {
            return new PrivateKey(keyFile, secret);
        }

        @Override
        public String toString() {
            return "PrivateKey[" + keyFile + ", passphrase=" + (passphrase.isPresent() ? "set" : "empty") + "]";
        }
    }
}
