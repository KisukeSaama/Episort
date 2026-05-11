package com.episort.ai;

import com.episort.ai.embedded.EmbeddedLlamaRuntime;
import com.episort.ai.embedded.LlamaServerClient;
import com.episort.ai.embedded.Qwen3ModelDownloader;
import java.util.Objects;
import java.util.Optional;

/**
 * Probes the embedded local AI runtime: checks that the bundled binaries are
 * present, that the model is downloaded, that the in-process child server is
 * up and that {@code GET /health} returns 200. Reports a recoverable
 * diagnostic naming the missing piece otherwise.
 */
public final class BundledLocalAiRuntimeProbe implements AiRuntimeProbe {
    static final String RUNTIME_NAME = "episort-local";

    private final EmbeddedLlamaRuntime runtime;
    @SuppressWarnings("unused")
    private final Qwen3ModelDownloader modelDownloader;

    public BundledLocalAiRuntimeProbe(EmbeddedLlamaRuntime runtime, Qwen3ModelDownloader modelDownloader) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.modelDownloader = modelDownloader;
    }

    @Override
    public AiRuntimeStatus probe() {
        if (!runtime.runtimeBinariesAvailable()) {
            return AiRuntimeStatus.unavailable(
                    new AiHardwareSignals(false, 0),
                    false,
                    "Local AI runtime binaries are missing from the installation.");
        }
        if (!runtime.modelAvailable()) {
            return AiRuntimeStatus.unavailable(
                    new AiHardwareSignals(false, 0),
                    false,
                    "Local AI model has not been downloaded yet.");
        }
        Optional<java.net.URI> baseUri = runtime.baseUri();
        if (baseUri.isEmpty()) {
            return AiRuntimeStatus.unavailable(
                    new AiHardwareSignals(false, 0),
                    true,
                    "Local AI runtime is starting.");
        }
        if (!new LlamaServerClient(baseUri.get()).isHealthy()) {
            return AiRuntimeStatus.unavailable(
                    new AiHardwareSignals(false, 0),
                    true,
                    "Local AI runtime is loading the model.");
        }
        return AiRuntimeStatus.available(
                new AiHardwareSignals(true, AiHardwareSignals.MINIMUM_VRAM_MEGABYTES),
                AiBundledModel.QWEN3_1_7B,
                RUNTIME_NAME);
    }
}
