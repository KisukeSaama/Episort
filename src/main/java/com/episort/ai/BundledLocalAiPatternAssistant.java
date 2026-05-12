package com.episort.ai;

import com.episort.ai.embedded.LlamaServerClient;
import com.episort.ai.embedded.LlamaServerClient.ChatMessage;
import com.episort.scanner.InventoryGroupType;
import com.google.gson.JsonArray;
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
 * (llama.cpp / Qwen3 4B Instruct 2507). Runs in two passes to stay within the model's
 * reliable structured-output window:
 *
 * <ol>
 *   <li>One small "global" call returning {@code {mediaType, patterns,
 *       confidence, explanation}} — bounded ~150 output tokens, never
 *       truncated even on large groups.</li>
 *   <li>One per-file call per filename returning {@code {filename, pattern,
 *       tokens, normalizedOrder, confidence}}. We tried packing N filenames
 *       into one batched call to amortize round-trips, but a small local model
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
            + "You are a media filename classifier. Given a batch of filenames from a single\n"
            + "folder, decide whether they are a TV series, a movie, or undetermined.\n"
            + "Return one JSON object matching the response schema; the schema is enforced.\n"
            + "\n"
            + "Examples:\n"
            + "\n"
            + "Input:\n"
            + "  Inception.2010.1080p.BluRay.x264.mkv\n"
            + "Output:\n"
            + "  {\"mediaType\":\"movie\",\"confidence\":0.95,\"patterns\":[\"Title (yyyy)\"],"
            + "\"explanation\":\"Single feature with year and resolution tag.\"}\n"
            + "\n"
            + "Input:\n"
            + "  Breaking.Bad.S01E01.mkv\n"
            + "  Breaking.Bad.S01E02.mkv\n"
            + "  Breaking.Bad.S01E03.mkv\n"
            + "Output:\n"
            + "  {\"mediaType\":\"series\",\"confidence\":0.98,\"patterns\":[\"SxxExx\"],"
            + "\"explanation\":\"Shared title with consecutive SxxExx markers.\"}\n"
            + "\n"
            + "Input:\n"
            + "  scan001.dat\n"
            + "  scan002.dat\n"
            + "Output:\n"
            + "  {\"mediaType\":\"unknown\",\"confidence\":0.30,\"patterns\":[],"
            + "\"explanation\":\"Non-video filenames; cannot determine type.\"}\n"
            + "\n"
            + "Rules (in priority order):\n"
            + "1. Pick \"series\" when filenames share a recurring SxxExx, NxNN, or absolute-\n"
            + "   episode marker. A single file with an episode marker still counts.\n"
            + "2. Pick \"movie\" when filenames look like standalone titles (year tag,\n"
            + "   resolution tag, no episode marker). A single video file with no episode\n"
            + "   marker is almost always a movie.\n"
            + "3. Pick \"unknown\" only when neither shape fits.\n"
            + "4. patterns: 0 to 3 short labels describing the input shape you observed\n"
            + "   (\"SxxExx\", \"NxNN\", \"Absolute\", \"Title (yyyy)\", \"Date YYYY-MM-DD\").\n"
            + "   Use [] when unsure.\n"
            + "5. confidence: keep it below 0.80 when filenames disagree with each other or\n"
            + "   when you had to guess. 0.95+ is reserved for unambiguous cases.\n"
            + "6. explanation: one short sentence, no quotes, no newlines.";

    private static final String PER_FILE_SYSTEM_PROMPT = ""
            + "You parse ONE media filename into structured tokens. Return one JSON object\n"
            + "matching the response schema; the schema is enforced.\n"
            + "\n"
            + "Three invariants override every other rule:\n"
            + "  (a) The \"filename\" field MUST echo the input filename character-for-character.\n"
            + "      Never paraphrase, lowercase, or strip anything.\n"
            + "  (b) When you are uncertain about a token's [start,end) offsets WITHIN the\n"
            + "      filename, OMIT the token. Empty tokens [] is allowed and preferred over\n"
            + "      wrong offsets.\n"
            + "  (c) Out-of-band tokens use start=0 end=0 and are REQUIRED when the value\n"
            + "      comes from the parent folder rather than from a span in the filename:\n"
            + "      - SERIES from a parent folder when the filename's series segment is\n"
            + "        missing, abbreviated, or just a release-tag (e.g. \"sgi-hkyu\",\n"
            + "        \"sgi\", scene-group initials). Take the CLEANED parent-folder title\n"
            + "        (strip release/resolution/codec/language tags first:\n"
            + "        \"Haikyu.S01.MULTi.1080p.BluRay.x264-SHiNiGAMi\" -> \"Haikyu\") and\n"
            + "        emit it as a SERIES token with start=0 end=0. Never leave SERIES\n"
            + "        empty when the parent folder reveals it. Rule (b) does NOT apply\n"
            + "        here: start=0 end=0 is the known-good convention, not a guess.\n"
            + "      - SEASON when it comes from the parent folder (\".S0N.\" / \"Season N\")\n"
            + "        or from the absolute-pattern default \"01\".\n"
            + "\n"
            + "Examples:\n"
            + "\n"
            + "Never emit EXTENSION tokens — the file extension is handled outside the model\n"
            + "from the filesystem; you don't need to tag it.\n"
            + "\n"
            + "A — vanilla SxxExx series:\n"
            + "  Parent folders: Breaking Bad / Season 01\n"
            + "  Filename: Breaking.Bad.S01E03.Bit.By.A.Dead.Bee.mkv\n"
            + "  Output:\n"
            + "    {\"filename\":\"Breaking.Bad.S01E03.Bit.By.A.Dead.Bee.mkv\",\n"
            + "     \"pattern\":\"SxxExx\",\n"
            + "     \"tokens\":[\n"
            + "       {\"role\":\"SERIES\",\"rawValue\":\"Breaking.Bad\",\"normalizedValue\":\"Breaking Bad\",\"start\":0,\"end\":12},\n"
            + "       {\"role\":\"SEASON\",\"rawValue\":\"01\",\"normalizedValue\":\"01\",\"start\":14,\"end\":16},\n"
            + "       {\"role\":\"EPISODE\",\"rawValue\":\"03\",\"normalizedValue\":\"03\",\"start\":17,\"end\":19},\n"
            + "       {\"role\":\"TITLE\",\"rawValue\":\"Bit.By.A.Dead.Bee\",\"normalizedValue\":\"Bit By A Dead Bee\",\"start\":20,\"end\":37}],\n"
            + "     \"normalizedOrder\":\"S01E03\",\n"
            + "     \"confidence\":0.95}\n"
            + "\n"
            + "B — movie with year tag (no episode marker):\n"
            + "  Parent folders: Movies\n"
            + "  Filename: Inception (2010).mkv\n"
            + "  Output:\n"
            + "    {\"filename\":\"Inception (2010).mkv\",\n"
            + "     \"pattern\":\"unknown\",\n"
            + "     \"tokens\":[\n"
            + "       {\"role\":\"TITLE\",\"rawValue\":\"Inception\",\"normalizedValue\":\"Inception\",\"start\":0,\"end\":9},\n"
            + "       {\"role\":\"YEAR\",\"rawValue\":\"2010\",\"normalizedValue\":\"2010\",\"start\":11,\"end\":15}],\n"
            + "     \"normalizedOrder\":\"\",\n"
            + "     \"confidence\":0.90}\n"
            + "\n"
            + "C — anime with release-tag-only series segment; season + series come from folder:\n"
            + "  Parent folders: Haikyu.S01.MULTi.1080p.BluRay.x264-SHiNiGAMi\n"
            + "  Filename: sgi-hkyu02.1080p.multi.mkv\n"
            + "  Output:\n"
            + "    {\"filename\":\"sgi-hkyu02.1080p.multi.mkv\",\n"
            + "     \"pattern\":\"absolute\",\n"
            + "     \"tokens\":[\n"
            + "       {\"role\":\"SERIES\",\"rawValue\":\"Haikyu\",\"normalizedValue\":\"Haikyu\",\"start\":0,\"end\":0},\n"
            + "       {\"role\":\"SEASON\",\"rawValue\":\"01\",\"normalizedValue\":\"01\",\"start\":0,\"end\":0},\n"
            + "       {\"role\":\"EPISODE\",\"rawValue\":\"02\",\"normalizedValue\":\"02\",\"start\":8,\"end\":10}],\n"
            + "     \"normalizedOrder\":\"S01E02\",\n"
            + "     \"confidence\":0.75}\n"
            + "\n"
            + "Rules (in priority order):\n"
            + "1. Echo the input filename in the \"filename\" field, byte-for-byte.\n"
            + "2. pattern: pick \"SxxExx\", \"NxNN\", \"absolute\", or \"unknown\". Use \"unknown\"\n"
            + "   for movies and any filename without an episode marker.\n"
            + "3. Token offsets are half-open [start,end) byte positions in the filename. If\n"
            + "   you cannot count them with certainty, omit the token.\n"
            + "4. SERIES from parent folder: when the filename's series segment is missing,\n"
            + "   abbreviated, or release-tag-only (e.g. \"sgi-hkyu\", \"sgi\"), take the cleaned\n"
            + "   parent-folder title as the SERIES token. Strip release/resolution/codec/\n"
            + "   language tags first (e.g. \"Haikyu.S01.MULTi.1080p.BluRay.x264-SHiNiGAMi\"\n"
            + "   -> \"Haikyu\"). Set start=0 and end=0 for folder-derived tokens.\n"
            + "5. SEASON: emit a SEASON token whenever normalizedOrder is set. Use real offsets\n"
            + "   if the season digits appear in the filename; use start=0 end=0 with a two-\n"
            + "   digit normalizedValue when the season comes from the folder (\".S0N.\" or\n"
            + "   \"Season N\") or from the absolute-pattern default \"01\".\n"
            + "6. EPISODE for absolute pattern: trailing digits glued to a series shorthand\n"
            + "   (e.g. \"hkyu02\") are an absolute-number EPISODE token. normalizedOrder uses\n"
            + "   season \"01\" unless the parent folder explicitly says otherwise.\n"
            + "7. normalizedOrder: \"SXXEXX\" for series; \"\" for movies; never a title or any\n"
            + "   other text.\n"
            + "8. confidence: 0.0 to 1.0. Lower it when offsets are uncertain or you had to\n"
            + "   guess. Reserve 0.90+ for parses you would bet on.";

    /**
     * Hand-built JSON Schemas mirroring the two system prompts. Sent on every
     * structured call so llama-server can compile them to GBNF and constrain
     * decoding at the token level. The system prompts stay (they teach the
     * model the semantics — which token roles to pick, when to set
     * normalizedOrder, etc.); the schemas merely ensure the output is
     * syntactically usable. {@code minItems:0} on {@code tokens} gives the
     * model an escape hatch when it cannot tag anything sensibly, rather than
     * forcing it to fabricate spans.
     */
    private static final JsonObject GLOBAL_SCHEMA = buildGlobalSchema();
    private static final JsonObject PER_FILE_SCHEMA = buildPerFileSchema();

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
                client, minimizedContext, global.patterns,
                request.parentFolderChain(), request.perFileParentFolderChains(), tick);

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
        List<ChatMessage> messages = List.of(
                ChatMessage.system(GLOBAL_SYSTEM_PROMPT),
                ChatMessage.user(userBuilder.toString()));
        String raw;
        try {
            raw = client.complete("pattern-global", messages, GLOBAL_MAX_TOKENS, GLOBAL_SCHEMA);
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
            List<List<String>> perFileParentFolderChains,
            Runnable tick) {
        int n = filenames.size();
        if (n == 0) {
            return List.of();
        }
        List<AiFilePatternParse> parses = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String filename = filenames.get(i);
            List<String> chain = parentFolderChain;
            if (perFileParentFolderChains != null && i < perFileParentFolderChains.size()) {
                List<String> perFile = perFileParentFolderChains.get(i);
                if (perFile != null && !perFile.isEmpty()) {
                    chain = perFile;
                }
            }
            AiFilePatternParse parse =
                    runSingleFile(client, filename, globalPatterns, chain);
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
        List<ChatMessage> messages = List.of(
                ChatMessage.system(PER_FILE_SYSTEM_PROMPT),
                ChatMessage.user(userBlock.toString()));
        String raw;
        try {
            raw = client.complete("pattern-file", messages, PER_FILE_MAX_TOKENS, PER_FILE_SCHEMA);
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

    private static JsonObject buildGlobalSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.addProperty("additionalProperties", false);

        JsonObject props = new JsonObject();
        props.add("mediaType", stringEnum("series", "movie", "unknown"));
        props.add("confidence", numberRange(0.0, 1.0));
        props.add("patterns", stringArray(0, 3));

        JsonObject explanation = new JsonObject();
        explanation.addProperty("type", "string");
        explanation.addProperty("maxLength", 120);
        props.add("explanation", explanation);

        schema.add("properties", props);
        schema.add("required", stringArrayOf("mediaType", "confidence", "patterns", "explanation"));
        return schema;
    }

    private static JsonObject buildPerFileSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.addProperty("additionalProperties", false);

        JsonObject props = new JsonObject();

        JsonObject filename = new JsonObject();
        filename.addProperty("type", "string");
        props.add("filename", filename);

        props.add("pattern", stringEnum("SxxExx", "NxNN", "absolute", "unknown"));

        // tokens[]: minItems=0 so the model can emit [] when it cannot tag
        // anything; otherwise it would be forced to fabricate spans to satisfy
        // the schema, which is worse than an empty list.
        JsonObject token = new JsonObject();
        token.addProperty("type", "object");
        token.addProperty("additionalProperties", false);
        JsonObject tokenProps = new JsonObject();
        // EXTENSION is intentionally omitted: extensions are handled outside
        // the model from the filesystem, not derived from AI tokens.
        tokenProps.add("role",
                stringEnum("SERIES", "SEASON", "EPISODE", "TITLE", "YEAR", "NOISE"));
        JsonObject str = new JsonObject(); str.addProperty("type", "string");
        tokenProps.add("rawValue", deepCopy(str));
        tokenProps.add("normalizedValue", deepCopy(str));
        JsonObject intGteZero = new JsonObject();
        intGteZero.addProperty("type", "integer");
        intGteZero.addProperty("minimum", 0);
        tokenProps.add("start", deepCopy(intGteZero));
        tokenProps.add("end", deepCopy(intGteZero));
        token.add("properties", tokenProps);
        token.add("required",
                stringArrayOf("role", "rawValue", "normalizedValue", "start", "end"));

        JsonObject tokensArr = new JsonObject();
        tokensArr.addProperty("type", "array");
        tokensArr.addProperty("minItems", 0);
        tokensArr.addProperty("maxItems", 12);
        tokensArr.add("items", token);
        props.add("tokens", tokensArr);

        // normalizedOrder is either "" (movies / unknown) or "SxxExxx".
        JsonObject order = new JsonObject();
        order.addProperty("type", "string");
        order.addProperty("maxLength", 16);
        props.add("normalizedOrder", order);

        props.add("confidence", numberRange(0.0, 1.0));

        schema.add("properties", props);
        schema.add("required",
                stringArrayOf("filename", "pattern", "tokens", "normalizedOrder", "confidence"));
        return schema;
    }

    private static JsonObject stringEnum(String... values) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "string");
        JsonArray arr = new JsonArray();
        for (String v : values) arr.add(v);
        obj.add("enum", arr);
        return obj;
    }

    private static JsonObject numberRange(double min, double max) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "number");
        obj.addProperty("minimum", min);
        obj.addProperty("maximum", max);
        return obj;
    }

    private static JsonObject stringArray(int minItems, int maxItems) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "array");
        obj.addProperty("minItems", minItems);
        obj.addProperty("maxItems", maxItems);
        JsonObject items = new JsonObject();
        items.addProperty("type", "string");
        obj.add("items", items);
        return obj;
    }

    private static JsonArray stringArrayOf(String... values) {
        JsonArray arr = new JsonArray();
        for (String v : values) arr.add(v);
        return arr;
    }

    /** Gson JsonObjects are mutable; deep-copy before reusing one as a sub-schema. */
    private static JsonObject deepCopy(JsonObject source) {
        return JsonParser.parseString(source.toString()).getAsJsonObject();
    }
}
