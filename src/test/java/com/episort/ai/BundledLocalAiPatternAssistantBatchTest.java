package com.episort.ai;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.ai.embedded.LlamaServerClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the batched per-file extraction path of
 * {@link BundledLocalAiPatternAssistant}: nominal success, anti-desync
 * fallback (length / index / parse / schema), automatic chunking when the
 * caller exceeds {@code batchSize}, and the LRU cache short-circuit.
 *
 * <p>Each test queues a deterministic sequence of fake llama-server
 * responses: the first reply is consumed by the global pass, then one reply
 * per batch (success or malformed). When the batch path validates, the
 * fallback is not exercised; when it fails, each subsequent reply feeds the
 * per-file fallback for that batch.
 */
class BundledLocalAiPatternAssistantBatchTest {

    private static final String GLOBAL_OK =
            "{\"mediaType\":\"series\",\"confidence\":0.9,\"patterns\":[\"SxxExx\"],"
                    + "\"explanation\":\"S01E0x naming\"}";

    private FakeLlamaServer fake;

    @BeforeEach
    void start() throws Exception {
        fake = FakeLlamaServer.start();
    }

    @AfterEach
    void stop() {
        fake.close();
    }

    private BundledLocalAiPatternAssistant assistant(int batchSize) {
        LlamaServerClient client = fake.client();
        return new BundledLocalAiPatternAssistant(() -> Optional.of(client), batchSize);
    }

    /**
     * Compact batch entry as emitted by the LLM under the new schema:
     * {@code {i,p,t,o,c}} with empty token array. The filename is unused by
     * the parser (re-injected from input order via {@code i}) — it is kept in
     * the helper signature only so the test source reads in parallel with the
     * inputs being asserted.
     */
    private static String batchEntry(int i, @SuppressWarnings("unused") String filename, String order) {
        return "{\"i\":" + i + ",\"p\":\"SxxExx\","
                + "\"t\":[],\"o\":\"" + order + "\",\"c\":0.9}";
    }

    /** Per-file fallback response (rich schema, unchanged). */
    private static String singleEntry(String filename, String order) {
        return "{\"filename\":\"" + filename + "\",\"pattern\":\"SxxExx\","
                + "\"tokens\":[],\"normalizedOrder\":\"" + order + "\",\"confidence\":0.7}";
    }

    @Test
    void nominalBatchKeepsEveryEntryInInputOrder() {
        BundledLocalAiPatternAssistant ai = assistant(4);
        String batch = "[" + batchEntry(1, "Show.S01E01.mkv", "S01E01") + ","
                + batchEntry(2, "Show.S01E02.mkv", "S01E02") + ","
                + batchEntry(3, "Show.S01E03.mkv", "S01E03") + "]";
        fake.setNextContents(GLOBAL_OK, batch);

        AiPatternSuggestion suggestion = ai.suggestPattern(new AiPatternSuggestionRequest(
                List.of("Show.S01E01.mkv", "Show.S01E02.mkv", "Show.S01E03.mkv"), ""));

        assertEquals(3, suggestion.fileParses().size());
        assertEquals("Show.S01E01.mkv", suggestion.fileParses().get(0).filename());
        assertEquals("Show.S01E02.mkv", suggestion.fileParses().get(1).filename());
        assertEquals("Show.S01E03.mkv", suggestion.fileParses().get(2).filename());
        assertEquals("S01E02", suggestion.fileParses().get(1).normalizedOrder().orElseThrow());
    }

    @Test
    void missingEntryInBatchFallsBackToPerFileForThatBatch() {
        BundledLocalAiPatternAssistant ai = assistant(4);
        // Batch returns only 2 entries when 3 were sent → length failure → fallback.
        String malformedBatch = "[" + batchEntry(1, "Show.S01E01.mkv", "S01E01") + ","
                + batchEntry(2, "Show.S01E02.mkv", "S01E02") + "]";
        fake.setNextContents(
                GLOBAL_OK,
                malformedBatch,
                singleEntry("Show.S01E01.mkv", "S01E01"),
                singleEntry("Show.S01E02.mkv", "S01E02"),
                singleEntry("Show.S01E03.mkv", "S01E03"));

        AiPatternSuggestion suggestion = ai.suggestPattern(new AiPatternSuggestionRequest(
                List.of("Show.S01E01.mkv", "Show.S01E02.mkv", "Show.S01E03.mkv"), ""));

        assertEquals(3, suggestion.fileParses().size());
        // Fallback should still produce in-order, valid parses for every file.
        for (AiFilePatternParse parse : suggestion.fileParses()) {
            assertEquals("SxxExx", parse.pattern());
        }
    }

    @Test
    void duplicateIndexInBatchFallsBackToPerFile() {
        BundledLocalAiPatternAssistant ai = assistant(4);
        String dupBatch = "[" + batchEntry(1, "A.mkv", "S01E01") + ","
                + batchEntry(1, "B.mkv", "S01E02") + ","
                + batchEntry(3, "C.mkv", "S01E03") + "]";
        fake.setNextContents(
                GLOBAL_OK, dupBatch,
                singleEntry("A.mkv", "S01E01"),
                singleEntry("B.mkv", "S01E02"),
                singleEntry("C.mkv", "S01E03"));

        AiPatternSuggestion suggestion = ai.suggestPattern(new AiPatternSuggestionRequest(
                List.of("A.mkv", "B.mkv", "C.mkv"), ""));

        assertEquals(3, suggestion.fileParses().size());
        assertEquals("A.mkv", suggestion.fileParses().get(0).filename());
        assertEquals("B.mkv", suggestion.fileParses().get(1).filename());
        assertEquals("C.mkv", suggestion.fileParses().get(2).filename());
    }

    @Test
    void outOfRangeIndexInBatchFallsBackToPerFile() {
        BundledLocalAiPatternAssistant ai = assistant(4);
        String badIdxBatch = "[" + batchEntry(1, "A.mkv", "S01E01") + ","
                + batchEntry(7, "B.mkv", "S01E02") + ","
                + batchEntry(3, "C.mkv", "S01E03") + "]";
        fake.setNextContents(
                GLOBAL_OK, badIdxBatch,
                singleEntry("A.mkv", "S01E01"),
                singleEntry("B.mkv", "S01E02"),
                singleEntry("C.mkv", "S01E03"));

        AiPatternSuggestion suggestion = ai.suggestPattern(new AiPatternSuggestionRequest(
                List.of("A.mkv", "B.mkv", "C.mkv"), ""));

        assertEquals(3, suggestion.fileParses().size());
    }

    @Test
    void malformedJsonBatchFallsBackToPerFile() {
        BundledLocalAiPatternAssistant ai = assistant(4);
        fake.setNextContents(
                GLOBAL_OK,
                "not even close to JSON",
                singleEntry("A.mkv", "S01E01"),
                singleEntry("B.mkv", "S01E02"));

        AiPatternSuggestion suggestion = ai.suggestPattern(new AiPatternSuggestionRequest(
                List.of("A.mkv", "B.mkv"), ""));

        assertEquals(2, suggestion.fileParses().size());
        assertEquals("A.mkv", suggestion.fileParses().get(0).filename());
        assertEquals("B.mkv", suggestion.fileParses().get(1).filename());
    }

    @Test
    void singletonBatchGoesThroughBatchedPathNotPerFile() {
        BundledLocalAiPatternAssistant ai = assistant(4);
        // The n==1 short-circuit was deliberately removed: a singleton batch
        // (e.g. a folder with exactly one file) now uses the compact batched
        // schema like any other batch. The per-file route should only be hit
        // through validation fallback, not via normal splitting.
        String batch = "[" + batchEntry(1, "Show.S01E01.mkv", "S01E01") + "]";
        fake.setNextContents(GLOBAL_OK, batch);

        AiPatternSuggestion suggestion = ai.suggestPattern(new AiPatternSuggestionRequest(
                List.of("Show.S01E01.mkv"), ""));

        assertEquals(1, suggestion.fileParses().size());
        assertEquals("Show.S01E01.mkv", suggestion.fileParses().get(0).filename());
        assertEquals("S01E01", suggestion.fileParses().get(0).normalizedOrder().orElseThrow());
        // 1 global + 1 batched call, no per-file call.
        assertEquals(1, fake.countRequestsContaining("NUMBERED BATCH"));
        assertEquals(0, fake.countRequestsContaining("You parse ONE media filename"));
    }

    @Test
    void listLargerThanBatchSizeSplitsIntoSubBatchesPreservingOrder() {
        BundledLocalAiPatternAssistant ai = assistant(2);
        // 5 files, batch size 2 -> bounds (0,2)(2,4)(4,5); the trailing singleton
        // (size 1 <= BATCH_ABSORB_THRESHOLD) is folded into the previous batch:
        // result = 2 batches of sizes 2 and 3.
        String batchA = "[" + batchEntry(1, "E01.mkv", "S01E01") + ","
                + batchEntry(2, "E02.mkv", "S01E02") + "]";
        // batch B: 3 entries, with out-of-order indices to also exercise re-slotting.
        String batchB = "[" + batchEntry(3, "E05.mkv", "S01E05") + ","
                + batchEntry(1, "E03.mkv", "S01E03") + ","
                + batchEntry(2, "E04.mkv", "S01E04") + "]";
        fake.setNextContents(GLOBAL_OK, batchA, batchB);

        AiPatternSuggestion suggestion = ai.suggestPattern(new AiPatternSuggestionRequest(
                List.of("E01.mkv", "E02.mkv", "E03.mkv", "E04.mkv", "E05.mkv"), ""));

        assertEquals(5, suggestion.fileParses().size());
        List<String> filenamesInOrder = new ArrayList<>();
        for (AiFilePatternParse parse : suggestion.fileParses()) {
            filenamesInOrder.add(parse.filename());
        }
        assertEquals(List.of("E01.mkv", "E02.mkv", "E03.mkv", "E04.mkv", "E05.mkv"),
                filenamesInOrder);
        // Out-of-order indices in batch B re-slotted correctly.
        assertEquals("S01E03", suggestion.fileParses().get(2).normalizedOrder().orElseThrow());
        assertEquals("S01E04", suggestion.fileParses().get(3).normalizedOrder().orElseThrow());
        assertEquals("S01E05", suggestion.fileParses().get(4).normalizedOrder().orElseThrow());
        // Only batched calls, no per-file route hit.
        assertEquals(2, fake.countRequestsContaining("NUMBERED BATCH"));
        assertEquals(0, fake.countRequestsContaining("You parse ONE media filename"));
    }

    @Test
    void cacheHitOnSecondCallSkipsTheBatchPrompt() {
        BundledLocalAiPatternAssistant ai = assistant(4);
        String batch = "[" + batchEntry(1, "Cached.S01E01.mkv", "S01E01") + ","
                + batchEntry(2, "Cached.S01E02.mkv", "S01E02") + "]";
        // First call: global + batch. Second call: global only (per-file pass entirely
        // satisfied by the cache, so no additional /v1/chat/completions request).
        // If the assistant tried to issue a third LLM call it would receive GLOBAL_OK
        // again and the batch parse would fail (envelope, not array), so a per-file
        // fallback would run — and the test would still observe the right output.
        // To assert "cache really hit", we starve the queue and only leave responses
        // that would NOT validate as per-file parses; cache miss would surface as
        // empty fileParses.
        fake.setNextContents(GLOBAL_OK, batch, GLOBAL_OK);

        AiPatternSuggestion first = ai.suggestPattern(new AiPatternSuggestionRequest(
                List.of("Cached.S01E01.mkv", "Cached.S01E02.mkv"), ""));
        assertEquals(2, first.fileParses().size());

        AiPatternSuggestion second = ai.suggestPattern(new AiPatternSuggestionRequest(
                List.of("Cached.S01E01.mkv", "Cached.S01E02.mkv"), ""));

        assertEquals(2, second.fileParses().size());
        // Cached parses preserve filename + order from the first batch.
        assertEquals("S01E01", second.fileParses().get(0).normalizedOrder().orElseThrow());
        assertEquals("S01E02", second.fileParses().get(1).normalizedOrder().orElseThrow());
    }

    @Test
    void partialCacheHitOnlyBatchesTheMisses() {
        BundledLocalAiPatternAssistant ai = assistant(4);
        // Warm the cache with one filename via a singleton batch (now routed
        // through the batched path, not the per-file shortcut).
        String warmupBatch = "[" + batchEntry(1, "Warm.S01E01.mkv", "S01E01") + "]";
        fake.setNextContents(GLOBAL_OK, warmupBatch);
        AiPatternSuggestion warm = ai.suggestPattern(new AiPatternSuggestionRequest(
                List.of("Warm.S01E01.mkv"), ""));
        assertNotNull(warm);
        assertEquals(1, warm.fileParses().size());

        // Now request both: Warm.* is cached, only New.* needs a batch -- a
        // singleton batch that still goes through the batched path.
        String missBatch = "[" + batchEntry(1, "New.S01E02.mkv", "S01E02") + "]";
        fake.setNextContents(GLOBAL_OK, missBatch);

        AiPatternSuggestion suggestion = ai.suggestPattern(new AiPatternSuggestionRequest(
                List.of("Warm.S01E01.mkv", "New.S01E02.mkv"), ""));

        assertEquals(2, suggestion.fileParses().size());
        assertEquals("Warm.S01E01.mkv", suggestion.fileParses().get(0).filename());
        assertEquals("New.S01E02.mkv", suggestion.fileParses().get(1).filename());
        assertEquals("S01E01", suggestion.fileParses().get(0).normalizedOrder().orElseThrow());
        assertEquals("S01E02", suggestion.fileParses().get(1).normalizedOrder().orElseThrow());
    }

    /**
     * Guard-rail: the few-shot in {@link BundledLocalAiPatternAssistant#BATCH_SYSTEM_PROMPT}
     * MUST present its Output example on a SINGLE line. The model imitates the
     * format it sees; a pretty-printed example reintroduces ~30% whitespace
     * inflation in every batch reply and re-triggers truncation on n>=7 batches.
     * If a future edit reformats the example across multiple lines, this test
     * fires.
     */
    @Test
    void batchSystemPromptFewShotOutputIsOnSingleLine() {
        String prompt = BundledLocalAiPatternAssistant.BATCH_SYSTEM_PROMPT;
        int outputAnchor = prompt.lastIndexOf("Output:\n");
        assertTrue(outputAnchor >= 0, "few-shot Output: section missing");
        String afterOutput = prompt.substring(outputAnchor + "Output:\n".length());
        int arrayStart = afterOutput.indexOf('[');
        int arrayEnd = afterOutput.lastIndexOf(']');
        assertTrue(arrayStart >= 0 && arrayEnd > arrayStart, "few-shot array delimiters missing");
        String fewShotJson = afterOutput.substring(arrayStart, arrayEnd + 1);
        assertFalse(fewShotJson.contains("\n"),
                "few-shot Output JSON must be a single line; found newline in:\n" + fewShotJson);
        // Also: no run of >1 space inside the JSON (single leading indent is fine
        // since we slice from '[', not from the line start).
        assertFalse(fewShotJson.contains("  "),
                "few-shot Output JSON must be minified; found double-space in:\n" + fewShotJson);
    }

    /**
     * The on-wire batch reply may arrive pretty-printed even with the strict
     * minification instruction (sampling jitter, GBNF tolerating optional ws).
     * The parser must accept both shapes — otherwise we'd fall back to per-file
     * for a purely cosmetic difference.
     */
    @Test
    void prettyPrintedBatchResponseStillParses() {
        BundledLocalAiPatternAssistant ai = assistant(4);
        String prettyBatch = "[\n"
                + "  {\n"
                + "    \"i\": 1,\n"
                + "    \"p\": \"SxxExx\",\n"
                + "    \"t\": [],\n"
                + "    \"o\": \"S01E01\",\n"
                + "    \"c\": 0.9\n"
                + "  },\n"
                + "  {\n"
                + "    \"i\": 2,\n"
                + "    \"p\": \"SxxExx\",\n"
                + "    \"t\": [],\n"
                + "    \"o\": \"S01E02\",\n"
                + "    \"c\": 0.9\n"
                + "  }\n"
                + "]";
        fake.setNextContents(GLOBAL_OK, prettyBatch);

        AiPatternSuggestion suggestion = ai.suggestPattern(new AiPatternSuggestionRequest(
                List.of("Show.S01E01.mkv", "Show.S01E02.mkv"), ""));

        assertEquals(2, suggestion.fileParses().size());
        assertEquals("Show.S01E01.mkv", suggestion.fileParses().get(0).filename());
        assertEquals("Show.S01E02.mkv", suggestion.fileParses().get(1).filename());
        assertEquals("S01E01", suggestion.fileParses().get(0).normalizedOrder().orElseThrow());
        assertEquals("S01E02", suggestion.fileParses().get(1).normalizedOrder().orElseThrow());
    }

    @Test
    void promptCountForReflectsBatching() {
        // 10 files, batch=4 -> bounds (0,4)(4,8)(8,10). last=2 <= T -> absorbed:
        // 2 batches (sizes 4 and 6). + 1 global = 3 prompts total.
        assertEquals(3, BundledLocalAiPatternAssistant.promptCountFor(10, 4));
        // Empty input -> no prompts.
        assertEquals(0, BundledLocalAiPatternAssistant.promptCountFor(0, 4));
        // Single file -> 1 batch (singleton, no absorption possible) + 1 global.
        assertEquals(2, BundledLocalAiPatternAssistant.promptCountFor(1, 8));
        // batch=0 clamps to 1. 10 chunks of 1; last=1 absorbed into prev (now size 2) -> 9 chunks.
        assertEquals(10, BundledLocalAiPatternAssistant.promptCountFor(10, 0));
    }

    @Test
    void publicSignatureAndOrderingUnchangedAcrossBatchAndFallback() {
        // Mixed scenario: batch A succeeds, batch B fails -> per-file fallback
        // for batch B's entries. Order across both batches must equal input
        // order. Use N=7 batch=4: bounds (0,4)(4,7), last=3 > T=2 -> NOT
        // absorbed, so we keep two distinct batches we can independently
        // succeed/fail. Without that sizing, absorption would coalesce
        // everything into one batch and we'd no longer exercise the per-batch
        // fallback boundary.
        BundledLocalAiPatternAssistant ai = assistant(4);
        String batchA = "[" + batchEntry(1, "F1.mkv", "S01E01") + ","
                + batchEntry(2, "F2.mkv", "S01E02") + ","
                + batchEntry(3, "F3.mkv", "S01E03") + ","
                + batchEntry(4, "F4.mkv", "S01E04") + "]";
        String malformedBatchB = "[" + batchEntry(1, "F5.mkv", "S01E05") + "]"; // length=1 vs n=3
        fake.setNextContents(
                GLOBAL_OK,
                batchA,
                malformedBatchB,
                singleEntry("F5.mkv", "S01E05"),
                singleEntry("F6.mkv", "S01E06"),
                singleEntry("F7.mkv", "S01E07"));

        AiPatternSuggestion suggestion = ai.suggestPattern(new AiPatternSuggestionRequest(
                List.of("F1.mkv", "F2.mkv", "F3.mkv", "F4.mkv", "F5.mkv", "F6.mkv", "F7.mkv"), ""));

        assertTrue(suggestion.advisoryOnly());
        assertFalse(suggestion.validationAuthority());
        assertFalse(suggestion.executionAuthority());
        assertEquals(7, suggestion.fileParses().size());
        assertEquals(List.of("F1.mkv", "F2.mkv", "F3.mkv", "F4.mkv", "F5.mkv", "F6.mkv", "F7.mkv"),
                suggestion.fileParses().stream().map(AiFilePatternParse::filename).toList());
        // Per-file route IS reachable -- but only via validation fallback
        // (batch B's length failure), not via normal splitting.
        assertEquals(2, fake.countRequestsContaining("NUMBERED BATCH"));
        assertEquals(3, fake.countRequestsContaining("You parse ONE media filename"));
    }

    /**
     * Pure-function audit of the absorption logic covering the exact 7 cases
     * agreed in the spec. Locks down the contract that drives
     * {@link BundledLocalAiPatternAssistant#runPerFilePass} so that a future
     * refactor of the splitting cannot silently regress the tail behavior
     * (which was the root cause of the sgi-hkyu25 per-file leak).
     */
    @Test
    void computeBatchBoundsAbsorbsSmallTail() {
        // Format: (expectedSizes, total, batchSize) with threshold = BATCH_ABSORB_THRESHOLD (2).
        assertChunkSizes(new int[]{1}, 1, 8);            // N=1: singleton, no prev -> kept
        assertChunkSizes(new int[]{9}, 9, 8);            // last=1<=2 -> merged into 9
        assertChunkSizes(new int[]{8, 8}, 16, 8);        // last=8 > 2 -> kept
        assertChunkSizes(new int[]{8, 9}, 17, 8);        // last=1<=2 -> merged
        assertChunkSizes(new int[]{8, 10}, 18, 8);       // last=2<=2 -> merged
        assertChunkSizes(new int[]{8, 8, 9}, 25, 8);     // last=1<=2 -> merged into 3rd
        assertChunkSizes(new int[]{8, 8, 8, 8}, 32, 8);  // last=8 > 2 -> kept
        // Edge: empty input -> no chunks.
        assertEquals(0, BundledLocalAiPatternAssistant.computeBatchBounds(
                0, 8, BundledLocalAiPatternAssistant.BATCH_ABSORB_THRESHOLD).length);
        // Edge: merge ceiling. batchSize=32, N=34 -> bounds (0,32)(32,34); last=2<=T,
        // but merged size would be 34 > MAX_BATCH_SIZE=32 -> keep split.
        assertChunkSizes(new int[]{32, 2}, 34, 32);
    }

    private static void assertChunkSizes(int[] expected, int total, int batchSize) {
        int[][] bounds = BundledLocalAiPatternAssistant.computeBatchBounds(
                total, batchSize, BundledLocalAiPatternAssistant.BATCH_ABSORB_THRESHOLD);
        int[] sizes = new int[bounds.length];
        for (int i = 0; i < bounds.length; i++) {
            sizes[i] = bounds[i][1] - bounds[i][0];
        }
        assertArrayEquals(expected, sizes,
                "total=" + total + " batchSize=" + batchSize
                        + " got bounds.length=" + bounds.length);
    }

    /**
     * End-to-end check of the absorbed splitting on the production-shaped
     * sgi-hkyu25 scenario: 25 files at batch=8 must produce exactly 3 batched
     * calls (sizes 8/8/9 after absorbing the singleton tail) and ZERO per-file
     * calls during nominal success.
     */
    @Test
    void absorbedSplittingNeverRoutesToPerFileOnNominalSuccess() {
        BundledLocalAiPatternAssistant ai = assistant(8);
        List<String> files = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            files.add(String.format("Show.S01E%02d.mkv", i));
        }
        fake.setNextContents(GLOBAL_OK,
                buildBatchReply(1, 8),
                buildBatchReply(9, 16),
                buildBatchReply(17, 25));

        AiPatternSuggestion suggestion = ai.suggestPattern(new AiPatternSuggestionRequest(files, ""));

        assertEquals(25, suggestion.fileParses().size());
        for (int i = 0; i < 25; i++) {
            assertEquals(String.format("Show.S01E%02d.mkv", i + 1),
                    suggestion.fileParses().get(i).filename(),
                    "input-order preserved at slot " + i);
        }
        // 1 global + 3 batched calls, NO per-file call.
        assertEquals(1, fake.countRequestsContaining("media filename classifier"),
                "exactly one global call expected");
        assertEquals(3, fake.countRequestsContaining("NUMBERED BATCH"),
                "exactly three batched calls expected (sizes 8/8/9)");
        assertEquals(0, fake.countRequestsContaining("You parse ONE media filename"),
                "no per-file call allowed during nominal batched splitting");
        assertEquals(4, fake.totalRequests());
    }

    private static String buildBatchReply(int firstGlobalIndex, int lastGlobalIndex) {
        StringBuilder sb = new StringBuilder("[");
        for (int g = firstGlobalIndex; g <= lastGlobalIndex; g++) {
            if (g > firstGlobalIndex) sb.append(",");
            int localIdx = g - firstGlobalIndex + 1;
            sb.append("{\"i\":").append(localIdx)
                    .append(",\"p\":\"SxxExx\",\"t\":[],\"o\":\"S01E")
                    .append(String.format("%02d", g))
                    .append("\",\"c\":0.9}");
        }
        return sb.append("]").toString();
    }

    /**
     * Guard-rail mirroring {@link #batchSystemPromptFewShotOutputIsOnSingleLine}:
     * each of the three Output: blocks in {@code PER_FILE_SYSTEM_PROMPT} must
     * present its JSON on a single physical line. The per-file path is the
     * fallback safety net -- a pretty-printed few-shot would re-introduce the
     * exact verbosity tax we are removing on the nominal batched path.
     */
    @Test
    void perFileSystemPromptFewShotOutputsAreOnSingleLine() {
        String prompt = BundledLocalAiPatternAssistant.PER_FILE_SYSTEM_PROMPT;
        String[] parts = prompt.split("Output:\n");
        // parts[0] is the preamble + rules; parts[1..3] are the three example bodies.
        assertEquals(4, parts.length,
                "expected 3 few-shot Output: blocks in PER_FILE_SYSTEM_PROMPT, got "
                        + (parts.length - 1));
        for (int idx = 1; idx < parts.length; idx++) {
            String body = parts[idx];
            int open = body.indexOf('{');
            assertTrue(open >= 0, "few-shot #" + idx + " missing opening brace");
            int close = matchingBrace(body, open);
            assertTrue(close > open,
                    "few-shot #" + idx + " missing matching closing brace");
            String json = body.substring(open, close + 1);
            assertFalse(json.contains("\n"),
                    "few-shot #" + idx + " JSON must be on a single line; found newline in:\n"
                            + json);
        }
    }

    /**
     * Production bug: the model omitted SEASON in batches 2+ of a 25-file
     * Haikyu folder, returning {@code o:"S09"} instead of {@code "S01E09"} so
     * the scan UI displayed "--" for season. With the folder revealing
     * {@code S01}, the Java auto-correction must inject a synthetic SEASON
     * token and recompute {@code o} to {@code "S01E09"}.
     */
    @Test
    void missingSeasonTokenIsInjectedFromFolderHint() {
        BundledLocalAiPatternAssistant ai = assistant(4);
        String batch = "["
                + "{\"i\":1,\"p\":\"absolute\","
                + "\"t\":[{\"r\":\"SERIES\",\"v\":\"\",\"n\":\"Haikyu\"},"
                + "{\"r\":\"EPISODE\",\"v\":\"09\",\"n\":\"09\"}],"
                + "\"o\":\"S09\",\"c\":0.7}"
                + "]";
        fake.setNextContents(GLOBAL_OK, batch);

        AiPatternSuggestion suggestion = ai.suggestPattern(new AiPatternSuggestionRequest(
                List.of("sgi-hkyu09.1080p.multi.mkv"), "",
                List.of("Haikyu.S01.MULTi.1080p.BluRay.x264-SHiNiGAMi")));

        assertEquals(1, suggestion.fileParses().size());
        AiFilePatternParse parse = suggestion.fileParses().get(0);
        long seasons = parse.tokens().stream().filter(t -> "SEASON".equals(t.role())).count();
        assertEquals(1, seasons, "exactly one SEASON token expected after auto-correction");
        AiPatternToken seasonTok = parse.tokens().stream()
                .filter(t -> "SEASON".equals(t.role())).findFirst().orElseThrow();
        assertEquals("", seasonTok.rawValue(), "folder-derived SEASON must use v=\"\"");
        assertEquals("01", seasonTok.normalizedValue());
        assertEquals("S01E09", parse.normalizedOrder().orElseThrow());
    }

    /**
     * Production bug companion: batch 1 of the same Haikyu folder returned
     * {@code v:"S01"} even though the filename {@code sgi-hkyu01.1080p.multi.mkv}
     * contains no "S01" substring. The fidelity correction must rewrite v to
     * the empty string while keeping the normalized value intact.
     */
    @Test
    void hallucinatedRawValueIsStrippedWhenNotASubstring() {
        BundledLocalAiPatternAssistant ai = assistant(4);
        String batch = "["
                + "{\"i\":1,\"p\":\"absolute\","
                + "\"t\":[{\"r\":\"SERIES\",\"v\":\"\",\"n\":\"Haikyu\"},"
                + "{\"r\":\"SEASON\",\"v\":\"S01\",\"n\":\"01\"},"
                + "{\"r\":\"EPISODE\",\"v\":\"01\",\"n\":\"01\"}],"
                + "\"o\":\"S01E01\",\"c\":0.85}"
                + "]";
        fake.setNextContents(GLOBAL_OK, batch);

        AiPatternSuggestion suggestion = ai.suggestPattern(new AiPatternSuggestionRequest(
                List.of("sgi-hkyu01.1080p.multi.mkv"), "",
                List.of("Haikyu.S01.MULTi.1080p.BluRay.x264-SHiNiGAMi")));

        AiFilePatternParse parse = suggestion.fileParses().get(0);
        AiPatternToken seasonTok = parse.tokens().stream()
                .filter(t -> "SEASON".equals(t.role())).findFirst().orElseThrow();
        assertEquals("", seasonTok.rawValue(), "hallucinated 'S01' must be normalized to empty");
        assertEquals("01", seasonTok.normalizedValue());
        assertEquals("S01E01", parse.normalizedOrder().orElseThrow());
    }

    /**
     * When the parent folder carries no season marker the auto-correction
     * stays passive: nothing is injected, the model's output passes through
     * unchanged. Defends against a future regression where the corrector
     * starts guessing "01" out of thin air on movies or unmarked folders.
     */
    @Test
    void folderWithoutSeasonMarkerLeavesEntryUntouched() {
        BundledLocalAiPatternAssistant ai = assistant(4);
        // Model returns a movie-shaped entry: TITLE + YEAR, no SEASON/EPISODE.
        String batch = "["
                + "{\"i\":1,\"p\":\"unknown\","
                + "\"t\":[{\"r\":\"TITLE\",\"v\":\"Inception\",\"n\":\"Inception\"},"
                + "{\"r\":\"YEAR\",\"v\":\"2010\",\"n\":\"2010\"}],"
                + "\"o\":\"\",\"c\":0.9}"
                + "]";
        fake.setNextContents(GLOBAL_OK, batch);

        AiPatternSuggestion suggestion = ai.suggestPattern(new AiPatternSuggestionRequest(
                List.of("Inception (2010).mkv"), "", List.of("Movies")));

        AiFilePatternParse parse = suggestion.fileParses().get(0);
        long seasons = parse.tokens().stream().filter(t -> "SEASON".equals(t.role())).count();
        assertEquals(0, seasons, "no SEASON should be injected for unmarked folders");
        assertTrue(parse.normalizedOrder().isEmpty(), "movie order must stay empty");
    }

    @Test
    void extractFolderSeasonRecognisesSDigitForm() {
        assertEquals("01", BundledLocalAiPatternAssistant.extractFolderSeason(
                List.of("Haikyu.S01.MULTi.1080p.BluRay.x264-SHiNiGAMi")).orElseThrow());
        assertEquals("02", BundledLocalAiPatternAssistant.extractFolderSeason(
                List.of("Show.S2.x264")).orElseThrow());
    }

    @Test
    void extractFolderSeasonRecognisesSeasonWord() {
        assertEquals("02", BundledLocalAiPatternAssistant.extractFolderSeason(
                List.of("Friends", "Season 02")).orElseThrow());
        assertEquals("03", BundledLocalAiPatternAssistant.extractFolderSeason(
                List.of("Friends", "Season 3")).orElseThrow());
        assertEquals("02", BundledLocalAiPatternAssistant.extractFolderSeason(
                List.of("Plus belle la vie", "Saison 2")).orElseThrow());
    }

    @Test
    void extractFolderSeasonRejectsConflictingFolders() {
        // S01 in one folder, Season 02 in another -> ambiguous -> empty.
        assertTrue(BundledLocalAiPatternAssistant.extractFolderSeason(
                List.of("Show.S01.x264", "Season 02")).isEmpty());
    }

    @Test
    void extractFolderSeasonReturnsEmptyOnUnmarkedFolders() {
        assertTrue(BundledLocalAiPatternAssistant.extractFolderSeason(
                List.of("Movies", "BluRay Rips")).isEmpty());
        assertTrue(BundledLocalAiPatternAssistant.extractFolderSeason(List.of()).isEmpty());
        assertTrue(BundledLocalAiPatternAssistant.extractFolderSeason(null).isEmpty());
    }

    @Test
    void extractFolderSeasonIgnoresFalsePositivesFromReleaseTags() {
        // "1080p" / "x264" / "x265" must not be parsed as a season.
        assertTrue(BundledLocalAiPatternAssistant.extractFolderSeason(
                List.of("Movies.1080p.x264")).isEmpty());
        // "VOSTFR" contains "S" but it's preceded by a letter -> rejected.
        assertTrue(BundledLocalAiPatternAssistant.extractFolderSeason(
                List.of("Anime.VOSTFR.1080p")).isEmpty());
    }

    @Test
    void extractSeasonFromOrderSalvagesSxxExxOnly() {
        assertEquals("01", BundledLocalAiPatternAssistant.extractSeasonFromOrder("S01E09").orElseThrow());
        assertEquals("03", BundledLocalAiPatternAssistant.extractSeasonFromOrder("s3e10").orElseThrow());
        // No E digit -> not salvageable (matches the production bug pattern "S09").
        assertTrue(BundledLocalAiPatternAssistant.extractSeasonFromOrder("S09").isEmpty());
        assertTrue(BundledLocalAiPatternAssistant.extractSeasonFromOrder("").isEmpty());
        assertTrue(BundledLocalAiPatternAssistant.extractSeasonFromOrder(null).isEmpty());
    }

    /**
     * Pure-function audit of the per-entry correction without spinning up the
     * fake server, so future regressions surface immediately at the seam where
     * the fix lives.
     */
    @Test
    void autoCorrectBatchEntryInjectsSeasonAndRecomputesOrder() {
        List<BatchTokenOffsetReconstructor.RawToken> input = List.of(
                new BatchTokenOffsetReconstructor.RawToken("SERIES", "", "Haikyu"),
                new BatchTokenOffsetReconstructor.RawToken("EPISODE", "09", "09"));
        BundledLocalAiPatternAssistant.CorrectedBatchEntry out =
                BundledLocalAiPatternAssistant.autoCorrectBatchEntry(
                        input, "sgi-hkyu09.1080p.multi.mkv", Optional.of("01"), "S09");
        assertEquals(3, out.tokens.size());
        assertEquals("SEASON", out.tokens.get(1).role());
        assertEquals("", out.tokens.get(1).rawValue());
        assertEquals("01", out.tokens.get(1).normalizedValue());
        assertEquals("S01E09", out.order);
    }

    @Test
    void autoCorrectBatchEntryUsesSeasonSalvagedFromOrderField() {
        // No folder hint, but caller fell back to extractSeasonFromOrder("S01E09")
        // before invoking the corrector -- mirrors how entryToParse threads the
        // two seasons together.
        Optional<String> salvaged = BundledLocalAiPatternAssistant.extractSeasonFromOrder("S01E09");
        List<BatchTokenOffsetReconstructor.RawToken> input = List.of(
                new BatchTokenOffsetReconstructor.RawToken("SERIES", "Show", "Show"),
                new BatchTokenOffsetReconstructor.RawToken("EPISODE", "E09", "09"));
        BundledLocalAiPatternAssistant.CorrectedBatchEntry out =
                BundledLocalAiPatternAssistant.autoCorrectBatchEntry(
                        input, "Show.E09.mkv", salvaged, "S01E09");
        assertEquals(3, out.tokens.size());
        assertEquals("SEASON", out.tokens.get(1).role());
        assertEquals("01", out.tokens.get(1).normalizedValue());
        assertEquals("S01E09", out.order);
    }

    @Test
    void autoCorrectBatchEntryStripsHallucinationsWithoutTouchingValidTokens() {
        List<BatchTokenOffsetReconstructor.RawToken> input = List.of(
                new BatchTokenOffsetReconstructor.RawToken("SERIES", "", "Haikyu"),
                new BatchTokenOffsetReconstructor.RawToken("SEASON", "S01", "01"),
                new BatchTokenOffsetReconstructor.RawToken("EPISODE", "01", "01"));
        BundledLocalAiPatternAssistant.CorrectedBatchEntry out =
                BundledLocalAiPatternAssistant.autoCorrectBatchEntry(
                        input, "sgi-hkyu01.1080p.multi.mkv", Optional.of("01"), "S01E01");
        assertEquals(3, out.tokens.size());
        assertEquals("", out.tokens.get(1).rawValue(), "S01 not in filename -> v=\"\"");
        assertEquals("01", out.tokens.get(2).rawValue(), "real substring '01' kept verbatim");
        assertEquals("S01E01", out.order);
    }

    @Test
    void autoCorrectBatchEntryNoOpWhenNothingNeedsFixing() {
        List<BatchTokenOffsetReconstructor.RawToken> input = List.of(
                new BatchTokenOffsetReconstructor.RawToken("SERIES", "Show", "Show"),
                new BatchTokenOffsetReconstructor.RawToken("SEASON", "S01", "01"),
                new BatchTokenOffsetReconstructor.RawToken("EPISODE", "E03", "03"));
        BundledLocalAiPatternAssistant.CorrectedBatchEntry out =
                BundledLocalAiPatternAssistant.autoCorrectBatchEntry(
                        input, "Show.S01E03.mkv", Optional.of("01"), "S01E03");
        assertEquals(input, out.tokens);
        assertEquals("S01E03", out.order);
    }

    /** Brace-matching aware of string escapes; used by the prompt guard-rail. */
    private static int matchingBrace(String s, int openIdx) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (inString) {
                if (c == '\\') escape = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') inString = true;
            else if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }
}
