package com.episort.tvdb;

import com.episort.ai.AiPatternAssistant;
import com.episort.ai.AiPatternSuggestion;
import com.episort.ai.AiPatternSuggestionRequest;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class AiTvdbCandidateRanker {
    private final AiPatternAssistant assistant;

    public AiTvdbCandidateRanker(AiPatternAssistant assistant) {
        this.assistant = Objects.requireNonNull(assistant, "assistant");
    }

    public List<TvdbCandidate> rankAdvisory(String originalQuery, List<TvdbCandidate> candidates) {
        List<TvdbCandidate> safeCandidates = candidates == null ? List.of() : List.copyOf(candidates);
        if (safeCandidates.isEmpty()) {
            return List.of();
        }
        AiPatternSuggestion suggestion = assistant.suggestPattern(new AiPatternSuggestionRequest(
                safeCandidates.stream().map(candidate -> candidate.identity().displayName()).toList(),
                originalQuery == null ? "" : originalQuery));
        String proposed = suggestion == null || suggestion.suggestedPatterns().isEmpty()
                ? ""
                : suggestion.suggestedPatterns().get(0).toLowerCase(java.util.Locale.ROOT);
        return safeCandidates.stream()
                .map(candidate -> candidate.withAdvisoryAiScore(score(candidate, proposed)))
                .sorted(Comparator
                        .comparing((TvdbCandidate candidate) -> candidate.advisoryAiScore().value()).reversed()
                        .thenComparing(TvdbCandidate::tvdbRank))
                .toList();
    }

    private double score(TvdbCandidate candidate, String proposed) {
        if (proposed.isBlank()) {
            return 0.5;
        }
        String name = candidate.identity().displayName().toLowerCase(java.util.Locale.ROOT);
        return proposed.contains(name) || name.contains(proposed) ? 0.95 : 0.35;
    }
}
