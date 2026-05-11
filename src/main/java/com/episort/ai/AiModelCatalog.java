package com.episort.ai;

import com.episort.ai.embedded.Qwen3ModelDownloader;
import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * Curated list of local AI models exposed to the user in Settings. Qwen3 1.7B
 * Q8 is the only entry today; the catalog scaffolding (entries(), findById())
 * is kept so a second model can be re-introduced later without disturbing
 * downstream callers ({@code AiModelLibrary}, {@code AiModelsSection},
 * {@code EmbeddedLlamaRuntime#extraArgsFor}).
 *
 * <p>Earlier iterations carried Mistral and NVIDIA entries, but at 1.7B-class
 * the chat/tooling quality gap was too wide to justify the extra UX surface
 * (template parse failures, malformed JSON envelopes, phantom episode tags).
 */
public final class AiModelCatalog {

    public static final String QWEN3_ID = "qwen3-1.7b-q8";
    public static final String DEFAULT_SELECTED_ID = QWEN3_ID;

    private static final List<AiModelEntry> ENTRIES = List.of(
            new AiModelEntry(
                    QWEN3_ID,
                    "Qwen3 1.7B Q8",
                    "Qwen",
                    "Lightweight model — long context, tool calling, low VRAM.",
                    Qwen3ModelDownloader.MODEL_FILENAME,
                    Optional.of(Qwen3ModelDownloader.DEFAULT_MODEL_URL),
                    true));

    private AiModelCatalog() {}

    public static List<AiModelEntry> entries() {
        return ENTRIES;
    }

    public static Optional<AiModelEntry> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return ENTRIES.stream().filter(e -> e.id().equals(id)).findFirst();
    }

    static URI qwen3Url() {
        return Qwen3ModelDownloader.DEFAULT_MODEL_URL;
    }
}
