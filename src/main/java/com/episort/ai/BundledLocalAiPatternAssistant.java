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
 * (llama.cpp / Qwen3 1.7B). Runs in two passes to stay within the model's
 * reliable structured-output window:
 *
 * <ol>
 *   <li>One small "global" call returning {@code {mediaType, patterns,
 *       confidence, explanation}} — bounded ~150 output tokens, never
 *       truncated even on large groups.</li>
 *   <li>One per-file call per filename returning {@code {filename, pattern,
 *       tokens, normalizedOrder, confidence}}. We tried packing N filenames
 *       into one batched call to amortize round-trips, but a 1.7B model
 *       loses track when asked to emit a structured entry per item in one
 *       shot — entries got merged, dropped, or cross-contaminated. Per-file
 *       calls stay sharp, and {@code cache_prompt=true} on the server reuses
 *       the ~700-token system-prompt KV across calls, so each call only
 *       prefills the small per-call user block.</li>
 * </ol>
 *
 * <p>The {@link LlamaServerClient} supplier is consulted on every call so the
 * assistant works across runtime restarts and during the first-run boot
 * window. If the supplier returns empty, we surface an advisory fallback —
 * never an exception — so callers can keep the user-visible flow alive.
 */
public final class BundledLocalAiPatternAssistant implements AiPatternAssistant {
    private static final String GLOBAL_SYSTEM_PROMPT = ""
            + "You classify a batch of media filenames. Output ONE JSON object, nothing else. "
            + "No prose, no markdown, no code fences, no <think> blocks.\n"
            + "Schema (all keys required, exactly these keys):\n"
            + "{\"mediaType\":\"series|movie|unknown\","
            + "\"confidence\":0.0,"
            + "\"patterns\":[\"...\"],"
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

    private static final String PER_FILE_SYSTEM_PROMPT = ""
            + "You parse ONE media filename. Output ONE JSON object, nothing else. "
            + "No prose, no markdown, no code fences, no <think> blocks.\n"
            + "Schema (all keys required, exactly these keys):\n"
            + "{\"filename\":\"...\","
            + "\"pattern\":\"SxxExx|NxNN|absolute|unknown\","
            + "\"tokens\":[{\"role\":\"SERIES|SEASON|EPISODE|TITLE|EXTENSION|NOISE\","
            + "\"rawValue\":\"...\",\"normalizedValue\":\"...\",\"start\":0,\"end\":1}],"
            + "\"normalizedOrder\":\"S01E02\","
            + "\"confidence\":0.0}\n"
            + "Rules:\n"
            + "- filename: echo the input filename exactly.\n"
            + "- pattern: pick one of the listed values; \"unknown\" if no episode marker.\n"
            + "- tokens: every span MUST have correct half-open [start,end) offsets in the input.\n"
            + "- SERIES token: when a parent folder name is provided and the filename's series\n"
            + "  segment is missing, abbreviated, or release-tag noise (e.g. \"sgi-hkyu\",\n"
            + "  \"sgi\", scene tags), prefer the clean series title from the parent folder for\n"
            + "  rawValue and normalizedValue. Strip release tags, resolution, codec, language\n"
            + "  tags from the parent folder to get the clean title (e.g.\n"
            + "  \"Haikyu.S01.MULTi.1080p.BluRay.x264-SHiNiGAMi\" -> \"Haikyu\"). When the SERIES\n"
            + "  token is taken from the folder, set its start=0 and end=0 (the span is not in\n"
            + "  the filename); do NOT emit a separate NOISE token covering the filename's\n"
            + "  series-like fragment.\n"
            + "- Folder-derived SERIES does NOT change pattern/EPISODE/SEASON detection: still\n"
            + "  parse the filename normally for season/episode digits. Trailing digits glued\n"
            + "  to a series shorthand (e.g. \"hkyu02\") are an absolute-number EPISODE token;\n"
            + "  pattern=\"absolute\" and normalizedOrder=\"S01E02\" (assume season 1 unless the\n"
            + "  parent folder says otherwise, e.g. \".S02.\" -> season 2).\n"
            + "- SEASON token: ALWAYS emit one when normalizedOrder is set. If the season\n"
            + "  digits appear in the filename, use their real [start,end). Otherwise (season\n"
            + "  derived from the parent folder via \".S0N.\"/\"Season N\", or the implicit\n"
            + "  \"assume season 1\" rule for absolute patterns), emit it with start=0 and\n"
            + "  end=0 and the two-digit normalizedValue (e.g. \"01\"). Never omit SEASON\n"
            + "  just because the filename has no season marker.\n"
            + "- normalizedOrder: S01E02 form when applicable; empty string for movies. Never\n"
            + "  put a series title or non-SxxExx text in normalizedOrder.\n"
            + "- confidence in [0,1], two decimals.\n"
            + "Worked example:\n"
            + "  Parent folders: Haikyu.S01.MULTi.1080p.BluRay.x264-SHiNiGAMi\n"
            + "  Filename: sgi-hkyu02.1080p.multi.mkv\n"
            + "  -> {\"filename\":\"sgi-hkyu02.1080p.multi.mkv\",\"pattern\":\"absolute\",\n"
            + "      \"tokens\":[{\"role\":\"SERIES\",\"rawValue\":\"Haikyu\",\"normalizedValue\":\"Haikyu\",\"start\":0,\"end\":0},\n"
            + "                {\"role\":\"SEASON\",\"rawValue\":\"01\",\"normalizedValue\":\"01\",\"start\":0,\"end\":0},\n"
            + "                {\"role\":\"EPISODE\",\"rawValue\":\"02\",\"normalizedValue\":\"02\",\"start\":8,\"end\":10},\n"
            + "                {\"role\":\"EXTENSION\",\"rawValue\":\"mkv\",\"normalizedValue\":\"mkv\",\"start\":23,\"end\":26}],\n"
            + "      \"normalizedOrder\":\"S01E02\",\"confidence\":0.80}";

    // Global pass: just an envelope of 4 short keys. ~150 tokens is plenty.
    private static final int GLOBAL_MAX_TOKENS = 256;
    // Per-file pass: tokens[] is the longest field; comfortable upper bound.
    private static final int PER_FILE_MAX_TOKENS = 384;
    // Filename samples sent to the global classification pass. Enough variety
    // for reliable mediaType detection without bloating the prompt.
    private static final int GLOBAL_SAMPLE_SIZE = 24;

    private final Supplier<Optional<LlamaServerClient>> clientSupplier;

    public BundledLocalAiPatternAssistant(Supplier<Optional<LlamaServerClient>> clientSupplier) {
        this.clientSupplier = Objects.requireNonNull(clientSupplier, "clientSupplier");
    }

    @Override
    public AiPatternSuggestion suggestPattern(AiPatternSuggestionRequest request) {
        return suggestPattern(request, () -> {});
    }

    @Override
    public AiPatternSuggestion suggestPattern(AiPatternSuggestionRequest request, Runnable promptTick) {
        Runnable tick = promptTick == null ? () -> {} : promptTick;
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
        LlamaServerClient client = maybeClient.get();

        GlobalEnvelope global = runGlobalPass(client, minimizedContext, request.parentFolderChain(), tick);
        if (global == null) {
            return AiPatternSuggestion.advisory(
                    "Local AI response could not be parsed as a pattern envelope.",
                    List.of(), minimizedContext);
        }

        List<AiFilePatternParse> fileParses = runPerFilePass(
                client, minimizedContext, global.patterns, request.parentFolderChain(), tick);

        return AiPatternSuggestion.advisory(
                global.explanation.isBlank() ? "Local AI proposed pattern hints." : global.explanation,
                global.patterns,
                fileParses,
                minimizedContext,
                global.mediaType,
                global.confidence);
    }

    /**
     * Number of LLM prompts {@link #suggestPattern} will issue for a given
     * filename count: one global pass + one call per filename. Used by the
     * progress reporter to size its bar.
     */
    public static int promptCountFor(int filenameCount) {
        if (filenameCount <= 0) return 0;
        return 1 + filenameCount;
    }

    private GlobalEnvelope runGlobalPass(
            LlamaServerClient client, List<String> filenames, List<String> parentFolderChain, Runnable tick) {
        List<String> capped = filenames.size() > GLOBAL_SAMPLE_SIZE
                ? filenames.subList(0, GLOBAL_SAMPLE_SIZE)
                : filenames;
        StringBuilder userBuilder = new StringBuilder();
        if (parentFolderChain != null && !parentFolderChain.isEmpty()) {
            userBuilder.append("Parent folders (outermost to innermost): ")
                    .append(String.join(" / ", parentFolderChain))
                    .append('\n');
        }
        userBuilder.append("Filenames:\n").append(String.join("\n", capped)).append("\n/no_think");
        String userBlock = userBuilder.toString();
        String prompt = wrapChatMl(GLOBAL_SYSTEM_PROMPT, userBlock);
        String raw;
        try {
            raw = client.complete("pattern-global", prompt, GLOBAL_MAX_TOKENS);
        } catch (RuntimeException ex) {
            tick.run();
            return null;
        }
        tick.run();
        JsonObject root = extractObject(raw);
        if (root == null) {
            return null;
        }
        return new GlobalEnvelope(
                stringValue(root, "explanation"),
                extractStringArray(root, "patterns"),
                extractMediaType(root),
                extractConfidence(root));
    }

    private List<AiFilePatternParse> runPerFilePass(
            LlamaServerClient client,
            List<String> filenames,
            List<String> globalPatterns,
            List<String> parentFolderChain,
            Runnable tick) {
        int n = filenames.size();
        if (n == 0) {
            return List.of();
        }
        List<AiFilePatternParse> parses = new ArrayList<>(n);
        for (String filename : filenames) {
            AiFilePatternParse parse =
                    runSingleFile(client, filename, globalPatterns, parentFolderChain);
            if (parse != null) {
                parses.add(parse);
            }
            tick.run();
        }
        return parses;
    }

    private AiFilePatternParse runSingleFile(
            LlamaServerClient client,
            String filename,
            List<String> globalPatterns,
            List<String> parentFolderChain) {
        StringBuilder userBlock = new StringBuilder();
        if (parentFolderChain != null && !parentFolderChain.isEmpty()) {
            userBlock.append("Parent folders (outermost to innermost): ")
                    .append(String.join(" / ", parentFolderChain))
                    .append('\n');
        }
        if (!globalPatterns.isEmpty()) {
            userBlock.append("Group hint patterns: ")
                    .append(String.join(", ", globalPatterns))
                    .append('\n');
        }
        userBlock.append("Filename: ").append(filename).append("\n/no_think");
        String prompt = wrapChatMl(PER_FILE_SYSTEM_PROMPT, userBlock.toString());
        String raw;
        try {
            raw = client.complete("pattern-file", prompt, PER_FILE_MAX_TOKENS);
        } catch (RuntimeException ex) {
            return null;
        }
        JsonObject obj = extractObject(raw);
        if (obj == null) {
            return null;
        }
        String returnedName = stringValue(obj, "filename");
        if (returnedName.isBlank()) {
            // Trust the input filename when the model omitted it; cheaper than
            // discarding an otherwise-useful parse.
            returnedName = filename;
        }
        return new AiFilePatternParse(
                returnedName,
                stringValue(obj, "pattern"),
                extractTokens(obj),
                optionalString(obj, "normalizedOrder"),
                optionalDouble(obj, "confidence"));
    }

    private static String wrapChatMl(String system, String user) {
        return "<|im_start|>system\n" + system + "<|im_end|>\n"
                + "<|im_start|>user\n" + user + "<|im_end|>\n"
                + "<|im_start|>assistant\n";
    }

    private static JsonObject extractObject(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        int braceStart = trimmed.indexOf('{');
        int braceEnd = trimmed.lastIndexOf('}');
        if (braceStart < 0 || braceEnd <= braceStart) {
            return null;
        }
        try {
            return JsonParser.parseString(trimmed.substring(braceStart, braceEnd + 1)).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException ex) {
            return null;
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

    private record GlobalEnvelope(
            String explanation,
            List<String> patterns,
            Optional<InventoryGroupType> mediaType,
            OptionalDouble confidence) {
    }
}
