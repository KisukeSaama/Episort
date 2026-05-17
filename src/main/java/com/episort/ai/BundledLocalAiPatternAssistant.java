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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pattern assistant backed by the embedded Episort local AI runtime
 * (llama.cpp / Qwen3 4B Instruct 2507). Runs in two passes:
 *
 * <ol>
 *   <li>One small "global" call returning {@code {mediaType, patterns,
 *       confidence, explanation}} — bounded ~150 output tokens, never
 *       truncated even on large groups.</li>
 *   <li>A per-file pass that defaults to batched extraction (N filenames per
 *       LLM call) using a <em>compact</em> JSON schema ({@code {i,p,t,o,c}}
 *       with {@code {r,v,n}} tokens, no filename echo, no per-token offsets).
 *       Character offsets are reconstructed in Java by
 *       {@link BatchTokenOffsetReconstructor} so the model's output budget
 *       stays small (~3x compression vs. the rich schema). Each batch is
 *       validated post-decode: JSON parse OK, array length matches the
 *       batch size, and the {@code i} field of every entry forms a strict
 *       permutation of {@code [1..N]}. Any failure rolls that batch back to
 *       the legacy per-file path (rich schema), which remains intact as the
 *       canonical fallback. A previous attempt at batching with a rich
 *       schema was reverted because output truncation broke the JSON shape;
 *       the new path combines llama-server's strict JSON-schema mode
 *       (compiled to GBNF per call), a compact payload that fits under the
 *       token budget, and the anti-desync validation to make a batch's
 *       failure observable and locally recoverable rather than silent.</li>
 * </ol>
 *
 * <p>An in-memory LRU cache short-circuits repeat extractions when a filename
 * is re-analyzed under the same parent-folder chain and global pattern hints
 * (e.g. the user re-runs the refinement after toggling a UI option).
 *
 * <p>The {@link LlamaServerClient} supplier is consulted on every call so the
 * assistant works across runtime restarts and during the first-run boot
 * window. If the supplier returns empty, we surface an advisory fallback —
 * never an exception — so callers can keep the user-visible flow alive.
 *
 * <p><b>Diagnostic telemetry.</b> Cumulative counters track
 * {@code batchesAttempted}, {@code batchesFallbackTotal} and
 * {@code fallbacksByCause} (keys: {@code parse}, {@code length}, {@code index},
 * {@code schema}, {@code transport}); per-file calls also track
 * {@code perFileCount}, {@code perFileSumChars} and {@code perFileMaxChars}.
 * A summary line is logged at {@code INFO} every {@value #DIAG_LOG_EVERY}
 * events so a session-long fallback rate is visible without enabling
 * {@code FINE}. Per-event diagnostic details (enriched fallback cause with
 * observed lengths, missing indices, malformed fields, and per-file output
 * length) are emitted at {@code FINE} under the logger name
 * {@code com.episort.ai.BundledLocalAiPatternAssistant} — toggle with
 * {@code -Djava.util.logging.ConsoleHandler.level=FINE} plus a matching logger
 * level. The snapshot is also accessible via {@link #telemetrySnapshot()}.
 */
public final class BundledLocalAiPatternAssistant implements AiPatternAssistant {
    private static final Logger LOGGER = Logger.getLogger(BundledLocalAiPatternAssistant.class.getName());

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

    static final String PER_FILE_SYSTEM_PROMPT = ""
            + "Output MUST be MINIFIED JSON: no whitespace between tokens, no newlines, no\n"
            + "indentation. Emit the whole object on a SINGLE line. Pretty-printed output\n"
            + "wastes the response budget and will be rejected.\n"
            + "\n"
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
            + "Examples (each Output line is intentionally on ONE line -- imitate that exact\n"
            + "density):\n"
            + "\n"
            + "Never emit EXTENSION tokens -- the file extension is handled outside the model\n"
            + "from the filesystem; you don't need to tag it.\n"
            + "\n"
            + "A -- vanilla SxxExx series:\n"
            + "  Parent folders: Breaking Bad / Season 01\n"
            + "  Filename: Breaking.Bad.S01E03.Bit.By.A.Dead.Bee.mkv\n"
            + "  Output:\n"
            + "    {\"filename\":\"Breaking.Bad.S01E03.Bit.By.A.Dead.Bee.mkv\",\"pattern\":\"SxxExx\",\"tokens\":[{\"role\":\"SERIES\",\"rawValue\":\"Breaking.Bad\",\"normalizedValue\":\"Breaking Bad\",\"start\":0,\"end\":12},{\"role\":\"SEASON\",\"rawValue\":\"01\",\"normalizedValue\":\"01\",\"start\":14,\"end\":16},{\"role\":\"EPISODE\",\"rawValue\":\"03\",\"normalizedValue\":\"03\",\"start\":17,\"end\":19},{\"role\":\"TITLE\",\"rawValue\":\"Bit.By.A.Dead.Bee\",\"normalizedValue\":\"Bit By A Dead Bee\",\"start\":20,\"end\":37}],\"normalizedOrder\":\"S01E03\",\"confidence\":0.95}\n"
            + "\n"
            + "B -- movie with year tag (no episode marker):\n"
            + "  Parent folders: Movies\n"
            + "  Filename: Inception (2010).mkv\n"
            + "  Output:\n"
            + "    {\"filename\":\"Inception (2010).mkv\",\"pattern\":\"unknown\",\"tokens\":[{\"role\":\"TITLE\",\"rawValue\":\"Inception\",\"normalizedValue\":\"Inception\",\"start\":0,\"end\":9},{\"role\":\"YEAR\",\"rawValue\":\"2010\",\"normalizedValue\":\"2010\",\"start\":11,\"end\":15}],\"normalizedOrder\":\"\",\"confidence\":0.90}\n"
            + "\n"
            + "C -- anime with release-tag-only series segment; season + series come from folder:\n"
            + "  Parent folders: Haikyu.S01.MULTi.1080p.BluRay.x264-SHiNiGAMi\n"
            + "  Filename: sgi-hkyu02.1080p.multi.mkv\n"
            + "  Output:\n"
            + "    {\"filename\":\"sgi-hkyu02.1080p.multi.mkv\",\"pattern\":\"absolute\",\"tokens\":[{\"role\":\"SERIES\",\"rawValue\":\"Haikyu\",\"normalizedValue\":\"Haikyu\",\"start\":0,\"end\":0},{\"role\":\"SEASON\",\"rawValue\":\"01\",\"normalizedValue\":\"01\",\"start\":0,\"end\":0},{\"role\":\"EPISODE\",\"rawValue\":\"02\",\"normalizedValue\":\"02\",\"start\":8,\"end\":10}],\"normalizedOrder\":\"S01E02\",\"confidence\":0.75}\n"
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

    static final String BATCH_SYSTEM_PROMPT = ""
            + "Output MUST be MINIFIED JSON: no whitespace between tokens, no newlines, no\n"
            + "indentation. Emit the whole array as a SINGLE line. Pretty-printed output\n"
            + "wastes the response budget and will be rejected.\n"
            + "\n"
            + "INVARIANT -- token completeness:\n"
            + "Every entry MUST include exactly one SEASON token and exactly one EPISODE\n"
            + "token whenever season/episode can be inferred from either the filename OR\n"
            + "the parent folders. When the value comes from the parent folder only,\n"
            + "use v=\"\" and put the normalized value in n. NEVER omit SEASON because\n"
            + "the filename does not carry it.\n"
            + "  WRONG:  \"t\":[{\"r\":\"SERIES\",...},{\"r\":\"EPISODE\",\"v\":\"09\",\"n\":\"09\"}]\n"
            + "  RIGHT:  \"t\":[{\"r\":\"SERIES\",...},{\"r\":\"SEASON\",\"v\":\"\",\"n\":\"01\"},{\"r\":\"EPISODE\",\"v\":\"09\",\"n\":\"09\"}]\n"
            + "\n"
            + "INVARIANT -- rawValue fidelity:\n"
            + "v MUST be a verbatim substring of the filename, OR \"\" when the token is\n"
            + "folder-derived. NEVER fabricate a v that does not appear in the filename\n"
            + "(e.g. do not write v=\"S01\" if the filename has no \"S01\" -- use v=\"\" instead).\n"
            + "\n"
            + "You parse a NUMBERED BATCH of media filenames into structured tokens. The user\n"
            + "message contains N entries formatted as `[k] filename`. Return a JSON ARRAY of\n"
            + "exactly N objects, one per entry, each carrying its 1-based index in \"i\". The\n"
            + "set of \"i\" values MUST equal {1..N}: no duplicates, no skips. Keep the array\n"
            + "in input order. The schema is enforced.\n"
            + "\n"
            + "Per-entry fields (compact, no offsets):\n"
            + "  i: 1-based index matching the input entry.\n"
            + "  p: pattern enum -- \"SxxExx\", \"NxNN\", \"absolute\", or \"unknown\" (\"unknown\"\n"
            + "     for movies and any filename without an episode marker).\n"
            + "  t: array of {r,v,n} tokens where\n"
            + "       r = role: \"SERIES\", \"SEASON\", \"EPISODE\", \"TITLE\", \"YEAR\", \"NOISE\".\n"
            + "       v = rawValue: the EXACT substring lifted from the filename, OR \"\" when\n"
            + "           the token is derived from the parent folder (SERIES/SEASON when\n"
            + "           the filename does not carry them, e.g. release-tag stems like\n"
            + "           \"sgi-hkyu\"). Never emit EXTENSION tokens. t:[] is acceptable.\n"
            + "       n = normalizedValue: e.g. \"Haikyu\", \"01\", \"03\", \"Bit By A Dead Bee\".\n"
            + "  o: normalizedOrder -- \"SXXEXX\" for series, \"\" otherwise.\n"
            + "  c: confidence 0.0-1.0.\n"
            + "\n"
            + "Folder-derived tokens use v:\"\" with the cleaned title in n. The Java side\n"
            + "reconstructs character offsets from v deterministically; do NOT include any\n"
            + "offsets in your output.\n"
            + "\n"
            + "Example (N=2, parent folder hint shown above each filename). The Output line\n"
            + "below is intentionally on ONE line -- imitate that exact density:\n"
            + "Input:\n"
            + "  Parent folders for [1]: Breaking Bad / Season 01\n"
            + "  Parent folders for [2]: Haikyu.S01.MULTi.1080p.BluRay.x264-SHiNiGAMi\n"
            + "  [1] Breaking.Bad.S01E03.mkv\n"
            + "  [2] sgi-hkyu02.1080p.multi.mkv\n"
            + "Output:\n"
            + "  [{\"i\":1,\"p\":\"SxxExx\",\"t\":[{\"r\":\"SERIES\",\"v\":\"Breaking.Bad\",\"n\":\"Breaking Bad\"},{\"r\":\"SEASON\",\"v\":\"S01\",\"n\":\"01\"},{\"r\":\"EPISODE\",\"v\":\"E03\",\"n\":\"03\"}],\"o\":\"S01E03\",\"c\":0.92},{\"i\":2,\"p\":\"absolute\",\"t\":[{\"r\":\"SERIES\",\"v\":\"\",\"n\":\"Haikyu\"},{\"r\":\"SEASON\",\"v\":\"\",\"n\":\"01\"},{\"r\":\"EPISODE\",\"v\":\"02\",\"n\":\"02\"}],\"o\":\"S01E02\",\"c\":0.75}]";

    /**
     * Hand-built JSON Schemas mirroring the system prompts. Sent on every
     * structured call so llama-server can compile them to GBNF and constrain
     * decoding at the token level. The schemas merely ensure the output is
     * syntactically usable.
     */
    private static final JsonObject GLOBAL_SCHEMA = buildGlobalSchema();
    private static final JsonObject PER_FILE_SCHEMA = buildPerFileSchema();

    private static final int GLOBAL_MAX_TOKENS = 256;
    private static final int PER_FILE_MAX_TOKENS = 384;
    private static final int GLOBAL_SAMPLE_SIZE = 24;

    static final int DEFAULT_BATCH_SIZE = 8;
    static final int MIN_BATCH_SIZE = 1;
    static final int MAX_BATCH_SIZE = 32;
    private static final int CACHE_CAPACITY = 1024;

    /**
     * If the trailing batch produced by greedy splitting carries fewer than (or
     * exactly) this many filenames, fold it into the previous batch instead of
     * issuing a tiny call. The few-shot is tuned for plural batches (N>=2); a
     * leftover of 1 or 2 wastes a round-trip and -- before this absorption was
     * added -- a singleton tail was routed through the legacy per-file path,
     * losing both the compact-schema win and the prompt-cache reuse. With the
     * default batch size of 8 the merged batch tops out at 10 entries (well
     * under {@link #MAX_BATCH_SIZE}); larger configured batch sizes are still
     * clamped at the ceiling defensively in
     * {@link #computeBatchBounds(int, int, int)}.
     */
    static final int BATCH_ABSORB_THRESHOLD = 2;

    /**
     * Emit a summary log every N batch attempts and every N per-file calls so
     * the fallback rate and per-file output verbosity stay diagnosable without
     * the user having to flip the FINE logger on permanently.
     */
    private static final int DIAG_LOG_EVERY = 16;

    /**
     * Per-entry output budget for the batch path. With the compact schema
     * (keys {@code i,p,t,o,c} and tokens {@code {r,v,n}}, no filename echo,
     * no offsets) and the few-shot enforcing MINIFIED output, a 3-token
     * entry is ~35-45 model tokens and a 5-token entry is ~60-70. The
     * generous {@code 80*n+80} envelope keeps slack for long titles AND
     * for the residual whitespace llama-server's JSON-schema GBNF still
     * tolerates (ws is optional in the compiled grammar; we minimize via
     * the prompt, but the ceiling must absorb the model occasionally
     * inserting a few spaces between tokens). A previous {@code 50*n+40}
     * tuning targeted the strictly-minified case and truncated the
     * response mid-array when the model pretty-printed -- restore margin
     * here rather than rely on the prompt being perfectly obeyed.
     */
    static int batchMaxTokens(int batchSize) {
        return 80 * Math.max(1, batchSize) + 80;
    }

    private final Supplier<Optional<LlamaServerClient>> clientSupplier;
    private final int batchSize;
    private final Map<String, AiFilePatternParse> cache;

    // Cumulative telemetry: lifetime of the JVM, never reset.
    private final AtomicInteger batchesAttempted = new AtomicInteger();
    private final AtomicInteger batchesFallbackTotal = new AtomicInteger();
    private final ConcurrentMap<String, AtomicInteger> fallbacksByCause = new ConcurrentHashMap<>();
    private final AtomicInteger perFileCount = new AtomicInteger();
    private final AtomicLong perFileSumChars = new AtomicLong();
    private final AtomicInteger perFileMaxChars = new AtomicInteger();

    public BundledLocalAiPatternAssistant(Supplier<Optional<LlamaServerClient>> clientSupplier) {
        this(clientSupplier, resolveBatchSizeFromEnv());
    }

    public BundledLocalAiPatternAssistant(
            Supplier<Optional<LlamaServerClient>> clientSupplier, int batchSize) {
        this.clientSupplier = Objects.requireNonNull(clientSupplier, "clientSupplier");
        this.batchSize = clampBatchSize(batchSize);
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, AiFilePatternParse> eldest) {
                return size() > CACHE_CAPACITY;
            }
        });
    }

    int batchSize() {
        return batchSize;
    }

    /**
     * Diagnostic snapshot of cumulative batch/per-file telemetry. Exposed for
     * tests and possible status-bar surfacing; not part of the public API.
     */
    record TelemetrySnapshot(
            int batchesAttempted,
            int batchesFallbackTotal,
            Map<String, Integer> fallbacksByCause,
            int perFileCount,
            long perFileSumChars,
            int perFileMaxChars) {
    }

    TelemetrySnapshot telemetrySnapshot() {
        Map<String, Integer> causes = new LinkedHashMap<>();
        for (Map.Entry<String, AtomicInteger> e : fallbacksByCause.entrySet()) {
            causes.put(e.getKey(), e.getValue().get());
        }
        return new TelemetrySnapshot(
                batchesAttempted.get(),
                batchesFallbackTotal.get(),
                causes,
                perFileCount.get(),
                perFileSumChars.get(),
                perFileMaxChars.get());
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
     * filename count assuming batched per-file extraction with the default
     * batch size: one global pass + ceil(filenameCount / batch_size). Used by
     * the progress reporter to size its bar; actual ticks may be fewer when
     * cache hits short-circuit batches.
     */
    public static int promptCountFor(int filenameCount) {
        return promptCountFor(filenameCount, resolveBatchSizeFromEnv());
    }

    static int promptCountFor(int filenameCount, int batchSize) {
        if (filenameCount <= 0) return 0;
        int b = clampBatchSize(batchSize);
        return 1 + computeBatchBounds(filenameCount, b, BATCH_ABSORB_THRESHOLD).length;
    }

    /**
     * Greedy batch splitting with trailing-tail absorption. Returns an array of
     * {@code [start, end)} pairs covering {@code [0, total)}. A trailing batch
     * of size {@code <= absorbThreshold} is folded into the previous one (when
     * one exists), provided the merged size does not exceed
     * {@link #MAX_BATCH_SIZE}. The output is contiguous and in input order.
     *
     * <p>Examples with batchSize=8, threshold=2:
     * <ul>
     *   <li>N=9  -> [(0,9)]           (last=1 -> merged)</li>
     *   <li>N=16 -> [(0,8),(8,16)]    (last=8 -> kept)</li>
     *   <li>N=17 -> [(0,8),(8,17)]    (last=1 -> merged)</li>
     *   <li>N=18 -> [(0,8),(8,18)]    (last=2 -> merged)</li>
     *   <li>N=25 -> [(0,8),(8,16),(16,25)] (last=1 -> merged into 3rd)</li>
     *   <li>N=32 -> [(0,8),(8,16),(16,24),(24,32)] (last=8 -> kept)</li>
     *   <li>N=1  -> [(0,1)]           (no previous, kept as singleton)</li>
     * </ul>
     */
    static int[][] computeBatchBounds(int total, int batchSize, int absorbThreshold) {
        if (total <= 0) return new int[0][];
        int b = clampBatchSize(batchSize);
        int chunks = (total + b - 1) / b;
        int[][] bounds = new int[chunks][2];
        for (int i = 0; i < chunks; i++) {
            bounds[i][0] = i * b;
            bounds[i][1] = Math.min((i + 1) * b, total);
        }
        if (chunks >= 2 && absorbThreshold > 0) {
            int lastSize = bounds[chunks - 1][1] - bounds[chunks - 1][0];
            if (lastSize <= absorbThreshold) {
                int mergedEnd = bounds[chunks - 1][1];
                int prevStart = bounds[chunks - 2][0];
                int mergedSize = mergedEnd - prevStart;
                if (mergedSize <= MAX_BATCH_SIZE) {
                    int[][] merged = new int[chunks - 1][];
                    for (int i = 0; i < chunks - 1; i++) {
                        merged[i] = bounds[i];
                    }
                    merged[chunks - 2] = new int[]{prevStart, mergedEnd};
                    return merged;
                }
            }
        }
        return bounds;
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

        AiFilePatternParse[] result = new AiFilePatternParse[n];
        List<Integer> missOriginalIndices = new ArrayList<>();
        List<String> missFilenames = new ArrayList<>();
        List<List<String>> missChains = new ArrayList<>();
        List<String> missKeys = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            String filename = filenames.get(i);
            List<String> chain = chainFor(i, parentFolderChain, perFileParentFolderChains);
            String key = cacheKey(filename, chain, globalPatterns);
            AiFilePatternParse cached = cache.get(key);
            if (cached != null) {
                result[i] = cached;
                continue;
            }
            missOriginalIndices.add(i);
            missFilenames.add(filename);
            missChains.add(chain);
            missKeys.add(key);
        }

        int missCount = missFilenames.size();
        int[][] bounds = computeBatchBounds(missCount, batchSize, BATCH_ABSORB_THRESHOLD);
        for (int[] bound : bounds) {
            int start = bound[0];
            int end = bound[1];
            List<String> batchFilenames = missFilenames.subList(start, end);
            List<List<String>> batchChains = missChains.subList(start, end);

            List<AiFilePatternParse> batchResults = runBatch(
                    client, batchFilenames, globalPatterns, batchChains, parentFolderChain);
            if (batchResults == null) {
                batchResults = new ArrayList<>(batchFilenames.size());
                for (int i = 0; i < batchFilenames.size(); i++) {
                    batchResults.add(runSingleFile(
                            client, batchFilenames.get(i), globalPatterns, batchChains.get(i)));
                }
            }
            for (int i = 0; i < batchResults.size(); i++) {
                AiFilePatternParse parse = batchResults.get(i);
                int origIdx = missOriginalIndices.get(start + i);
                result[origIdx] = parse;
                if (parse != null) {
                    cache.put(missKeys.get(start + i), parse);
                }
            }
            tick.run();
        }

        List<AiFilePatternParse> out = new ArrayList<>(n);
        for (AiFilePatternParse parse : result) {
            if (parse != null) {
                out.add(parse);
            }
        }
        return out;
    }

    private List<String> chainFor(
            int index, List<String> parentFolderChain, List<List<String>> perFileChains) {
        if (perFileChains != null && index < perFileChains.size()) {
            List<String> perFile = perFileChains.get(index);
            if (perFile != null && !perFile.isEmpty()) {
                return perFile;
            }
        }
        return parentFolderChain == null ? List.of() : parentFolderChain;
    }

    private static String cacheKey(String filename, List<String> chain, List<String> patterns) {
        return filename + ' '
                + (chain == null ? "" : String.join("/", chain)) + ' '
                + (patterns == null ? "" : String.join(",", patterns));
    }

    /**
     * Runs one batched per-file call. Returns the in-order list of parses on
     * success, or {@code null} when validation (parse / length / index set /
     * schema) failed — letting the caller fall back to per-file calls for
     * this batch. The returned list contains exactly {@code batchFilenames.size()}
     * entries, in input order, each non-null.
     */
    private List<AiFilePatternParse> runBatch(
            LlamaServerClient client,
            List<String> batchFilenames,
            List<String> globalPatterns,
            List<List<String>> batchChains,
            List<String> defaultParentChain) {
        int n = batchFilenames.size();
        if (n == 0) {
            return List.of();
        }
        // No n==1 short-circuit: a singleton batch goes through the same
        // compact-schema + minified-output path so the prompt-cache reuse and
        // the absorbed splitting both pay off uniformly. The legacy per-file
        // route remains reachable, but only through the validation fallback
        // below -- never through normal splitting. Removing the shortcut was
        // necessary because singletons produced by a leftover (e.g. 25 files
        // batch=8 -> 8+8+8+1 before absorption) were silently bypassing the
        // batched path and re-introducing the per-file pretty-printed verbosity
        // we paid to remove.
        StringBuilder userBlock = new StringBuilder();
        List<String> sharedChain = collapseChains(batchChains, defaultParentChain);
        List<Optional<String>> perEntryFolderSeasons = new ArrayList<>(n);
        if (!sharedChain.isEmpty()) {
            userBlock.append("Parent folders (outermost to innermost): ")
                    .append(String.join(" / ", sharedChain))
                    .append('\n');
            Optional<String> sharedSeason = extractFolderSeason(sharedChain);
            if (sharedSeason.isPresent()) {
                userBlock.append("Folder-inferred season (use as-is for all entries, v=\"\", n=\"")
                        .append(sharedSeason.get()).append("\"): ")
                        .append(sharedSeason.get()).append('\n');
            }
            for (int i = 0; i < n; i++) {
                perEntryFolderSeasons.add(sharedSeason);
            }
        } else {
            for (int i = 0; i < n; i++) {
                List<String> chain = chainOrDefault(batchChains, i, defaultParentChain);
                if (!chain.isEmpty()) {
                    userBlock.append("Parent folders for [").append(i + 1).append("]: ")
                            .append(String.join(" / ", chain))
                            .append('\n');
                }
                perEntryFolderSeasons.add(extractFolderSeason(chain));
            }
            Optional<String> uniformSeason = uniformSeason(perEntryFolderSeasons);
            if (uniformSeason.isPresent()) {
                userBlock.append("Folder-inferred season (use as-is for all entries, v=\"\", n=\"")
                        .append(uniformSeason.get()).append("\"): ")
                        .append(uniformSeason.get()).append('\n');
            }
        }
        if (!globalPatterns.isEmpty()) {
            userBlock.append("Group hint patterns: ")
                    .append(String.join(", ", globalPatterns))
                    .append('\n');
        }
        userBlock.append("Filenames (N=").append(n).append("):\n");
        for (int i = 0; i < n; i++) {
            userBlock.append('[').append(i + 1).append("] ").append(batchFilenames.get(i)).append('\n');
        }
        userBlock.append("/no_think");

        List<ChatMessage> messages = List.of(
                ChatMessage.system(BATCH_SYSTEM_PROMPT),
                ChatMessage.user(userBlock.toString()));
        int attempt = batchesAttempted.incrementAndGet();
        String raw;
        try {
            raw = client.complete("pattern-batch", messages, batchMaxTokens(n), buildBatchSchema(n));
        } catch (RuntimeException ex) {
            recordFallback("transport", n,
                    "ex=" + ex.getClass().getSimpleName() + ",msg=" + snippet(ex.getMessage(), 0, 120));
            maybeLogBatchSummary(attempt);
            return null;
        }

        BatchParseResult parsed = parseAndValidateBatch(raw, batchFilenames, perEntryFolderSeasons);
        if (parsed.cause != null) {
            recordFallback(parsed.cause, n, parsed.detail);
            maybeLogBatchSummary(attempt);
            return null;
        }
        maybeLogBatchSummary(attempt);
        return parsed.parses;
    }

    private void recordFallback(String cause, int batchSize, String detail) {
        batchesFallbackTotal.incrementAndGet();
        fallbacksByCause.computeIfAbsent(cause, k -> new AtomicInteger()).incrementAndGet();
        LOGGER.log(Level.FINE, () -> "[ai-batch] fallback cause=" + cause + " batch=" + batchSize
                + (detail == null ? "" : " detail=" + detail));
    }

    private void maybeLogBatchSummary(int attempt) {
        if (attempt > 0 && attempt % DIAG_LOG_EVERY == 0) {
            int failed = batchesFallbackTotal.get();
            LOGGER.log(Level.INFO, () -> "[ai-batch] summary attempted=" + attempt
                    + " failed=" + failed + " causes=" + snapshotCauses());
        }
    }

    private void recordPerFileOutput(String raw) {
        int len = raw == null ? 0 : raw.length();
        int count = perFileCount.incrementAndGet();
        perFileSumChars.addAndGet(len);
        perFileMaxChars.accumulateAndGet(len, Math::max);
        LOGGER.log(Level.FINE, () -> "[ai-perfile] outChars=" + len);
        if (count > 0 && count % DIAG_LOG_EVERY == 0) {
            long sum = perFileSumChars.get();
            int max = perFileMaxChars.get();
            long avg = count == 0 ? 0 : sum / count;
            LOGGER.log(Level.INFO, () -> "[ai-perfile] summary calls=" + count
                    + " avgChars=" + avg + " maxChars=" + max);
        }
    }

    private String snapshotCauses() {
        if (fallbacksByCause.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, AtomicInteger> e : fallbacksByCause.entrySet()) {
            if (!first) sb.append(',');
            sb.append(e.getKey()).append('=').append(e.getValue().get());
            first = false;
        }
        return sb.append('}').toString();
    }

    private static List<String> chainOrDefault(
            List<List<String>> batchChains, int index, List<String> defaultParentChain) {
        if (batchChains != null && index < batchChains.size()) {
            List<String> c = batchChains.get(index);
            if (c != null && !c.isEmpty()) {
                return c;
            }
        }
        return defaultParentChain == null ? List.of() : defaultParentChain;
    }

    /**
     * If every batch entry resolves to the same parent-folder chain, returns
     * that chain so the prompt can emit a single shared header (cheaper +
     * matches the per-file behaviour). Otherwise returns an empty list,
     * which signals the caller to emit per-entry chains.
     */
    private static List<String> collapseChains(
            List<List<String>> batchChains, List<String> defaultParentChain) {
        if (batchChains == null || batchChains.isEmpty()) {
            return defaultParentChain == null ? List.of() : defaultParentChain;
        }
        List<String> first = chainOrDefault(batchChains, 0, defaultParentChain);
        for (int i = 1; i < batchChains.size(); i++) {
            List<String> other = chainOrDefault(batchChains, i, defaultParentChain);
            if (!first.equals(other)) {
                return List.of();
            }
        }
        return first;
    }

    private BatchParseResult parseAndValidateBatch(
            String raw, List<String> batchFilenames, List<Optional<String>> perEntryFolderSeasons) {
        int n = batchFilenames.size();
        String trimmed = raw == null ? "" : raw.trim();
        int outLen = trimmed.length();
        int arrStart = trimmed.indexOf('[');
        int arrEnd = trimmed.lastIndexOf(']');
        if (arrStart < 0 || arrEnd <= arrStart) {
            return BatchParseResult.failure("parse",
                    "outLen=" + outLen + ",noArrayBrackets,head=" + snippet(trimmed, 0, 80)
                            + ",tail=" + snippet(trimmed, Math.max(0, outLen - 80), 80));
        }
        JsonArray array;
        try {
            JsonElement element = JsonParser.parseString(trimmed.substring(arrStart, arrEnd + 1));
            if (!element.isJsonArray()) {
                return BatchParseResult.failure("parse",
                        "outLen=" + outLen + ",rootNotArray,head=" + snippet(trimmed, arrStart, 80));
            }
            array = element.getAsJsonArray();
        } catch (JsonSyntaxException | IllegalStateException ex) {
            return BatchParseResult.failure("parse",
                    "outLen=" + outLen + ",jsonSyntax=" + ex.getClass().getSimpleName()
                            + ",tail=" + snippet(trimmed, Math.max(0, arrEnd - 80), 80));
        }
        if (array.size() != n) {
            return BatchParseResult.failure("length", "got=" + array.size() + ",want=" + n
                    + ",outLen=" + outLen);
        }

        AiFilePatternParse[] slots = new AiFilePatternParse[n];
        Set<Integer> seen = new HashSet<>();
        int entryPos = 0;
        for (JsonElement element : array) {
            int entryIdx = entryPos++;
            if (!element.isJsonObject()) {
                return BatchParseResult.failure("schema", "entry=" + entryIdx + ",notObject");
            }
            JsonObject obj = element.getAsJsonObject();
            int i;
            try {
                if (!obj.has("i")) {
                    return BatchParseResult.failure("index",
                            "kind=missingField,entry=" + entryIdx + ",field=i");
                }
                i = obj.get("i").getAsInt();
            } catch (RuntimeException ex) {
                return BatchParseResult.failure("index",
                        "kind=notInt,entry=" + entryIdx);
            }
            if (i < 1 || i > n) {
                return BatchParseResult.failure("index",
                        "kind=outOfRange,entry=" + entryIdx + ",value=" + i + ",range=1.." + n);
            }
            if (!seen.add(i)) {
                return BatchParseResult.failure("index",
                        "kind=duplicate,entry=" + entryIdx + ",value=" + i);
            }
            int slot = i - 1;
            String expectedFilename = batchFilenames.get(slot);
            String missingField = firstMissingRequiredField(obj);
            if (missingField != null) {
                return BatchParseResult.failure("schema",
                        "entry=" + entryIdx + ",i=" + i + ",missingField=" + missingField);
            }
            Optional<String> folderSeason = (perEntryFolderSeasons != null && slot < perEntryFolderSeasons.size())
                    ? perEntryFolderSeasons.get(slot)
                    : Optional.empty();
            slots[slot] = entryToParse(obj, expectedFilename, folderSeason);
            if (slots[slot] == null) {
                return BatchParseResult.failure("schema",
                        "entry=" + entryIdx + ",i=" + i + ",entryToParseReturnedNull");
            }
        }
        if (seen.size() != n) {
            int firstMissing = -1;
            for (int k = 1; k <= n; k++) {
                if (!seen.contains(k)) { firstMissing = k; break; }
            }
            return BatchParseResult.failure("index",
                    "kind=missing,firstMissing=" + firstMissing + ",seen=" + seen.size() + "/" + n);
        }
        return BatchParseResult.success(List.of(slots));
    }

    private static String firstMissingRequiredField(JsonObject obj) {
        if (!obj.has("p")) return "p";
        if (!obj.has("t")) return "t";
        if (!obj.has("o")) return "o";
        if (!obj.has("c")) return "c";
        return null;
    }

    /**
     * Safe substring used in diagnostic logs: clamps length, replaces control
     * characters so a single multiline reply doesn't shred the log line.
     */
    private static String snippet(String s, int from, int max) {
        if (s == null || s.isEmpty() || from >= s.length()) return "";
        int end = Math.min(s.length(), from + max);
        StringBuilder sb = new StringBuilder(end - from);
        for (int i = from; i < end; i++) {
            char c = s.charAt(i);
            if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else if (c == '\t') sb.append("\\t");
            else if (c < 0x20) sb.append('?');
            else sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Builds the rich per-file parse from a compact batch entry. The compact
     * schema omits the {@code filename} echo (re-injected from the input
     * order) and the per-token offsets (rebuilt via
     * {@link BatchTokenOffsetReconstructor}).
     *
     * <p>Two deterministic auto-corrections run before reconstruction:
     * <ol>
     *   <li><b>rawValue fidelity:</b> any token whose {@code v} is not a
     *       verbatim substring of the filename is normalized to {@code v=""}
     *       (folder-derived convention). Defeats hallucinations like the model
     *       emitting {@code v="S01"} on a filename that contains no "S01".</li>
     *   <li><b>SEASON completeness:</b> when an entry has an EPISODE token but
     *       no SEASON token and the season can be inferred (from the folder
     *       hint or from a recognisable {@code o="SxxEyy"} field), a synthetic
     *       SEASON token {@code {r:"SEASON", v:"", n:"<season>"}} is inserted
     *       between SERIES and EPISODE.</li>
     * </ol>
     *
     * <p>If any token was added/changed, {@code o} is recomputed as
     * {@code "S<season>E<episode>"} from the surviving SEASON/EPISODE
     * normalized values so the downstream UI does not show {@code "--"} for
     * the season column.
     */
    private AiFilePatternParse entryToParse(
            JsonObject obj, String expectedFilename, Optional<String> folderSeason) {
        try {
            List<BatchTokenOffsetReconstructor.RawToken> raws = readRawTokens(obj);
            String originalOrder = stringValue(obj, "o");
            Optional<String> knownSeason = folderSeason.isPresent()
                    ? folderSeason
                    : extractSeasonFromOrder(originalOrder);

            CorrectedBatchEntry corrected = autoCorrectBatchEntry(
                    raws, expectedFilename, knownSeason, originalOrder);

            String orderValue = corrected.order == null ? "" : corrected.order;
            Optional<String> normalizedOrder = orderValue.isBlank()
                    ? Optional.empty()
                    : Optional.of(orderValue);

            return new AiFilePatternParse(
                    expectedFilename,
                    stringValue(obj, "p"),
                    BatchTokenOffsetReconstructor.reconstruct(expectedFilename, corrected.tokens),
                    normalizedOrder,
                    optionalDouble(obj, "c"));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private List<BatchTokenOffsetReconstructor.RawToken> readRawTokens(JsonObject entry) {
        if (!entry.has("t") || !entry.get("t").isJsonArray()) {
            return new ArrayList<>();
        }
        List<BatchTokenOffsetReconstructor.RawToken> raws = new ArrayList<>();
        for (JsonElement element : entry.getAsJsonArray("t")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject tokenObj = element.getAsJsonObject();
            try {
                raws.add(new BatchTokenOffsetReconstructor.RawToken(
                        stringValue(tokenObj, "r"),
                        stringValue(tokenObj, "v"),
                        stringValue(tokenObj, "n")));
            } catch (RuntimeException ignored) {
                // Skip malformed tokens, keep the rest of the entry usable.
            }
        }
        return raws;
    }

    /** Output of {@link #autoCorrectBatchEntry}: possibly-rewritten token list and {@code o} field. */
    static final class CorrectedBatchEntry {
        final List<BatchTokenOffsetReconstructor.RawToken> tokens;
        final String order;

        CorrectedBatchEntry(List<BatchTokenOffsetReconstructor.RawToken> tokens, String order) {
            this.tokens = tokens;
            this.order = order;
        }
    }

    /**
     * Applies the two batch auto-corrections (rawValue fidelity, SEASON
     * completeness) and recomputes {@code o} when tokens changed. Pure
     * function: no model calls, no I/O, unit-testable. Logs each correction at
     * FINE so the Debug window can trace which entries needed help.
     */
    static CorrectedBatchEntry autoCorrectBatchEntry(
            List<BatchTokenOffsetReconstructor.RawToken> input,
            String filename,
            Optional<String> knownSeason,
            String originalOrder) {
        List<BatchTokenOffsetReconstructor.RawToken> tokens = new ArrayList<>(input);
        boolean changed = false;
        String safeFilename = filename == null ? "" : filename;

        // (b) Hallucination fix: v must be a verbatim substring of the filename.
        for (int i = 0; i < tokens.size(); i++) {
            BatchTokenOffsetReconstructor.RawToken t = tokens.get(i);
            String v = t.rawValue();
            if (!v.isEmpty() && !safeFilename.contains(v)) {
                LOGGER.log(Level.FINE, () -> "[ai-batch] auto-correct file=" + safeFilename
                        + " token=" + t.role() + " action=stripHallucinatedRawValue v='" + v + "'");
                tokens.set(i, new BatchTokenOffsetReconstructor.RawToken(t.role(), "", t.normalizedValue()));
                changed = true;
            }
        }

        // (a) Missing-SEASON fix: inject a synthetic SEASON when EPISODE is present and a season is known.
        boolean hasSeason = false;
        boolean hasEpisode = false;
        int episodeIdx = -1;
        int firstNonSeriesIdx = -1;
        for (int i = 0; i < tokens.size(); i++) {
            String role = tokens.get(i).role();
            if ("SEASON".equals(role)) hasSeason = true;
            if ("EPISODE".equals(role)) {
                hasEpisode = true;
                if (episodeIdx < 0) episodeIdx = i;
            }
            if (firstNonSeriesIdx < 0 && !"SERIES".equals(role)) {
                firstNonSeriesIdx = i;
            }
        }
        if (!hasSeason && hasEpisode && knownSeason.isPresent()) {
            String season = knownSeason.get();
            int insertAt = firstNonSeriesIdx >= 0 ? firstNonSeriesIdx : tokens.size();
            BatchTokenOffsetReconstructor.RawToken synth =
                    new BatchTokenOffsetReconstructor.RawToken("SEASON", "", season);
            tokens.add(insertAt, synth);
            LOGGER.log(Level.FINE, () -> "[ai-batch] auto-correct file=" + safeFilename
                    + " action=injectSeason season=" + season + " atIndex=" + insertAt);
            changed = true;
        }

        // (c) Recompute o from corrected tokens when anything changed.
        String order = originalOrder == null ? "" : originalOrder;
        if (changed) {
            String season = firstNormalizedFor(tokens, "SEASON");
            String episode = firstNormalizedFor(tokens, "EPISODE");
            if (!season.isEmpty() && !episode.isEmpty()) {
                String recomputed = "S" + season + "E" + episode;
                if (!recomputed.equals(order)) {
                    final String before = order;
                    LOGGER.log(Level.FINE, () -> "[ai-batch] auto-correct file=" + safeFilename
                            + " action=recomputeOrder before='" + before + "' after='" + recomputed + "'");
                    order = recomputed;
                }
            }
        }
        return new CorrectedBatchEntry(tokens, order);
    }

    private static String firstNormalizedFor(
            List<BatchTokenOffsetReconstructor.RawToken> tokens, String role) {
        for (BatchTokenOffsetReconstructor.RawToken t : tokens) {
            if (role.equals(t.role()) && !t.normalizedValue().isEmpty()) {
                return t.normalizedValue();
            }
        }
        return "";
    }

    /**
     * Folder season regex: matches "S01", "S2", "Season 01", "Saison 2",
     * case-insensitive, anchored at non-letter boundaries so we don't trip on
     * arbitrary "S\d" runs inside release tags. Bounded to 1-2 digits so 3+ digit
     * runs (resolutions, group ids) don't masquerade as seasons.
     */
    private static final Pattern FOLDER_SEASON_PATTERN = Pattern.compile(
            "(?i)(?<![A-Za-z])(?:S|(?:Season|Saison)\\s*)(\\d{1,2})(?!\\d)");

    /**
     * Returns the normalized 2-digit season inferred from the parent-folder
     * chain when at least one folder reveals a season AND every season-bearing
     * folder agrees on the same value. Returns {@link Optional#empty()} when
     * nothing matches or when folders disagree -- conservative on purpose so
     * the prompt hint and the auto-correction never lock in a guess.
     */
    static Optional<String> extractFolderSeason(List<String> chain) {
        if (chain == null || chain.isEmpty()) return Optional.empty();
        String found = null;
        for (String segment : chain) {
            if (segment == null || segment.isBlank()) continue;
            Matcher m = FOLDER_SEASON_PATTERN.matcher(segment);
            while (m.find()) {
                String raw = m.group(1);
                String norm = raw.length() == 1 ? "0" + raw : raw;
                if (found == null) {
                    found = norm;
                } else if (!found.equals(norm)) {
                    return Optional.empty();
                }
            }
        }
        return found == null ? Optional.empty() : Optional.of(found);
    }

    /** Returns the uniform season iff every present {@link Optional} agrees. */
    private static Optional<String> uniformSeason(List<Optional<String>> seasons) {
        String found = null;
        for (Optional<String> s : seasons) {
            if (s.isEmpty()) continue;
            if (found == null) {
                found = s.get();
            } else if (!found.equals(s.get())) {
                return Optional.empty();
            }
        }
        return found == null ? Optional.empty() : Optional.of(found);
    }

    private static final Pattern ORDER_SEASON_PATTERN = Pattern.compile("(?i)S(\\d{1,3})E\\d");

    /** Salvages a season from a model-emitted {@code o} like "S01E09". */
    static Optional<String> extractSeasonFromOrder(String order) {
        if (order == null || order.isEmpty()) return Optional.empty();
        Matcher m = ORDER_SEASON_PATTERN.matcher(order);
        if (!m.find()) return Optional.empty();
        String raw = m.group(1);
        String norm = raw.length() == 1 ? "0" + raw : raw;
        return Optional.of(norm);
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
        recordPerFileOutput(raw);
        JsonObject obj = extractObject(raw);
        if (obj == null) {
            return null;
        }
        String returnedName = stringValue(obj, "filename");
        if (returnedName.isBlank()) {
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

    private static int resolveBatchSizeFromEnv() {
        String value = System.getenv("EPISORT_AI_BATCH_SIZE");
        if (value == null || value.isBlank()) {
            return DEFAULT_BATCH_SIZE;
        }
        try {
            return clampBatchSize(Integer.parseInt(value.trim()));
        } catch (NumberFormatException ex) {
            return DEFAULT_BATCH_SIZE;
        }
    }

    private static int clampBatchSize(int requested) {
        if (requested < MIN_BATCH_SIZE) return MIN_BATCH_SIZE;
        if (requested > MAX_BATCH_SIZE) return MAX_BATCH_SIZE;
        return requested;
    }

    private record GlobalEnvelope(
            String explanation,
            List<String> patterns,
            Optional<InventoryGroupType> mediaType,
            OptionalDouble confidence) {
    }

    private record BatchParseResult(List<AiFilePatternParse> parses, String cause, String detail) {
        static BatchParseResult success(List<AiFilePatternParse> parses) {
            return new BatchParseResult(parses, null, null);
        }

        static BatchParseResult failure(String cause, String detail) {
            return new BatchParseResult(null, cause, detail);
        }
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
        JsonObject token = buildTokenSchema();

        JsonObject tokensArr = new JsonObject();
        tokensArr.addProperty("type", "array");
        tokensArr.addProperty("minItems", 0);
        tokensArr.addProperty("maxItems", 12);
        tokensArr.add("items", token);
        props.add("tokens", tokensArr);

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

    /**
     * Compact batch schema, applied per call so llama-server can compile it to
     * GBNF. Each entry carries {@code {i,p,t,o,c}}: no filename echo (the
     * caller injects it from input order via {@code i}) and no per-token
     * offsets (rebuilt in Java by {@link BatchTokenOffsetReconstructor}). The
     * shrink cuts the per-entry output budget by roughly 3x vs. the rich
     * schema, which is what makes 8-file batches fit comfortably under the
     * {@link #batchMaxTokens(int)} ceiling on Qwen3 4B.
     */
    static JsonObject buildBatchSchema(int n) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "object");
        entry.addProperty("additionalProperties", false);

        JsonObject props = new JsonObject();

        JsonObject indexProp = new JsonObject();
        indexProp.addProperty("type", "integer");
        indexProp.addProperty("minimum", 1);
        indexProp.addProperty("maximum", n);
        props.add("i", indexProp);

        props.add("p", stringEnum("SxxExx", "NxNN", "absolute", "unknown"));

        JsonObject token = buildCompactTokenSchema();
        JsonObject tokensArr = new JsonObject();
        tokensArr.addProperty("type", "array");
        tokensArr.addProperty("minItems", 0);
        tokensArr.addProperty("maxItems", 12);
        tokensArr.add("items", token);
        props.add("t", tokensArr);

        JsonObject order = new JsonObject();
        order.addProperty("type", "string");
        order.addProperty("maxLength", 16);
        props.add("o", order);

        props.add("c", numberRange(0.0, 1.0));

        entry.add("properties", props);
        entry.add("required", stringArrayOf("i", "p", "t", "o", "c"));

        JsonObject array = new JsonObject();
        array.addProperty("type", "array");
        array.addProperty("minItems", n);
        array.addProperty("maxItems", n);
        array.add("items", entry);
        return array;
    }

    private static JsonObject buildCompactTokenSchema() {
        JsonObject token = new JsonObject();
        token.addProperty("type", "object");
        token.addProperty("additionalProperties", false);
        JsonObject tokenProps = new JsonObject();
        tokenProps.add("r",
                stringEnum("SERIES", "SEASON", "EPISODE", "TITLE", "YEAR", "NOISE"));
        JsonObject str = new JsonObject(); str.addProperty("type", "string");
        tokenProps.add("v", deepCopy(str));
        tokenProps.add("n", deepCopy(str));
        token.add("properties", tokenProps);
        token.add("required", stringArrayOf("r", "v", "n"));
        return token;
    }

    private static JsonObject buildTokenSchema() {
        JsonObject token = new JsonObject();
        token.addProperty("type", "object");
        token.addProperty("additionalProperties", false);
        JsonObject tokenProps = new JsonObject();
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
        return token;
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
