package com.episort.tmdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.scanner.InventoryGroupType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TmdbCandidateScorerTest {
    private final TmdbCandidateScorer scorer = new TmdbCandidateScorer();

    @Test
    void picksExactTitleEvenWhenTmdbRanksSpinOffFirst() {
        List<TmdbCandidate> pool = List.of(
                series("1", "Breaking Bad: Original Minisodes", 2009, 0),
                series("2", "Breaking Bad", 2008, 1));

        TmdbCandidateScorer.ScoredCandidate best = scorer
                .bestMatch("Breaking Bad", InventoryGroupType.LIKELY_SERIES, pool)
                .orElseThrow();

        assertEquals("2", best.candidate().identity().id());
        assertTrue(best.automatic());
    }

    @Test
    void matchesLocalizedFolderNameThroughAlternateTitles() {
        TmdbCandidate candidate = new TmdbCandidate(
                new TmdbIdentity("7", TmdbMediaType.SERIES, "Game of Thrones"),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(2011), Optional.empty(), Optional.empty(),
                OptionalDoubleScore.empty(), 0,
                List.of("Le Trone de Fer"));

        TmdbCandidateScorer.ScoredCandidate best = scorer
                .bestMatch("Le Trone de Fer", InventoryGroupType.LIKELY_SERIES, List.of(candidate))
                .orElseThrow();

        assertEquals("7", best.candidate().identity().id());
        assertTrue(best.automatic());
    }

    @Test
    void yearInFolderNameSelectsTheRightRemake() {
        List<TmdbCandidate> pool = List.of(
                series("old", "Doctor Who", 1963, 0),
                series("new", "Doctor Who", 2005, 1));

        TmdbCandidateScorer.ScoredCandidate best = scorer
                .bestMatch("Doctor Who 2005", InventoryGroupType.LIKELY_SERIES, pool)
                .orElseThrow();

        assertEquals("new", best.candidate().identity().id());
        assertTrue(best.automatic());
    }

    @Test
    void identicalTitlesWithoutYearHintStayManual() {
        List<TmdbCandidate> pool = List.of(
                series("uk", "The Office", 2001, 0),
                series("us", "The Office", 2005, 1));

        TmdbCandidateScorer.ScoredCandidate best = scorer
                .bestMatch("The Office", InventoryGroupType.LIKELY_SERIES, pool)
                .orElseThrow();

        assertFalse(best.automatic());
        assertTrue(best.suggestible());
    }

    @Test
    void rankingPutsClosestTitleFirst() {
        List<TmdbCandidate> pool = List.of(
                series("a", "Haikyu!! Figure Animation", 2015, 0),
                series("b", "Haikyu!!", 2014, 1));

        List<TmdbCandidate> ranked = scorer.rankedByRelevance(
                "Haikyu", InventoryGroupType.LIKELY_SERIES, pool);

        assertEquals("b", ranked.get(0).identity().id());
    }

    @Test
    void sequelNumberKeepsTheTwoInstalmentsApart() {
        List<TmdbCandidate> pool = List.of(
                movie("first", "My Lucky Stars", 1985, 0, "Le Flic de Hong Kong"),
                movie("second", "Twinkle Twinkle Lucky Stars", 1985, 1, "Le Flic de Hong Kong 2"));

        TmdbCandidateScorer.ScoredCandidate one = scorer
                .bestMatch("Le Flic de Hong Kong 1985", InventoryGroupType.LIKELY_MOVIE, pool)
                .orElseThrow();
        TmdbCandidateScorer.ScoredCandidate two = scorer
                .bestMatch("Le Flic de Hong Kong 2 1985", InventoryGroupType.LIKELY_MOVIE, pool)
                .orElseThrow();

        assertEquals("first", one.candidate().identity().id());
        assertEquals("second", two.candidate().identity().id());
    }

    @Test
    void aLoneWrongInstalmentIsNeverAutomatic() {
        List<TmdbCandidate> pool = List.of(
                movie("first", "My Lucky Stars", 1985, 0, "Le Flic de Hong Kong"));

        TmdbCandidateScorer.ScoredCandidate best = scorer
                .bestMatch("Le Flic de Hong Kong 2 1985", InventoryGroupType.LIKELY_MOVIE, pool)
                .orElseThrow();

        assertFalse(best.automatic());
    }

    @Test
    void aNumberThatBelongsToTheTitleIsNotAnInstalmentMarker() {
        List<TmdbCandidate> pool = List.of(
                movie("br", "Blade Runner 2049", 2017, 0),
                movie("ap", "Apollo 13", 1995, 1));

        assertTrue(scorer.bestMatch("Blade Runner 2049", InventoryGroupType.LIKELY_MOVIE, pool)
                .orElseThrow().automatic());
        assertTrue(scorer.bestMatch("Apollo 13", InventoryGroupType.LIKELY_MOVIE, pool)
                .orElseThrow().automatic());
    }

    private static TmdbCandidate movie(String id, String name, int year, int rank, String... aliases) {
        return new TmdbCandidate(
                new TmdbIdentity(id, TmdbMediaType.MOVIE, name),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(year), Optional.empty(), Optional.empty(),
                OptionalDoubleScore.empty(), rank, List.of(aliases));
    }

    private static TmdbCandidate series(String id, String name, int year, int rank) {
        return new TmdbCandidate(
                new TmdbIdentity(id, TmdbMediaType.SERIES, name),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(year), Optional.empty(), Optional.empty(),
                OptionalDoubleScore.empty(), rank, List.of());
    }
}
