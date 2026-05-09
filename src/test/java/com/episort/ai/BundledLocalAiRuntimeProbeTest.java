package com.episort.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.ai.embedded.EmbeddedLlamaRuntime;
import com.episort.ai.embedded.Qwen3ModelDownloader;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BundledLocalAiRuntimeProbeTest {

    @Test
    void reportsUnavailableWhenRuntimeBinariesMissing() throws Exception {
        Path tempBase = Files.createTempDirectory("episort-probe-");
        EmbeddedLlamaRuntime runtime = new EmbeddedLlamaRuntime(
                tempBase.resolve("missing"), tempBase.resolve("ext"), tempBase.resolve("model.gguf"));
        Qwen3ModelDownloader downloader = new Qwen3ModelDownloader(
                tempBase, URI.create("http://x"), HttpClient.newHttpClient());

        AiRuntimeStatus status = new BundledLocalAiRuntimeProbe(runtime, downloader).probe();

        assertFalse(status.runtimeAvailable());
        assertTrue(status.diagnostic().toLowerCase().contains("missing"));
    }

    @Test
    void reportsUnavailableWhenModelMissing() throws Exception {
        Path tempBase = Files.createTempDirectory("episort-probe-");
        Path runtimeDir = Files.createDirectories(tempBase.resolve("runtime"));
        Files.writeString(runtimeDir.resolve("placeholder.zip"), "");
        EmbeddedLlamaRuntime runtime = new EmbeddedLlamaRuntime(
                runtimeDir, tempBase.resolve("ext"), tempBase.resolve("model.gguf"));
        Qwen3ModelDownloader downloader = new Qwen3ModelDownloader(
                tempBase, URI.create("http://x"), HttpClient.newHttpClient());

        AiRuntimeStatus status = new BundledLocalAiRuntimeProbe(runtime, downloader).probe();

        assertFalse(status.runtimeAvailable());
        assertTrue(status.diagnostic().toLowerCase().contains("model"));
    }

    @Test
    void reportsUnavailableWhenRuntimeNotStartedYet() throws Exception {
        Path tempBase = Files.createTempDirectory("episort-probe-");
        Path runtimeDir = Files.createDirectories(tempBase.resolve("runtime"));
        Files.writeString(runtimeDir.resolve("placeholder.zip"), "");
        Path modelPath = tempBase.resolve("model.gguf");
        Files.writeString(modelPath, "fake");
        EmbeddedLlamaRuntime runtime = new EmbeddedLlamaRuntime(
                runtimeDir, tempBase.resolve("ext"), modelPath);
        Qwen3ModelDownloader downloader = new Qwen3ModelDownloader(
                tempBase, URI.create("http://x"), HttpClient.newHttpClient()) {
            @Override
            public boolean isPresent() {
                return true;
            }
        };

        AiRuntimeStatus status = new BundledLocalAiRuntimeProbe(runtime, downloader).probe();

        assertFalse(status.runtimeAvailable());
        assertTrue(status.diagnostic().toLowerCase().contains("starting"));
    }

    @Test
    void reportsAvailableWhenRuntimeIsHealthy() throws Exception {
        try (FakeLlamaServer fake = FakeLlamaServer.start()) {
            AiRuntimeStatus status = fake.probe().probe();

            assertTrue(status.runtimeAvailable());
            assertEquals(AiBundledModel.QWEN3_8B, status.model().orElseThrow());
            assertTrue(status.hardwareSignals().minimumVramAvailable());
        }
    }

    @Test
    void prerequisiteServiceAcceptsAvailableProbe() throws Exception {
        try (FakeLlamaServer fake = FakeLlamaServer.start()) {
            AiPrerequisiteService service = new AiPrerequisiteService(fake.probe());

            AiPrerequisiteCheckResult result = service.check();

            assertTrue(result.aiWorkflowsAvailable());
            assertTrue(result.missingPrerequisites().isEmpty());
        }
    }

    @SuppressWarnings("unused")
    private static Optional<URI> noUri() {
        return Optional.empty();
    }
}
