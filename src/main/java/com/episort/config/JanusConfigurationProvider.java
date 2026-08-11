package com.episort.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/** Resolves Janus settings from process variables, .env, then bundled release defaults. */
public final class JanusConfigurationProvider {
    static final String URL = "JANUS_URL";
    static final String APPLICATION_ID = "JANUS_APPLICATION_ID";
    static final String API_KEY = "JANUS_API_KEY";
    private static final String RESOURCE = "/janus-client.properties";

    private JanusConfigurationProvider() {
    }

    public static Optional<JanusConfiguration> load() {
        return load(System.getenv(), Path.of(".env").toAbsolutePath().normalize(), bundledDefaults());
    }

    static Optional<JanusConfiguration> load(
            Map<String, String> environment, Path dotenvPath, Map<String, String> defaults) {
        Map<String, String> dotenv = readDotenv(dotenvPath);
        String url = resolve(environment, dotenv, defaults, URL);
        String applicationId = resolve(environment, dotenv, defaults, APPLICATION_ID);
        String apiKey = resolve(environment, dotenv, defaults, API_KEY);
        if (url.isBlank() || applicationId.isBlank() || apiKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new JanusConfiguration(URI.create(url), applicationId, apiKey));
    }

    private static String resolve(
            Map<String, String> environment, Map<String, String> dotenv,
            Map<String, String> defaults, String name) {
        for (Map<String, String> source : List.of(environment, dotenv, defaults)) {
            String value = Optional.ofNullable(source.get(name)).orElse("").trim();
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private static Map<String, String> bundledDefaults() {
        try (InputStream input = JanusConfigurationProvider.class.getResourceAsStream(RESOURCE)) {
            if (input == null) return Map.of();
            Properties properties = new Properties();
            properties.load(input);
            Map<String, String> values = new HashMap<>();
            for (String name : properties.stringPropertyNames()) values.put(name, properties.getProperty(name));
            return values;
        } catch (IOException exception) {
            return Map.of();
        }
    }

    private static Map<String, String> readDotenv(Path path) {
        if (!Files.isRegularFile(path)) return Map.of();
        try {
            Map<String, String> values = new HashMap<>();
            for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String line = rawLine.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("export ")) line = line.substring(7).stripLeading();
                int separator = line.indexOf('=');
                if (separator <= 0) continue;
                String name = line.substring(0, separator).trim();
                if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) continue;
                String value = line.substring(separator + 1).trim();
                if (value.length() >= 2 && (value.charAt(0) == '\'' || value.charAt(0) == '"')
                        && value.charAt(value.length() - 1) == value.charAt(0)) {
                    value = value.substring(1, value.length() - 1);
                }
                values.put(name, value);
            }
            return values;
        } catch (IOException | SecurityException exception) {
            return Map.of();
        }
    }
}
