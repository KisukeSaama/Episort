package com.episort.ai;

import java.util.List;
import java.util.Objects;

/**
 * Single-selected-item context passed to {@link AiContextualAssistant}. Each
 * variant carries only the minimal fields the user actually selected — never
 * the full inventory — so that AI requests stay scoped to the one element the
 * user is asking about.
 */
public sealed interface AiContextualSelection
        permits AiContextualSelection.File,
                AiContextualSelection.Group,
                AiContextualSelection.Match,
                AiContextualSelection.Conflict,
                AiContextualSelection.Ambiguity {

    String displayName();

    record File(String filename) implements AiContextualSelection {
        public File {
            Objects.requireNonNull(filename, "filename");
        }
        @Override
        public String displayName() {
            return filename;
        }
    }

    record Group(String seedName, List<String> filenames) implements AiContextualSelection {
        public Group {
            Objects.requireNonNull(seedName, "seedName");
            filenames = filenames == null ? List.of() : List.copyOf(filenames);
        }
        @Override
        public String displayName() {
            return seedName;
        }
    }

    record Match(String filename, String proposedTitle, double confidence) implements AiContextualSelection {
        public Match {
            Objects.requireNonNull(filename, "filename");
            Objects.requireNonNull(proposedTitle, "proposedTitle");
        }
        @Override
        public String displayName() {
            return filename;
        }
    }

    record Conflict(String filename, String reason) implements AiContextualSelection {
        public Conflict {
            Objects.requireNonNull(filename, "filename");
            reason = reason == null ? "" : reason;
        }
        @Override
        public String displayName() {
            return filename;
        }
    }

    record Ambiguity(String filename, List<String> candidates) implements AiContextualSelection {
        public Ambiguity {
            Objects.requireNonNull(filename, "filename");
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
        @Override
        public String displayName() {
            return filename;
        }
    }
}
