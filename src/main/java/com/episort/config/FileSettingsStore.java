package com.episort.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

public final class FileSettingsStore implements SettingsStore {
    private static final String WORKSPACE_DIRECTORY = "workspaceDirectory";
    private static final String LANGUAGE = "language";

    private final Path settingsFile;

    public FileSettingsStore(Path settingsFile) {
        this(settingsFile, true);
    }

    private FileSettingsStore(Path settingsFile, boolean normalizeAbsolute) {
        this.settingsFile = normalizeAbsolute ? settingsFile.toAbsolutePath().normalize() : settingsFile.normalize();
    }

    public static FileSettingsStore userProfileStore() {
        return userProfileStore(
                System.getProperty("os.name", ""),
                System.getenv(),
                Path.of(System.getProperty("user.home", ".")));
    }

    static FileSettingsStore userProfileStore(String osName, Map<String, String> environment, Path userHome) {
        String normalizedOsName = osName.toLowerCase();
        if (normalizedOsName.contains("win")) {
            String localAppData = environment.get("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                return new FileSettingsStore(Path.of(localAppData + "\\Episort\\settings.properties"), false);
            }
            return new FileSettingsStore(Path.of(userHome + "\\AppData\\Local\\Episort\\settings.properties"), false);
        }

        if (normalizedOsName.contains("mac")) {
            return new FileSettingsStore(userHome.resolve(Path.of("Library", "Application Support", "Episort", "settings.properties")));
        }

        String xdgConfigHome = environment.get("XDG_CONFIG_HOME");
        if (xdgConfigHome != null && !xdgConfigHome.isBlank()) {
            return new FileSettingsStore(Path.of(xdgConfigHome, "episort", "settings.properties"));
        }
        return new FileSettingsStore(Path.of(
                userHome.toString(),
                ".config",
                "episort",
                "settings.properties"));
    }

    @Override
    public AppSettings load() {
        if (!Files.exists(settingsFile)) {
            return AppSettings.empty();
        }

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(settingsFile)) {
            properties.load(inputStream);
        } catch (IOException exception) {
            throw new SettingsStoreException("Unable to load Episort settings.", exception);
        }

        String workspaceDirectory = properties.getProperty(WORKSPACE_DIRECTORY);
        if (workspaceDirectory == null || workspaceDirectory.isBlank()) {
            return AppSettings.empty();
        }

        try {
            return new AppSettings(Path.of(workspaceDirectory));
        } catch (InvalidPathException exception) {
            throw new InvalidSettingsException("Invalid workspace path in settings.", exception);
        }
    }

    @Override
    public void save(AppSettings settings) {
        Properties properties = readExistingProperties();
        properties.remove(WORKSPACE_DIRECTORY);
        settings.workspaceDirectory()
                .ifPresent(workspace -> properties.setProperty(WORKSPACE_DIRECTORY, workspace.toString()));
        writeProperties(properties);
    }

    public Optional<String> loadLanguage() {
        if (!Files.exists(settingsFile)) {
            return Optional.empty();
        }
        Properties properties = readExistingProperties();
        String value = properties.getProperty(LANGUAGE);
        return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value);
    }

    public void saveLanguage(String language) {
        Properties properties = readExistingProperties();
        if (language == null || language.isBlank()) {
            properties.remove(LANGUAGE);
        } else {
            properties.setProperty(LANGUAGE, language);
        }
        writeProperties(properties);
    }

    private Properties readExistingProperties() {
        Properties properties = new Properties();
        if (!Files.exists(settingsFile)) {
            return properties;
        }
        try (InputStream inputStream = Files.newInputStream(settingsFile)) {
            properties.load(inputStream);
        } catch (IOException exception) {
            throw new SettingsStoreException("Unable to load Episort settings.", exception);
        }
        return properties;
    }

    private void writeProperties(Properties properties) {
        try {
            Files.createDirectories(settingsFile.getParent());
            Path temporaryFile = Files.createTempFile(settingsFile.getParent(), "settings", ".tmp");
            try (OutputStream outputStream = Files.newOutputStream(temporaryFile)) {
                properties.store(outputStream, "Episort settings");
            }
            moveAtomicallyWhenPossible(temporaryFile, settingsFile);
        } catch (IOException exception) {
            throw new SettingsStoreException("Unable to save Episort settings.", exception);
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
    public Optional<Path> settingsFile() {
        return Optional.of(settingsFile);
    }

    public Path settingsFilePath() {
        return settingsFile;
    }
}
