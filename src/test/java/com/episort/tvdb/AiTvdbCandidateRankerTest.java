package com.episort.tvdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.ai.AiPatternSuggestion;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiTvdbCandidateRankerTest {
    @Test
    void advisoryAiRankingReordersButKeepsOriginalTvdbRank() {
        AiTvdbCandidateRanker ranker = new AiTvdbCandidateRanker(request ->
                AiPatternSuggestion.advisory("advisory", List.of("Candidate B"), request.minimizedSelectedItemContext()));
        TvdbCandidate first = candidate("1", "Candidate A", 0);
        TvdbCandidate second = candidate("2", "Candidate B", 1);

        List<TvdbCandidate> ranked = ranker.rankAdvisory("Candidate", List.of(first, second));

        assertEquals("2", ranked.get(0).identity().id());
        assertEquals(1, ranked.get(0).tvdbRank());
        assertTrue(ranked.get(0).advisoryAiScore().present());
    }

    private static TvdbCandidate candidate(String id, String name, int rank) {
        return new TvdbCandidate(
                new TvdbIdentity(id, TvdbMediaType.SERIES, name),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                OptionalDoubleScore.empty(),
                rank);
    }
}
