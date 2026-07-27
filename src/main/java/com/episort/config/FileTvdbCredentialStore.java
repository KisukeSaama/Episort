package com.episort.config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

public final class FileTvdbCredentialStore implements TvdbCredentialStore {
    private static final String FORMAT = "format";
    private static final String PAYLOAD = "payload";
    private static final String API_KEY = "apiKey";
    private static final String SUBSCRIBER_PIN = "subscriberPin";
    private static final int PAYLOAD_VERSION = 1;
    private static final int MAX_FIELD_BYTES = 16_384;

    private final Path credentialFile;
    private final CredentialProtector credentialProtector;

    public FileTvdbCredentialStore(Path credentialFile) {
        this(credentialFile, platformProtector());
    }

    FileTvdbCredentialStore(Path credentialFile, CredentialProtector credentialProtector) {
        this.credentialFile = credentialFile.toAbsolutePath().normalize();
        this.credentialProtector = credentialProtector;
    }

    public static FileTvdbCredentialStore userProfileStore() {
        Path settingsFile = FileSettingsStore.userProfileStore().settingsFile().orElseThrow();
        return new FileTvdbCredentialStore(settingsFile.resolveSibling("tvdb-credentials.properties"));
    }

    @Override
    public Optional<TvdbCredentials> load() {
        if (!Files.exists(credentialFile)) {
            return Optional.empty();
        }

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(credentialFile)) {
            properties.load(inputStream);
        } catch (IOException exception) {
            throw new SettingsStoreException("Unable to load TVDB credentials.", exception);
        }

        if (credentialProtector.format().equals(properties.getProperty(FORMAT))) {
            return loadProtected(properties);
        }

        // Migrate the legacy plaintext file only after it has been read successfully.
        String apiKey = properties.getProperty(API_KEY);
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }

        TvdbCredentials credentials =
                new TvdbCredentials(apiKey, Optional.ofNullable(properties.getProperty(SUBSCRIBER_PIN)));
        save(credentials);
        return Optional.of(credentials);
    }

    @Override
    public void save(TvdbCredentials credentials) {
        byte[] plaintext = serialize(credentials);
        byte[] protectedPayload = null;
        try {
            protectedPayload = credentialProtector.protect(plaintext);
            writeProtectedPayload(protectedPayload);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
            if (protectedPayload != null) {
                Arrays.fill(protectedPayload, (byte) 0);
            }
        }
    }

    private Optional<TvdbCredentials> loadProtected(Properties properties) {
        String payload = properties.getProperty(PAYLOAD);
        if (payload == null || payload.isBlank()) {
            throw new SettingsStoreException("TVDB credential storage is invalid.", null);
        }

        byte[] protectedPayload;
        try {
            protectedPayload = Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException exception) {
            throw new SettingsStoreException("TVDB credential storage is invalid.", exception);
        }

        byte[] plaintext = null;
        try {
            plaintext = credentialProtector.unprotect(protectedPayload);
            return Optional.of(deserialize(plaintext));
        } finally {
            Arrays.fill(protectedPayload, (byte) 0);
            if (plaintext != null) {
                Arrays.fill(plaintext, (byte) 0);
            }
        }
    }

    private void writeProtectedPayload(byte[] protectedPayload) {
        Properties properties = new Properties();
        properties.setProperty(FORMAT, credentialProtector.format());
        properties.setProperty(PAYLOAD, Base64.getEncoder().encodeToString(protectedPayload));

        try {
            Files.createDirectories(credentialFile.getParent());
            Path temporaryFile = Files.createTempFile(credentialFile.getParent(), "tvdb-credentials", ".tmp");
            try {
                hardenOwnerOnlyPermissions(temporaryFile);
                try (OutputStream outputStream = Files.newOutputStream(temporaryFile)) {
                    properties.store(outputStream, "Episort TVDB credentials");
                }
                moveAtomicallyWhenPossible(temporaryFile, credentialFile);
                hardenOwnerOnlyPermissions(credentialFile);
            } catch (IOException exception) {
                Files.deleteIfExists(temporaryFile);
                throw exception;
            }
        } catch (IOException exception) {
            throw new SettingsStoreException("Unable to save TVDB credentials.", exception);
        }
    }

    private static byte[] serialize(TvdbCredentials credentials) {
        byte[] apiKey = credentials.apiKey().getBytes(StandardCharsets.UTF_8);
        byte[] subscriberPin = credentials.subscriberPin()
                .map(value -> value.getBytes(StandardCharsets.UTF_8))
                .orElse(null);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                DataOutputStream data = new DataOutputStream(output)) {
            data.writeInt(PAYLOAD_VERSION);
            writeBytes(data, apiKey);
            if (subscriberPin == null) {
                data.writeInt(-1);
            } else {
                writeBytes(data, subscriberPin);
            }
            data.flush();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new SettingsStoreException("Unable to prepare TVDB credentials.", exception);
        } finally {
            Arrays.fill(apiKey, (byte) 0);
            if (subscriberPin != null) {
                Arrays.fill(subscriberPin, (byte) 0);
            }
        }
    }

    private static TvdbCredentials deserialize(byte[] plaintext) {
        try (DataInputStream data = new DataInputStream(new ByteArrayInputStream(plaintext))) {
            if (data.readInt() != PAYLOAD_VERSION) {
                throw new SettingsStoreException("TVDB credential storage version is unsupported.", null);
            }
            String apiKey = new String(readBytes(data), StandardCharsets.UTF_8);
            int pinLength = data.readInt();
            Optional<String> subscriberPin = pinLength < 0
                    ? Optional.empty()
                    : Optional.of(new String(readBytes(data, pinLength), StandardCharsets.UTF_8));
            if (data.available() != 0) {
                throw new SettingsStoreException("TVDB credential storage is invalid.", null);
            }
            return new TvdbCredentials(apiKey, subscriberPin);
        } catch (IOException | IllegalArgumentException exception) {
            throw new SettingsStoreException("TVDB credential storage is invalid.", exception);
        }
    }

    private static void writeBytes(DataOutputStream output, byte[] value) throws IOException {
        output.writeInt(value.length);
        output.write(value);
    }

    private static byte[] readBytes(DataInputStream input) throws IOException {
        return readBytes(input, input.readInt());
    }

    private static byte[] readBytes(DataInputStream input, int length) throws IOException {
        if (length < 0 || length > MAX_FIELD_BYTES || length > input.available()) {
            throw new IOException("Invalid protected credential field.");
        }
        return input.readNBytes(length);
    }

    private static CredentialProtector platformProtector() {
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return new WindowsDpapiCredentialProtector();
        }
        return new UnsupportedCredentialProtector();
    }

    private void hardenOwnerOnlyPermissions(Path file) {
        try {
            Files.setPosixFilePermissions(file, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows ACL support will be added when the OS-backed credential store lands.
        }
    }

    private void moveAtomicallyWhenPossible(Path temporaryFile, Path targetFile) throws IOException {
        try {
            Files.move(temporaryFile, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            Files.move(temporaryFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public Optional<Path> credentialFile() {
        return Optional.of(credentialFile);
    }

    private static final class UnsupportedCredentialProtector implements CredentialProtector {
        @Override
        public String format() {
            return "unsupported";
        }

        @Override
        public byte[] protect(byte[] plaintext) {
            throw unavailable();
        }

        @Override
        public byte[] unprotect(byte[] protectedData) {
            throw unavailable();
        }

        private SettingsStoreException unavailable() {
            return new SettingsStoreException(
                    "Secure TVDB credential storage is unavailable on this operating system.", null);
        }
    }
}
