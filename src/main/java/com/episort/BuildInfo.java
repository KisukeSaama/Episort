package com.episort;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

/**
 * What the build knows about itself.
 *
 * <p>The version is written by Gradle into {@code build-info.properties} at build
 * time. Read from a resource rather than hardcoded in Java, so the number on the
 * About screen is the number the artifact was built with and cannot drift from it.
 * When the resource is missing — a raw classpath run, an IDE that skipped
 * {@code processResources} — the answer is empty and the screen shows the design
 * system's {@code —} rather than a plausible guess.
 */
public final class BuildInfo {
    private static final String RESOURCE = "/build-info.properties";
    private static final Properties PROPERTIES = load();

    private BuildInfo() {
    }

    public static Optional<String> version() {
        return value("version");
    }

    private static Optional<String> value(String key) {
        String found = PROPERTIES.getProperty(key);
        return found == null || found.isBlank() ? Optional.empty() : Optional.of(found.trim());
    }

    private static Properties load() {
        Properties properties = new Properties();
        try (InputStream stream = BuildInfo.class.getResourceAsStream(RESOURCE)) {
            if (stream != null) {
                properties.load(stream);
            }
        } catch (IOException ignored) {
            // An unreadable build stamp is not a reason to refuse to start; the
            // screen that shows it already handles the absent case.
        }
        return properties;
    }
}
