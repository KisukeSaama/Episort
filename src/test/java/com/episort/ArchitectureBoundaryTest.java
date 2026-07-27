package com.episort;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ArchitectureBoundaryTest {
    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/episort");

    @Test
    void requiredPackageBoundariesExist() {
        List<String> packages = List.of(
                "ui",
                "workflow",
                "config",
                "filesystem",
                "scanner",
                "matching",
                "planning",
                "tvdb",
                "analysis",
                "persistence",
                "logging",
                "ui/settings");

        for (String packageName : packages) {
            assertTrue(Files.isDirectory(SOURCE_ROOT.resolve(packageName)), packageName + " package is missing");
        }
    }

    @Test
    void nonUiPackagesDoNotImportJavaFx() throws IOException {
        try (Stream<Path> sourceFiles = Files.walk(SOURCE_ROOT)) {
            List<Path> violations = sourceFiles
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !SOURCE_ROOT.relativize(path).toString().equals("EpisortApplication.java"))
                    .filter(path -> !isUiPackage(path))
                    .filter(path -> importsJavaFx(path))
                    .toList();

            assertTrue(violations.isEmpty(), "JavaFX imports outside ui package: " + violations);
        }
    }

    private static boolean importsJavaFx(Path path) {
        try {
            return Files.readString(path).contains("import javafx.");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to inspect " + path, exception);
        }
    }

    private static boolean isUiPackage(Path path) {
        Path relativePath = SOURCE_ROOT.relativize(path);
        return relativePath.getNameCount() > 1 && relativePath.getName(0).toString().equals("ui");
    }
}
