package com.episort.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.episort.ai.embedded.EmbeddedLlamaRuntime;
import com.episort.ai.embedded.LlamaServerClient;
import com.episort.ai.embedded.Qwen3ModelDownloader;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Live smoke test against the embedded llama.cpp runtime running on the local
 * machine. Skipped by default; runs only when invoked with
 * {@code -PrunLocalLlm=true} (see {@code build.gradle.kts}). Requires the
 * model to be downloaded and the runtime binaries extracted.
 */
@Tag("local-llm")
class Qwen3LiveSmokeTest {

    @Test
    void liveRuntimeReturnsAnAdvisorySuggestion() throws Exception {
        Qwen3ModelDownloader downloader = new Qwen3ModelDownloader();
        assumeTrue(downloader.isPresent(), "Qwen3 GGUF model not present locally");
        EmbeddedLlamaRuntime runtime = new EmbeddedLlamaRuntime(
                EmbeddedLlamaRuntime.defaultRuntimeZipDir(),
                EmbeddedLlamaRuntime.defaultExtractionDir(),
                downloader.modelPath());
        assumeTrue(runtime.runtimeBinariesAvailable(), "Local AI runtime binaries are not bundled");

        try {
            runtime.startBlocking(Duration.ofMinutes(2));
            BundledLocalAiPatternAssistant assistant = new BundledLocalAiPatternAssistant(
                    () -> runtime.baseUri().map(LlamaServerClient::new));

            AiPatternSuggestion suggestion = assistant.suggestPattern(new AiPatternSuggestionRequest(
                    List.of("Show.Name.S01E01.mkv", "Show.Name.S01E02.mkv"), ""));

            assertTrue(suggestion.advisoryOnly());
            assertFalse(suggestion.validationAuthority());
            assertFalse(suggestion.executionAuthority());
        } finally {
            runtime.close();
        }
    }

    @SuppressWarnings("unused")
    private static Optional<String> noResponse() {
        return Optional.empty();
    }
}
