package com.episort.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

public final class FileTvdbCredentialStore implements TvdbCredentialStore {
    private static final String API_KEY = "apiKey";
    private static final String SUBSCRIBER_PIN = "subscriberPin";

    private final Path credentialFile;

    public FileTvdbCredentialStore(Path credentialFile) {
        this.credentialFile = credentialFile.toAbsolutePath().normalize();
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

        String apiKey = properties.getProperty(API_KEY);
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new TvdbCredentials(apiKey, Optional.ofNullable(properties.getProperty(SUBSCRIBER_PIN))));
    }

    @Override
    public void save(TvdbCredentials credentials) {
        Properties properties = new Properties();
        properties.setProperty(API_KEY, credentials.apiKey());
        credentials.subscriberPin().ifPresent(pin -> properties.setProperty(SUBSCRIBER_PIN, pin));

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
}
