package com.episort.ai;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Bundled in-process contextual assistant. Reuses the
 * {@link AiPatternAssistant} runtime delivered in story 3.3 — there is no
 * second model — and never sends more than the single selected item to it.
 * Output is always advisory.
 */
public final class BundledLocalAiContextualAssistant implements AiContextualAssistant {
    private final AiPatternAssistant patternAssistant;

    public BundledLocalAiContextualAssistant(AiPatternAssistant patternAssistant) {
        this.patternAssistant = Objects.requireNonNull(patternAssistant, "patternAssistant");
    }

    @Override
    public AiExplanation explain(AiContextualRequest request) {
        AiContextualSelection selection = request.selection();
        return switch (selection) {
            case AiContextualSelection.File f -> explainFile(f);
            case AiContextualSelection.Group g -> explainGroup(g);
            case AiContextualSelection.Match m -> explainMatch(m);
            case AiContextualSelection.Conflict c -> explainConflict(c);
            case AiContextualSelection.Ambiguity a -> explainAmbiguity(a);
        };
    }

    private AiExplanation explainFile(AiContextualSelection.File file) {
        AiPatternSuggestion suggestion = askPatternAssistant(List.of(file.filename()));
        String text = "File '" + file.filename() + "': " + suggestion.explanation();
        return AiExplanation.advisory(text, correctionFromPatterns(suggestion));
    }

    private AiExplanation explainGroup(AiContextualSelection.Group group) {
        AiPatternSuggestion suggestion = askPatternAssistant(group.filenames());
        String text = "Group '" + group.seedName() + "' (" + group.filenames().size()
                + " item(s)): " + suggestion.explanation();
        return AiExplanation.advisory(text, correctionFromPatterns(suggestion));
    }

    private AiExplanation explainMatch(AiContextualSelection.Match match) {
        AiPatternSuggestion suggestion = askPatternAssistant(List.of(match.filename()));
        long confidencePercent = Math.round(Math.max(0.0, Math.min(1.0, match.confidence())) * 100);
        String text = "Match for '" + match.filename() + "' proposes '" + match.proposedTitle()
                + "' (confidence " + confidencePercent + "%). " + suggestion.explanation();
        Optional<String> correction = suggestion.suggestedPatterns().isEmpty()
                ? Optional.of("If this match looks wrong, edit the pattern or pick a different TVDB candidate.")
                : Optional.of("Detected pattern hints: " + String.join(", ", suggestion.suggestedPatterns())
                        + ". Adjust the match if these hints disagree with the proposed title.");
        return AiExplanation.advisory(text, correction);
    }

    private AiExplanation explainConflict(AiContextualSelection.Conflict conflict) {
        AiPatternSuggestion suggestion = askPatternAssistant(List.of(conflict.filename()));
        String text = "Conflict on '" + conflict.filename() + "': "
                + (conflict.reason().isBlank() ? "no reason provided" : conflict.reason())
                + ". " + suggestion.explanation();
        return AiExplanation.advisory(
                text,
                Optional.of("Resolve by selecting a different match or adjusting the destination manually."));
    }

    private AiExplanation explainAmbiguity(AiContextualSelection.Ambiguity ambiguity) {
        AiPatternSuggestion suggestion = askPatternAssistant(List.of(ambiguity.filename()));
        String text = "Ambiguity on '" + ambiguity.filename() + "' with "
                + ambiguity.candidates().size() + " candidate(s). " + suggestion.explanation();
        Optional<String> correction = ambiguity.candidates().isEmpty()
                ? Optional.of("Pick a TVDB candidate manually to disambiguate.")
                : Optional.of("Pick one of the proposed candidates: "
                        + String.join(", ", ambiguity.candidates()));
        return AiExplanation.advisory(text, correction);
    }

    private AiPatternSuggestion askPatternAssistant(List<String> filenames) {
        return patternAssistant.suggestPattern(new AiPatternSuggestionRequest(filenames, ""));
    }

    private Optional<String> correctionFromPatterns(AiPatternSuggestion suggestion) {
        if (suggestion.suggestedPatterns().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("Consider following pattern hint(s): "
                + String.join(", ", suggestion.suggestedPatterns()));
    }
}
