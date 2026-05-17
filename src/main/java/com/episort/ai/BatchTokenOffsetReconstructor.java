package com.episort.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * Reconstructs character offsets {@code [start,end)} for tokens emitted by the
 * batched LLM path. The compact batch schema drops offsets to save output
 * budget; reconstruction is deterministic and unit-testable without invoking
 * the model.
 *
 * <p>Algorithm, applied per token in the order the model returned them:
 * <ol>
 *   <li>An empty {@code rawValue} resolves to {@code (0,0)} — the folder-
 *       derived convention already used by the per-file path for SERIES /
 *       SEASON tokens lifted from the parent folder.</li>
 *   <li>Otherwise, locate {@code rawValue} in the filename starting from a
 *       running cursor. A left-to-right scan handles duplicates such as
 *       {@code "01"} appearing both as season and episode digits.</li>
 *   <li>If not found ahead of the cursor, fall back to a fresh scan from
 *       position 0 (covers the rare case where the model emits tokens out
 *       of file order).</li>
 *   <li>If still not found, settle on {@code (0,0)}. The downstream
 *       consumer ({@link com.episort.ui.scan.ScanInputParse#positionsSummary()})
 *       is display-only; an unanchored token degrades the tooltip but does
 *       not affect rename, matching, or any other logic.</li>
 * </ol>
 */
public final class BatchTokenOffsetReconstructor {
    private BatchTokenOffsetReconstructor() {}

    /** Compact-schema raw token: role + rawValue + normalizedValue (no offsets). */
    public record RawToken(String role, String rawValue, String normalizedValue) {
        public RawToken {
            role = role == null ? "" : role;
            rawValue = rawValue == null ? "" : rawValue;
            normalizedValue = normalizedValue == null ? "" : normalizedValue;
        }
    }

    public static List<AiPatternToken> reconstruct(String filename, List<RawToken> rawTokens) {
        if (rawTokens == null || rawTokens.isEmpty()) {
            return List.of();
        }
        String haystack = filename == null ? "" : filename;
        List<AiPatternToken> out = new ArrayList<>(rawTokens.size());
        int cursor = 0;
        for (RawToken raw : rawTokens) {
            String v = raw.rawValue();
            int start = 0;
            int end = 0;
            if (!v.isEmpty()) {
                int pos = haystack.indexOf(v, cursor);
                if (pos < 0 && cursor > 0) {
                    pos = haystack.indexOf(v);
                }
                if (pos >= 0) {
                    start = pos;
                    end = pos + v.length();
                    cursor = end;
                }
            }
            try {
                out.add(new AiPatternToken(raw.role(), v, raw.normalizedValue(), start, end));
            } catch (IllegalArgumentException ignored) {
                // AiPatternToken validates role nullity and (start,end) shape;
                // we've already normalized both, so this is a belt-and-braces
                // guard against future tightening of the record's invariants.
            }
        }
        return out;
    }
}
