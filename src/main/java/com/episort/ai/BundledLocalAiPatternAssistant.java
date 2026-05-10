package com.episort.ai;

import com.episort.ai.embedded.LlamaServerClient;
import com.episort.scanner.InventoryGroupType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * Pattern assistant backed by the embedded Episort local AI runtime
 * (llama.cpp / Qwen3 1.7B). Sends a single completion request with the
 * minimized selected-item context and parses a JSON envelope of the form:
 *
 * <pre>{"patterns": ["SxxExx", ...], "explanation": "..."}</pre>
 *
 * <p>The {@link LlamaServerClient} supplier is consulted on every call so the
 * assistant works across runtime restarts and during the first-run boot
 * window. If the supplier returns empty, we surface an advisory fallback —
 * never an exception — so callers can keep the user-visible flow alive.
 */
public final class BundledLocalAiPatternAssistant implements AiPatternAssistant {
    private static final String SYSTEM_PROMPT = ""
            + "You classify media filenames. Output ONE JSON object, nothing else. "
            + "No prose, no markdown, no code fences, no <think> blocks.\n"
            + "Schema (all keys required, exactly these keys):\n"
            + "{\"mediaType\":\"series|movie|unknown\","
            + "\"confidence\":0.0,"
            + "\"patterns\":[\"...\"],"
            + "\"files\":[{\"filename\":\"...\",\"pattern\":\"SxxExx|NxNN|absolute|unknown\","
            + "\"tokens\":[{\"role\":\"SERIES|SEASON|EPISODE|TITLE|EXTENSION|NOISE\","
            + "\"rawValue\":\"...\",\"normalizedValue\":\"...\",\"start\":0,\"end\":1}],"
            + "\"normalizedOrder\":\"S01E02\",\"confidence\":0.0}],"
            + "\"explanation\":\"<= 12 words\"}\n"
            + "Rules:\n"
            + "- mediaType=\"series\" iff filenames share a recurring SxxExx / 1xNN / "
            + "absolute-episode marker.\n"
            + "- mediaType=\"movie\" iff filenames look like single titles, often with "
            + "(year) or resolution tags, no episode marker.\n"
            + "- Otherwise mediaType=\"unknown\".\n"
            + "- confidence in [0,1], two decimals.\n"
            + "- patterns: 1 to 3 short labels (e.g. \"SxxExx\", \"Title (yyyy)\", "
            + "\"1xNN\", \"Date YYYY-MM-DD\"). Use [] if unsure.\n"
            + "- explanation: one short sentence, no quotes inside.";
    // Enough to fit the full envelope (~120 tokens typical). Small enough to
    // stop fast on a 4070 Super (~20–40ms/token range).
    private static final int DEFAULT_MAX_TOKENS = 1024;

    private final Supplier<Optional<LlamaServerClient>> clientSupplier;

    public BundledLocalAiPatternAssistant(Supplier<Optional<LlamaServerClient>> clientSupplier) {
        this.clientSupplier = Objects.requireNonNull(clientSupplier, "clientSupplier");
    }

    @Override
    public AiPatternSuggestion suggestPattern(AiPatternSuggestionRequest request) {
        List<String> minimizedContext = request.minimizedSelectedItemContext();
        if (minimizedContext.isEmpty()) {
            return AiPatternSuggestion.advisory(
                    "No filenames provided; cannot suggest a pattern.", List.of(), List.of());
        }
        Optional<LlamaServerClient> maybeClient = clientSupplier.get();
        if (maybeClient.isEmpty()) {
            return AiPatternSuggestion.advisory(
                    "Local AI runtime is not ready yet.", List.of(), minimizedContext);
        }
        // Wrap in Qwen3 ChatML so the model sees a real system role, not a
        // bare prompt. Without this Qwen3 tends to ramble in plain prose.
        // Cap the filename list to keep the prompt small and the KV cache
        // reusable across groups (cache_prompt is on).
        List<String> capped = minimizedContext.size() > 24
                ? minimizedContext.subList(0, 24)
                : minimizedContext;
        String userBlock = "Filenames:\n" + String.join("\n", capped) + "\n/no_think";
        String prompt = "<|im_start|>system\n" + SYSTEM_PROMPT + "<|im_end|>\n"
                + "<|im_start|>user\n" + userBlock + "<|im_end|>\n"
                + "<|im_start|>assistant\n";
        String raw;
        try {
            raw = maybeClient.get().complete("pattern", prompt, DEFAULT_MAX_TOKENS);
        } catch (RuntimeException ex) {
            return AiPatternSuggestion.advisory(
                    "Local AI runtime did not return a suggestion.", List.of(), minimizedContext);
        }
        return parseEnvelope(raw, minimizedContext);
    }

    private AiPatternSuggestion parseEnvelope(String raw, List<String> minimizedContext) {
        String trimmed = raw == null ? "" : raw.trim();
        int braceStart = trimmed.indexOf('{');
        int braceEnd = trimmed.lastIndexOf('}');
        if (braceStart < 0 || braceEnd <= braceStart) {
            return AiPatternSuggestion.advisory(
                    fallbackExplanation(trimmed), List.of(), minimizedContext);
        }
        String json = trimmed.substring(braceStart, braceEnd + 1);
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            String explanation = root.has("explanation") ? root.get("explanation").getAsString() : "";
            List<String> patterns = extractStringArray(root, "patterns");
            List<AiFilePatternParse> files = extractFileParses(root);
            Optional<InventoryGroupType> mediaType = extractMediaType(root);
            OptionalDouble confidence = extractConfidence(root);
            return AiPatternSuggestion.advisory(
                    explanation.isBlank() ? "Local AI proposed pattern hints." : explanation,
                    patterns,
                    files,
                    minimizedContext,
                    mediaType,
                    confidence);
        } catch (JsonSyntaxException | IllegalStateException ex) {
            return AiPatternSuggestion.advisory(
                    fallbackExplanation(trimmed), List.of(), minimizedContext);
        }
    }

    private List<String> extractStringArray(JsonObject root, String key) {
        if (!root.has(key) || !root.get(key).isJsonArray()) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (JsonElement element : root.getAsJsonArray(key)) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                String value = element.getAsString().trim();
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
        }
        return new ArrayList<>(values);
    }

    private List<AiFilePatternParse> extractFileParses(JsonObject root) {
        if (!root.has("files") || !root.get("files").isJsonArray()) {
            return List.of();
        }
        List<AiFilePatternParse> parses = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("files")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject obj = element.getAsJsonObject();
            String filename = stringValue(obj, "filename");
            if (filename.isBlank()) {
                continue;
            }
            parses.add(new AiFilePatternParse(
                    filename,
                    stringValue(obj, "pattern"),
                    extractTokens(obj),
                    optionalString(obj, "normalizedOrder"),
                    optionalDouble(obj, "confidence")));
        }
        return parses;
    }

    private List<AiPatternToken> extractTokens(JsonObject file) {
        if (!file.has("tokens") || !file.get("tokens").isJsonArray()) {
            return List.of();
        }
        List<AiPatternToken> tokens = new ArrayList<>();
        for (JsonElement element : file.getAsJsonArray("tokens")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject obj = element.getAsJsonObject();
            try {
                tokens.add(new AiPatternToken(
                        stringValue(obj, "role"),
                        stringValue(obj, "rawValue"),
                        stringValue(obj, "normalizedValue"),
                        obj.has("start") ? obj.get("start").getAsInt() : 0,
                        obj.has("end") ? obj.get("end").getAsInt() : 0));
            } catch (RuntimeException ignored) {
                // Ignore malformed per-token output while preserving other files.
            }
        }
        return tokens;
    }

    private Optional<String> optionalString(JsonObject obj, String key) {
        String value = stringValue(obj, key);
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private String stringValue(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive() || !obj.get(key).getAsJsonPrimitive().isString()) {
            return "";
        }
        return obj.get(key).getAsString().trim();
    }

    private OptionalDouble optionalDouble(JsonObject obj, String key) {
        if (!obj.has(key)) {
            return OptionalDouble.empty();
        }
        try {
            double value = obj.get(key).getAsDouble();
            if (Double.isNaN(value) || value < 0.0) {
                return OptionalDouble.empty();
            }
            return OptionalDouble.of(Math.min(1.0, value));
        } catch (RuntimeException ex) {
            return OptionalDouble.empty();
        }
    }

    private Optional<InventoryGroupType> extractMediaType(JsonObject root) {
        if (!root.has("mediaType")) {
            return Optional.empty();
        }
        JsonElement el = root.get("mediaType");
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
            return Optional.empty();
        }
        String value = el.getAsString().trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "series", "tv", "show", "tvshow" -> Optional.of(InventoryGroupType.LIKELY_SERIES);
            case "movie", "film" -> Optional.of(InventoryGroupType.LIKELY_MOVIE);
            case "unknown", "other" -> Optional.of(InventoryGroupType.UNKNOWN);
            default -> Optional.empty();
        };
    }

    private OptionalDouble extractConfidence(JsonObject root) {
        if (!root.has("confidence")) {
            return OptionalDouble.empty();
        }
        try {
            double raw = root.get("confidence").getAsDouble();
            if (Double.isNaN(raw) || raw < 0.0) return OptionalDouble.empty();
            return OptionalDouble.of(Math.min(1.0, raw));
        } catch (RuntimeException ignored) {
            return OptionalDouble.empty();
        }
    }

    private String fallbackExplanation(String raw) {
        if (raw.isBlank()) {
            return "Local AI returned an empty response.";
        }
        return "Local AI response could not be parsed as a pattern envelope.";
    }
}
