package com.episort.ai;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Catalog entry describing one local GGUF model the user can manage from
 * Settings. {@code sourceUrl} is empty when no GGUF mirror is wired yet — in
 * that case the library refuses to download and the UI surfaces a TODO state.
 *
 * <p>{@code extraServerArgs} are appended to the {@code llama-server} command
 * line when this model is loaded. Reserved for per-model workarounds (e.g. a
 * GGUF whose embedded Jinja chat template fails to compile in llama.cpp and
 * needs {@code --no-jinja --chat-template <fallback>}).
 */
public record AiModelEntry(
        String id,
        String displayName,
        String provider,
        String description,
        String fileName,
        Optional<URI> sourceUrl,
        boolean recommended,
        List<String> extraServerArgs) {

    public AiModelEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(sourceUrl, "sourceUrl");
        extraServerArgs = extraServerArgs == null ? List.of() : List.copyOf(extraServerArgs);
    }

    public AiModelEntry(
            String id,
            String displayName,
            String provider,
            String description,
            String fileName,
            Optional<URI> sourceUrl,
            boolean recommended) {
        this(id, displayName, provider, description, fileName, sourceUrl, recommended, List.of());
    }

    public boolean downloadable() {
        return sourceUrl.isPresent();
    }
}
