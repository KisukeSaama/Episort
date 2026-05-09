package com.episort.ai.embedded;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class EmbeddedLlamaRuntimeTest {

    @Test
    void runtimeBinariesAvailableReportsTrueWhenZipsArePresent() throws IOException {
        Path workspace = Files.createTempDirectory("episort-rt-");
        Path runtimeDir = Files.createDirectories(workspace.resolve("runtime"));
        Files.writeString(runtimeDir.resolve("placeholder.zip"), "");

        EmbeddedLlamaRuntime runtime = new EmbeddedLlamaRuntime(
                runtimeDir, workspace.resolve("ext"), workspace.resolve("model.gguf"));

        assertTrue(runtime.runtimeBinariesAvailable());
    }

    @Test
    void runtimeBinariesAvailableReportsFalseWhenDirEmpty() throws IOException {
        Path workspace = Files.createTempDirectory("episort-rt-");
        Path runtimeDir = Files.createDirectories(workspace.resolve("runtime"));

        EmbeddedLlamaRuntime runtime = new EmbeddedLlamaRuntime(
                runtimeDir, workspace.resolve("ext"), workspace.resolve("model.gguf"));

        assertFalse(runtime.runtimeBinariesAvailable());
    }

    @Test
    void startBlockingFailsFastWhenBinariesAreMissing() throws IOException {
        Path workspace = Files.createTempDirectory("episort-rt-");
        EmbeddedLlamaRuntime runtime = new EmbeddedLlamaRuntime(
                workspace.resolve("absent"), workspace.resolve("ext"), workspace.resolve("model.gguf"));

        assertThrows(IOException.class, () -> runtime.startBlocking(Duration.ofSeconds(1)));
    }

    @Test
    void startBlockingFailsFastWhenModelIsMissing() throws IOException {
        Path workspace = Files.createTempDirectory("episort-rt-");
        Path runtimeDir = Files.createDirectories(workspace.resolve("runtime"));
        Files.writeString(runtimeDir.resolve("placeholder.zip"), "");

        EmbeddedLlamaRuntime runtime = new EmbeddedLlamaRuntime(
                runtimeDir, workspace.resolve("ext"), workspace.resolve("missing.gguf"));

        assertThrows(IOException.class, () -> runtime.startBlocking(Duration.ofSeconds(1)));
    }

    @Test
    void zipExtractionRefusesEntriesEscapingTargetDirectory() throws IOException {
        Path workspace = Files.createTempDirectory("episort-rt-");
        Path runtimeDir = Files.createDirectories(workspace.resolve("runtime"));
        Path malicious = runtimeDir.resolve("evil.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(malicious))) {
            zos.putNextEntry(new ZipEntry("../escape.txt"));
            zos.write("pwn".getBytes());
            zos.closeEntry();
        }
        Path modelPath = workspace.resolve("model.gguf");
        Files.writeString(modelPath, "fake");
        EmbeddedLlamaRuntime runtime = new EmbeddedLlamaRuntime(
                runtimeDir, workspace.resolve("ext"), modelPath);

        assertThrows(IOException.class, () -> runtime.startBlocking(Duration.ofSeconds(1)));

        // The escaped file must not exist next to the extraction directory.
        Path escaped = workspace.resolve("escape.txt");
        Path siblingEscaped = workspace.resolve("ext").resolveSibling("escape.txt");
        assertFalse(Files.exists(escaped));
        assertFalse(Files.exists(siblingEscaped));

        cleanup(workspace);
    }

    private static void cleanup(Path workspace) throws IOException {
        try (var stream = Files.walk(workspace)) {
            stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }

    @SuppressWarnings("unused")
    private static void writeZip(Path zip, String entryName, byte[] data) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry(entryName));
            try (OutputStream os = zos) {
                os.write(data);
            }
            zos.closeEntry();
        }
    }
}
